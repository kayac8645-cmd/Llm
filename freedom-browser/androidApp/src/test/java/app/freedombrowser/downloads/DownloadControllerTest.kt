package app.freedombrowser.downloads

import app.freedombrowser.data.DownloadDao
import app.freedombrowser.data.DownloadEntity
import app.freedombrowser.data.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.concurrent.TimeUnit

/** In-memory DAO so the engine can be tested without Room. */
class FakeDownloadDao : DownloadDao {
    private val rows = MutableStateFlow<Map<Long, DownloadEntity>>(emptyMap())
    private val nextId = java.util.concurrent.atomic.AtomicLong(1)
    private val finished = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)
    override fun all(): Flow<List<DownloadEntity>> = rows.map { it.values.toList() }
    override suspend fun get(id: Long) = rows.value[id]
    override suspend fun active() = rows.value.values.filter { it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.RUNNING }
    override suspend fun insert(entity: DownloadEntity): Long { val id = nextId.getAndIncrement(); rows.update { it + (id to entity.copy(id = id)) }; return id }
    override suspend fun update(entity: DownloadEntity) { rows.update { it + (entity.id to entity) } }
    override suspend fun progress(id: Long, bytes: Long, total: Long) { rows.update { m -> m[id]?.let { m + (id to it.copy(downloadedBytes = bytes, totalBytes = total)) } ?: m } }
    override suspend fun delete(id: Long) { rows.update { it - id } }
    override suspend fun clearFinished() { rows.update { m -> m.filterValues { it.status !in finished } } }
    override suspend fun clearPrivateFinished() { rows.update { m -> m.filterValues { !(it.isPrivate && it.status in finished) } } }
}

