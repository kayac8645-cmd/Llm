package app.freedombrowser.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.freedombrowser.shared.SearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { DARK, LIGHT, SYSTEM }
enum class HomepageMode { START_PAGE, CUSTOM_URL }

data class BrowserSettings(
    val searchEngine: SearchEngine = SearchEngine.DEFAULT,
    val homepageMode: HomepageMode = HomepageMode.START_PAGE,
    val homepageUrl: String = "",
    val theme: ThemeMode = ThemeMode.DARK,
    val textZoom: Int = 100,
    val adBlockEnabled: Boolean = true,
    val blockPopups: Boolean = true,
    val blockThirdPartyCookies: Boolean = true,
    val safeBrowsing: Boolean = true,
    val restoreTabs: Boolean = true,
    val askDownloadLocation: Boolean = false,
    /** SAF tree URI for downloads; empty = public "Download" folder. */
    val downloadTreeUri: String = "",
    val blockListUrl: String = DEFAULT_BLOCKLIST_URL,
    val blockListUpdatedAt: Long = 0,
    val translateTemplate: String = DEFAULT_TRANSLATE_TEMPLATE,
    val aiEnabled: Boolean = false,
    val aiEndpoint: String = "",
    val aiModel: String = "",
    val diagnosticsLogging: Boolean = false,
    /** New-tab page: user shortcuts (JSON list of {id,title,url}) and hidden "recently visited" sites. */
    val shortcutsJson: String = "[]",
    val hiddenSitesJson: String = "[]",
    val showTopSites: Boolean = true,
    /** Hide DuckDuckGo's logo / Duck.ai / menu row on result pages (cosmetic CSS only). */
    val hideDuckDuckGoHeader: Boolean = true,
) {
    companion object {
        /** StevenBlack unified hosts (MIT licensed), downloaded only when the user taps "Update". */
        const val DEFAULT_BLOCKLIST_URL = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"
        const val DEFAULT_TRANSLATE_TEMPLATE = "https://translate.google.com/translate?sl=auto&tl={lang}&u={url}"
    }
}

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object K {
        val engine = stringPreferencesKey("search_engine")
        val homeMode = stringPreferencesKey("homepage_mode")
        val homeUrl = stringPreferencesKey("homepage_url")
        val theme = stringPreferencesKey("theme")
        val textZoom = intPreferencesKey("text_zoom")
        val adBlock = booleanPreferencesKey("adblock")
        val popups = booleanPreferencesKey("block_popups")
        val thirdParty = booleanPreferencesKey("block_3p_cookies")
        val safeBrowsing = booleanPreferencesKey("safe_browsing")
        val restore = booleanPreferencesKey("restore_tabs")
        val askLocation = booleanPreferencesKey("ask_download_location")
        val tree = stringPreferencesKey("download_tree")
        val blockListUrl = stringPreferencesKey("blocklist_url")
        val blockListUpdated = stringPreferencesKey("blocklist_updated")
        val translate = stringPreferencesKey("translate_template")
        val aiEnabled = booleanPreferencesKey("ai_enabled")
        val aiEndpoint = stringPreferencesKey("ai_endpoint")
        val aiModel = stringPreferencesKey("ai_model")
        val diagnostics = booleanPreferencesKey("diagnostics")
        val shortcuts = stringPreferencesKey("shortcuts")
        val hiddenSites = stringPreferencesKey("hidden_sites")
        val showTopSites = booleanPreferencesKey("show_top_sites")
        val hideDdgHeader = booleanPreferencesKey("hide_ddg_header")
    }

    val settings: Flow<BrowserSettings> = context.dataStore.data.map(::read)

    private fun read(p: Preferences): BrowserSettings {
        val d = BrowserSettings()
        return BrowserSettings(
            searchEngine = SearchEngine.fromId(p[K.engine]),
            homepageMode = p[K.homeMode]?.let { runCatching { HomepageMode.valueOf(it) }.getOrNull() } ?: d.homepageMode,
            homepageUrl = p[K.homeUrl] ?: d.homepageUrl,
            theme = p[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.theme,
            textZoom = p[K.textZoom] ?: d.textZoom,
            adBlockEnabled = p[K.adBlock] ?: d.adBlockEnabled,
            blockPopups = p[K.popups] ?: d.blockPopups,
            blockThirdPartyCookies = p[K.thirdParty] ?: d.blockThirdPartyCookies,
            safeBrowsing = p[K.safeBrowsing] ?: d.safeBrowsing,
            restoreTabs = p[K.restore] ?: d.restoreTabs,
            askDownloadLocation = p[K.askLocation] ?: d.askDownloadLocation,
            downloadTreeUri = p[K.tree] ?: d.downloadTreeUri,
            blockListUrl = p[K.blockListUrl] ?: d.blockListUrl,
            blockListUpdatedAt = p[K.blockListUpdated]?.toLongOrNull() ?: 0,
            translateTemplate = p[K.translate] ?: d.translateTemplate,
            aiEnabled = p[K.aiEnabled] ?: d.aiEnabled,
            aiEndpoint = p[K.aiEndpoint] ?: d.aiEndpoint,
            aiModel = p[K.aiModel] ?: d.aiModel,
            diagnosticsLogging = p[K.diagnostics] ?: d.diagnosticsLogging,
            shortcutsJson = p[K.shortcuts] ?: d.shortcutsJson,
            hiddenSitesJson = p[K.hiddenSites] ?: d.hiddenSitesJson,
            showTopSites = p[K.showTopSites] ?: d.showTopSites,
            hideDuckDuckGoHeader = p[K.hideDdgHeader] ?: d.hideDuckDuckGoHeader,
        )
    }

    suspend fun update(transform: (BrowserSettings) -> BrowserSettings) {
        context.dataStore.edit { p ->
            val current = read(p)
            val n = transform(current)
            p[K.engine] = n.searchEngine.id
            p[K.homeMode] = n.homepageMode.name
            p[K.homeUrl] = n.homepageUrl
            p[K.theme] = n.theme.name
            p[K.textZoom] = n.textZoom.coerceIn(50, 300)
            p[K.adBlock] = n.adBlockEnabled
            p[K.popups] = n.blockPopups
            p[K.thirdParty] = n.blockThirdPartyCookies
            p[K.safeBrowsing] = n.safeBrowsing
            p[K.restore] = n.restoreTabs
            p[K.askLocation] = n.askDownloadLocation
            p[K.tree] = n.downloadTreeUri
            p[K.blockListUrl] = n.blockListUrl
            p[K.blockListUpdated] = n.blockListUpdatedAt.toString()
            p[K.translate] = n.translateTemplate
            p[K.aiEnabled] = n.aiEnabled
            p[K.aiEndpoint] = n.aiEndpoint
            p[K.aiModel] = n.aiModel
            p[K.diagnostics] = n.diagnosticsLogging
            p[K.shortcuts] = n.shortcutsJson
            p[K.hiddenSites] = n.hiddenSitesJson
            p[K.showTopSites] = n.showTopSites
            p[K.hideDdgHeader] = n.hideDuckDuckGoHeader
        }
    }
}
