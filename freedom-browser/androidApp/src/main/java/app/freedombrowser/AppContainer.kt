package app.freedombrowser

import android.content.Context
import android.net.Uri
import app.freedombrowser.data.BookmarkRepository
import app.freedombrowser.data.FreedomDatabase
import app.freedombrowser.data.HistoryRepository
import app.freedombrowser.data.SecretStore
import app.freedombrowser.data.SettingsRepository
import app.freedombrowser.data.SiteSettingsRepository
import app.freedombrowser.downloads.DownloadController
import app.freedombrowser.downloads.MediaStoreTarget
import app.freedombrowser.downloads.TreeTarget
import app.freedombrowser.privacy.BlockListRepository
import app.freedombrowser.privacy.PrivacyState
import app.freedombrowser.util.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Manual dependency container; one instance per process. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database = FreedomDatabase.create(appContext)
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val settings = SettingsRepository(appContext)
    val secrets = SecretStore(appContext)
    val bookmarks = BookmarkRepository(database.bookmarks())
    val history = HistoryRepository(database.history())
    val siteSettings = SiteSettingsRepository(database.siteSettings())
    val sessionDao = database.session()
    val blockLists = BlockListRepository(appContext, http)
    val privacy = PrivacyState(blockLists)
    val network = NetworkMonitor(appContext)

    /** Set by the browser engine so downloads can reuse the page's cookies (never persisted). */
    @Volatile var cookieProvider: (String, Boolean) -> String? = { _, _ -> null }

    val downloads = DownloadController(
        dao = database.downloads(),
        http = http,
        scope = scope,
        targetFor = { d -> d.targetTree?.takeIf { it.isNotEmpty() }?.let { TreeTarget(appContext, Uri.parse(it)) } ?: MediaStoreTarget(appContext) },
        cookieProvider = { url, private -> cookieProvider(url, private) },
    )
}
