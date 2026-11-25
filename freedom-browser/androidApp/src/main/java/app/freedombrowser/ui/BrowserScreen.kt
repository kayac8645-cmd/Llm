package app.freedombrowser.ui

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserUiState
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.FindState
import app.freedombrowser.browser.TabState
import app.freedombrowser.browser.WebViewManager
import app.freedombrowser.shared.DisplayUrl
import app.freedombrowser.shared.SecurityLevel

@Composable
fun BrowserScreen(
    state: BrowserUiState,
    vm: BrowserViewModel,
    manager: WebViewManager,
    actions: PageActions,
) {
    val tab = state.currentTab ?: return
    var showMenu by remember { mutableStateOf(false) }
    var showSiteInfo by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        AddressBar(
            tab = tab,
            tabCount = state.tabs.tabs.size,
            onSubmit = { vm.submitInput(it) },
            onShowTabs = { vm.show(app.freedombrowser.browser.Screen.TABS) },
            onSiteInfo = { showSiteInfo = true },
        )
        if (tab.isLoading && !tab.isStartPage) {
            LinearProgressIndicator(progress = { tab.progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp), drawStopIndicator = {})
        } else Spacer(Modifier.height(2.dp))
        AnimatedVisibility(!state.isOnline) { OfflineBanner() }
        state.find?.let { FindBar(it, vm) }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                tab.error != null -> ErrorPage(tab, onRetry = vm::reload, onBack = { vm.goBack() })
                tab.isStartPage -> StartPage(tab.isPrivate, onSubmit = { vm.submitInput(it) }, onOpen = vm::show)
                else -> WebViewHost(tab.id, manager)
            }
        }
        BottomBar(tab, vm, onMenu = { showMenu = true })
    }

    if (showMenu) MainMenuSheet(state, vm, actions, onDismiss = { showMenu = false })
    if (showSiteInfo) SiteInfoSheet(state, tab, vm, onDismiss = { showSiteInfo = false })
}

@Composable
private fun WebViewHost(tabId: Long, manager: WebViewManager) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx -> FrameLayout(ctx).apply { layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT) } },
        update = { frame ->
            val wv = manager.view(tabId)
            if (frame.getChildAt(0) !== wv) {
                frame.removeAllViews()
                if (wv != null) {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    frame.addView(wv)
                }
            }
        },
        onRelease = { it.removeAllViews() },
    )
}

@Composable
private fun AddressBar(tab: TabState, tabCount: Int, onSubmit: (String) -> Unit, onShowTabs: () -> Unit, onSiteInfo: () -> Unit) {
    val colors = LocalFreedomColors.current
    var focused by remember { mutableStateOf(false) }
    var field by remember(tab.id) { mutableStateOf(TextFieldValue("")) }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(tab.url, focused) {
        if (!focused) field = TextFieldValue(DisplayUrl.format(tab.url))
    }
    val outline = if (tab.isPrivate) colors.private else if (focused) MaterialTheme.colorScheme.primary else colors.outline
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(colors.surfaceRaised).border(1.dp, outline, RoundedCornerShape(22.dp))
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SecurityIcon(tab, onSiteInfo)
                BasicTextField(
                    value = field,
                    onValueChange = { field = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                    keyboardActions = KeyboardActions(onGo = {
                        onSubmit(field.text)
                        focusManager.clearFocus()
                    }),
                    modifier = Modifier.weight(1f).focusRequester(focusRequester).onFocusChanged { fs ->
                        if (fs.isFocused && !focused) {
                            val full = tab.url
                            field = TextFieldValue(full, TextRange(0, full.length))
                        }
                        focused = fs.isFocused
                    }.semantics { contentDescription = "Address bar" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (field.text.isEmpty()) Text(
                                stringResource(if (tab.isPrivate) R.string.address_hint_private else R.string.address_hint),
                                color = colors.textSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
                            inner()
                        }
                    },
                )
                if (focused && field.text.isNotEmpty()) {
                    IconButton(onClick = { field = TextFieldValue("") }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Close, stringResource(R.string.clear), Modifier.size(18.dp))
                    }
                } else if (tab.blockedCount > 0) {
                    Row(Modifier.clickable(onClick = onSiteInfo).padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Shield, stringResource(R.string.blocked_count, tab.blockedCount), tint = colors.accent, modifier = Modifier.size(16.dp))
                        Text(tab.blockedCount.toString(), style = MaterialTheme.typography.labelSmall, color = colors.accent, modifier = Modifier.padding(start = 2.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            TabCountButton(tabCount, tab.isPrivate, onShowTabs)
        }
    }
}

@Composable
private fun SecurityIcon(tab: TabState, onClick: () -> Unit) {
    val colors = LocalFreedomColors.current
    val (icon, tint, desc) = when {
        tab.isPrivate && tab.isStartPage -> Triple(Icons.Filled.VisibilityOff, colors.private, R.string.private_tab)
        else -> when (tab.security) {
            SecurityLevel.SECURE -> Triple(Icons.Filled.Lock, colors.textSecondary, R.string.connection_secure)
            SecurityLevel.INSECURE -> Triple(Icons.Filled.Warning, colors.warning, R.string.connection_insecure)
            SecurityLevel.CERT_ERROR -> Triple(Icons.Filled.Warning, colors.danger, R.string.connection_cert_error)
            else -> Triple(Icons.Filled.Info, colors.textSecondary, R.string.site_info)
        }
    }
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) { Icon(icon, stringResource(desc), tint = tint, modifier = Modifier.size(18.dp)) }
}

