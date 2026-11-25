package app.freedombrowser

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentCallbacks2
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.print.PrintAttributes
import android.print.PrintManager
import android.provider.MediaStore
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.drawToBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.webkit.WebViewCompat
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Screen
import app.freedombrowser.browser.WebViewManager
import app.freedombrowser.data.DownloadEntity
import app.freedombrowser.downloads.DownloadService
import app.freedombrowser.ui.BookmarksScreen
import app.freedombrowser.ui.BrowserScreen
import app.freedombrowser.ui.DownloadsScreen
import app.freedombrowser.ui.FreedomTheme
import app.freedombrowser.ui.HistoryScreen
import app.freedombrowser.ui.LibraryViewModel
import app.freedombrowser.ui.PageActions
import app.freedombrowser.ui.PromptHost
import app.freedombrowser.ui.RuntimePermissions
import app.freedombrowser.ui.SettingsScreen
import app.freedombrowser.ui.TabsScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity(), PageActions {
    private val container get() = (application as FreedomApp).container
    private lateinit var vm: BrowserViewModel
    private lateinit var lib: LibraryViewModel
    private lateinit var manager: WebViewManager

    private var permissionCallback: ((Boolean) -> Unit)? = null
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val cb = permissionCallback
        permissionCallback = null
        // Location: coarse alone is acceptable.
        val granted = result.isNotEmpty() && result.entries.all { (perm, ok) -> ok || (perm == Manifest.permission.ACCESS_FINE_LOCATION && result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) }
        cb?.invoke(granted)
    }
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        manager.completeFileChooser(WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data))
    }
    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            vm.updateSettings { it.copy(downloadTreeUri = uri.toString()) }
        }
    }
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val runtimePermissions = RuntimePermissions { perms, onResult ->
        val missing = perms.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) onResult(true) else {
            permissionCallback?.invoke(false)
            permissionCallback = onResult
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T = when (modelClass) {
                BrowserViewModel::class.java -> BrowserViewModel(
                    container,
                    { listener -> WebViewManager(applicationContext, listener, container.privacy) { container.network.currentlyOnline() } },
                    privateSupported = WebViewManager.privateProfilesSupported(),
                )
                LibraryViewModel::class.java -> LibraryViewModel(container)
                else -> error("Unknown ViewModel $modelClass")
            } as T
        }
        val provider = ViewModelProvider(this, factory)
        vm = provider[BrowserViewModel::class.java]
        lib = provider[LibraryViewModel::class.java]
        manager = vm.engine as WebViewManager
        manager.attach(this)
        container.cookieProvider = manager::cookiesFor

        lifecycleScope.launch {
            var asked = false
            container.downloads.activeCount.collect { n ->
                if (n > 0) {
                    if (!asked) { asked = true; ensureNotificationPermission() }
                    DownloadService.start(applicationContext)
                }
            }
        }

        setContent {
            val state by vm.state.collectAsState()
            val fileRequest by manager.fileChooser.collectAsState()
            val customView by manager.customView.collectAsState()
            val blockListSize = container.privacy.blockList.size
            val snackbar = remember { SnackbarHostState() }

            LaunchedEffect(Unit) {
                vm.messages.collect { m ->
                    val text = getString(m.text, *m.args.toTypedArray())
                    val result = snackbar.showSnackbar(text, m.actionLabel?.let(::getString), withDismissAction = m.actionLabel == null, duration = SnackbarDuration.Short)
                    if (result == SnackbarResult.ActionPerformed) m.action?.invoke()
                }
            }
            LaunchedEffect(fileRequest) {
                val req = fileRequest ?: return@LaunchedEffect
                try {
                    fileChooserLauncher.launch(req.params.createIntent())
                } catch (_: ActivityNotFoundException) {
                    manager.completeFileChooser(null)
                }
            }
            LaunchedEffect(customView) { setFullscreenMode(customView != null) }

            FreedomTheme(state.settings.theme) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().imePadding()) {
                    when (state.screen) {
                        Screen.BROWSER -> BrowserScreen(state, vm, manager, this@MainActivity)
                        Screen.TABS -> TabsScreen(state, vm)
                        Screen.BOOKMARKS -> BookmarksScreen(lib, vm)
                        Screen.HISTORY -> HistoryScreen(lib, vm)
                        Screen.DOWNLOADS -> DownloadsScreen(lib, vm, this@MainActivity)
                        Screen.SETTINGS -> SettingsScreen(state, vm, this@MainActivity, blockListSize, container.blockLists.hasCustomList)
                    }
                    PromptHost(state.prompt, vm, this@MainActivity, runtimePermissions)
                    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
                }
                customView?.let { cv ->
                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                        AndroidView(factory = { ctx ->
                            FrameLayout(ctx).apply {
                                (cv.view.parent as? ViewGroup)?.removeView(cv.view)
                                addView(cv.view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                            }
                        }, modifier = Modifier.fillMaxSize())
                    }
                }
                BackHandler(enabled = customView != null || state.screen == Screen.TABS || state.screen == Screen.SETTINGS || state.find != null ||
                    (state.screen == Screen.BROWSER && state.currentTab?.let { it.canGoBack || !it.isStartPage } == true)) {
                    when {
                        customView != null -> manager.exitFullscreen()
                        state.find != null -> vm.closeFind()
                        state.screen != Screen.BROWSER -> vm.show(Screen.BROWSER)
                        else -> vm.goBack()
                    }
                }
            }
        }
        handleIntent(intent, isLaunch = savedInstanceState == null)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent, isLaunch = false)
    }

    private fun handleIntent(intent: Intent?, isLaunch: Boolean) {
        intent ?: return
        when (intent.action) {
            ACTION_SHOW_DOWNLOADS -> vm.show(Screen.DOWNLOADS)
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let { url ->
                lifecycleScope.launch { vm.openNewTab(url) }
            }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { q -> vm.openNewTab()?.let { vm.submitInput(q, it) } }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { t -> vm.openNewTab()?.let { vm.submitInput(t, it) } }
        }
    }

    private fun setFullscreenMode(on: Boolean) {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else controller.show(WindowInsetsCompat.Type.systemBars())
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        @Suppress("DEPRECATION")
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) vm.onTrimMemory(aggressive = level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL || level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND)
    }

    override fun onDestroy() {
        manager.detach()
        super.onDestroy()
    }

    // region PageActions ---------------------------------------------------------------------

    override fun share(url: String, title: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, url).putExtra(Intent.EXTRA_SUBJECT, title)
        startActivity(Intent.createChooser(send, null))
    }

    override fun copy(text: String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Lumo", text))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(this, R.string.copied, Toast.LENGTH_SHORT).show()
    }

    override fun print(tabId: Long, title: String) {
        val wv = manager.view(tabId) ?: return
        val job = title.ifBlank { "Lumo" }
        getSystemService(PrintManager::class.java).print(job, wv.createPrintDocumentAdapter(job), PrintAttributes.Builder().build())
    }

    override fun screenshot(tabId: Long) {
        val wv: WebView = manager.view(tabId) ?: return
        if (wv.width == 0 || wv.height == 0) return
        val bitmap = runCatching { wv.drawToBitmap(Bitmap.Config.ARGB_8888) }.getOrNull() ?: return toast(R.string.screenshot_failed)
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val name = "Lumo_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".png"
                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DISPLAY_NAME, name)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lumo")
                    }
                    val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
                    contentResolver.openOutputStream(uri)!!.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }.isSuccess
            }
            toast(if (ok) R.string.screenshot_saved else R.string.screenshot_failed)
        }
    }

    override fun openExternal(url: String) {
        try {
            val intent = if (url.startsWith("intent:", true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    // Never let a web page target a specific (possibly non-exported) component.
                    component = null
                    selector = null
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            } else Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            startActivity(intent)
        } catch (_: Exception) {
            val fallback = runCatching { Intent.parseUri(url, Intent.URI_INTENT_SCHEME).getStringExtra("browser_fallback_url") }.getOrNull()
            if (fallback != null && (fallback.startsWith("https://") || fallback.startsWith("http://"))) vm.openNewTab(fallback)
            else toast(R.string.no_app_for_link)
        }
    }

    override fun clearEngineData(cookies: Boolean, cache: Boolean) {
        if (cookies) {
            android.webkit.CookieManager.getInstance().removeAllCookies(null)
            android.webkit.WebStorage.getInstance().deleteAllData()
            android.webkit.GeolocationPermissions.getInstance().clearAll()
        }
        if (cache) manager.clearCache()
    }

    override fun openDownload(entity: DownloadEntity) {
        val uri = entity.contentUri?.let(Uri::parse) ?: return
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, entity.mimeType ?: contentResolver.getType(uri) ?: "*/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try { startActivity(intent) } catch (_: ActivityNotFoundException) { toast(R.string.no_app_for_file) }
    }

    override fun shareDownload(entity: DownloadEntity) {
        val uri = entity.contentUri?.let(Uri::parse) ?: return
        val send = Intent(Intent.ACTION_SEND).setType(entity.mimeType ?: "*/*").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(send, entity.fileName))
    }

    override fun pickDownloadFolder() {
        try { folderPicker.launch(null) } catch (_: ActivityNotFoundException) { toast(R.string.no_app_for_file) }
    }

    override fun appVersion(): String = BuildConfig.VERSION_NAME

    override fun engineVersion(): String = WebViewCompat.getCurrentWebViewPackage(this)?.let { "${it.packageName} ${it.versionName}" } ?: "Android System WebView"

    // endregion

    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(res: Int) = Toast.makeText(this, res, Toast.LENGTH_SHORT).show()

    companion object {
        const val ACTION_SHOW_DOWNLOADS = "app.freedombrowser.SHOW_DOWNLOADS"
    }
}
