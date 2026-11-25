package app.freedombrowser.shared

/**
 * Minimal, dependency-free URL splitter for absolute hierarchical URLs (`scheme://authority/...`).
 * It deliberately does not try to be a full WHATWG parser; engines do the real parsing. We only
 * need scheme/host/port for UI, privacy decisions and redaction.
 */
data class WebUrl(
    val scheme: String,
    val userInfo: String?,
    val host: String,
    val port: Int?,
    val path: String,
    val query: String?,
    val fragment: String?,
) {
    val isHttps: Boolean get() = scheme == "https"
    val isHttp: Boolean get() = scheme == "http"
    val isWeb: Boolean get() = isHttp || isHttps

    /** Origin-like key used for per-site settings: lower-case host without `www.`. */
    val siteKey: String get() = host.removePrefix("www.")

    override fun toString(): String = buildString {
        append(scheme).append("://")
        if (userInfo != null) append(userInfo).append('@')
        append(if (host.contains(':')) "[$host]" else host)
        if (port != null) append(':').append(port)
        append(path)
        if (query != null) append('?').append(query)
        if (fragment != null) append('#').append(fragment)
    }

    companion object {
        private val SCHEME = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")

        fun parse(input: String): WebUrl? {
            val s = input.trim()
            val m = SCHEME.find(s) ?: return null
            val scheme = m.groupValues[1].lowercase()
            var rest = s.substring(m.value.length)
            if (!rest.startsWith("//")) return null
            rest = rest.substring(2)
            val authorityEnd = rest.indexOfAny(charArrayOf('/', '?', '#')).let { if (it < 0) rest.length else it }
            val authority = rest.substring(0, authorityEnd)
            val tail = rest.substring(authorityEnd)
            val at = authority.lastIndexOf('@')
            val userInfo = if (at >= 0) authority.substring(0, at) else null
            val hostPort = if (at >= 0) authority.substring(at + 1) else authority
            val host: String
            var port: Int? = null
            if (hostPort.startsWith("[")) {
                val close = hostPort.indexOf(']')
                if (close < 0) return null
                host = hostPort.substring(1, close).lowercase()
                val after = hostPort.substring(close + 1)
                if (after.isNotEmpty()) {
                    if (!after.startsWith(":")) return null
                    port = parsePort(after.substring(1)) ?: return null
                }
            } else {
                val colon = hostPort.lastIndexOf(':')
                if (colon >= 0) {
                    host = hostPort.substring(0, colon).lowercase()
                    val p = hostPort.substring(colon + 1)
                    if (p.isNotEmpty()) port = parsePort(p) ?: return null
                } else {
                    host = hostPort.lowercase()
                }
            }
            if (host.isEmpty() && scheme != "file") return null
            val hashIdx = tail.indexOf('#')
            val fragment = if (hashIdx >= 0) tail.substring(hashIdx + 1) else null
            val beforeHash = if (hashIdx >= 0) tail.substring(0, hashIdx) else tail
            val qIdx = beforeHash.indexOf('?')
            val query = if (qIdx >= 0) beforeHash.substring(qIdx + 1) else null
            val path = if (qIdx >= 0) beforeHash.substring(0, qIdx) else beforeHash
            return WebUrl(scheme, userInfo, host.trimEnd('.'), port, path.ifEmpty { "/" }, query, fragment)
        }

        private fun parsePort(p: String): Int? = p.toIntOrNull()?.takeIf { it in 0..65535 && p.all { c -> c.isDigit() } }

        /** Host of [url] or null when the URL is not hierarchical. */
        fun hostOf(url: String): String? = parse(url)?.host?.takeIf { it.isNotEmpty() }
    }
}