@Composable
private fun TabCountButton(count: Int, isPrivate: Boolean, onClick: () -> Unit) {
    val colors = LocalFreedomColors.current
    val label = stringResource(R.string.tabs_open, count)
    Box(
        Modifier.size(48.dp).clickable(onClick = onClick).semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).border(1.5.dp, if (isPrivate) colors.private else MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center,
        ) { Text(if (count > 99) "99+" else count.toString(), style = MaterialTheme.typography.labelSmall) }
    }
}

@Composable
private fun BottomBar(tab: TabState, vm: BrowserViewModel, onMenu: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goBack() }, enabled = tab.canGoBack || !tab.isStartPage) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) }
            IconButton(onClick = vm::goForward, enabled = tab.canGoForward) { Icon(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.forward)) }
            IconButton(onClick = vm::goHome) { Icon(Icons.Filled.Home, stringResource(R.string.home)) }
            if (tab.isLoading && !tab.isStartPage) IconButton(onClick = vm::stop) { Icon(Icons.Filled.Close, stringResource(R.string.stop)) }
            else IconButton(onClick = vm::reload, enabled = !tab.isStartPage) { Icon(Icons.Filled.Refresh, stringResource(R.string.reload)) }
            IconButton(onClick = onMenu) { Icon(Icons.Filled.MoreVert, stringResource(R.string.menu)) }
        }
    }
}

@Composable
private fun OfflineBanner() {
    val colors = LocalFreedomColors.current
    Row(Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.WifiOff, null, tint = colors.warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.offline_banner), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FindBar(find: FindState, vm: BrowserViewModel) {
    val colors = LocalFreedomColors.current
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(Modifier.fillMaxWidth().background(colors.surfaceRaised).padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BasicTextField(
            value = find.query, onValueChange = vm::find, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.findNext(true) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
            decorationBox = { inner -> Box { if (find.query.isEmpty()) Text(stringResource(R.string.find_in_page), color = colors.textSecondary); inner() } },
        )
        Text(if (find.query.isEmpty()) "" else "${find.active}/${find.total}", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        IconButton(onClick = { vm.findNext(false) }) { Icon(Icons.Filled.KeyboardArrowUp, stringResource(R.string.find_previous)) }
        IconButton(onClick = { vm.findNext(true) }) { Icon(Icons.Filled.KeyboardArrowDown, stringResource(R.string.find_next)) }
        IconButton(onClick = vm::closeFind) { Icon(Icons.Filled.Close, stringResource(R.string.close)) }
    }
}
