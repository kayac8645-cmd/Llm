package app.freedombrowser.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserUiState
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Screen
import app.freedombrowser.browser.TabState
import app.freedombrowser.shared.AiAssist
import app.freedombrowser.shared.PermissionDecision
import app.freedombrowser.shared.SecurityLevel
import app.freedombrowser.shared.SitePermission
import app.freedombrowser.shared.WebUrl
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainMenuSheet(state: BrowserUiState, vm: BrowserViewModel, actions: PageActions, onDismiss: () -> Unit) {
    val tab = state.currentTab ?: return
    val hasPage = tab.url.isNotEmpty() && tab.error == null
    var showZoom by remember { mutableStateOf(false) }
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    fun run(action: () -> Unit) { action(); onDismiss() }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            MenuItem(Icons.Filled.Add, R.string.new_tab) { run { vm.openNewTab() } }
            MenuItem(Icons.Filled.VisibilityOff, R.string.new_private_tab, enabled = state.privateSupported) { run { vm.openNewTab(isPrivate = true) } }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(Icons.Filled.Bookmarks, R.string.bookmarks_title) { run { vm.show(Screen.BOOKMARKS) } }
            MenuItem(Icons.Filled.History, R.string.history_title) { run { vm.show(Screen.HISTORY) } }
            MenuItem(Icons.Filled.Download, R.string.downloads_title) { run { vm.show(Screen.DOWNLOADS) } }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val bookmarked = tab.url in state.bookmarkedUrls
            MenuItem(if (bookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (bookmarked) R.string.remove_bookmark else R.string.add_bookmark, enabled = hasPage) { run(vm::toggleBookmark) }
            MenuItem(Icons.Filled.Search, R.string.find_in_page, enabled = hasPage) { run(vm::openFind) }
            MenuToggle(Icons.Filled.DesktopWindows, R.string.desktop_site, tab.desktopMode, enabled = !tab.isStartPage) { vm.toggleDesktopMode() }
            MenuItem(Icons.Filled.ZoomIn, R.string.text_size, enabled = hasPage) { showZoom = !showZoom }
            if (showZoom) ZoomSlider(tab.textZoom ?: state.settings.textZoom) { vm.setTextZoom(it) }
            MenuItem(Icons.Filled.Share, R.string.share, enabled = hasPage) { run { actions.share(tab.url, tab.title) } }
            MenuItem(Icons.Filled.ContentCopy, R.string.copy_link, enabled = hasPage) { run { actions.copy(tab.url) } }
            MenuItem(Icons.Filled.Print, R.string.print_pdf, enabled = hasPage) { run { actions.print(tab.id, tab.title) } }
            MenuItem(Icons.Filled.Photo, R.string.screenshot, enabled = hasPage) { run { actions.screenshot(tab.id) } }
            MenuItem(Icons.Filled.Translate, R.string.translate_page, enabled = hasPage) { run(vm::requestTranslate) }
            if (state.settings.aiEnabled) {
                MenuItem(Icons.Filled.AutoAwesome, R.string.ai_summarize, enabled = hasPage) { run { vm.requestAi(AiAssist.Task.SUMMARIZE) } }
                MenuItem(Icons.AutoMirrored.Filled.Help, R.string.ai_ask, enabled = hasPage) { run { vm.requestAi(AiAssist.Task.ASK) } }
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            MenuItem(Icons.Filled.Settings, R.string.settings_title) { run { vm.show(Screen.SETTINGS) } }
        }
    }
}

@Composable
private fun ZoomSlider(current: Int, onChange: (Int) -> Unit) {
    var value by remember { mutableFloatStateOf(current.toFloat()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("${value.roundToInt()}%", Modifier.width(52.dp), style = MaterialTheme.typography.labelSmall)
        Slider(value = value, onValueChange = { value = it }, onValueChangeFinished = { onChange(value.roundToInt()) }, valueRange = 50f..250f, steps = 19, modifier = Modifier.weight(1f))
    }
}

@Composable
fun MenuItem(icon: ImageVector, label: Int, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = LocalFreedomColors.current
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(enabled = enabled, onClick = onClick).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (enabled) MaterialTheme.colorScheme.onSurface else colors.textSecondary.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, color = if (enabled) MaterialTheme.colorScheme.onSurface else colors.textSecondary.copy(alpha = 0.6f))
    }
}

@Composable
private fun MenuToggle(icon: ImageVector, label: Int, checked: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clickable(enabled = enabled, onClick = onToggle).padding(start = 24.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(20.dp))
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

/** Connection details, tracker blocking exception and permissions for the current site. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(state: BrowserUiState, tab: TabState, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val colors = LocalFreedomColors.current
    val site = WebUrl.parse(tab.url)?.siteKey
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp)) {
            Text(site ?: stringResource(if (tab.isPrivate) R.string.private_tab else R.string.app_name), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            val secText = when (tab.security) {
                SecurityLevel.SECURE -> R.string.connection_secure
                SecurityLevel.INSECURE -> R.string.connection_insecure_detail
                SecurityLevel.CERT_ERROR -> R.string.connection_cert_error
                else -> R.string.connection_none
            }
            Text(stringResource(secText), style = MaterialTheme.typography.bodyMedium, color = if (tab.security == SecurityLevel.SECURE) colors.textSecondary else colors.warning)
            if (tab.isPrivate) Text(stringResource(R.string.private_explainer_short), style = MaterialTheme.typography.bodyMedium, color = colors.private, modifier = Modifier.padding(top = 4.dp))
            if (site == null) return@Column
            val siteSettings = state.siteSettings[site]
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.block_trackers_here), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        if (!state.settings.adBlockEnabled) stringResource(R.string.adblock_globally_off) else stringResource(R.string.blocked_count, tab.blockedCount),
                        style = MaterialTheme.typography.labelSmall, color = colors.textSecondary,
                    )
                }
                Switch(
                    checked = siteSettings?.adBlockException != true,
                    enabled = state.settings.adBlockEnabled,
                    onCheckedChange = { vm.setAdBlockException(site, !it) },
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.site_permissions), style = MaterialTheme.typography.titleMedium)
            for (p in listOf(SitePermission.CAMERA, SitePermission.MICROPHONE, SitePermission.LOCATION, SitePermission.POPUPS)) {
                PermissionRow(p, siteSettings?.decision(p) ?: PermissionDecision.ASK) { vm.setSitePermission(site, p, it) }
            }
            Text(stringResource(R.string.notifications_unsupported_android), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
            TextButton(onClick = { vm.resetSite(site) }, modifier = Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.reset_site_settings)) }
        }
    }
}

@Composable
fun PermissionRow(permission: SitePermission, decision: PermissionDecision, onChange: (PermissionDecision) -> Unit) {
    val label = when (permission) {
        SitePermission.CAMERA -> R.string.perm_camera
        SitePermission.MICROPHONE -> R.string.perm_microphone
        SitePermission.LOCATION -> R.string.perm_location
        SitePermission.NOTIFICATIONS -> R.string.perm_notifications
        SitePermission.POPUPS -> R.string.perm_popups
    }
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val options = PermissionDecision.entries
            options.forEachIndexed { i, d ->
                SegmentedButton(
                    selected = d == decision, onClick = { onChange(d) },
                    shape = SegmentedButtonDefaults.itemShape(i, options.size),
                ) {
                    Text(stringResource(when (d) { PermissionDecision.ASK -> R.string.decision_ask; PermissionDecision.ALLOW -> R.string.decision_allow; PermissionDecision.BLOCK -> R.string.decision_block }))
                }
            }
        }
    }
}
