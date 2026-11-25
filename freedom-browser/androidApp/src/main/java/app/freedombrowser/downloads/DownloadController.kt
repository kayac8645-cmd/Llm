package app.freedombrowser.downloads

import android.util.Base64
import app.freedombrowser.data.DownloadDao
import app.freedombrowser.data.DownloadEntity
import app.freedombrowser.data.DownloadStatus
import app.freedombrowser.shared.FileNames
import app.freedombrowser.shared.SpeedMeter
import app.freedombrowser.util.Diagnostics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

data class DownloadRequest(
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val userAgent: String?,
    val referer: String?,
    val isPrivate: Boolean,
    val targetTree: String?,
)

/**
 * Freedom's download engine: HTTP(S) via OkHttp with Range-based resume when the server supports
 * it, plus `data:` URLs. Cookies are fetched on demand through [cookieProvider] and never stored.
 */
class DownloadController(
    private val dao: DownloadDao,
    private val http: OkHttpClient,
    private val scope: CoroutineScope,
    private val targetFor: (DownloadEntity) -> DownloadTarget,
    private val cookieProvider: (url: String, isPrivate: Boolean) -> String?,
    private val clock: () -> Long = System::currentTimeMillis,
    maxParallel: Int = 3,
) {
    private val jobs = ConcurrentHashMap<Long, Job>()
    private val permits = Semaphore(maxParallel)
    private val _speeds = MutableStateFlow<Map<Long, Long>>(emptyMap())
    /** Current bytes/second per running download id. */
    val speeds: StateFlow<Map<Long, Long>> = _speeds
    private val _activeCount = MutableStateFlow(0)
    val activeCount: StateFlow<Int> = _activeCount

    val downloads get() = dao.all()

    /** Marks downloads that were running when the process died as paused/failed. */
    suspend fun recoverAfterRestart() {
        for (d in dao.active()) {
            dao.update(
                if (d.supportsResume && d.contentUri != null) d.copy(status = DownloadStatus.PAUSED)
                else d.copy(status = DownloadStatus.FAILED, error = "Interrupted"),
            )
        }
    }

    suspend fun enqueue(request: DownloadRequest): Long {
        val entity = DownloadEntity(
            url = request.url,
            fileName = FileNames.sanitize(request.fileName),
            mimeType = request.mimeType,
            userAgent = request.userAgent,
            referer = request.referer,
            isPrivate = request.isPrivate,
            targetTree = request.targetTree,
            createdAt = clock(),
        )
        val id = dao.insert(entity)
        start(id)
        return id
    }

    fun pause(id: Long) = scope.launch {
        val d = dao.get(id) ?: return@launch
        if (!d.supportsResume) return@launch // pausing is offered only when the server supports resume
        jobs.remove(id)?.cancelAndJoin()
        dao.update(dao.get(id)!!.copy(status = DownloadStatus.PAUSED))
    }

    fun resume(id: Long) = start(id)

    fun retry(id: Long) = scope.launch {
        val d = dao.get(id) ?: return@launch
        if (!d.supportsResume && d.contentUri != null) {
            // Restart from zero, reusing the same file.
            dao.update(d.copy(downloadedBytes = 0, error = null))
        }
        start(id)
    }

    fun cancel(id: Long) = scope.launch {
        jobs.remove(id)?.cancelAndJoin()
        val d = dao.get(id) ?: return@launch
        d.contentUri?.let { runCatching { targetFor(d).delete(it) } }
        dao.update(d.copy(status = DownloadStatus.CANCELLED, contentUri = null, downloadedBytes = 0, error = null))
    }

    fun remove(id: Long, deleteFile: Boolean) = scope.launch {
        jobs.remove(id)?.cancelAndJoin()
        val d = dao.get(id) ?: return@launch
        if (deleteFile || d.status != DownloadStatus.COMPLETED) d.contentUri?.let { runCatching { targetFor(d).delete(it) } }
        dao.delete(id)
    }

    fun clearFinished() = scope.launch { dao.clearFinished() }

    /** Private-session downloads stay on disk, but disappear from the list when the session ends. */
    fun onPrivateSessionEnded() = scope.launch { dao.clearPrivateFinished() }

    private fun start(id: Long): Job {
        jobs[id]?.let { if (it.isActive) return it }
        val job = scope.launch {
            dao.get(id)?.let { dao.update(it.copy(status = DownloadStatus.QUEUED, error = null)) }
            _activeCount.update { it + 1 }
            try {
                permits.withPermit { execute(id) }
            } finally {
                _activeCount.update { it - 1 }
                _speeds.update { it - id }
                jobs.remove(id)
            }
        }
        jobs[id] = job
        return job
    }

    private suspend fun execute(id: Long) {
        var d = dao.get(id) ?: return
        d = d.copy(status = DownloadStatus.RUNNING)
        dao.update(d)
        val target = targetFor(d)
        try {
            if (d.contentUri == null) {
                val (handle, name) = target.create(d.fileName, d.mimeType)
                d = d.copy(contentUri = handle, fileName = name, downloadedBytes = 0)
                dao.update(d)
            }
            d = if (d.url.startsWith("data:", ignoreCase = true)) writeDataUrl(d, target) else transfer(d, target)
            target.finish(d.contentUri!!)
            dao.update(d.copy(status = DownloadStatus.COMPLETED, completedAt = clock(), error = null))
            Diagnostics.log("download.completed", d.url)
        } catch (e: CancellationException) {
            withContext(NonCancellable) { dao.get(id)?.let { cur -> if (cur.status == DownloadStatus.RUNNING) dao.update(cur.copy(status = DownloadStatus.PAUSED)) } }
            throw e
        } catch (e: Exception) {
            if (!currentCoroutineContext().isActive) {
                // The socket was closed because the user paused/cancelled: not a failure.
                withContext(NonCancellable) { dao.get(id)?.let { cur -> if (cur.status == DownloadStatus.RUNNING) dao.update(cur.copy(status = DownloadStatus.PAUSED)) } }
                throw CancellationException("Stopped")
            }
            Diagnostics.log("download.failed", d.url, e.javaClass.simpleName)
            val cur = dao.get(id) ?: d
            dao.update(cur.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName))
        }
    }

    private fun writeDataUrl(d: DownloadEntity, target: DownloadTarget): DownloadEntity {
        val comma = d.url.indexOf(',')
        if (comma < 0) throw IOException("Malformed data URL")
        val meta = d.url.substring(5, comma)
        val payload = d.url.substring(comma + 1)
        val bytes = if (meta.endsWith(";base64", ignoreCase = true)) Base64.decode(payload, Base64.DEFAULT)
        else app.freedombrowser.shared.UrlCodec.decode(payload).toByteArray()
        target.open(d.contentUri!!, append = false).use { it.write(bytes) }
        return d.copy(downloadedBytes = bytes.size.toLong(), totalBytes = bytes.size.toLong())
    }

    private suspend fun transfer(start: DownloadEntity, target: DownloadTarget): DownloadEntity {
        var d = start
        val resumeFrom = if (d.supportsResume && d.downloadedBytes > 0) d.downloadedBytes else 0L
        val builder = Request.Builder().url(d.url)
        d.userAgent?.let { builder.header("User-Agent", it) }
        d.referer?.let { builder.header("Referer", it) }
        cookieProvider(d.url, d.isPrivate)?.takeIf { it.isNotBlank() }?.let { builder.header("Cookie", it) }
        if (resumeFrom > 0) {
            builder.header("Range", "bytes=$resumeFrom-")
            d.etag?.let { builder.header("If-Range", it) }
        }
        val call = http.newCall(builder.build())
        return coroutineScope {
        // Closes the socket as soon as this coroutine is cancelled so blocking reads return.
        val watcher = launch { try { awaitCancellation() } finally { call.cancel() } }
        try {
            call.execute().use { response ->
                if (response.code == 416 && d.totalBytes > 0 && d.downloadedBytes >= d.totalBytes) return@coroutineScope d
                if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
                val partial = response.code == 206 && resumeFrom > 0
                val acceptsRanges = response.header("Accept-Ranges")?.contains("bytes", true) == true || response.code == 206
                val etag = response.header("ETag") ?: response.header("Last-Modified")
                val bodyLength = response.body.contentLength()
                val total = when {
                    partial -> response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull() ?: (if (bodyLength >= 0) resumeFrom + bodyLength else -1)
                    else -> bodyLength
                }
                var written = if (partial) resumeFrom else 0L
                d = d.copy(totalBytes = total, downloadedBytes = written, supportsResume = acceptsRanges, etag = etag,
                    mimeType = d.mimeType ?: response.header("Content-Type")?.substringBefore(';'))
                dao.update(d)
                val meter = SpeedMeter()
                meter.update(written, clock())
                var lastReport = clock()
                try {
                    target.open(d.contentUri!!, append = partial).use { out ->
                        val source = response.body.source()
                        val buffer = ByteArray(64 * 1024)
                        while (currentCoroutineContext().isActive) {
                            val n = source.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            written += n
                            val now = clock()
                            if (now - lastReport >= 500) {
                                lastReport = now
                                val speed = meter.update(written, now)
                                _speeds.update { it + (d.id to speed) }
                                dao.progress(d.id, written, total)
                            }
                        }
                        out.flush()
                    }
                } finally {
                    // Persist the exact byte count on pause/failure so a resume appends at the right offset.
                    withContext(NonCancellable) { dao.progress(d.id, written, total) }
                }
                if (!currentCoroutineContext().isActive) throw CancellationException()
                if (total >= 0 && written < total) throw IOException("Connection closed early")
                d.copy(downloadedBytes = written, totalBytes = if (total >= 0) total else written)
            }
        } finally {
            watcher.cancel()
        }
        }
    }
}
