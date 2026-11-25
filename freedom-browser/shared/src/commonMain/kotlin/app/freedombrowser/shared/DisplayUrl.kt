package app.freedombrowser.shared

/** Connection security as shown next to the address bar. */
enum class SecurityLevel { SECURE, INSECURE, INTERNAL, CERT_ERROR, NONE }

object DisplayUrl {
    /** Shortened, human-friendly URL: Unicode host, no scheme for http(s), no trailing slash. */
    fun format(url: String): String {
        if (url.isBlank() || url == "about:blank") return ""
        val parsed = WebUrl.parse(url) ?: return url
        if (!parsed.isWeb) return url
        val host = safeUnicodeHost(parsed.host)
        val sb = StringBuilder()
        sb.append(host)
        if (parsed.port != null) sb.append(':').append(parsed.port)
        if (parsed.path != "/" || parsed.query != null) sb.append(UrlCodec.decode(parsed.path))
        if (parsed.query != null) sb.append('?').append(parsed.query)
        return sb.toString()
    }

    /**
     * Shows the Unicode form of an IDN host only when it cannot be confused with an ASCII host
     * (no mixing of Latin letters with Cyrillic/Greek look-alikes); otherwise punycode is kept.
     */
    fun safeUnicodeHost(asciiHost: String): String {
        if (!asciiHost.contains("xn--")) return asciiHost
        val unicode = idnToUnicode(asciiHost)
        val label = unicode.split('.')
        val confusable = label.any { l ->
            val hasLatin = l.any { it in 'a'..'z' }
            val hasLookAlikeScript = l.any { it.code in 0x0370..0x03FF || it.code in 0x0400..0x04FF }
            hasLatin && hasLookAlikeScript
        }
        return if (confusable) asciiHost else unicode
    }

    fun securityLevel(url: String, hasCertificateError: Boolean = false): SecurityLevel {
        if (hasCertificateError) return SecurityLevel.CERT_ERROR
        val lower = url.lowercase()
        return when {
            url.isBlank() || lower == "about:blank" -> SecurityLevel.NONE
            lower.startsWith("https://") -> SecurityLevel.SECURE
            lower.startsWith("http://") -> {
                // Local development servers are not flagged as insecure, but are not shown as encrypted either.
                val host = WebUrl.hostOf(url)
                if (host == "localhost" || host == "127.0.0.1" || host == "::1") SecurityLevel.NONE else SecurityLevel.INSECURE
            }
            lower.startsWith("freedom:") || lower.startsWith("about:") -> SecurityLevel.INTERNAL
            else -> SecurityLevel.NONE
        }
    }

    /**
     * Removes user info, query string and fragment so URLs can be written to diagnostics
     * without leaking tokens or personal data.
     */
    fun redact(url: String): String {
        val parsed = WebUrl.parse(url) ?: return url.substringBefore('?').substringBefore('#').take(64)
        return parsed.copy(userInfo = null, query = null, fragment = null).toString()
    }
}
