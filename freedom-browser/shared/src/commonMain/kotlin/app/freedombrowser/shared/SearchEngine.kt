package app.freedombrowser.shared

/** Search engines the user can choose as default. Templates use `{q}` for the encoded query. */
enum class SearchEngine(val id: String, val displayName: String, private val template: String) {
    GOOGLE("google", "Google", "https://www.google.com/search?q={q}"),
    BING("bing", "Bing", "https://www.bing.com/search?q={q}"),
    DUCKDUCKGO("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q={q}"),
    BRAVE("brave", "Brave Search", "https://search.brave.com/search?q={q}");

    fun searchUrl(query: String): String = template.replace("{q}", UrlCodec.encodeQueryComponent(query.trim()))

    companion object {
        val DEFAULT = DUCKDUCKGO
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
