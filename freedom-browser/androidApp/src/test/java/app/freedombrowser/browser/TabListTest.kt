package app.freedombrowser.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabListTest {
    private fun tab(id: Long, url: String = "https://site$id.com/", private: Boolean = false) = TabState(id = id, isPrivate = private, url = url, title = "T$id")

    @Test fun openSelectsForegroundTabsOnly() {
        var l = TabList().open(tab(1))
        assertEquals(1L, l.selectedId)
        l = l.open(tab(2), background = true)
        assertEquals(1L, l.selectedId)
        l = l.open(tab(3), afterId = 1)
        assertEquals(listOf(1L, 3L, 2L), l.tabs.map { it.id })
        assertEquals(3L, l.selectedId)
    }

    @Test fun closingSelectedPicksRightNeighbourThenLeft() {
        var l = TabList().open(tab(1)).open(tab(2)).open(tab(3)).select(2)
        l = l.close(2)
        assertEquals(3L, l.selectedId)
        l = l.close(3)
        assertEquals(1L, l.selectedId)
        l = l.close(1)
        assertNull(l.selectedId)
        assertTrue(l.tabs.isEmpty())
    }

    @Test fun closingPrivateTabPrefersAnotherPrivateTab() {
        val l = TabList().open(tab(1)).open(tab(2, private = true)).open(tab(3)).open(tab(4, private = true)).select(4)
        assertEquals(2L, l.close(4).selectedId)
    }

    @Test fun recentlyClosedExcludesPrivateAndStartPages() {
        val l = TabList().open(tab(1)).open(tab(2, private = true)).open(tab(3, url = "")).close(1).close(2).close(3)
        assertEquals(listOf("https://site1.com/"), l.recentlyClosed.map { it.url })
        val (after, restored) = l.popRecentlyClosed()
        assertEquals("https://site1.com/", restored!!.url)
        assertTrue(after.recentlyClosed.isEmpty())
        assertNull(after.popRecentlyClosed().second)
    }

    @Test fun recentlyClosedIsBounded() {
        var l = TabList()
        repeat(40) { l = l.open(tab(it.toLong())) }
        repeat(40) { l = l.close(it.toLong()) }
        assertEquals(TabList.MAX_RECENTLY_CLOSED, l.recentlyClosed.size)
        assertEquals("https://site39.com/", l.recentlyClosed.first().url)
    }

    @Test fun closeAllPrivateKeepsNormalTabs() {
        val l = TabList().open(tab(1)).open(tab(2, private = true)).open(tab(3, private = true)).closeAll(privateOnly = true)
        assertEquals(listOf(1L), l.tabs.map { it.id })
        assertEquals(1L, l.selectedId)
        assertTrue(l.recentlyClosed.isEmpty())
    }

    @Test fun manyTabsAreAllowed() {
        var l = TabList()
        repeat(500) { l = l.open(tab(it.toLong()), background = true) }
        assertEquals(500, l.tabs.size)
    }

    @Test fun updateOnlyTouchesTarget() {
        val l = TabList().open(tab(1)).open(tab(2)).update(1) { it.copy(title = "x") }
        assertEquals("x", l.tab(1)!!.title)
        assertEquals("T2", l.tab(2)!!.title)
    }
}
