package app.freedombrowser.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import app.freedombrowser.AppContainer
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.FakeEngine
import app.freedombrowser.browser.PageError
import app.freedombrowser.browser.Screen
import app.freedombrowser.browser.TabState
import app.freedombrowser.data.ThemeMode
import app.freedombrowser.shared.PageErrorKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * Renders real Compose screens with Robolectric's native graphics and saves PNGs to
 * build/outputs/screenshots (copied to docs/screenshots by scripts). Also asserts key controls exist.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w411dp-h891dp-xxhdpi")
class UiScreenshotTest {
    @get:Rule val compose = createComposeRule()

    private fun save(name: String) {
        val bmp = compose.onRoot().captureToImage().asAndroidBitmap()
        val dir = File("build/outputs/screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dir, "$name.png").length() > 0)
    }

    private fun content(theme: ThemeMode = ThemeMode.DARK, body: @Composable () -> Unit) =
        compose.setContent { FreedomTheme(theme) { androidx.compose.material3.Surface { body() } } }

    @Test fun startPage() {
        content { StartPage(isPrivate = false, onSubmit = {}, onOpen = {}) }
        compose.onNodeWithText("Downloads").assertExists()
        compose.onNodeWithText("Bookmarks").assertExists()
        save("android-start-dark")
    }

    @Test fun startPageLight() {
        content(ThemeMode.LIGHT) { StartPage(isPrivate = false, onSubmit = {}, onOpen = {}) }
        save("android-start-light")
    }

    @Test fun privateStartPage() {
        content { StartPage(isPrivate = true, onSubmit = {}, onOpen = {}) }
        compose.onNodeWithText("Private browsing").assertExists()
        save("android-private-start")
    }

    @Test fun certificateErrorPage() {
        val tab = TabState(1, false, url = "https://expired.example/",
            error = PageError(PageErrorKind.CERTIFICATE, 1, "Certificate expired", "https://expired.example/"))
        content { ErrorPage(tab, onRetry = {}, onBack = {}) }
        compose.onNodeWithText("Back to safety").assertExists()
        save("android-error-certificate")
    }

    private fun viewModel(): BrowserViewModel {
        val c = AppContainer(ApplicationProvider.getApplicationContext())
        return BrowserViewModel(c, { FakeEngine() }, privateSupported = true)
    }

    @Test fun tabsScreen() {
        val vm = viewModel()
        vm.openNewTab("https://kotlinlang.org/")
        vm.onTitle(vm.tabs.selectedId!!, "Kotlin Programming Language")
        vm.openNewTab("https://developer.android.com/")
        vm.onTitle(vm.tabs.selectedId!!, "Android Developers")
        vm.show(Screen.TABS)
        content { TabsScreen(vm.state.value, vm) }
        compose.onNodeWithText("Close all").assertExists()
        save("android-tabs")
    }

    @Test fun settingsScreen() {
        val vm = viewModel()
        val actions = object : PageActions {
            override fun share(url: String, title: String) = Unit
            override fun copy(text: String) = Unit
            override fun print(tabId: Long, title: String) = Unit
            override fun screenshot(tabId: Long) = Unit
            override fun openExternal(url: String) = Unit
            override fun clearEngineData(cookies: Boolean, cache: Boolean) = Unit
            override fun openDownload(entity: app.freedombrowser.data.DownloadEntity) = Unit
            override fun shareDownload(entity: app.freedombrowser.data.DownloadEntity) = Unit
            override fun pickDownloadFolder() = Unit
            override fun appVersion() = "1.0.0"
            override fun engineVersion() = "Android System WebView"
        }
        content { SettingsScreen(vm.state.value, vm, actions, blockListSize = 125, hasCustomList = false) }
        compose.onNodeWithText("DuckDuckGo").assertExists()
        compose.onNodeWithText("Block ads and trackers").assertExists()
        save("android-settings")
        compose.onNodeWithText("Clear browsing data").performScrollTo().performClick()
        compose.onNodeWithText("Time range (history)").assertExists()
    }
}
