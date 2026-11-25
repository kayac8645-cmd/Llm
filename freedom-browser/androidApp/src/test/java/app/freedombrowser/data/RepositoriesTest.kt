package app.freedombrowser.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.freedombrowser.shared.HistoryRange
import app.freedombrowser.shared.PermissionDecision
import app.freedombrowser.shared.SitePermission
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RepositoriesTest {
    private lateinit var db: FreedomDatabase
    private var now = 1_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), FreedomDatabase::class.java).allowMainThreadQueries().build()
    }

    @After fun tearDown() = db.close()

    @Test fun privateVisitsAreNeverStored() = runBlocking {
        val history = HistoryRepository(db.history()) { now }
        history.record("https://secret.example/", "Secret", isPrivate = true)
        history.updateTitle("https://secret.example/", "Secret", isPrivate = true)
        assertEquals(0, db.history().count())
        history.record("https://public.example/", "Public", isPrivate = false)
        assertEquals(1, db.history().count())
    }

    @Test fun historyCollapsesRepeatsAndIgnoresNonWebUrls() = runBlocking {
        val history = HistoryRepository(db.history()) { now }
        history.record("https://a.com/", "", false)
        history.record("https://a.com/", "A title", false)
        history.record("about:blank", "", false)
        history.record("data:text/html,x", "", false)
        val all = history.recent().first()
        assertEquals(1, all.size)
        assertEquals("A title", all[0].title)
    }

    @Test fun clearHistoryByRange() = runBlocking {
        val history = HistoryRepository(db.history()) { now }
        db.history().insert(HistoryEntity(url = "https://old.com/", title = "old", visitedAt = now - 3 * 86_400_000L))
        db.history().insert(HistoryEntity(url = "https://recent.com/", title = "recent", visitedAt = now - 60_000L))
        history.clear(HistoryRange.LAST_HOUR)
        assertEquals(listOf("https://old.com/"), history.recent().first().map { it.url })
        history.clear(HistoryRange.ALL_TIME)
        assertEquals(0, db.history().count())
    }

    @Test fun historySearchMatchesTitleAndUrl() = runBlocking {
        val history = HistoryRepository(db.history()) { now }
        history.record("https://kotlinlang.org/docs", "Kotlin Docs", false)
        now += 60_000
        history.record("https://example.com/", "Example", false)
        assertEquals(1, history.search("kotlin").first().size)
        assertEquals(1, history.search("example.com").first().size)
        assertEquals(2, history.search("").first().size)
    }

    @Test fun bookmarksFoldersAndCyclePrevention() = runBlocking {
        val repo = BookmarkRepository(db.bookmarks())
        val a = (repo.addFolder("A") as BookmarkRepository.Result.Ok).id
        val b = (repo.addFolder("B", a) as BookmarkRepository.Result.Ok).id
        val bm = (repo.addBookmark("Ex", "example.com", b) as BookmarkRepository.Result.Ok).id
        assertEquals("https://example.com/", db.bookmarks().get(bm)!!.url)
        // Moving A into its own child B must be rejected.
        assertEquals(BookmarkRepository.Result.Invalid("cycle"), repo.edit(a, "A", null, b))
        // Moving B to the root is fine.
        assertTrue(repo.edit(b, "B2", null, 0) is BookmarkRepository.Result.Ok)
        assertEquals(0L, db.bookmarks().get(b)!!.parentId)
        // Invalid URLs are rejected; duplicates return the existing id.
        assertTrue(repo.addBookmark("x", "javascript:alert(1)") is BookmarkRepository.Result.Invalid)
        assertEquals(BookmarkRepository.Result.Ok(bm), repo.addBookmark("dup", "https://example.com/"))
        // Deleting a folder deletes its subtree.
        repo.delete(b)
        assertNull(db.bookmarks().get(bm))
        assertTrue(db.bookmarks().get(a) != null)
    }

    @Test fun siteSettingsRoundTripAndCleanup() = runBlocking {
        val repo = SiteSettingsRepository(db.siteSettings())
        repo.setPermission("news.com", SitePermission.CAMERA, PermissionDecision.BLOCK)
        repo.setAdBlockException("news.com", true)
        val s = repo.get("news.com")
        assertEquals(PermissionDecision.BLOCK, s.decision(SitePermission.CAMERA))
        assertTrue(s.adBlockException)
        repo.setPermission("news.com", SitePermission.CAMERA, PermissionDecision.ASK)
        repo.setAdBlockException("news.com", false)
        // Back to defaults: the row is removed.
        assertTrue(repo.all().first().isEmpty())
        assertFalse(repo.get("news.com").adBlockException)
    }
}
