package app.freedombrowser.browser

import android.graphics.Bitmap
import android.net.http.SslError
import app.freedombrowser.data.BrowserSettings
import app.freedombrowser.shared.SitePermission

/** Commands the ViewModel sends to the page engine (WebView on Android; fakes in tests). */
interface TabEngine {
    /** Makes sure a live engine view exists for [tab]; loads [tab].url if it had to be created. */
    fun ensure(tab: TabState)
    fun load(tabId: Long, url: String)
    fun goBack(tabId: Long)
    fun goForward(tabId: Long)
    fun reload(tabId: Long)
    fun stop(tabId: Long)
    fun destroy(tabId: Long)
    fun hasView(tabId: Long): Boolean
    fun setDesktopMode(tabId: Long, enabled: Boolean)
    fun setTextZoom(tabId: Long, percent: Int)
    fun find(tabId: Long, query: String)
    fun findNext(tabId: Long, forward: Boolean)
    fun clearFind(tabId: Long)
    fun applySettings(settings: BrowserSettings)
    /** Releases background views when memory is short; never touches [keepTabId]. */
    fun trimMemory(keepTabId: Long?, aggressive: Boolean)
    /** Runs a read-only script in the page and returns its JSON-encoded result. */
    fun evaluate(tabId: Long, script: String, callback: (String?) -> Unit)
    fun onPrivateSessionEnded()
}

/** Events from the page engine to the ViewModel. All calls happen on the main thread. */
interface EngineListener {
    fun onPageStarted(tabId: Long, url: String)
    fun onPageFinished(tabId: Long, url: String)
    fun onProgress(tabId: Long, progress: Int)
    fun onTitle(tabId: Long, title: String)
    fun onFavicon(tabId: Long, icon: Bitmap)
    fun onHistoryUpdate(tabId: Long, url: String, canGoBack: Boolean, canGoForward: Boolean)
    fun onMainFrameError(tabId: Long, error: PageError)
    fun onSslError(tabId: Long, error: SslError, isMainFrame: Boolean)
    fun onRendererGone(tabId: Long, crashed: Boolean)
    fun onRequestBlocked(tabId: Long)
    fun onDownloadRequested(tabId: Long, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?, contentLength: Long)
    /** A page wants a new window. Returns the id of the tab created for it, or null to refuse. */
    fun onCreateWindow(openerTabId: Long, userGesture: Boolean): Long?
    fun onCloseWindow(tabId: Long)
    fun onPermissionRequest(tabId: Long, origin: String, permissions: Set<SitePermission>, protectedMedia: Boolean, respond: (granted: Set<SitePermission>, grantProtectedMedia: Boolean) -> Unit)
    fun onPermissionRequestCanceled(tabId: Long)
    fun onHttpAuthRequest(tabId: Long, host: String, realm: String, proceed: (String, String) -> Unit, cancel: () -> Unit)
    fun onExternalLink(tabId: Long, url: String)
    fun onLongPress(tabId: Long, target: LongPressTarget)
    fun onFindResult(tabId: Long, activeIndex: Int, total: Int)
    fun pageHostFor(tabId: Long): String?
}

data class LongPressTarget(val linkUrl: String?, val imageUrl: String?)
