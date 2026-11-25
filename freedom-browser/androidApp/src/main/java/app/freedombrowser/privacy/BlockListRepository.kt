package app.freedombrowser.privacy

import android.content.Context
import app.freedombrowser.shared.BlockList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException

/**
 * Loads the bundled domain list plus an optional maintained list the user downloaded.
 * The active [BlockList] is swapped atomically so WebView request threads never see a partial list.
 */
class BlockListRepository(private val context: Context, private val http: OkHttpClient) {
    @Volatile
    var current: BlockList = BlockList.empty()
        private set

    private val customFile get() = File(context.filesDir, "blocklists/custom.txt")

    suspend fun load() = withContext(Dispatchers.IO) {
        val bundled = context.assets.open("blocklist/freedom-default.txt").bufferedReader().use { it.readText() }
        val custom = customFile.takeIf { it.exists() }?.readText().orEmpty()
        current = BlockList.parse(bundled, custom)
    }

    /** Downloads a hosts/domain list from [url]; only called after an explicit user action. */
    suspend fun update(url: String): Int = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).header("User-Agent", "FreedomBrowser").build()
        val body = http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val len = response.body.contentLength()
            if (len > MAX_BYTES) throw IOException("List too large")
            response.body.source().readUtf8().also { if (it.length > MAX_BYTES) throw IOException("List too large") }
        }
        val parsed = BlockList.parse(body)
        if (parsed.size == 0) throw IOException("No usable rules in list")
        customFile.parentFile?.mkdirs()
        val tmp = File(customFile.path + ".tmp")
        tmp.writeText(body)
        if (!tmp.renameTo(customFile)) { customFile.delete(); tmp.renameTo(customFile) }
        load()
        parsed.size
    }

    suspend fun removeCustom() = withContext(Dispatchers.IO) {
        customFile.delete()
        load()
    }

    val hasCustomList: Boolean get() = customFile.exists()

    companion object {
        private const val MAX_BYTES = 20L * 1024 * 1024
    }
}
