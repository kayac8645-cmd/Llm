package app.freedombrowser.browser

import app.freedombrowser.data.BrowserSettings
import app.freedombrowser.shared.AiAssist
import app.freedombrowser.shared.SitePermission
import app.freedombrowser.shared.SiteSettings

enum class Screen { BROWSER, TABS, BOOKMARKS, HISTORY, DOWNLOADS, SETTINGS }

data class PendingDownload(
    val tabId: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val contentLength: Long,
    val userAgent: String?,
    val referer: String?,
    val isPrivate: Boolean,
    val dangerous: Boolean,
)

data class FindState(val query: String = "", val active: Int = 0, val total: Int = 0)

sealed interface Prompt {
    data class Permission(
        val tabId: Long,
        val site: String,
        val permissions: Set<SitePermission>,
        val protectedMedia: Boolean,
        /** True when the user already allowed everything for this site; only the OS permission check remains. */
        val preApproved: Boolean,
        val respond: (Set<SitePermission>, Boolean) -> Unit,
    ) : Prompt
    data class Download(val pending: PendingDownload) : Prompt
    data class HttpAuth(val host: String, val realm: String, val proceed: (String, String) -> Unit, val cancel: () -> Unit) : Prompt
    data class ExternalApp(val url: String) : Prompt
    data class LongPress(val tabId: Long, val target: LongPressTarget) : Prompt
    data class Translate(val targetUrl: String, val providerHost: String) : Prompt
    data class AiConsent(val task: AiAssist.Task, val endpointHost: String) : Prompt
    data class AiResult(val title: String, val text: String?, val loading: Boolean, val error: String?) : Prompt
}

/** One-off UI messages (snackbars). [action] is a label resource with its callback. */
data class UiMessage(val text: Int, val args: List<Any> = emptyList(), val actionLabel: Int? = null, val action: (() -> Unit)? = null)

data class BrowserUiState(
    val tabs: TabList = TabList(),
    val settings: BrowserSettings = BrowserSettings(),
    val screen: Screen = Screen.BROWSER,
    val isOnline: Boolean = true,
    val find: FindState? = null,
    val prompt: Prompt? = null,
    val siteSettings: Map<String, SiteSettings> = emptyMap(),
    val bookmarkedUrls: Set<String> = emptySet(),
    val privateSupported: Boolean = true,
    val aiKeyConfigured: Boolean = false,
    val restored: Boolean = false,
) {
    val currentTab: TabState? get() = tabs.selected
}
