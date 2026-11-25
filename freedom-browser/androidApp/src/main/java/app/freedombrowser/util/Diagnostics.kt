package app.freedombrowser.util

import android.content.Context
import android.util.Log
import app.freedombrowser.shared.DisplayUrl
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only diagnostic log (opt-in, Settings > About). Never uploaded. URLs are redacted to
 * scheme/host/path; no page content, cookies, form data or query strings are written.
 */
object Diagnostics {
    @Volatile var enabled = false
    private var file: File? = null
    private const val MAX_SIZE = 256 * 1024

    fun init(context: Context) {
        file = File(context.filesDir, "diagnostics.log")
    }

    fun log(event: String, url: String? = null, detail: String? = null) {
        val line = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())).append(' ').append(event)
            if (url != null) append(' ').append(DisplayUrl.redact(url))
            if (detail != null) append(' ').append(detail.take(200))
        }
        Log.i("Lumo", line)
        if (!enabled) return
        val f = file ?: return
        runCatching {
            if (f.length() > MAX_SIZE) f.writeText("")
            f.appendText(line + "\n")
        }
    }

    fun read(): String = file?.takeIf { it.exists() }?.readText().orEmpty()
    fun clear() { file?.delete() }
}
