package app.freedombrowser.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TopSitesTest {
    private val now = 100L * 86_400_000L
    private fun v(url: String, daysAgo: Int, title: String = "") = Visit(url, title, now - daysAgo * 86_400_000L)

    @Test fun ranksBySiteFrecency() {
        val visits = listOf(
            v("https://github.com/", 1, "GitHub"), v("https://github.com/a", 1), v("https://github.com/b", 2),
            v("https://www.wikipedia.org/", 1, "Wikipedia"),
            v("https://old.example/", 200), v("https://old.example/", 200), v("https://old.example/", 200),
            v("javascript:alert(1)", 0),
        )
        val top = TopSites.compute(visits, now)
        assertEquals(listOf("github.com", "wikipedia.org", "old.example"), top.map { it.host })
        assertEquals("https://github.com/", top[0].url)
        assertEquals("GitHub", top[0].title)
    }

    @Test fun excludesAndLimits() {
        val visits = (1..20).map { v("https://site$it.com/", it) }
        assertEquals(8, TopSites.compute(visits, now).size)
        assertTrue(TopSites.compute(visits, now, exclude = setOf("site1.com")).none { it.host == "site1.com" })
    }

    @Test fun inlineCompletion() {
        val visits = listOf(v("https://www.github.com/x", 1), v("https://github.com/", 1), v("https://gitlab.com/", 1))
        assertEquals("github.com", Autocomplete.inline("git", visits))
        assertEquals("gitlab.com", Autocomplete.inline("gitl", visits))
        assertNull(Autocomplete.inline("g", visits))
        assertNull(Autocomplete.inline("git hub", visits))
        assertNull(Autocomplete.inline("github.com", visits))
    }

    @Test fun suggestionsPreferBookmarksAndHostPrefix() {
        val visits = listOf(v("https://kotlinlang.org/docs", 1, "Kotlin docs"), v("https://example.com/kotlin", 1, "About Kotlin"))
        val s = Autocomplete.suggest("kotlin", visits, listOf("Kotlin home" to "https://kotlinlang.org/"))
        assertEquals("https://kotlinlang.org/", s.first().url)
        assertTrue(s.first().isBookmark)
        assertEquals(3, s.size)
    }
}
