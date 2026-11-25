package app.freedombrowser.browser

import android.graphics.Bitmap
import android.net.http.SslError
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freedombrowser.AppContainer
import app.freedombrowser.R
import app.freedombrowser.data.BrowserSettings
import app.freedombrowser.data.HomepageMode
import app.freedombrowser.data.SecretStore
import app.freedombrowser.data.SessionTabEntity
import app.freedombrowser.downloads.DownloadRequest
import app.freedombrowser.shared.AiAssist
import app.freedombrowser.shared.Autocomplete
import app.freedombrowser.shared.TopSites
import app.freedombrowser.shared.Visit
import app.freedombrowser.shared.DisplayUrl
import app.freedombrowser.shared.FileNames
import app.freedombrowser.shared.HistoryRange
import app.freedombrowser.shared.PageErrorKind
import app.freedombrowser.shared.PermissionDecision
import app.freedombrowser.shared.ResolvedInput
import app.freedombrowser.shared.SitePermission
import app.freedombrowser.shared.UrlCodec
import app.freedombrowser.shared.UrlResolver
import app.freedombrowser.shared.WebUrl
import app.freedombrowser.util.Diagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns browser state (tabs, prompts, screens) and coordinates repositories with the page engine.
 * The engine is created through [engineFactory] so tests can substitute a fake.
 */
