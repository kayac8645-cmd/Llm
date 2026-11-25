@file:OptIn(ExperimentalJsExport::class)

package app.freedombrowser.shared

import kotlin.js.Date

/**
 * JavaScript facade used by the Windows UI (which runs inside CEF). Exposes the same domain
 * logic the Android app uses, with JS-friendly signatures (strings, numbers, arrays).
 */
@JsExport
object FreedomCore {
    /** Returns `[kind, url, query]` where kind is `navigate`, `search` or `empty`. */
    fun resolveInput(input: String, engineId: String): Array<String> = when (val r = UrlResolver.resolve(input, SearchEngine.fromId(engineId))) {
        is ResolvedInput.Navigate -> arrayOf("navigate", r.url, "")
        is ResolvedInput.Search -> arrayOf("search", r.url, r.query)
        ResolvedInput.Empty -> arrayOf("empty", "", "")
    }

    /** `[[id, name], ...]` */
    fun searchEngines(): Array<Array<String>> = SearchEngine.entries.map { arrayOf(it.id, it.displayName) }.toTypedArray()
    fun defaultSearchEngine(): String = SearchEngine.DEFAULT.id
    fun searchUrl(engineId: String, query: String): String = SearchEngine.fromId(engineId).searchUrl(query)

    fun displayUrl(url: String): String = DisplayUrl.format(url)
    fun securityLevel(url: String, certError: Boolean): String = DisplayUrl.securityLevel(url, certError).name.lowercase()
    fun redactUrl(url: String): String = DisplayUrl.redact(url)
    fun hostOf(url: String): String = WebUrl.hostOf(url) ?: ""
    fun siteKey(url: String): String = WebUrl.parse(url)?.siteKey ?: ""

    fun isPotentiallyDangerous(fileName: String, mimeType: String): Boolean = FileNames.isPotentiallyDangerous(fileName, mimeType.ifEmpty { null })
    fun sanitizeFileName(name: String): String = FileNames.sanitize(name)

    fun formatBytes(bytes: Double): String = Formatters.bytes(bytes.toLong())
    fun formatSpeed(bytesPerSecond: Double): String = Formatters.speed(bytesPerSecond.toLong())
    fun formatEta(remaining: Double, bytesPerSecond: Double): String = Formatters.eta(remaining.toLong(), bytesPerSecond.toLong())

    fun historyCutoff(rangeId: String, nowMillis: Double): Double = HistoryRange.fromId(rangeId).cutoff(nowMillis.toLong()).toDouble()
    fun historyRanges(): Array<String> = HistoryRange.entries.map { it.id }.toTypedArray()
    fun textMatches(query: String, title: String, url: String): Boolean = TextSearch.matches(query, title, url)

    /** [parentPairs] is a flat array `[id0, parent0, id1, parent1, ...]` (parent "" = root). */
    fun bookmarkMoveCreatesCycle(nodeId: String, newParentId: String, parentPairs: Array<String>): Boolean {
        val map = HashMap<Long, Long?>()
        var i = 0
        while (i + 1 < parentPairs.size) {
            map[parentPairs[i].toLong()] = parentPairs[i + 1].toLongOrNull()
            i += 2
        }
        return BookmarkTree.wouldCreateCycle(nodeId.toLong(), newParentId.toLongOrNull(), { map[it] })
    }
    fun validateBookmarkUrl(url: String): String = BookmarkTree.validateUrl(url) ?: ""

    fun errorKindForNetError(code: Int): String = PageErrors.fromChromiumNetError(code).id
    fun errorDetails(kindId: String, code: Int, description: String, url: String, appVersion: String): String =
        PageErrors.details(PageErrorKind.fromId(kindId), code, description, url, "Windows x86", appVersion)

    fun tokensCss(theme: String): String = DesignTokens.toCss(if (theme == "light") DesignTokens.Light else DesignTokens.Dark)

    fun now(): Double = Date.now()

    /** visits: flat [url0, title0, time0, url1, ...]; returns [[url, title, host], ...] */
    fun topSites(visits: Array<String>, now: Double, limit: Int, exclude: Array<String>): Array<Array<String>> =
        TopSites.compute(toVisits(visits), now.toLong(), limit, exclude.toSet()).map { arrayOf(it.url, it.title, it.host) }.toTypedArray()

    fun inlineCompletion(typed: String, visits: Array<String>, bookmarks: Array<String>): String =
        Autocomplete.inline(typed, toVisits(visits), toPairs(bookmarks)) ?: ""

    /** Returns [[url, title, "1"|"0" isBookmark], ...] */
    fun suggestions(query: String, visits: Array<String>, bookmarks: Array<String>, limit: Int): Array<Array<String>> =
        Autocomplete.suggest(query, toVisits(visits), toPairs(bookmarks), limit).map { arrayOf(it.url, it.title, if (it.isBookmark) "1" else "0") }.toTypedArray()

    private fun toVisits(flat: Array<String>): List<Visit> {
        val out = ArrayList<Visit>(flat.size / 3)
        var i = 0
        while (i + 2 < flat.size) { out += Visit(flat[i], flat[i + 1], flat[i + 2].toDoubleOrNull()?.toLong() ?: 0L); i += 3 }
        return out
    }

    private fun toPairs(flat: Array<String>): List<Pair<String, String>> {
        val out = ArrayList<Pair<String, String>>(flat.size / 2)
        var i = 0
        while (i + 1 < flat.size) { out += flat[i] to flat[i + 1]; i += 2 }
        return out
    }
}
