package app.freedombrowser.downloads

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import app.freedombrowser.shared.FileNames
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

/** Where a download's bytes are written. Implementations must be safe to call from IO threads. */
interface DownloadTarget {
    /** Creates the destination for [desiredName] (made unique) and returns (handle, actualName). */
    fun create(desiredName: String, mimeType: String?): Pair<String, String>
    /** Opens the destination; [append] continues a partial file (resume). */
    fun open(handle: String, append: Boolean): OutputStream
    fun finish(handle: String)
    fun delete(handle: String)
}

/** Public "Download" folder through MediaStore (no storage permission needed on Android 10+). */
class MediaStoreTarget(private val context: Context) : DownloadTarget {
    private val resolver get() = context.contentResolver

    override fun create(desiredName: String, mimeType: String?): Pair<String, String> {
        val name = FileNames.unique(FileNames.sanitize(desiredName), ::exists)
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mimeType ?: "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/")
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Cannot create file in Downloads")
        // MediaStore may rename on conflicts with files we cannot see; report the real name.
        val actual = resolver.query(uri, arrayOf(MediaStore.Downloads.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: name
        return uri.toString() to actual
    }

    private fun exists(name: String): Boolean =
        resolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Downloads._ID),
            "${MediaStore.Downloads.DISPLAY_NAME} = ? AND ${MediaStore.Downloads.RELATIVE_PATH} = ?",
            arrayOf(name, Environment.DIRECTORY_DOWNLOADS + "/"),
            null,
        )?.use { it.count > 0 } ?: false

    override fun open(handle: String, append: Boolean): OutputStream =
        resolver.openOutputStream(Uri.parse(handle), if (append) "wa" else "wt") ?: throw IOException("Cannot open output")

    override fun finish(handle: String) {
        resolver.update(Uri.parse(handle), ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
    }

    override fun delete(handle: String) {
        runCatching { resolver.delete(Uri.parse(handle), null, null) }
    }
}

/** A folder the user picked with the Storage Access Framework. */
class TreeTarget(private val context: Context, private val treeUri: Uri) : DownloadTarget {
    private val root get() = DocumentFile.fromTreeUri(context, treeUri) ?: throw IOException("Download folder is not accessible")

    override fun create(desiredName: String, mimeType: String?): Pair<String, String> {
        val dir = root
        if (!dir.canWrite()) throw IOException("Download folder is not writable; choose it again in Settings")
        val name = FileNames.unique(FileNames.sanitize(desiredName)) { dir.findFile(it) != null }
        // Generic MIME type prevents providers from appending a second extension.
        val file = dir.createFile("application/octet-stream", name) ?: throw IOException("Cannot create file")
        return file.uri.toString() to (file.name ?: name)
    }

    override fun open(handle: String, append: Boolean): OutputStream =
        context.contentResolver.openOutputStream(Uri.parse(handle), if (append) "wa" else "wt") ?: throw IOException("Cannot open output")

    override fun finish(handle: String) = Unit

    override fun delete(handle: String) {
        runCatching { DocumentFile.fromSingleUri(context, Uri.parse(handle))?.delete() }
    }
}

/** Plain directory target (used by tests). */
class FileTarget(private val dir: File) : DownloadTarget {
    override fun create(desiredName: String, mimeType: String?): Pair<String, String> {
        dir.mkdirs()
        val name = FileNames.unique(FileNames.sanitize(desiredName)) { File(dir, it).exists() }
        val f = File(dir, name)
        f.createNewFile()
        return f.path to name
    }
    override fun open(handle: String, append: Boolean): OutputStream = FileOutputStream(File(handle), append)
    override fun finish(handle: String) = Unit
    override fun delete(handle: String) { File(handle).delete() }
}
