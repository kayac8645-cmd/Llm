package app.freedombrowser.browser

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.HttpAuthHandler
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.ProfileStore
import androidx.webkit.UserAgentMetadata
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import app.freedombrowser.data.BrowserSettings
import app.freedombrowser.privacy.PrivacyState
import app.freedombrowser.shared.PageErrors
import app.freedombrowser.shared.SitePermission
import app.freedombrowser.shared.SiteTweaks
import app.freedombrowser.util.Diagnostics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.ByteArrayInputStream

/** Pending `<input type=file>` request the Activity must fulfil. */
class FileChooserRequest(val params: WebChromeClient.FileChooserParams, val callback: ValueCallback<Array<Uri>?>)

/** Fullscreen element (e.g. video) the Activity should show. */
class CustomViewRequest(val view: View, val callback: WebChromeClient.CustomViewCallback)

/**
 * Android implementation of [TabEngine]: one [WebView] per live tab.
 *
 * - Views are created with a [MutableContextWrapper] so they can follow the current Activity.
 * - At most [maxLiveViews] views stay alive; older background tabs are discarded (their
 *   back/forward state kept in memory) and restored on demand.
 * - Private tabs use a separate WebView profile (cookies, storage, cache) when the installed
 *   WebView supports MULTI_PROFILE; the profile is deleted when the last private tab closes.
 * - No JavaScript interface is ever added to any WebView.
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewManager(
    appContext: Context,
    private val listener: EngineListener,
    private val privacy: PrivacyState,
    private val isOnline: () -> Boolean,
) : TabEngine {
    private val contextWrapper = MutableContextWrapper(appContext)
    private val appContext = appContext.applicationContext
    private val views = LinkedHashMap<Long, WebView>(16, 0.75f, true) // access-ordered (LRU)
    private val savedStates = HashMap<Long, Bundle>()
    private val privateTabs = HashSet<Long>()
    private val desktopTabs = HashSet<Long>()
    private val ddgScripts = HashMap<Long, androidx.webkit.ScriptHandler>()
    private val documentStartScripts = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var settings = BrowserSettings()
    private val defaultUserAgent: String = WebSettings.getDefaultUserAgent(appContext)

    private val _fileChooser = MutableStateFlow<FileChooserRequest?>(null)
    val fileChooser: StateFlow<FileChooserRequest?> = _fileChooser
    private val _customView = MutableStateFlow<CustomViewRequest?>(null)
    val customView: StateFlow<CustomViewRequest?> = _customView

    val maxLiveViews: Int = run {
        val am = appContext.getSystemService(ActivityManager::class.java)
        val budget = if (am.isLowRamDevice) 2 else (am.memoryClass / 48).coerceIn(2, 8)
        budget
    }

    val supportsPrivateProfiles: Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)

    init {
        // A crash may have left the previous private profile behind; remove it.
        if (supportsPrivateProfiles) runCatching { ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE) }
    }

    fun attach(activity: Context) { contextWrapper.baseContext = activity }
    fun detach() { contextWrapper.baseContext = appContext }

    fun view(tabId: Long): WebView? = views[tabId]

    override fun hasView(tabId: Long) = views.containsKey(tabId)

    override fun ensure(tab: TabState) {
        if (views.containsKey(tab.id)) { views[tab.id]; return }
        val wv = create(tab.id, tab.isPrivate, tab.desktopMode, tab.textZoom)
        val state = savedStates.remove(tab.id)
        if (state != null && wv.restoreState(state) != null) return
        if (tab.url.isNotEmpty()) wv.loadUrl(tab.url)
        enforceBudget(keep = tab.id)
    }

    override fun load(tabId: Long, url: String) {
        val wv = views[tabId] ?: return
        wv.loadUrl(url)
    }

    override fun goBack(tabId: Long) { views[tabId]?.takeIf { it.canGoBack() }?.goBack() }
    override fun goForward(tabId: Long) { views[tabId]?.takeIf { it.canGoForward() }?.goForward() }
    override fun reload(tabId: Long) { views[tabId]?.reload() }
    override fun stop(tabId: Long) { views[tabId]?.stopLoading() }

    override fun destroy(tabId: Long) {
        ddgScripts.remove(tabId)
        savedStates.remove(tabId)
        desktopTabs.remove(tabId)
        val wv = views.remove(tabId) ?: run { privateTabs.remove(tabId); return }
        (wv.parent as? ViewGroup)?.removeView(wv)
        wv.stopLoading()
        wv.webChromeClient = null
        wv.destroy()
        privateTabs.remove(tabId)
    }

    override fun setDesktopMode(tabId: Long, enabled: Boolean) {
        if (enabled) desktopTabs.add(tabId) else desktopTabs.remove(tabId)
        val wv = views[tabId] ?: return
        applyUserAgent(wv, enabled)
        wv.reload()
    }

    override fun setTextZoom(tabId: Long, percent: Int) {
        views[tabId]?.settings?.textZoom = percent.coerceIn(50, 300)
    }

    override fun find(tabId: Long, query: String) { views[tabId]?.findAllAsync(query) }
    override fun findNext(tabId: Long, forward: Boolean) { views[tabId]?.findNext(forward) }
    override fun clearFind(tabId: Long) { views[tabId]?.clearMatches() }

    override fun applySettings(settings: BrowserSettings) {
        this.settings = settings
        for ((id, wv) in views) { configure(wv, id); applySiteTweaks(id, wv) }
    }

    /** DuckDuckGo result-page tweak: injected at document start for duckduckgo.com origins only. */
    private fun applySiteTweaks(tabId: Long, wv: WebView) {
        if (!documentStartScripts) return
        val installed = ddgScripts[tabId]
        if (settings.hideDuckDuckGoHeader && installed == null) {
            ddgScripts[tabId] = WebViewCompat.addDocumentStartJavaScript(wv, SiteTweaks.DUCKDUCKGO_HIDE_HEADER_JS, SiteTweaks.DUCKDUCKGO_ORIGINS)
        } else if (!settings.hideDuckDuckGoHeader && installed != null) {
            installed.remove()
            ddgScripts.remove(tabId)
        }
    }

    override fun trimMemory(keepTabId: Long?, aggressive: Boolean) {
        val limit = if (aggressive) 1 else maxLiveViews
        enforceBudget(keepTabId, limit)
    }

    override fun evaluate(tabId: Long, script: String, callback: (String?) -> Unit) {
        val wv = views[tabId] ?: return callback(null)
        wv.evaluateJavascript(script) { callback(it) }
    }

    override fun onPrivateSessionEnded() {
        if (!supportsPrivateProfiles) return
        // WebView releases the profile asynchronously after destroy(); retry briefly.
        fun attempt(remaining: Int) {
            val ok = runCatching { ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE) }.isSuccess
            if (!ok && remaining > 0) mainHandler.postDelayed({ attempt(remaining - 1) }, 500)
        }
        attempt(10)
    }

    override fun capture(tabId: Long): Bitmap? {
        val wv = views[tabId] ?: return null
        if (wv.width <= 0 || wv.height <= 0 || wv.parent == null) return null
        return runCatching {
            val scale = 0.35f
            val bmp = Bitmap.createBitmap((wv.width * scale).toInt().coerceAtLeast(1), (wv.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.RGB_565)
            val canvas = android.graphics.Canvas(bmp)
            canvas.scale(scale, scale)
            canvas.translate(-wv.scrollX.toFloat(), -wv.scrollY.toFloat())
            wv.draw(canvas)
            bmp
        }.getOrNull()
    }

    fun cookiesFor(url: String, isPrivate: Boolean): String? =
        if (isPrivate && supportsPrivateProfiles) runCatching { ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE).cookieManager.getCookie(url) }.getOrNull()
        else CookieManager.getInstance().getCookie(url)

    fun userAgentFor(tabId: Long): String = views[tabId]?.settings?.userAgentString ?: defaultUserAgent

    /** Any live WebView (or a temporary one) – used for clearing the shared HTTP cache. */
    fun clearCache() {
        val wv = views.values.firstOrNull() ?: WebView(contextWrapper)
        wv.clearCache(true)
        if (!views.containsValue(wv)) wv.destroy()
    }

    private fun enforceBudget(keep: Long?, limit: Int = maxLiveViews) {
        if (views.size <= limit) return
        val victims = views.keys.filter { it != keep }.take(views.size - limit) // eldest first
        for (id in victims) {
            val wv = views[id] ?: continue
            if (wv.parent != null) continue // currently shown
            val b = Bundle()
            wv.saveState(b)
            destroyViewOnly(id)
            savedStates[id] = b
        }
    }

    private fun destroyViewOnly(tabId: Long) {
        ddgScripts.remove(tabId)
        val wv = views.remove(tabId) ?: return
        wv.stopLoading()
        wv.destroy()
    }

    private fun create(tabId: Long, isPrivate: Boolean, desktop: Boolean, zoom: Int?): WebView {
        val wv = WebView(contextWrapper)
        if (isPrivate) {
            privateTabs.add(tabId)
            if (supportsPrivateProfiles) WebViewCompat.setProfile(wv, PRIVATE_PROFILE)
        }
        if (desktop) desktopTabs.add(tabId)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        configure(wv, tabId)
        applySiteTweaks(tabId, wv)
        zoom?.let { wv.settings.textZoom = it }
        wv.webViewClient = Client(tabId)
        wv.webChromeClient = Chrome(tabId)
        wv.setDownloadListener { url, ua, cd, mime, len -> listener.onDownloadRequested(tabId, url, ua, cd, mime, len) }
        wv.setOnLongClickListener { v ->
            val hit = (v as WebView).hitTestResult
            when (hit.type) {
                WebView.HitTestResult.SRC_ANCHOR_TYPE -> { listener.onLongPress(tabId, LongPressTarget(hit.extra, null)); true }
                WebView.HitTestResult.IMAGE_TYPE -> { listener.onLongPress(tabId, LongPressTarget(null, hit.extra)); true }
                WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                    val msg = mainHandler.obtainMessage()
                    v.requestFocusNodeHref(msg)
                    val href = msg.data.getString("url")
                    listener.onLongPress(tabId, LongPressTarget(href?.takeIf { it.isNotBlank() }, hit.extra)); true
                }
                else -> false
            }
        }
        wv.setFindListener { active, total, done -> if (done) listener.onFindResult(tabId, if (total == 0) 0 else active + 1, total) }
        views[tabId] = wv
        return wv
    }

    private fun configure(wv: WebView, tabId: Long) {
        val isPrivate = privateTabs.contains(tabId)
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = true // we decide in onCreateWindow (user gesture + popup setting)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.loadWithOverviewMode = true
        s.useWideViewPort = true
        s.mediaPlaybackRequiresUserGesture = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setGeolocationEnabled(true)
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.textZoom = settings.textZoom
        s.safeBrowsingEnabled = settings.safeBrowsing
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, false)
        }
        if (isPrivate && !supportsPrivateProfiles) s.cacheMode = WebSettings.LOAD_NO_CACHE
        val cookies = if (isPrivate && supportsPrivateProfiles) ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE).cookieManager else CookieManager.getInstance()
        cookies.setAcceptCookie(true)
        cookies.setAcceptThirdPartyCookies(wv, !settings.blockThirdPartyCookies)
        applyUserAgent(wv, desktopTabs.contains(tabId))
    }

    private fun applyUserAgent(wv: WebView, desktop: Boolean) {
        val s = wv.settings
        if (desktop) {
            val chrome = Regex("Chrome/([\\d.]+)").find(defaultUserAgent)?.groupValues?.get(1) ?: "120.0.0.0"
            s.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$chrome Safari/537.36"
            s.useWideViewPort = true
            s.loadWithOverviewMode = true
        } else {
            s.userAgentString = null // WebView default
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.USER_AGENT_METADATA)) {
            val current = WebSettingsCompat.getUserAgentMetadata(s)
            WebSettingsCompat.setUserAgentMetadata(
                s,
                UserAgentMetadata.Builder(current).setMobile(!desktop).setPlatform(if (desktop) "Linux" else current.platform).build(),
            )
        }
    }

    private inner class Client(private val tabId: Long) : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val url = request.url.toString()
            val scheme = request.url.scheme?.lowercase() ?: return true
            return when (scheme) {
                "http", "https" -> false
                "about" -> url != "about:blank"
                // Local files, app content, scripts and internal schemes are never opened from web pages.
                "javascript", "file", "content", "chrome", "freedom" -> true
                // Top-level data:/blob: navigations are a phishing vector (Chromium blocks them too).
                "data", "blob" -> request.isForMainFrame
                else -> {
                    if (request.isForMainFrame && request.hasGesture()) listener.onExternalLink(tabId, url)
                    true
                }
            }
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            listener.onPageStarted(tabId, url)
        }

        override fun onPageFinished(view: WebView, url: String) {
            // Older WebViews without document-start scripts: apply the cosmetic tweak after load.
            if (!documentStartScripts && settings.hideDuckDuckGoHeader && SiteTweaks.isDuckDuckGoResults(url)) {
                view.evaluateJavascript(SiteTweaks.DUCKDUCKGO_HIDE_HEADER_JS, null)
            }
            listener.onPageFinished(tabId, url)
            listener.onHistoryUpdate(tabId, url, view.canGoBack(), view.canGoForward())
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            listener.onHistoryUpdate(tabId, url, view.canGoBack(), view.canGoForward())
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
            if (!request.isForMainFrame) return
            val code = error.errorCode
            if (code == ERROR_UNKNOWN && error.description?.contains("ERR_BLOCKED_BY_CLIENT") == true) return
            val kind = PageErrors.fromAndroidWebViewError(code, isOnline())
            listener.onMainFrameError(tabId, PageError(kind, code, error.description?.toString().orEmpty(), request.url.toString()))
        }

        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
            // Never bypass certificate errors.
            handler.cancel()
            val mainUrl = view.url
            val isMain = mainUrl == null || mainUrl == error.url || Uri.parse(error.url).host == Uri.parse(mainUrl).host
            listener.onSslError(tabId, error, isMain)
        }

        override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
            listener.onHttpAuthRequest(tabId, host, realm, { u, p -> handler.proceed(u, p) }, { handler.cancel() })
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            Diagnostics.log("renderer.gone", null, if (detail.didCrash()) "crash" else "killed")
            // The view is unusable now; drop it (keeping no state) and let the ViewModel offer a reload.
            views.remove(tabId)
            (view.parent as? ViewGroup)?.removeView(view)
            view.destroy()
            listener.onRendererGone(tabId, detail.didCrash())
            return true
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val pageHost = listener.pageHostFor(tabId)
            val block = privacy.blockList.shouldBlock(request.url.toString(), pageHost, request.isForMainFrame, privacy.adBlockEnabled, privacy.adBlockExceptions)
            if (!block) return null
            mainHandler.post { listener.onRequestBlocked(tabId) }
            return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
    }

    private inner class Chrome(private val tabId: Long) : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) = listener.onProgress(tabId, newProgress)
        override fun onReceivedTitle(view: WebView, title: String?) = listener.onTitle(tabId, title.orEmpty())
        override fun onReceivedIcon(view: WebView, icon: Bitmap?) { icon?.let { listener.onFavicon(tabId, it) } }

        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean {
            val newId = listener.onCreateWindow(tabId, isUserGesture) ?: return false
            val opener = privateTabs.contains(tabId)
            val wv = views[newId] ?: create(newId, opener, false, null)
            (resultMsg.obj as WebView.WebViewTransport).webView = wv
            resultMsg.sendToTarget()
            enforceBudget(keep = newId)
            return true
        }

        override fun onCloseWindow(window: WebView) = listener.onCloseWindow(tabId)

        override fun onPermissionRequest(request: PermissionRequest) {
            val wanted = HashSet<SitePermission>()
            var protectedMedia = false
            for (r in request.resources) when (r) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> wanted += SitePermission.CAMERA
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> wanted += SitePermission.MICROPHONE
                PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> protectedMedia = true
            }
            listener.onPermissionRequest(tabId, request.origin.toString(), wanted, protectedMedia) { granted, grantProtected ->
                val res = ArrayList<String>()
                if (SitePermission.CAMERA in granted) res += PermissionRequest.RESOURCE_VIDEO_CAPTURE
                if (SitePermission.MICROPHONE in granted) res += PermissionRequest.RESOURCE_AUDIO_CAPTURE
                if (grantProtected) res += PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                if (res.isEmpty()) request.deny() else request.grant(res.toTypedArray())
            }
        }

        override fun onPermissionRequestCanceled(request: PermissionRequest) = listener.onPermissionRequestCanceled(tabId)

        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
            listener.onPermissionRequest(tabId, origin, setOf(SitePermission.LOCATION), false) { granted, _ ->
                callback.invoke(origin, SitePermission.LOCATION in granted, false)
            }
        }

        override fun onShowFileChooser(webView: WebView, filePathCallback: ValueCallback<Array<Uri>?>, fileChooserParams: FileChooserParams): Boolean {
            _fileChooser.value?.callback?.onReceiveValue(null)
            _fileChooser.value = FileChooserRequest(fileChooserParams, filePathCallback)
            return true
        }

        override fun onShowCustomView(view: View, callback: CustomViewCallback) {
            _customView.value?.callback?.onCustomViewHidden()
            _customView.value = CustomViewRequest(view, callback)
        }

        override fun onHideCustomView() { _customView.value = null }
    }

    fun completeFileChooser(result: Array<Uri>?) {
        _fileChooser.value?.callback?.onReceiveValue(result)
        _fileChooser.value = null
    }

    fun exitFullscreen() {
        _customView.value?.callback?.onCustomViewHidden()
        _customView.value = null
    }

    companion object {
        const val PRIVATE_PROFILE = "freedom-private"

        fun privateProfilesSupported(): Boolean = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
    }
}