@RunWith(RobolectricTestRunner::class)
class DownloadControllerTest {
    @get:Rule val tmp = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var scope: CoroutineScope
    private val dao = FakeDownloadDao()
    private lateinit var dir: File
    private var cookieRequests = 0

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dir = tmp.newFolder("dl")
    }

    @After fun tearDown() {
        scope.cancel()
        server.close()
    }

    private fun controller() = DownloadController(dao, OkHttpClient(), scope, { FileTarget(dir) }, { _, _ -> cookieRequests++; "a=b" })

    private fun request(path: String, name: String = "file.bin") =
        DownloadRequest(server.url(path).toString(), name, null, "UA", null, isPrivate = false, targetTree = null)

    private fun awaitStatus(id: Long, vararg statuses: DownloadStatus, timeoutMs: Long = 10_000): DownloadEntity = runBlocking {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            val d = dao.get(id)
            if (d != null && d.status in statuses) return@runBlocking d
            Thread.sleep(20)
        }
        error("Timed out; last state ${dao.get(id)}")
    }

    private fun bytes(n: Int) = ByteArray(n) { (it % 251).toByte() }

    @Test fun completesDownloadAndSendsCookiesAndUserAgent() {
        val payload = bytes(200_000)
        server.enqueue(MockResponse.Builder().body(Buffer().write(payload)).build())
        val c = controller()
        val id = runBlocking { c.enqueue(request("/a.bin")) }
        val d = awaitStatus(id, DownloadStatus.COMPLETED, DownloadStatus.FAILED)
        assertEquals(DownloadStatus.COMPLETED, d.status)
        assertArrayEquals(payload, File(dir, "file.bin").readBytes())
        assertEquals(200_000L, d.totalBytes)
        val recorded = server.takeRequest()
        assertEquals("a=b", recorded.headers["Cookie"])
        assertEquals("UA", recorded.headers["User-Agent"])
        assertTrue(cookieRequests > 0)
    }

    @Test fun duplicateNamesGetSuffix() {
        File(dir, "file.bin").writeText("existing")
        server.enqueue(MockResponse.Builder().body("new").build())
        val c = controller()
        val id = runBlocking { c.enqueue(request("/b")) }
        val d = awaitStatus(id, DownloadStatus.COMPLETED)
        assertEquals("file (1).bin", d.fileName)
        assertEquals("existing", File(dir, "file.bin").readText())
    }

    @Test fun pauseAndResumeUsesRangeRequest() {
        val payload = bytes(600_000)
        // First response is throttled so we can pause mid-transfer.
        server.enqueue(
            MockResponse.Builder().body(Buffer().write(payload)).addHeader("Accept-Ranges", "bytes").addHeader("ETag", "\"v1\"")
                .throttleBody(32 * 1024, 100, TimeUnit.MILLISECONDS).build(),
        )
        val c = controller()
        val id = runBlocking { c.enqueue(request("/big")) }
        // Wait until some bytes arrived and resume support is known.
        runBlocking {
            val end = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < end) {
                val d = dao.get(id)!!
                if (d.supportsResume && d.downloadedBytes > 0) break
                Thread.sleep(20)
            }
        }
        runBlocking { c.pause(id).join() }
        val paused = awaitStatus(id, DownloadStatus.PAUSED)
        val offset = File(dir, "file.bin").length()
        assertTrue("partial file expected", offset in 1 until payload.size)
        assertEquals(offset, paused.downloadedBytes)
        // Resume: server honours the range.
        val from = runBlocking { dao.get(id)!!.downloadedBytes }
        server.enqueue(
            MockResponse.Builder().code(206).body(Buffer().write(payload.copyOfRange(from.toInt(), payload.size)))
                .addHeader("Content-Range", "bytes $from-${payload.size - 1}/${payload.size}").addHeader("ETag", "\"v1\"").build(),
        )
        c.resume(id)
        val done = awaitStatus(id, DownloadStatus.COMPLETED, DownloadStatus.FAILED)
        assertEquals(DownloadStatus.COMPLETED, done.status)
        server.takeRequest() // first
        val second = server.takeRequest()
        assertEquals("bytes=$from-", second.headers["Range"])
        assertEquals("\"v1\"", second.headers["If-Range"])
        assertArrayEquals(payload, File(dir, "file.bin").readBytes())
    }

    @Test fun serverIgnoringRangeRestartsFromZero() {
        val payload = bytes(50_000)
        val c = controller()
        val id = runBlocking {
            val id = dao.insert(DownloadEntity(url = server.url("/r").toString(), fileName = "file.bin", mimeType = null, status = DownloadStatus.PAUSED, supportsResume = true, downloadedBytes = 10, totalBytes = 50_000))
            val handle = File(dir, "file.bin").apply { writeBytes(ByteArray(10)) }.path
            dao.update(dao.get(id)!!.copy(contentUri = handle))
            id
        }
        server.enqueue(MockResponse.Builder().body(Buffer().write(payload)).build()) // 200, not 206
        c.resume(id)
        awaitStatus(id, DownloadStatus.COMPLETED)
        assertArrayEquals(payload, File(dir, "file.bin").readBytes())
    }

    @Test fun httpErrorFailsAndRetrySucceeds() {
        server.enqueue(MockResponse.Builder().code(500).build())
        val c = controller()
        val id = runBlocking { c.enqueue(request("/e")) }
        val failed = awaitStatus(id, DownloadStatus.FAILED)
        assertEquals("HTTP 500", failed.error)
        server.enqueue(MockResponse.Builder().body("ok").build())
        c.retry(id)
        awaitStatus(id, DownloadStatus.COMPLETED)
        assertEquals("ok", File(dir, "file.bin").readText())
    }

    @Test fun cancelDeletesPartialFile() {
        server.enqueue(MockResponse.Builder().body(Buffer().write(bytes(400_000))).throttleBody(16 * 1024, 100, TimeUnit.MILLISECONDS).build())
        val c = controller()
        val id = runBlocking { c.enqueue(request("/c")) }
        runBlocking { while (dao.get(id)!!.contentUri == null) Thread.sleep(10) }
        runBlocking { c.cancel(id).join() }
        val d = awaitStatus(id, DownloadStatus.CANCELLED)
        assertEquals(null, d.contentUri)
        assertTrue(!File(dir, "file.bin").exists())
    }

    @Test fun dataUrlIsDecoded() {
        val c = controller()
        val id = runBlocking { c.enqueue(DownloadRequest("data:text/plain;base64,SGVsbG8=", "hello.txt", "text/plain", null, null, false, null)) }
        awaitStatus(id, DownloadStatus.COMPLETED)
        assertEquals("Hello", File(dir, "hello.txt").readText())
    }

    @Test fun privateFinishedEntriesAreRemovedWhenSessionEnds() {
        val c = controller()
        val id = runBlocking { c.enqueue(DownloadRequest("data:text/plain,x", "p.txt", null, null, null, true, null)) }
        awaitStatus(id, DownloadStatus.COMPLETED)
        runBlocking { c.onPrivateSessionEnded().join() }
        assertEquals(null, runBlocking { dao.get(id) })
        assertTrue(File(dir, "p.txt").exists())
    }
}
