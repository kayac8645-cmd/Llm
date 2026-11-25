package app.freedombrowser.browser

import androidx.test.core.app.ApplicationProvider
import app.freedombrowser.AppContainer
import app.freedombrowser.data.BrowserSettings
import app.freedombrowser.shared.PageErrorKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import android.os.Looper

/** Records engine commands instead of driving a WebView. */
class FakeEngine : TabEngine {
    val live = LinkedHashSet<Long>()
    val loads = ArrayList<Pair<Long, String>>()
    val destroyed = ArrayList<Long>()
    var privateSessionsEnded = 0
    var desktop = HashMap<Long, Boolean>()
    override fun ensure(tab: TabState) { if (live.add(tab.id) && tab.url.isNotEmpty()) loads += tab.id to tab.url }
    override fun load(tabId: Long, url: String) { loads += tabId to url }
    override fun goBack(tabId: Long) {}
    override fun goForward(tabId: Long) {}
    override fun reload(tabId: Long) {}
    override fun stop(tabId: Long) {}
    override fun destroy(tabId: Long) { live.remove(tabId); destroyed += tabId }
    override fun hasView(tabId: Long) = tabId in live
    override fun setDesktopMode(tabId: Long, enabled: Boolean) { desktop[tabId] = enabled }
    override fun setTextZoom(tabId: Long, percent: Int) {}
    override fun find(tabId: Long, query: String) {}
    override fun findNext(tabId: Long, forward: Boolean) {}
    override fun clearFind(tabId: Long) {}
    override fun applySettings(settings: BrowserSettings) {}
    override fun trimMemory(keepTabId: Long?, aggressive: Boolean) {}
    override fun evaluate(tabId: Long, script: String, callback: (String?) -> Unit) = callback("\"\"")
    override fun onPrivateSessionEnded() { privateSessionsEnded++ }
    override fun capture(tabId: Long): android.graphics.Bitmap? = null
}

@RunWith(RobolectricTestRunner::class)
class BrowserViewModelTest {
    private lateinit var container: AppContainer
    private lateinit var engine: FakeEngine
    private lateinit var vm: BrowserViewModel

    @Before fun setUp() {
        container = AppContainer(ApplicationProvider.getApplicationContext())
        runBlocking {
            container.sessionDao.clear()
            container.settings.update { BrowserSettings() }
        }
        engine = FakeEngine()
        vm = BrowserViewModel(container, { engine }, privateSupported = true)
        settle()
    }

    /** Lets Room/DataStore coroutines and main-thread work finish. */
    private fun settle(predicate: () -> Boolean = { true }) {
        val end = System.currentTimeMillis() + 5_000
        do {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(15)
            shadowOf(Looper.getMainLooper()).idle()
        } while (!(predicate() && vm.state.value.restored) && System.currentTimeMillis() < end)
    }

    @Test fun startsWithOneStartPageTab() {
        val tabs = vm.state.value.tabs
        assertEquals(1, tabs.tabs.size)
        assertTrue(tabs.selected!!.isStartPage)
        assertTrue(engine.live.isEmpty()) // no WebView until the user navigates
    }

    @Test fun addressInputNavigatesOrSearches() {
        vm.submitInput("example.com")
        val id = vm.state.value.tabs.selectedId!!
        assertEquals(id to "https://example.com/", engine.loads.last())
        vm.submitInput("hello world")
        assertEquals("https://duckduckgo.com/?q=hello%20world", engine.loads.last().second)
    }

    @Test fun privateTabNeverWritesHistory() {
        val pid = vm.openNewTab("https://private.example/", isPrivate = true)!!
        vm.onPageStarted(pid, "https://private.example/")
        vm.onTitle(pid, "Private page")
        vm.onHistoryUpdate(pid, "https://private.example/", false, false)
        vm.onPageFinished(pid, "https://private.example/")
        val nid = vm.openNewTab("https://normal.example/")!!
        vm.onPageStarted(nid, "https://normal.example/")
        vm.onHistoryUpdate(nid, "https://normal.example/", false, false)
        settle { runBlocking { container.history.recent().first().isNotEmpty() } }
        val urls = runBlocking { container.history.recent().first().map { it.url } }
        assertEquals(listOf("https://normal.example/"), urls)
    }

    @Test fun closingLastPrivateTabEndsPrivateSession() {
        val p1 = vm.openNewTab("https://a.example/", isPrivate = true)!!
        val p2 = vm.openNewTab("https://b.example/", isPrivate = true)!!
        vm.closeTab(p1)
        assertEquals(0, engine.privateSessionsEnded)
        vm.closeTab(p2)
        assertEquals(1, engine.privateSessionsEnded)
        assertTrue(vm.state.value.tabs.recentlyClosed.none { it.url.contains("example") })
    }

