package app.freedombrowser.shared

/**
 * Transparent domain-based ad/tracker blocking rules.
 *
 * Supported list formats (one rule per line):
 * - hosts files: `0.0.0.0 ads.example.com` / `127.0.0.1 ads.example.com`
 * - plain domain lists: `ads.example.com`
 * - simple Adblock-style domain anchors: `||ads.example.com^` (no paths, options or cosmetics)
 * Comments start with `#` or `!`. A rule blocks the domain and all of its subdomains.
 *
 * The Windows native host implements the identical algorithm in C (`blocklist.c`); both
 * are verified against `shared/testdata/blocklist-vectors.txt`.
 */
class BlockList private constructor(private val domains: HashSet<String>) {
    val size: Int get() = domains.size

    fun isBlockedHost(host: String): Boolean {
        var h = host.lowercase().trimEnd('.')
        while (true) {
            if (h in domains) return true
            val dot = h.indexOf('.')
            if (dot < 0) return false
            h = h.substring(dot + 1)
        }
    }

    /**
     * Decides whether a sub-resource request must be cancelled.
     * Main-frame navigations are never blocked so the user can always open a site deliberately.
     */
    fun shouldBlock(requestUrl: String, pageHost: String?, isMainFrame: Boolean, enabled: Boolean, exceptions: Set<String>): Boolean {
        if (!enabled || isMainFrame) return false
        if (pageHost != null && isException(pageHost, exceptions)) return false
        val host = WebUrl.hostOf(requestUrl) ?: return false
        return isBlockedHost(host)
    }

    companion object {
        private val SKIP_HOSTS = setOf("localhost", "localhost.localdomain", "local", "broadcasthost", "0.0.0.0", "ip6-localhost", "ip6-loopback")

        fun empty() = BlockList(HashSet())

        fun parse(vararg sources: String): BlockList {
            val set = HashSet<String>()
            for (text in sources) text.lineSequence().forEach { line -> parseLine(line)?.let(set::add) }
            return BlockList(set)
        }

        fun parseLine(raw: String): String? {
            var line = raw.trim()
            if (line.isEmpty() || line.startsWith('#') || line.startsWith('!') || line.startsWith('[')) return null
            val hash = line.indexOf(" #")
            if (hash >= 0) line = line.substring(0, hash).trim()
            if (line.startsWith("||")) {
                line = line.substring(2)
                if (!line.endsWith("^")) return null
                line = line.dropLast(1)
            } else {
                val parts = line.split(' ', '\t').filter { it.isNotEmpty() }
                line = when {
                    parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1" || parts[0] == "::" || parts[0] == "::1") -> parts[1]
                    parts.size == 1 -> parts[0]
                    else -> return null
                }
            }
            val host = line.lowercase().trimEnd('.')
            if (host in SKIP_HOSTS || !UrlResolver.looksLikeDomain(host)) return null
            return host
        }

        /** True when [pageHost] (or a parent domain of it) is listed in [exceptions]. */
        fun isException(pageHost: String, exceptions: Set<String>): Boolean {
            if (exceptions.isEmpty()) return false
            var h = pageHost.lowercase().removePrefix("www.")
            while (true) {
                if (h in exceptions) return true
                val dot = h.indexOf('.')
                if (dot < 0) return false
                h = h.substring(dot + 1)
            }
        }
    }
}
