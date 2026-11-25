package app.freedombrowser.shared

/** Result of interpreting text the user typed in the address bar. */
sealed class ResolvedInput {
    data class Navigate(val url: String) : ResolvedInput()
    data class Search(val url: String, val query: String) : ResolvedInput()
    data object Empty : ResolvedInput()

    val targetUrl: String? get() = when (this) {
        is Navigate -> url
        is Search -> url
        Empty -> null
    }
}

/**
 * Decides whether address-bar input is a URL or a search query.
 *
 * Security notes: `javascript:`, `data:`, `file:`, `content:` and `intent:` input is never
 * navigated to from the address bar; it is treated as a search query instead.
 */
object UrlResolver {
    private val ALLOWED_SCHEMES = setOf("http", "https")
    private val SCHEME_PREFIX = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):")
    private val IPV4 = Regex("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$")
    private val LABEL = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")
    private val TLD = Regex("^(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})$")

    fun resolve(rawInput: String, engine: SearchEngine): ResolvedInput {
        val input = rawInput.trim()
        if (input.isEmpty()) return ResolvedInput.Empty
        if (input.equals("about:blank", ignoreCase = true)) return ResolvedInput.Navigate("about:blank")

        val hasWhitespace = input.any { it.isWhitespace() }
        val schemeMatch = SCHEME_PREFIX.find(input)
        if (schemeMatch != null && input.startsWith(schemeMatch.value + "//")) {
            val scheme = schemeMatch.groupValues[1].lowercase()
            if (scheme in ALLOWED_SCHEMES && !hasWhitespace) {
                val normalized = normalizeAbsolute(input)
                if (normalized != null) return ResolvedInput.Navigate(normalized)
            }
            return search(input, engine)
        }
        if (hasWhitespace) return search(input, engine)

        // No scheme: decide whether it looks like a host (with optional port/path).
        val candidate = "http://$input"
        val parsed = WebUrl.parse(candidate) ?: return search(input, engine)
        if (parsed.userInfo != null) return search(input, engine)
        val asciiHost = toAsciiHost(parsed.host) ?: return search(input, engine)
        val isLocal = asciiHost == "localhost" || IPV4.matches(asciiHost) || parsed.host.contains(':')
        if (!isLocal && !looksLikeDomain(asciiHost)) return search(input, engine)
        val scheme = if (isLocal) "http" else "https"
        val url = parsed.copy(scheme = scheme, host = asciiHost)
        return ResolvedInput.Navigate(UrlCodec.encodeUnsafe(url.toString()))
    }

    /** Normalises an absolute http(s) URL: punycode host, encoded unsafe characters. */
    fun normalizeAbsolute(input: String): String? {
        val parsed = WebUrl.parse(input) ?: return null
        if (parsed.host.isEmpty()) return null
        val host = if (parsed.host.contains(':')) parsed.host else (toAsciiHost(parsed.host) ?: return null)
        return UrlCodec.encodeUnsafe(parsed.copy(host = host).toString())
    }

    fun looksLikeDomain(asciiHost: String): Boolean {
        val labels = asciiHost.split('.')
        if (labels.size < 2) return false
        if (labels.any { !LABEL.matches(it) }) return false
        return TLD.matches(labels.last())
    }

    private fun toAsciiHost(host: String): String? {
        if (host.isEmpty()) return null
        if (host.all { it.code < 0x80 }) return host.lowercase()
        return idnToAscii(host)?.lowercase()
    }

    private fun search(query: String, engine: SearchEngine) = ResolvedInput.Search(engine.searchUrl(query), query)
}
