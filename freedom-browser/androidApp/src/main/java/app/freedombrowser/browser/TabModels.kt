package app.freedombrowser.browser

import android.graphics.Bitmap
import app.freedombrowser.shared.PageErrorKind
import app.freedombrowser.shared.SecurityLevel

data class PageError(
    val kind: PageErrorKind,
    val code: Int,
    val description: String,
    val url: String,
)

/** Everything the UI needs to know about one tab. `url == ""` means the start page. */
data class TabState(
    val id: Long,
    val isPrivate: Boolean,
    val url: String = "",
    val title: String = "",
    val progress: Int = 100,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val security: SecurityLevel = SecurityLevel.NONE,
    val certificateError: Boolean = false,
    val desktopMode: Boolean = false,
    val textZoom: Int? = null,
    val error: PageError? = null,
    val blockedCount: Int = 0,
    val favicon: Bitmap? = null,
    /** Tab-switcher preview (captured when leaving the tab). */
    val thumbnail: Bitmap? = null,
    val pinned: Boolean = false,
    val lastActive: Long = 0,
) {
    val isStartPage: Boolean get() = url.isEmpty() && error == null
}

/** A closed normal tab that can be reopened. Private tabs are never remembered. */
data class ClosedTab(val url: String, val title: String, val desktopMode: Boolean, val index: Int)

/**
 * Pure, immutable tab-list state with the rules for opening, closing, selecting and restoring
 * tabs. Kept free of Android/WebView types so it can be unit-tested on the JVM.
 */
data class TabList(
    val tabs: List<TabState> = emptyList(),
    val selectedId: Long? = null,
    val recentlyClosed: List<ClosedTab> = emptyList(),
) {
    val selected: TabState? get() = tabs.firstOrNull { it.id == selectedId }
    val normalTabs: List<TabState> get() = tabs.filter { !it.isPrivate }
    val privateTabs: List<TabState> get() = tabs.filter { it.isPrivate }

    fun tab(id: Long) = tabs.firstOrNull { it.id == id }

    /** Opens [tab] after [afterId] (or at the end). Foreground tabs become selected. */
    fun open(tab: TabState, background: Boolean = false, afterId: Long? = null): TabList {
        val idx = afterId?.let { a -> tabs.indexOfFirst { it.id == a } }?.takeIf { it >= 0 }
        val newTabs = if (idx == null) tabs + tab else tabs.toMutableList().apply { add(idx + 1, tab) }
        return copy(tabs = newTabs, selectedId = if (background && selectedId != null) selectedId else tab.id)
    }

    fun select(id: Long, now: Long = 0): TabList =
        if (tabs.none { it.id == id }) this else copy(selectedId = id, tabs = tabs.map { if (it.id == id) it.copy(lastActive = now) else it })

    /**
     * Closes [id]. When the selected tab closes, the neighbour to the right (same mode preferred)
     * becomes selected, otherwise the one to the left.
     */
    fun close(id: Long): TabList {
        val index = tabs.indexOfFirst { it.id == id }
        if (index < 0) return this
        val closing = tabs[index]
        val remaining = tabs.filterIndexed { i, _ -> i != index }
        val closed = if (!closing.isPrivate && closing.url.isNotEmpty())
            (listOf(ClosedTab(closing.url, closing.title, closing.desktopMode, index)) + recentlyClosed).take(MAX_RECENTLY_CLOSED)
        else recentlyClosed
        val newSelected = if (selectedId != id) selectedId else pickNeighbour(remaining, index, closing.isPrivate)
        return TabList(remaining, newSelected, closed)
    }

    fun closeAll(privateOnly: Boolean = false): TabList {
        val (closing, keep) = tabs.partition { !privateOnly || it.isPrivate }
        val closed = (closing.filter { !it.isPrivate && it.url.isNotEmpty() }
            .mapIndexed { i, t -> ClosedTab(t.url, t.title, t.desktopMode, i) }.reversed() + recentlyClosed).take(MAX_RECENTLY_CLOSED)
        val sel = if (keep.any { it.id == selectedId }) selectedId else keep.lastOrNull()?.id
        return TabList(keep, sel, closed)
    }

    /** Pops the most recently closed tab; returns the new list and the tab to recreate (if any). */
    fun popRecentlyClosed(): Pair<TabList, ClosedTab?> {
        val first = recentlyClosed.firstOrNull() ?: return this to null
        return copy(recentlyClosed = recentlyClosed.drop(1)) to first
    }

    fun update(id: Long, transform: (TabState) -> TabState): TabList =
        copy(tabs = tabs.map { if (it.id == id) transform(it) else it })

    private fun pickNeighbour(remaining: List<TabState>, index: Int, wasPrivate: Boolean): Long? {
        if (remaining.isEmpty()) return null
        val sameMode = remaining.withIndex().filter { it.value.isPrivate == wasPrivate }
        val pool = sameMode.ifEmpty { remaining.withIndex().toList() }
        return (pool.firstOrNull { it.index >= index } ?: pool.last()).value.id
    }

    companion object {
        const val MAX_RECENTLY_CLOSED = 25
    }
}
