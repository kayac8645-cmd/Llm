package app.freedombrowser.shared

/** One history visit as seen by [TopSites] and [Autocomplete]. */
data class Visit(val url: String, val title: String, val time: Long)

data class TopSite(val url: String, val title: String, val host: String, val score: Double)

/**
 * "Recently visited" tiles for the new-tab page: one entry per site, ranked by frecency
 * (visit count weighted by recency). Only http(s) pages are considered.
 */
object TopSites {
    private const val DAY = 86_400_000.0

    fun compute(visits: List<Visit>, now: Long, limit: Int = 8, exclude: Set<String> = emptySet()): List<TopSite> {
        data class Acc(var score: Double, var url: String, var title: String, var latest: Long)
        val bySite = LinkedHashMap<String, Acc>()
        for (v in visits) {
            val parsed = WebUrl.parse(v.url) ?: continue
            if (!parsed.isWeb) continue
            val key = parsed.siteKey
            if (key.isEmpty() || key in exclude) continue
            val ageDays = ((now - v.time).coerceAtLeast(0)) / DAY
            val weight = when {
                ageDays < 4 -> 1.0
                ageDays < 14 -> 0.7
                ageDays < 31 -> 0.5
                ageDays < 90 -> 0.3
                else -> 0.1
            }
            val acc = bySite.getOrPut(key) { Acc(0.0, v.url, v.title, v.time) }
            acc.score += weight
            if (v.time >= acc.latest) {
                acc.latest = v.time
                // Prefer the site's root page as the tile target when it was visited.
                if (parsed.path == "/" || acc.url == v.url) { acc.url = v.url; acc.title = v.title.ifBlank { acc.title } }
            }
            if (parsed.path == "/" && parsed.query == null) { acc.url = v.url; if (v.title.isNotBlank()) acc.title = v.title }
        }
        return bySite.entries.sortedWith(compareByDescending<Map.Entry<String, Acc>> { it.value.score }.thenByDescending { it.value.latest })
            .take(limit)
            .map { (host, a) -> TopSite(a.url, a.title.ifBlank { host }, host, a.score) }
    }
}

/** Address-bar completion (inline completion + ranked suggestions) from history and bookmarks. */
object Autocomplete {
    data class Suggestion(val url: String, val title: String, val isBookmark: Boolean, val score: Double)

    /**
     * Inline completion like Chrome: when the typed text is a prefix of a visited host (with or
     * without "www."), returns the full completed text (typed text + remainder); otherwise null.
     */
    fun inline(typed: String, visits: List<Visit>, bookmarks: List<Pair<String, String>> = emptyList()): String? {
        val t = typed.trim().lowercase()
        if (t.length < 2 || t.any { it.isWhitespace() } || t.contains('/')) return null
        val counts = HashMap<String, Int>()
        for (v in visits) WebUrl.hostOf(v.url)?.let { counts[it] = (counts[it] ?: 0) + 1 }
        for ((_, url) in bookmarks) WebUrl.hostOf(url)?.let { counts[it] = (counts[it] ?: 0) + 3 }
        val candidates = counts.entries.mapNotNull { (host, n) ->
            val bare = host.removePrefix("www.")
            when {
                bare.startsWith(t) && bare.length > t.length -> bare to n
                host.startsWith(t) && host.length > t.length -> host to n
                else -> null
            }
        }
        return candidates.maxWithOrNull(compareBy<Pair<String, Int>> { it.second }.thenBy { -it.first.length })?.first
    }

    fun suggest(query: String, visits: List<Visit>, bookmarks: List<Pair<String, String>>, limit: Int = 6): List<Suggestion> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val map = LinkedHashMap<String, Suggestion>()
        for ((title, url) in bookmarks) if (TextSearch.matches(q, title, url)) {
            map[url] = Suggestion(url, title, true, 10.0 + prefixBonus(q, url))
        }
        for (v in visits.asReversed()) {
            if (!TextSearch.matches(q, v.title, v.url)) continue
            val prev = map[v.url]
            map[v.url] = if (prev == null) Suggestion(v.url, v.title, false, 1.0 + prefixBonus(q, v.url))
            else prev.copy(score = prev.score + 1.0, title = prev.title.ifBlank { v.title })
        }
        return map.values.sortedByDescending { it.score }.take(limit)
    }

    private fun prefixBonus(q: String, url: String): Double {
        val host = WebUrl.hostOf(url)?.removePrefix("www.") ?: return 0.0
        return if (host.startsWith(q.lowercase())) 5.0 else 0.0
    }
}
