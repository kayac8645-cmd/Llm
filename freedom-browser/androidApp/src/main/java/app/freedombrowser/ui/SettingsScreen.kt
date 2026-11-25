package app.freedombrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserUiState
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Screen
import app.freedombrowser.data.HomepageMode
import app.freedombrowser.data.ThemeMode
import app.freedombrowser.shared.AiAssist
import app.freedombrowser.shared.HistoryRange
import app.freedombrowser.shared.SearchEngine
import app.freedombrowser.util.Diagnostics
import java.text.DateFormat
import java.util.Date

private enum class SettingsPage { MAIN, SITES, PRIVACY_POLICY, LICENSES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: BrowserUiState, vm: BrowserViewModel, actions: PageActions, blockListSize: Int, hasCustomList: Boolean) {
    var page by remember { mutableStateOf(SettingsPage.MAIN) }
    val back = { if (page != SettingsPage.MAIN) page = SettingsPage.MAIN else vm.show(Screen.BROWSER) }
    BackHandler(onBack = back)
    val title = when (page) {
        SettingsPage.MAIN -> R.string.settings_title
        SettingsPage.SITES -> R.string.site_permissions
        SettingsPage.PRIVACY_POLICY -> R.string.privacy_policy
        SettingsPage.LICENSES -> R.string.licenses
    }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(title)) },
            navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
        )
        Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 32.dp)) {
            when (page) {
                SettingsPage.MAIN -> MainSettings(state, vm, actions, blockListSize, hasCustomList) { page = it }
                SettingsPage.SITES -> SitesSettings(state, vm)
                SettingsPage.PRIVACY_POLICY -> Text(stringResource(R.string.privacy_policy_text), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
                SettingsPage.LICENSES -> Text(stringResource(R.string.licenses_text), Modifier.padding(20.dp), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun MainSettings(state: BrowserUiState, vm: BrowserViewModel, actions: PageActions, blockListSize: Int, hasCustomList: Boolean, open: (SettingsPage) -> Unit) {
    val s = state.settings
    val colors = LocalFreedomColors.current
    var showClear by remember { mutableStateOf(false) }
    var editHome by remember { mutableStateOf(false) }
    var editAi by remember { mutableStateOf(false) }
    var editTranslate by remember { mutableStateOf(false) }
    var editListUrl by remember { mutableStateOf(false) }
    var showLog by remember { mutableStateOf(false) }

    Section(R.string.section_search)
    SearchEngine.entries.forEach { e -> RadioRow(e.displayName, s.searchEngine == e) { vm.updateSettings { it.copy(searchEngine = e) } } }

    Section(R.string.section_homepage)
    RadioRow(stringResource(R.string.homepage_start), s.homepageMode == HomepageMode.START_PAGE) { vm.updateSettings { it.copy(homepageMode = HomepageMode.START_PAGE) } }
    RadioRow(stringResource(R.string.homepage_custom, s.homepageUrl.ifBlank { "—" }), s.homepageMode == HomepageMode.CUSTOM_URL) { editHome = true }
    SwitchRow(R.string.restore_tabs, s.restoreTabs) { v -> vm.updateSettings { it.copy(restoreTabs = v) } }

    Section(R.string.section_appearance)
    ThemeMode.entries.forEach { m ->
        RadioRow(stringResource(when (m) { ThemeMode.DARK -> R.string.theme_dark; ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.SYSTEM -> R.string.theme_system }), s.theme == m) { vm.updateSettings { it.copy(theme = m) } }
    }
    Text(stringResource(R.string.default_text_size, s.textZoom), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
    androidx.compose.material3.Slider(
        value = s.textZoom.toFloat(), onValueChange = { v -> vm.updateSettings { it.copy(textZoom = v.toInt()) } },
        valueRange = 50f..200f, steps = 14, modifier = Modifier.padding(horizontal = 20.dp),
    )

    Section(R.string.section_downloads)
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.download_location), style = MaterialTheme.typography.bodyMedium)
            Text(if (s.downloadTreeUri.isBlank()) stringResource(R.string.download_location_default) else android.net.Uri.decode(s.downloadTreeUri.substringAfterLast("/")),
                style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        }
        TextButton(onClick = actions::pickDownloadFolder) { Text(stringResource(R.string.change)) }
        if (s.downloadTreeUri.isNotBlank()) TextButton(onClick = { vm.updateSettings { it.copy(downloadTreeUri = "") } }) { Text(stringResource(R.string.reset)) }
    }
    SwitchRow(R.string.ask_before_download, s.askDownloadLocation) { v -> vm.updateSettings { it.copy(askDownloadLocation = v) } }

    Section(R.string.section_privacy)
    SwitchRow(R.string.adblock_enabled, s.adBlockEnabled, R.string.adblock_hint) { v -> vm.updateSettings { it.copy(adBlockEnabled = v) } }
    Text(
        stringResource(R.string.blocklist_status, blockListSize, if (s.blockListUpdatedAt > 0) DateFormat.getDateTimeInstance().format(Date(s.blockListUpdatedAt)) else stringResource(R.string.never)),
        Modifier.padding(horizontal = 20.dp), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary,
    )
    Row(Modifier.padding(horizontal = 12.dp)) {
        TextButton(onClick = vm::updateBlockList) { Text(stringResource(R.string.blocklist_update)) }
        TextButton(onClick = { editListUrl = true }) { Text(stringResource(R.string.blocklist_source)) }
        if (hasCustomList) TextButton(onClick = vm::removeCustomBlockList) { Text(stringResource(R.string.blocklist_remove)) }
    }
    SwitchRow(R.string.block_popups, s.blockPopups) { v -> vm.updateSettings { it.copy(blockPopups = v) } }
    SwitchRow(R.string.block_third_party_cookies, s.blockThirdPartyCookies) { v -> vm.updateSettings { it.copy(blockThirdPartyCookies = v) } }
    SwitchRow(R.string.safe_browsing, s.safeBrowsing, R.string.safe_browsing_hint) { v -> vm.updateSettings { it.copy(safeBrowsing = v) } }
    ClickRow(R.string.site_permissions, R.string.site_permissions_hint) { open(SettingsPage.SITES) }
    ClickRow(R.string.clear_data, R.string.clear_data_hint) { showClear = true }

    Section(R.string.section_translate)
    ClickRow(R.string.translate_provider, null, subtitleText = s.translateTemplate) { editTranslate = true }

    Section(R.string.section_ai)
    SwitchRow(R.string.ai_enable, s.aiEnabled, R.string.ai_enable_hint) { v -> vm.updateSettings { it.copy(aiEnabled = v) } }
    if (s.aiEnabled) ClickRow(R.string.ai_configure, null, subtitleText = listOf(s.aiEndpoint, s.aiModel, if (state.aiKeyConfigured) "••••" else "").filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" }) { editAi = true }

    Section(R.string.section_about)
    Text(stringResource(R.string.about_version, actions.appVersion(), actions.engineVersion()), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.about_no_telemetry), Modifier.padding(horizontal = 20.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
    SwitchRow(R.string.diagnostics_log, s.diagnosticsLogging, R.string.diagnostics_log_hint) { v -> vm.updateSettings { it.copy(diagnosticsLogging = v) }; if (!v) Diagnostics.clear() }
    if (s.diagnosticsLogging) ClickRow(R.string.diagnostics_view, null) { showLog = true }
    ClickRow(R.string.privacy_policy, null) { open(SettingsPage.PRIVACY_POLICY) }
    ClickRow(R.string.licenses, null) { open(SettingsPage.LICENSES) }

    if (showClear) ClearDataDialog(vm, actions, onDismiss = { showClear = false })
    if (editHome) TextSettingDialog(R.string.homepage_custom_title, s.homepageUrl, onDismiss = { editHome = false }) { v ->
        vm.updateSettings { it.copy(homepageUrl = v.trim(), homepageMode = if (v.isBlank()) HomepageMode.START_PAGE else HomepageMode.CUSTOM_URL) }
        editHome = false
    }
    if (editTranslate) TextSettingDialog(R.string.translate_provider, s.translateTemplate, hint = R.string.translate_provider_hint, onDismiss = { editTranslate = false }) { v ->
        vm.updateSettings { it.copy(translateTemplate = v.trim().ifBlank { app.freedombrowser.data.BrowserSettings.DEFAULT_TRANSLATE_TEMPLATE }) }
        editTranslate = false
    }
    if (editListUrl) TextSettingDialog(R.string.blocklist_source, s.blockListUrl, onDismiss = { editListUrl = false }) { v ->
        vm.updateSettings { it.copy(blockListUrl = v.trim().ifBlank { app.freedombrowser.data.BrowserSettings.DEFAULT_BLOCKLIST_URL }) }
        editListUrl = false
    }
    if (editAi) AiSettingsDialog(state, vm, onDismiss = { editAi = false })
    if (showLog) AlertDialog(
        onDismissRequest = { showLog = false },
        title = { Text(stringResource(R.string.diagnostics_view)) },
        text = { Text(Diagnostics.read().takeLast(4000).ifBlank { "—" }, style = MaterialTheme.typography.labelSmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { actions.copy(Diagnostics.read()); showLog = false }) { Text(stringResource(R.string.copy)) } },
        dismissButton = { TextButton(onClick = { Diagnostics.clear(); showLog = false }) { Text(stringResource(R.string.clear)) } },
    )
}

@Composable
private fun SitesSettings(state: BrowserUiState, vm: BrowserViewModel) {
    val sites = state.siteSettings.values.sortedBy { it.site }
    if (sites.isEmpty()) Text(stringResource(R.string.no_site_settings), Modifier.padding(20.dp), color = LocalFreedomColors.current.textSecondary)
    sites.forEach { site ->
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(site.site, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.resetSite(site.site) }) { Text(stringResource(R.string.reset)) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.adblock_exception), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Switch(checked = site.adBlockException, onCheckedChange = { vm.setAdBlockException(site.site, it) })
            }
            listOf(app.freedombrowser.shared.SitePermission.CAMERA, app.freedombrowser.shared.SitePermission.MICROPHONE, app.freedombrowser.shared.SitePermission.LOCATION, app.freedombrowser.shared.SitePermission.POPUPS).forEach { p ->
                PermissionRow(p, site.decision(p)) { vm.setSitePermission(site.site, p, it) }
            }
            HorizontalDivider(Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun ClearDataDialog(vm: BrowserViewModel, actions: PageActions? = null, onDismiss: () -> Unit) {
    var range by remember { mutableStateOf(HistoryRange.LAST_HOUR) }
    var history by remember { mutableStateOf(true) }
    var cookies by remember { mutableStateOf(false) }
    var cache by remember { mutableStateOf(true) }
    var downloads by remember { mutableStateOf(false) }
    var perms by remember { mutableStateOf(false) }
    val ctxActions = actions ?: (androidx.compose.ui.platform.LocalContext.current as? PageActions)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.clear_data)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.time_range), style = MaterialTheme.typography.labelSmall)
                HistoryRange.entries.forEach { r ->
                    RadioRow(stringResource(when (r) {
                        HistoryRange.LAST_HOUR -> R.string.range_hour; HistoryRange.LAST_24_HOURS -> R.string.range_day
                        HistoryRange.LAST_7_DAYS -> R.string.range_week; HistoryRange.LAST_4_WEEKS -> R.string.range_month
                        HistoryRange.ALL_TIME -> R.string.range_all
                    }), range == r, compact = true) { range = r }
                }
                CheckRow(R.string.clear_history_item, history) { history = it }
                CheckRow(R.string.clear_cookies_item, cookies) { cookies = it }
                CheckRow(R.string.clear_cache_item, cache) { cache = it }
                CheckRow(R.string.clear_downloads_item, downloads) { downloads = it }
                CheckRow(R.string.clear_permissions_item, perms) { perms = it }
                if (cookies || cache) Text(stringResource(R.string.clear_engine_all_time_note), style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.clearBrowsingData(range, BrowserViewModel.ClearOptions(history, cookies, cache, downloads, perms)) { c, k -> ctxActions?.clearEngineData(c, k) }
                onDismiss()
            }) { Text(stringResource(R.string.clear)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun AiSettingsDialog(state: BrowserUiState, vm: BrowserViewModel, onDismiss: () -> Unit) {
    val s = state.settings
    var endpoint by remember { mutableStateOf(s.aiEndpoint) }
    var model by remember { mutableStateOf(s.aiModel) }
    var key by remember { mutableStateOf("") }
    val endpointOk = endpoint.isBlank() || AiAssist.isAcceptableEndpoint(endpoint)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_configure)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.ai_privacy_note), style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary)
                OutlinedTextField(endpoint, { endpoint = it }, label = { Text(stringResource(R.string.ai_endpoint)) }, singleLine = true, isError = !endpointOk,
                    placeholder = { Text("https://…/v1/chat/completions") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                OutlinedTextField(model, { model = it }, label = { Text(stringResource(R.string.ai_model)) }, singleLine = true)
                OutlinedTextField(key, { key = it }, label = { Text(stringResource(if (state.aiKeyConfigured) R.string.ai_key_replace else R.string.ai_key)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
                if (state.aiKeyConfigured) TextButton(onClick = { vm.setAiKey(null) }) { Text(stringResource(R.string.ai_key_remove)) }
            }
        },
        confirmButton = {
            TextButton(enabled = endpointOk, onClick = {
                vm.updateSettings { it.copy(aiEndpoint = endpoint.trim(), aiModel = model.trim()) }
                if (key.isNotBlank()) vm.setAiKey(key.trim())
                onDismiss()
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun TextSettingDialog(title: Int, initial: String, hint: Int? = null, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var v by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                hint?.let { Text(stringResource(it), style = MaterialTheme.typography.labelSmall) }
                OutlinedTextField(v, { v = it }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(v) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Section(title: Int) {
    Spacer(Modifier.height(16.dp))
    Text(stringResource(title), Modifier.padding(horizontal = 20.dp, vertical = 6.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun RadioRow(label: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected, onClick = onClick).padding(horizontal = if (compact) 0.dp else 12.dp, vertical = if (compact) 0.dp else 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CheckRow(label: Int, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked, onChange)
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun SwitchRow(label: Int, checked: Boolean, hint: Int? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(stringResource(it), style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary) }
        }
        Switch(checked, onChange)
    }
}

@Composable
private fun ClickRow(label: Int, hint: Int?, subtitleText: String? = null, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyMedium)
        val sub = subtitleText ?: hint?.let { stringResource(it) }
        sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary, maxLines = 2) }
    }
}