class BrowserViewModel(
    private val c: AppContainer,
    engineFactory: (EngineListener) -> TabEngine,
    private val privateSupported: Boolean = true,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel(), EngineListener {

    private val _state = MutableStateFlow(BrowserUiState(privateSupported = privateSupported))
    val state: StateFlow<BrowserUiState> = _state
    private val _messages = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val messages: SharedFlow<UiMessage> = _messages

    val engine: TabEngine = engineFactory(this)
    // Monotonic ids seeded from the clock so they never collide with restored ids from earlier sessions.
    private var nextId = clock()
    private val pageHosts = ConcurrentHashMap<Long, String>()
    private val certErrorTabs = HashSet<Long>()
    private val lastRecordedUrl = HashMap<Long, String>()
    @Volatile private var recentVisits: List<Visit> = emptyList()
    @Volatile private var bookmarkPairs: List<Pair<String, String>> = emptyList()
    private val json = Json { ignoreUnknownKeys = true }

    private val settings: BrowserSettings get() = _state.value.settings
    val tabs: TabList get() = _state.value.tabs

    init {
        viewModelScope.launch {
            c.settings.settings.collect { s ->
                _state.update { it.copy(settings = s, aiKeyConfigured = c.secrets.has(SecretStore.AI_API_KEY)) }
                c.privacy.adBlockEnabled = s.adBlockEnabled
                Diagnostics.enabled = s.diagnosticsLogging
                engine.applySettings(s)
            }
        }
        viewModelScope.launch {
            c.siteSettings.all().collect { list ->
                _state.update { it.copy(siteSettings = list.associateBy { s -> s.site }) }
                c.privacy.adBlockExceptions = list.filter { it.adBlockException }.map { it.site }.toSet()
            }
        }
        viewModelScope.launch { c.network.online.collect { online -> _state.update { it.copy(isOnline = online) } } }
        viewModelScope.launch { c.bookmarks.bookmarkedUrls().collect { urls -> _state.update { it.copy(bookmarkedUrls = urls) } } }
        viewModelScope.launch { restoreSession() }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(c.history.recent(3000), c.settings.settings) { h, s -> h to s }.collect { (h, s) ->
                recentVisits = h.asReversed().map { Visit(it.url, it.title, it.visitedAt) }
                val shortcuts = runCatching { json.decodeFromString<List<Shortcut>>(s.shortcutsJson) }.getOrDefault(emptyList())
                val hidden = runCatching { json.decodeFromString<List<String>>(s.hiddenSitesJson) }.getOrDefault(emptyList())
                val exclude = (hidden + shortcuts.mapNotNull { WebUrl.parse(it.url)?.siteKey }).toSet()
                val top = if (s.showTopSites) withContext(Dispatchers.Default) { TopSites.compute(recentVisits, clock(), 8, exclude) } else emptyList()
                _state.update { it.copy(shortcuts = shortcuts, topSites = top) }
            }
        }
        viewModelScope.launch { c.bookmarks.allBookmarks().collect { list -> bookmarkPairs = list.map { it.title to it.url!! } } }
        observeSessionForPersistence()
    }

    // region Session -------------------------------------------------------------------------

    private suspend fun restoreSession() {
        val s = c.settings.settings.first()
        val saved = if (s.restoreTabs) withContext(Dispatchers.IO) { c.sessionDao.all() } else emptyList()
        if (saved.isNotEmpty()) {
            nextId = maxOf(nextId, saved.maxOf { it.tabId } + 1)
            val restoredTabs = saved.map { TabState(id = it.tabId, isPrivate = false, url = it.url, title = it.title, desktopMode = it.desktopMode, security = DisplayUrl.securityLevel(it.url)) }
            val restoredSelected = (saved.firstOrNull { it.selected } ?: saved.last()).tabId
            // Tabs opened while restoring (e.g. from a VIEW intent) are kept and stay selected.
            _state.update { st ->
                st.copy(tabs = TabList(restoredTabs + st.tabs.tabs, st.tabs.selectedId ?: restoredSelected, st.tabs.recentlyClosed), restored = true)
            }
            tabs.selected?.let { if (it.url.isNotEmpty()) engine.ensure(it) }
        } else if (tabs.tabs.isEmpty()) {
            _state.update { it.copy(tabs = it.tabs.open(TabState(id = nextId++, isPrivate = false)), restored = true) }
        }
    }

    @OptIn(FlowPreview::class)
    private fun observeSessionForPersistence() {
        viewModelScope.launch {
            _state.map { st -> st.restored to st.tabs.normalTabs.map { Triple(it.id, it.url, it.title) } + (st.tabs.selectedId ?: -1L).let { id -> listOf(Triple(id, "", "")) } }
                .distinctUntilChanged()
                .debounce(800)
                .collect { (restored, _) -> if (restored) persistSession() }
        }
    }

    private suspend fun persistSession() {
        val list = tabs
        val rows = list.normalTabs.filter { it.url.isNotEmpty() }.mapIndexed { i, t ->
            SessionTabEntity(t.id, t.url, t.title, i, t.id == list.selectedId, t.desktopMode)
        }
        withContext(Dispatchers.IO) { c.sessionDao.replace(rows) }
    }

    // endregion
    // region Tabs & navigation ----------------------------------------------------------------

    fun openNewTab(url: String? = null, isPrivate: Boolean = false, background: Boolean = false, afterCurrent: Boolean = false): Long? {
        if (isPrivate && !privateSupported) {
            emit(UiMessage(R.string.private_unsupported))
            return null
        }
        val tab = TabState(id = nextId++, isPrivate = isPrivate, url = url.orEmpty(), security = url?.let { DisplayUrl.securityLevel(it) } ?: app.freedombrowser.shared.SecurityLevel.NONE, lastActive = clock())
        _state.update { it.copy(tabs = it.tabs.open(tab, background, if (afterCurrent) it.tabs.selectedId else null), screen = Screen.BROWSER) }
        if (url != null) {
            pageHosts[tab.id] = WebUrl.hostOf(url).orEmpty()
            engine.ensure(tab)
        }
        return tab.id
    }

    fun selectTab(id: Long) {
        captureCurrent()
        _state.update { it.copy(tabs = it.tabs.select(id, clock()), screen = Screen.BROWSER, find = null) }
        tabs.tab(id)?.let { if (it.url.isNotEmpty()) engine.ensure(it) }
    }

    fun closeTab(id: Long) {
        val closing = tabs.tab(id) ?: return
        engine.destroy(id)
        pageHosts.remove(id)
        certErrorTabs.remove(id)
        lastRecordedUrl.remove(id)
        _state.update { it.copy(tabs = it.tabs.close(id), find = if (it.tabs.selectedId == id) null else it.find) }
        afterTabsRemoved(closing.isPrivate)
        ensureOneTab()
        tabs.selected?.let { if (it.url.isNotEmpty()) engine.ensure(it) }
    }

    fun closeAllTabs(privateOnly: Boolean = false) {
        val closing = tabs.tabs.filter { !privateOnly || it.isPrivate }
        closing.forEach { engine.destroy(it.id); pageHosts.remove(it.id) }
        _state.update { it.copy(tabs = it.tabs.closeAll(privateOnly), find = null) }
        afterTabsRemoved(closing.any { it.isPrivate })
        ensureOneTab()
        tabs.selected?.let { if (it.url.isNotEmpty()) engine.ensure(it) }
    }

    fun reopenClosedTab() {
        val (list, closed) = tabs.popRecentlyClosed()
        if (closed == null) return
        _state.update { it.copy(tabs = list) }
        val id = openNewTab(closed.url) ?: return
        if (closed.desktopMode) toggleDesktopMode(id)
    }

    private fun afterTabsRemoved(privateClosed: Boolean) {
        if (privateClosed && tabs.privateTabs.isEmpty()) {
            engine.onPrivateSessionEnded()
            c.downloads.onPrivateSessionEnded()
        }
    }

    private fun ensureOneTab() {
        if (tabs.tabs.isEmpty()) openNewTab()
    }

    /** Handles text typed in the address bar or the start page. */
    fun submitInput(text: String, tabId: Long? = tabs.selectedId) {
        val target = when (val r = UrlResolver.resolve(text, settings.searchEngine)) {
            is ResolvedInput.Navigate -> r.url
            is ResolvedInput.Search -> r.url
            ResolvedInput.Empty -> return
        }
        val id = tabId ?: openNewTab() ?: return
        navigate(id, target)
    }

    fun navigate(tabId: Long, url: String) {
        certErrorTabs.remove(tabId)
        pageHosts[tabId] = WebUrl.hostOf(url).orEmpty()
        _state.update {
            it.copy(
                tabs = it.tabs.update(tabId) { t -> t.copy(url = url, error = null, isLoading = true, progress = 5, security = DisplayUrl.securityLevel(url), certificateError = false, blockedCount = 0) },
                screen = Screen.BROWSER,
            )
        }
        val tab = tabs.tab(tabId) ?: return
        if (engine.hasView(tabId)) engine.load(tabId, url) else engine.ensure(tab)
    }

    fun goBack(): Boolean {
        val tab = tabs.selected ?: return false
        if (tab.error != null && tab.canGoBack) {
            _state.update { it.copy(tabs = it.tabs.update(tab.id) { t -> t.copy(error = null) }) }
        }
        if (tab.canGoBack) { engine.goBack(tab.id); return true }
        if (tab.url.isNotEmpty() || tab.error != null) {
            // Back from the first page of a tab returns to the start page.
            engine.destroy(tab.id)
            pageHosts.remove(tab.id)
            _state.update { it.copy(tabs = it.tabs.update(tab.id) { t -> TabState(id = t.id, isPrivate = t.isPrivate, lastActive = t.lastActive) }) }
            return true
        }
        return false
    }

    fun goForward() { tabs.selected?.let { engine.goForward(it.id) } }

    fun reload() {
        val tab = tabs.selected ?: return
        val err = tab.error
        if (err != null || !engine.hasView(tab.id)) {
            navigate(tab.id, err?.url?.takeIf { it.isNotEmpty() } ?: tab.url)
        } else engine.reload(tab.id)
    }

    fun stop() { tabs.selected?.let { engine.stop(it.id); setTab(it.id) { t -> t.copy(isLoading = false, progress = 100) } } }

    fun goHome() {
        val tab = tabs.selected ?: return
        val home = settings.homepageUrl
        if (settings.homepageMode == HomepageMode.CUSTOM_URL && home.isNotBlank()) {
            (UrlResolver.resolve(home, settings.searchEngine) as? ResolvedInput.Navigate)?.let { return navigate(tab.id, it.url) }
        }
        engine.destroy(tab.id)
        pageHosts.remove(tab.id)
        setTab(tab.id) { TabState(id = it.id, isPrivate = it.isPrivate, lastActive = it.lastActive) }
    }

    fun toggleDesktopMode(tabId: Long? = tabs.selectedId) {
        val tab = tabId?.let(tabs::tab) ?: return
        val enabled = !tab.desktopMode
        setTab(tab.id) { it.copy(desktopMode = enabled) }
        engine.setDesktopMode(tab.id, enabled)
    }

    fun setTextZoom(percent: Int) {
        val tab = tabs.selected ?: return
        setTab(tab.id) { it.copy(textZoom = percent) }
        engine.setTextZoom(tab.id, percent)
    }

    fun show(screen: Screen) {
        if (screen == Screen.TABS) captureCurrent()
        _state.update { it.copy(screen = screen, find = if (screen == Screen.BROWSER) it.find else null) }
    }

    /** Stores a small preview of the current page for the tab switcher. */
    fun captureCurrent() {
        val tab = tabs.selected ?: return
        if (tab.url.isEmpty() || tab.error != null) return
        engine.capture(tab.id)?.let { bmp -> setTab(tab.id) { it.copy(thumbnail = bmp) } }
    }

    // ---- New-tab page: shortcuts and recently visited sites
    /** Atomic read-modify-write inside the DataStore transaction (rapid edits never overwrite each other). */
    private fun editShortcuts(transform: (List<Shortcut>) -> List<Shortcut>) = updateSettings {
        val current = runCatching { json.decodeFromString<List<Shortcut>>(it.shortcutsJson) }.getOrDefault(emptyList())
        it.copy(shortcutsJson = json.encodeToString(transform(current)))
    }

    /** Returns false when the address is not a valid web address. */
    fun addShortcut(title: String, url: String): Boolean {
        val valid = app.freedombrowser.shared.BookmarkTree.validateUrl(url) ?: return false
        val id = "${clock()}-${(nextShortcutSeq++)}"
        editShortcuts { it + Shortcut(id, title.trim().ifBlank { DisplayUrl.format(valid) }, valid) }
        return true
    }
    private var nextShortcutSeq = 0
    fun removeShortcut(id: String) = editShortcuts { list -> list.filter { it.id != id } }
    fun moveShortcut(id: String, delta: Int) = editShortcuts { current ->
        val list = current.toMutableList()
        val i = list.indexOfFirst { it.id == id }
        if (i < 0) return@editShortcuts current
        val j = (i + delta).coerceIn(0, list.lastIndex)
        list.add(j, list.removeAt(i))
        list
    }
    fun hideTopSite(host: String) = updateSettings {
        val hidden = runCatching { json.decodeFromString<List<String>>(it.hiddenSitesJson) }.getOrDefault(emptyList())
        it.copy(hiddenSitesJson = json.encodeToString((hidden + host).distinct()))
    }

    // ---- Address bar
    fun suggestions(query: String): List<Autocomplete.Suggestion> = Autocomplete.suggest(query, recentVisits.asReversed(), bookmarkPairs, 6)
    fun inlineCompletion(typed: String): String? = Autocomplete.inline(typed, recentVisits, bookmarkPairs)

    // endregion
    // region Find in page ----------------------------------------------------------------------

    fun openFind() = _state.update { it.copy(find = FindState()) }
    fun find(query: String) {
        val tab = tabs.selected ?: return
        _state.update { it.copy(find = FindState(query)) }
        if (query.isEmpty()) engine.clearFind(tab.id) else engine.find(tab.id, query)
    }
    fun findNext(forward: Boolean) { tabs.selected?.let { engine.findNext(it.id, forward) } }
    fun closeFind() {
        tabs.selected?.let { engine.clearFind(it.id) }
        _state.update { it.copy(find = null) }
    }

    // endregion
    // region Bookmarks, site settings, data ----------------------------------------------------

    fun toggleBookmark() {
        val tab = tabs.selected ?: return
        if (tab.url.isEmpty()) return
        viewModelScope.launch {
            val existing = c.bookmarks.findByUrl(tab.url)
            if (existing != null) {
                c.bookmarks.delete(existing.id)
                emit(UiMessage(R.string.bookmark_removed))
            } else {
                c.bookmarks.addBookmark(tab.title.ifBlank { DisplayUrl.format(tab.url) }, tab.url)
                emit(UiMessage(R.string.bookmark_added))
            }
        }
    }

    fun setAdBlockException(site: String, exempt: Boolean) = viewModelScope.launch {
        c.siteSettings.setAdBlockException(site, exempt)
        if (tabs.selected?.let { pageHosts[it.id]?.removePrefix("www.") } == site) reload()
    }

    fun setSitePermission(site: String, permission: SitePermission, decision: PermissionDecision) = viewModelScope.launch {
        c.siteSettings.setPermission(site, permission, decision)
    }

    fun resetSite(site: String) = viewModelScope.launch { c.siteSettings.reset(site) }

    fun updateSettings(transform: (BrowserSettings) -> BrowserSettings) = viewModelScope.launch { c.settings.update(transform) }

    data class ClearOptions(val history: Boolean, val cookies: Boolean, val cache: Boolean, val downloads: Boolean, val sitePermissions: Boolean)

    /** Clears the selected data. [clearEngineData] runs WebView-specific cleanup on the main thread. */
    fun clearBrowsingData(range: HistoryRange, options: ClearOptions, clearEngineData: (cookies: Boolean, cache: Boolean) -> Unit) = viewModelScope.launch {
        if (options.history) c.history.clear(range)
        if (options.downloads) c.downloads.clearFinished()
        if (options.sitePermissions) c.siteSettings.clearAll()
        if (options.cookies || options.cache) clearEngineData(options.cookies, options.cache)
        emit(UiMessage(R.string.data_cleared))
    }

    fun updateBlockList() = viewModelScope.launch {
        try {
            val n = c.blockLists.update(settings.blockListUrl)
            c.settings.update { it.copy(blockListUpdatedAt = clock()) }
            emit(UiMessage(R.string.blocklist_updated, listOf(n)))
        } catch (e: Exception) {
            emit(UiMessage(R.string.blocklist_update_failed, listOf(e.message ?: e.javaClass.simpleName)))
        }
    }

    fun removeCustomBlockList() = viewModelScope.launch {
        c.blockLists.removeCustom()
        c.settings.update { it.copy(blockListUpdatedAt = 0) }
    }

    // endregion
    // region Downloads --------------------------------------------------------------------------

    fun confirmDownload(pending: PendingDownload, fileName: String) {
        dismissPrompt()
        viewModelScope.launch {
            c.downloads.enqueue(
                DownloadRequest(
                    url = pending.url,
                    fileName = FileNames.sanitize(fileName.ifBlank { pending.fileName }),
                    mimeType = pending.mimeType,
                    userAgent = pending.userAgent,
                    referer = pending.referer,
                    isPrivate = pending.isPrivate,
                    targetTree = settings.downloadTreeUri.ifEmpty { null },
                ),
            )
            emit(UiMessage(R.string.download_started, listOf(fileName), R.string.downloads_title) { show(Screen.DOWNLOADS) })
        }
    }

    fun downloadUrl(url: String, tabId: Long? = tabs.selectedId) {
        val tab = tabId?.let(tabs::tab)
        onDownloadRequested(tab?.id ?: -1, url, null, null, null, -1)
    }

    // endregion
    // region Translation & AI ------------------------------------------------------------------

    fun requestTranslate() {
        val tab = tabs.selected ?: return
        if (tab.url.isEmpty()) return
        val target = settings.translateTemplate
            .replace("{lang}", Locale.getDefault().language)
            .replace("{url}", UrlCodec.encodeQueryComponent(tab.url))
        val host = WebUrl.hostOf(target) ?: return emit(UiMessage(R.string.translate_invalid_provider))
        _state.update { it.copy(prompt = Prompt.Translate(target, host)) }
    }

    fun confirmTranslate(url: String) {
        dismissPrompt()
        openNewTab(url, isPrivate = tabs.selected?.isPrivate == true, afterCurrent = true)
    }

    fun requestAi(task: AiAssist.Task) {
        val s = settings
        if (!s.aiEnabled || s.aiEndpoint.isBlank() || s.aiModel.isBlank()) return emit(UiMessage(R.string.ai_not_configured))
        val host = WebUrl.hostOf(s.aiEndpoint) ?: return emit(UiMessage(R.string.ai_not_configured))
        _state.update { it.copy(prompt = Prompt.AiConsent(task, host)) }
    }

    /** Runs only after the user confirmed the disclosure dialog. */
    fun runAi(task: AiAssist.Task, question: String?) {
        val tab = tabs.selected ?: return
        val s = settings
        _state.update { it.copy(prompt = Prompt.AiResult(tab.title, null, loading = true, error = null)) }
        engine.evaluate(tab.id, PAGE_TEXT_SCRIPT) { raw ->
            val text = raw?.let { runCatching { Json.parseToJsonElement(it).jsonPrimitive.content }.getOrNull() }.orEmpty()
            viewModelScope.launch {
                val result = runCatching {
                    withContext(Dispatchers.IO) {
                        require(AiAssist.isAcceptableEndpoint(s.aiEndpoint)) { "Endpoint must use HTTPS" }
                        val key = c.secrets.get(SecretStore.AI_API_KEY)
                        val body = AiAssist.buildRequestBody(s.aiModel, task, tab.title, text, question)
                        val req = Request.Builder().url(s.aiEndpoint).post(body.toRequestBody("application/json".toMediaType()))
                        if (!key.isNullOrBlank()) req.header("Authorization", "Bearer $key")
                        c.http.newCall(req.build()).execute().use { resp ->
                            val payload = resp.body.string()
                            if (!resp.isSuccessful) error("HTTP ${resp.code}")
                            AiAssist.parseResponse(payload) ?: error("Unexpected response")
                        }
                    }
                }
                _state.update { st ->
                    if (st.prompt !is Prompt.AiResult) st
                    else st.copy(prompt = Prompt.AiResult(tab.title, result.getOrNull(), loading = false, error = result.exceptionOrNull()?.message))
                }
            }
        }
    }

    fun setAiKey(key: String?) {
        c.secrets.put(SecretStore.AI_API_KEY, key)
        _state.update { it.copy(aiKeyConfigured = !key.isNullOrBlank()) }
    }

    // endregion
    // region Prompts ----------------------------------------------------------------------------

    fun dismissPrompt() = _state.update { it.copy(prompt = null) }

    /** Called when a permission prompt is answered; optionally remembers the decision per site. */
    fun answerPermission(prompt: Prompt.Permission, allow: Boolean, remember: Boolean, granted: Set<SitePermission>, grantProtected: Boolean) {
        if (remember) viewModelScope.launch {
            prompt.permissions.forEach { p -> c.siteSettings.setPermission(prompt.site, p, if (allow) PermissionDecision.ALLOW else PermissionDecision.BLOCK) }
        }
        prompt.respond(if (allow) granted else emptySet(), allow && grantProtected)
        if (_state.value.prompt === prompt) dismissPrompt()
    }

    private fun emit(m: UiMessage) { _messages.tryEmit(m) }

    private fun setTab(id: Long, f: (TabState) -> TabState) = _state.update { it.copy(tabs = it.tabs.update(id, f)) }

    fun onTrimMemory(aggressive: Boolean) = engine.trimMemory(tabs.selectedId, aggressive)

    // endregion
    // region EngineListener ------------------------------------------------------------------

    override fun onPageStarted(tabId: Long, url: String) {
        pageHosts[tabId] = WebUrl.hostOf(url).orEmpty()
        val cert = certErrorTabs.contains(tabId)
        setTab(tabId) { it.copy(url = url, isLoading = true, error = null, security = DisplayUrl.securityLevel(url, cert), blockedCount = 0) }
    }

    override fun onPageFinished(tabId: Long, url: String) = setTab(tabId) { it.copy(isLoading = false, progress = 100) }

    override fun onProgress(tabId: Long, progress: Int) = setTab(tabId) { it.copy(progress = progress, isLoading = progress < 100) }

    override fun onTitle(tabId: Long, title: String) {
        val tab = tabs.tab(tabId) ?: return
        setTab(tabId) { it.copy(title = title) }
        viewModelScope.launch { c.history.updateTitle(tab.url, title, tab.isPrivate) }
    }

    override fun onFavicon(tabId: Long, icon: Bitmap) = setTab(tabId) { it.copy(favicon = icon) }

    override fun onHistoryUpdate(tabId: Long, url: String, canGoBack: Boolean, canGoForward: Boolean) {
        val tab = tabs.tab(tabId) ?: return
        val changed = tab.url != url
        pageHosts[tabId] = WebUrl.hostOf(url).orEmpty()
        setTab(tabId) { it.copy(url = url, canGoBack = canGoBack, canGoForward = canGoForward, security = if (changed) DisplayUrl.securityLevel(url, it.certificateError) else it.security) }
        // One history entry per committed URL (also catches same-document navigations). Private tabs never record.
        if (tab.error == null && !tab.isPrivate && lastRecordedUrl[tabId] != url) {
            lastRecordedUrl[tabId] = url
            viewModelScope.launch { c.history.record(url, tab.title.takeIf { !changed }.orEmpty(), isPrivate = false) }
        }
    }

    override fun onMainFrameError(tabId: Long, error: PageError) {
        Diagnostics.log("page.error", error.url, error.kind.id + " " + error.code)
        setTab(tabId) { it.copy(error = error, isLoading = false, progress = 100) }
    }

    override fun onSslError(tabId: Long, error: SslError, isMainFrame: Boolean) {
        if (!isMainFrame) return
        certErrorTabs.add(tabId)
        val reason = when (error.primaryError) {
            SslError.SSL_EXPIRED -> "Certificate expired"
            SslError.SSL_NOTYETVALID -> "Certificate not yet valid"
            SslError.SSL_IDMISMATCH -> "Certificate name mismatch"
            SslError.SSL_UNTRUSTED -> "Untrusted certificate authority"
            SslError.SSL_DATE_INVALID -> "Certificate date invalid"
            else -> "Invalid certificate"
        }
        setTab(tabId) { it.copy(error = PageError(PageErrorKind.CERTIFICATE, error.primaryError, reason, error.url), certificateError = true, security = app.freedombrowser.shared.SecurityLevel.CERT_ERROR, isLoading = false) }
    }

    override fun onRendererGone(tabId: Long, crashed: Boolean) {
        val tab = tabs.tab(tabId) ?: return
        setTab(tabId) { it.copy(error = PageError(PageErrorKind.RENDERER_CRASH, if (crashed) 1 else 0, if (crashed) "Renderer crashed" else "Renderer stopped to free memory", tab.url), isLoading = false) }
    }

    override fun onRequestBlocked(tabId: Long) = setTab(tabId) { it.copy(blockedCount = it.blockedCount + 1) }

    override fun onDownloadRequested(tabId: Long, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long) {
        val tab = tabs.tab(tabId)
        if (url.startsWith("blob:", true)) return emit(UiMessage(R.string.download_blob_unsupported))
        if (!url.startsWith("http", true) && !url.startsWith("data:", true)) return emit(UiMessage(R.string.download_unsupported_scheme))
        val mime = mimeType?.takeIf { it.isNotBlank() && it != "application/octet-stream" } ?: mimeType
        val name = if (url.startsWith("data:", true)) FileNames.sanitize("download." + (FileNames.mimeExtension(url.substring(5).substringBefore(';').substringBefore(',')) ?: "bin"))
        else FileNames.guess(url, contentDisposition, mime)
        val pending = PendingDownload(
            tabId = tabId, url = url, fileName = name, mimeType = mime, contentLength = contentLength,
            userAgent = userAgent, referer = tab?.url?.takeIf { it.startsWith("http") }, isPrivate = tab?.isPrivate == true,
            dangerous = FileNames.isPotentiallyDangerous(name, mime),
        )
        if (pending.dangerous || settings.askDownloadLocation) _state.update { it.copy(prompt = Prompt.Download(pending)) }
        else confirmDownload(pending, name)
    }

    override fun onCreateWindow(openerTabId: Long, userGesture: Boolean): Long? {
        val opener = tabs.tab(openerTabId) ?: return null
        val site = WebUrl.parse(opener.url)?.siteKey.orEmpty()
        val allowed = _state.value.siteSettings[site]?.decision(SitePermission.POPUPS) == PermissionDecision.ALLOW
        if (!userGesture && settings.blockPopups && !allowed) {
            emit(UiMessage(R.string.popup_blocked, listOf(site), R.string.allow_always) { setSitePermission(site, SitePermission.POPUPS, PermissionDecision.ALLOW) })
            return null
        }
        val tab = TabState(id = nextId++, isPrivate = opener.isPrivate, lastActive = clock(), url = "")
        _state.update { it.copy(tabs = it.tabs.open(tab, afterId = openerTabId), screen = Screen.BROWSER) }
        return tab.id
    }

    override fun onCloseWindow(tabId: Long) = closeTab(tabId)

    override fun onPermissionRequest(tabId: Long, origin: String, permissions: Set<SitePermission>, protectedMedia: Boolean, respond: (Set<SitePermission>, Boolean) -> Unit) {
        val site = WebUrl.parse(origin)?.siteKey ?: origin
        val siteSettings = _state.value.siteSettings[site]
        val blocked = permissions.filter { siteSettings?.decision(it) == PermissionDecision.BLOCK }.toSet()
        val remaining = permissions - blocked
        if (remaining.isEmpty() && !protectedMedia) return respond(emptySet(), false)
        val preApproved = remaining.isNotEmpty() && remaining.all { siteSettings?.decision(it) == PermissionDecision.ALLOW } && !protectedMedia
        _state.update { it.copy(prompt = Prompt.Permission(tabId, site, remaining, protectedMedia, preApproved, respond)) }
    }

    override fun onPermissionRequestCanceled(tabId: Long) {
        if ((_state.value.prompt as? Prompt.Permission)?.tabId == tabId) dismissPrompt()
    }

    override fun onHttpAuthRequest(tabId: Long, host: String, realm: String, proceed: (String, String) -> Unit, cancel: () -> Unit) {
        _state.update { it.copy(prompt = Prompt.HttpAuth(host, realm, proceed, cancel)) }
    }

    override fun onExternalLink(tabId: Long, url: String) = _state.update { it.copy(prompt = Prompt.ExternalApp(url)) }

    override fun onLongPress(tabId: Long, target: LongPressTarget) = _state.update { it.copy(prompt = Prompt.LongPress(tabId, target)) }

    override fun onFindResult(tabId: Long, activeIndex: Int, total: Int) {
        if (tabId != tabs.selectedId) return
        _state.update { st -> st.find?.let { st.copy(find = it.copy(active = activeIndex, total = total)) } ?: st }
    }

    override fun pageHostFor(tabId: Long): String? = pageHosts[tabId]

    // endregion

    override fun onCleared() {
        tabs.tabs.forEach { engine.destroy(it.id) }
        super.onCleared()
    }

    companion object {
        /** Read-only: returns visible text; no data is written to the page. */
        const val PAGE_TEXT_SCRIPT = "(function(){try{return (document.body&&document.body.innerText||'').slice(0,20000);}catch(e){return '';}})()"
    }
}
