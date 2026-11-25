package app.freedombrowser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.Screen
import app.freedombrowser.browser.Shortcut
import app.freedombrowser.shared.TopSite
import app.freedombrowser.shared.WebUrl
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import app.freedombrowser.browser.TabState
import app.freedombrowser.shared.PageErrorKind
import app.freedombrowser.shared.PageErrors

/** New-tab page: logo, search field, user shortcuts, recently visited sites and library entry points. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StartPage(
    isPrivate: Boolean,
    onSubmit: (String) -> Unit,
    onOpen: (Screen) -> Unit,
    shortcuts: List<Shortcut> = emptyList(),
    topSites: List<TopSite> = emptyList(),
    onOpenUrl: (String) -> Unit = {},
    onAddShortcut: (String, String) -> Boolean = { _, _ -> false },
    onRemoveShortcut: (String) -> Unit = {},
    onMoveShortcut: (String, Int) -> Unit = { _, _ -> },
    onHideSite: (String) -> Unit = {},
) {
    val colors = LocalFreedomColors.current
    var text by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))
        if (isPrivate) Icon(Icons.Filled.VisibilityOff, null, tint = colors.private, modifier = Modifier.size(48.dp))
        else androidx.compose.foundation.Image(
            androidx.compose.ui.res.painterResource(R.drawable.lumo_mark), contentDescription = null, modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (isPrivate) R.string.private_title else R.string.app_name),
            style = MaterialTheme.typography.headlineMedium,
            color = if (isPrivate) colors.private else MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(24.dp))
        Row(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().height(52.dp)
                .background(colors.surfaceRaised, RoundedCornerShape(26.dp))
                .border(1.dp, colors.outline, RoundedCornerShape(26.dp)).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Search, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(10.dp))
            BasicTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { if (text.isNotBlank()) onSubmit(text) }),
                modifier = Modifier.weight(1f),
                decorationBox = { inner -> Box { if (text.isEmpty()) Text(stringResource(R.string.start_search_hint), color = colors.textSecondary); inner() } },
            )
        }
        if (!isPrivate) {
            Spacer(Modifier.height(20.dp))
            FlowRow(Modifier.widthIn(max = 560.dp), horizontalArrangement = Arrangement.Center, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shortcuts.forEachIndexed { i, sc ->
                    SiteTile(sc.title, sc.url, onClick = { onOpenUrl(sc.url) }, menu = listOfNotNull(
                        if (i > 0) R.string.move_left to { onMoveShortcut(sc.id, -1) } else null,
                        if (i < shortcuts.lastIndex) R.string.move_right to { onMoveShortcut(sc.id, 1) } else null,
                        R.string.remove to { onRemoveShortcut(sc.id) },
                    ))
                }
                AddTile { adding = true }
            }
            if (topSites.isNotEmpty()) {
                Text(stringResource(R.string.recently_visited), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
                FlowRow(Modifier.widthIn(max = 560.dp), horizontalArrangement = Arrangement.Center) {
                    topSites.forEach { site ->
                        SiteTile(site.title, site.url, onClick = { onOpenUrl(site.url) }, menu = listOf(
                            R.string.add_shortcut to { onAddShortcut(site.title, site.url) },
                            R.string.dont_show to { onHideSite(site.host) },
                        ))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { onOpen(Screen.DOWNLOADS) }, label = { Text(stringResource(R.string.downloads_title)) }, leadingIcon = { Icon(Icons.Filled.Download, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { onOpen(Screen.BOOKMARKS) }, label = { Text(stringResource(R.string.bookmarks_title)) }, leadingIcon = { Icon(Icons.Filled.Bookmarks, null, Modifier.size(18.dp)) })
            AssistChip(onClick = { onOpen(Screen.HISTORY) }, label = { Text(stringResource(R.string.history_title)) }, leadingIcon = { Icon(Icons.Filled.History, null, Modifier.size(18.dp)) })
        }
        if (isPrivate) {
            Spacer(Modifier.height(32.dp))
            Text(
                stringResource(R.string.private_explainer),
                style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary,
                textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 480.dp),
            )
        }
    }
    if (adding) AddShortcutDialog(onDismiss = { adding = false }) { title, url -> onAddShortcut(title, url).also { if (it) adding = false } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SiteTile(title: String, url: String, onClick: () -> Unit, menu: List<Pair<Int, () -> Unit>>) {
    val colors = LocalFreedomColors.current
    var expanded by remember { mutableStateOf(false) }
    val letter = (WebUrl.hostOf(url)?.removePrefix("www.") ?: title).take(1).uppercase()
    Box {
        Column(
            Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).combinedClickable(onClick = onClick, onLongClick = { expanded = true })
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(48.dp).background(colors.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) {
                Box(Modifier.size(28.dp).background(colors.accentSoft, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text(letter, color = colors.accent, style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 4.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu.forEach { (label, action) -> DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { expanded = false; action() }) }
        }
    }
}

@Composable
private fun AddTile(onClick: () -> Unit) {
    val colors = LocalFreedomColors.current
    Column(Modifier.width(80.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(48.dp).background(colors.surfaceRaised, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.Filled.Add, null) }
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.add_shortcut), maxLines = 1, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun AddShortcutDialog(onDismiss: () -> Unit, onSave: (String, String) -> Boolean) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_shortcut)) },
        text = {
            Column {
                androidx.compose.material3.OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.title)) })
                androidx.compose.material3.OutlinedTextField(url, { url = it; error = false }, singleLine = true, label = { Text(stringResource(R.string.url)) }, isError = error)
                if (error) Text(stringResource(R.string.bookmark_err_url), color = LocalFreedomColors.current.danger)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { if (!onSave(title, url)) error = true }) { Text(stringResource(R.string.save)) } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ErrorPage(tab: TabState, onRetry: () -> Unit, onBack: () -> Unit) {
    val err = tab.error ?: return
    val colors = LocalFreedomColors.current
    val ctx = LocalContext.current
    val (title, body) = when (err.kind) {
        PageErrorKind.OFFLINE -> R.string.err_offline_title to R.string.err_offline_body
        PageErrorKind.DNS -> R.string.err_dns_title to R.string.err_dns_body
        PageErrorKind.TIMEOUT -> R.string.err_timeout_title to R.string.err_timeout_body
        PageErrorKind.CONNECTION -> R.string.err_connection_title to R.string.err_connection_body
        PageErrorKind.TLS -> R.string.err_tls_title to R.string.err_tls_body
        PageErrorKind.CERTIFICATE -> R.string.err_cert_title to R.string.err_cert_body
        PageErrorKind.INVALID_URL -> R.string.err_invalid_title to R.string.err_invalid_body
        PageErrorKind.UNSAFE -> R.string.err_unsafe_title to R.string.err_unsafe_body
        PageErrorKind.BLOCKED -> R.string.err_blocked_title to R.string.err_blocked_body
        PageErrorKind.TOO_MANY_REDIRECTS -> R.string.err_redirects_title to R.string.err_redirects_body
        PageErrorKind.RENDERER_CRASH -> R.string.err_crash_title to R.string.err_crash_body
        PageErrorKind.HTTP, PageErrorKind.UNKNOWN -> R.string.err_unknown_title to R.string.err_unknown_body
    }
    val actions = (ctx as? PageActions)
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Spacer(Modifier.height(48.dp))
        Icon(
            if (err.kind == PageErrorKind.OFFLINE) Icons.Filled.CloudOff else Icons.Filled.ErrorOutline, null,
            tint = if (err.kind == PageErrorKind.CERTIFICATE || err.kind == PageErrorKind.UNSAFE) colors.danger else colors.textSecondary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(body), style = MaterialTheme.typography.bodyMedium, color = colors.textSecondary)
        Spacer(Modifier.height(12.dp))
        Text(err.url, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 2)
        if (err.description.isNotBlank()) Text(err.description, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (err.kind == PageErrorKind.CERTIFICATE || err.kind == PageErrorKind.UNSAFE) {
                Button(onClick = onBack) { Text(stringResource(R.string.back_to_safety)) }
            } else {
                Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
            }
            OutlinedButton(onClick = {
                actions?.copy(PageErrors.details(err.kind, err.code, err.description, err.url, "Android", actions.appVersion()))
            }) { Text(stringResource(R.string.copy_error_details)) }
        }
    }
}