    @Test fun closingAllTabsLeavesAFreshStartPage() {
        vm.openNewTab("https://a.example/")
        vm.openNewTab("https://b.example/")
        vm.closeAllTabs()
        val tabs = vm.state.value.tabs
        assertEquals(1, tabs.tabs.size)
        assertTrue(tabs.selected!!.isStartPage)
        assertEquals(2, tabs.recentlyClosed.size)
        vm.reopenClosedTab()
        assertEquals("https://b.example/", vm.state.value.tabs.selected!!.url)
    }

    @Test fun popupsWithoutGestureAreBlocked() {
        val id = vm.openNewTab("https://site.example/")!!
        assertNull(vm.onCreateWindow(id, userGesture = false))
        val popup = vm.onCreateWindow(id, userGesture = true)
        assertNotNull(popup)
        assertEquals(popup, vm.state.value.tabs.selectedId)
    }

    @Test fun dangerousDownloadsRequireConfirmation() {
        val id = vm.openNewTab("https://site.example/")!!
        vm.onDownloadRequested(id, "https://site.example/setup.exe", "UA", null, "application/octet-stream", 1000)
        val prompt = vm.state.value.prompt as Prompt.Download
        assertTrue(prompt.pending.dangerous)
        assertEquals("setup.exe", prompt.pending.fileName)
        vm.dismissPrompt()
        vm.onDownloadRequested(id, "blob:https://site.example/1", null, null, null, -1)
        assertNull(vm.state.value.prompt)
    }

    @Test fun errorsAreShownAndClearedByRetry() {
        val id = vm.openNewTab("https://down.example/")!!
        vm.onMainFrameError(id, PageError(PageErrorKind.DNS, -2, "ERR_NAME_NOT_RESOLVED", "https://down.example/"))
        assertEquals(PageErrorKind.DNS, vm.state.value.tabs.selected!!.error!!.kind)
        vm.reload()
        assertNull(vm.state.value.tabs.selected!!.error)
        assertEquals(id to "https://down.example/", engine.loads.last())
    }

    @Test fun desktopModeTogglesPerTab() {
        val id = vm.openNewTab("https://site.example/")!!
        vm.toggleDesktopMode(id)
        assertEquals(true, engine.desktop[id])
        assertTrue(vm.state.value.tabs.tab(id)!!.desktopMode)
        vm.toggleDesktopMode(id)
        assertFalse(vm.state.value.tabs.tab(id)!!.desktopMode)
    }

    @Test fun blockedRequestsAreCountedPerPage() {
        val id = vm.openNewTab("https://site.example/")!!
        vm.onRequestBlocked(id)
        vm.onRequestBlocked(id)
        assertEquals(2, vm.state.value.tabs.tab(id)!!.blockedCount)
        vm.onPageStarted(id, "https://other.example/")
        assertEquals(0, vm.state.value.tabs.tab(id)!!.blockedCount)
    }

    @Test fun privacyStateFollowsSettingsAndSiteExceptions() {
        runBlocking { container.settings.update { it.copy(adBlockEnabled = false) } }
        settle { !container.privacy.adBlockEnabled }
        assertFalse(container.privacy.adBlockEnabled)
        vm.setAdBlockException("news.com", true)
        settle { "news.com" in container.privacy.adBlockExceptions }
        assertTrue("news.com" in container.privacy.adBlockExceptions)
    }

    @Test fun shortcutsAddMoveRemove() {
        assertTrue(vm.addShortcut("Kotlin", "kotlinlang.org"))
        assertFalse(vm.addShortcut("Bad", "javascript:alert(1)"))
        assertTrue(vm.addShortcut("", "example.com"))
        settle { vm.state.value.shortcuts.size == 2 }
        assertEquals(listOf("https://kotlinlang.org/", "https://example.com/"), vm.state.value.shortcuts.map { it.url })
        val second = vm.state.value.shortcuts[1].id
        vm.moveShortcut(second, -1)
        settle { vm.state.value.shortcuts.firstOrNull()?.id == second }
        assertEquals("https://example.com/", vm.state.value.shortcuts.first().url)
        vm.removeShortcut(second)
        settle { vm.state.value.shortcuts.size == 1 }
        assertEquals(1, vm.state.value.shortcuts.size)
    }

    @Test fun topSitesAndSuggestionsComeFromHistory() {
        val id = vm.openNewTab("https://github.com/")!!
        vm.onHistoryUpdate(id, "https://github.com/", false, false)
        vm.onTitle(id, "GitHub")
        settle { vm.state.value.topSites.isNotEmpty() }
        assertEquals("github.com", vm.state.value.topSites.first().host)
        assertEquals("github.com", vm.inlineCompletion("git"))
        assertEquals("https://github.com/", vm.suggestions("github").first().url)
        vm.hideTopSite("github.com")
        settle { vm.state.value.topSites.isEmpty() }
        assertTrue(vm.state.value.topSites.isEmpty())
    }
}
