package app.freedombrowser.shared

/** File-name logic for downloads, shared by Android and Windows. */
object FileNames {
    private const val MAX_LENGTH = 120
    private val ILLEGAL = Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]")
    private val WINDOWS_RESERVED = setOf(
        "con", "prn", "aux", "nul",
        "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
        "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9",
    )

    /**
     * Extensions that can run code or change the system when opened. Downloads with these
     * extensions require an explicit confirmation and are never opened automatically.
     */
    val DANGEROUS_EXTENSIONS = setOf(
        "apk", "aab", "xapk", "exe", "msi", "msix", "msp", "appx", "appxbundle", "bat", "cmd", "com", "scr",
        "pif", "cpl", "dll", "sys", "drv", "ocx", "vbs", "vbe", "js", "jse", "wsf", "wsh", "hta", "ps1",
        "psm1", "reg", "lnk", "jar", "sh", "run", "bin", "deb", "rpm", "dmg", "pkg", "iso", "img", "vhd",
        "vhdx", "url", "scf", "inf", "application", "gadget", "msc", "chm", "xll", "docm", "xlsm", "pptm",
    )

    fun extension(fileName: String): String {
        val dot = fileName.lastIndexOf('.')
        return if (dot <= 0 || dot == fileName.length - 1) "" else fileName.substring(dot + 1).lowercase()
    }

    fun isPotentiallyDangerous(fileName: String, mimeType: String? = null): Boolean {
        if (extension(fileName) in DANGEROUS_EXTENSIONS) return true
        val mime = mimeType?.lowercase() ?: return false
        return mime == "application/vnd.android.package-archive" ||
            mime == "application/x-msdownload" || mime == "application/x-msdos-program" ||
            mime == "application/x-msi" || mime == "application/x-sh" || mime == "application/java-archive"
    }

    /** Makes [name] safe for every supported file system. Never returns an empty string. */
    fun sanitize(name: String): String {
        var n = name.replace(ILLEGAL, "_").trim().trim('.', ' ')
        if (n.isEmpty()) n = "download"
        val base = n.substringBeforeLast('.', n)
        if (base.lowercase() in WINDOWS_RESERVED) n = "_$n"
        if (n.length > MAX_LENGTH) {
            val ext = extension(n)
            val keep = MAX_LENGTH - (if (ext.isEmpty()) 0 else ext.length + 1)
            n = n.substring(0, keep).trimEnd('.', ' ') + (if (ext.isEmpty()) "" else ".$ext")
        }
        return n
    }

    /** Returns `name`, or `name (1).ext`, `name (2).ext`... — the first one for which [exists] is false. */
    fun unique(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val ext = extension(name)
        val base = if (ext.isEmpty()) name else name.substring(0, name.length - ext.length - 1)
        var i = 1
        while (true) {
            val candidate = if (ext.isEmpty()) "$base ($i)" else "$base ($i).$ext"
            if (!exists(candidate)) return candidate
            i++
            require(i < 10_000) { "Could not find a free file name for $name" }
        }
    }

    /**
     * Picks a file name from the Content-Disposition header (RFC 6266, including `filename*`),
     * falling back to the last URL path segment and finally to a MIME-based default.
     */
    fun guess(url: String, contentDisposition: String?, mimeType: String?): String {
        val fromHeader = contentDisposition?.let(::fromContentDisposition)
        val fromUrl = WebUrl.parse(url)?.path?.substringAfterLast('/')?.let(UrlCodec::decode)?.takeIf { it.isNotBlank() }
        var name = fromHeader ?: fromUrl ?: "download"
        if (extension(name).isEmpty()) {
            mimeExtension(mimeType)?.let { name = "$name.$it" }
        }
        return sanitize(name)
    }

    fun fromContentDisposition(header: String): String? {
        val star = Regex("filename\\*\\s*=\\s*([^']*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (star != null) {
            val charset = star.groupValues[1].trim().lowercase()
            val value = star.groupValues[2].trim().trim('"')
            if (charset == "utf-8" || charset.isEmpty()) return UrlCodec.decode(value).takeIf { it.isNotBlank() }
        }
        val quoted = Regex("filename\\s*=\\s*\"((?:[^\"\\\\]|\\\\.)*)\"", RegexOption.IGNORE_CASE).find(header)
        if (quoted != null) return quoted.groupValues[1].replace("\\\"", "\"").takeIf { it.isNotBlank() }
        val plain = Regex("filename\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE).find(header)
        return plain?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    fun mimeExtension(mimeType: String?): String? = when (mimeType?.substringBefore(';')?.trim()?.lowercase()) {
        "application/pdf" -> "pdf"
        "image/jpeg" -> "jpg"
        "image/png" -> "png"
        "image/gif" -> "gif"
        "image/webp" -> "webp"
        "image/svg+xml" -> "svg"
        "text/plain" -> "txt"
        "text/html" -> "html"
        "text/csv" -> "csv"
        "application/json" -> "json"
        "application/zip" -> "zip"
        "audio/mpeg" -> "mp3"
        "video/mp4" -> "mp4"
        "application/vnd.android.package-archive" -> "apk"
        "application/msword" -> "doc"
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "docx"
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx"
        "application/vnd.openxmlformats-officedocument.presentationml.presentation" -> "pptx"
        else -> null
    }
}
