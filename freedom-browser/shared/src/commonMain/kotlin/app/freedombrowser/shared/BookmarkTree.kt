package app.freedombrowser.shared

/** Rules for organising bookmarks into folders (shared by Android Room data and the Windows UI). */
object BookmarkTree {
    const val ROOT_ID = 0L

    /**
     * True if moving [nodeId] under [newParentId] would put a folder inside itself or one of its
     * descendants. [parentOf] returns the parent of a node (null/ROOT for top-level).
     */
    fun wouldCreateCycle(nodeId: Long, newParentId: Long?, parentOf: (Long) -> Long?): Boolean {
        var cursor = newParentId
        var guard = 0
        while (cursor != null && cursor != ROOT_ID) {
            if (cursor == nodeId) return true
            cursor = parentOf(cursor)
            if (++guard > 10_000) return true // corrupted data; refuse the move
        }
        return false
    }

    fun validateTitle(title: String): String? = title.trim().takeIf { it.isNotEmpty() && it.length <= 300 }

    /** Validates a bookmark URL; only http(s) URLs are stored. Returns the normalised URL or null. */
    fun validateUrl(url: String): String? {
        val r = UrlResolver.resolve(url, SearchEngine.DEFAULT)
        return (r as? ResolvedInput.Navigate)?.url?.takeIf { it.startsWith("http") }
    }
}
