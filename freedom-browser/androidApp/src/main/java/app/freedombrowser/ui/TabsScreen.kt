package app.freedombrowser.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserUiState
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Screen
import app.freedombrowser.browser.TabState
import app.freedombrowser.shared.DisplayUrl

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabsScreen(state: BrowserUiState, vm: BrowserViewModel) {
    val colors = LocalFreedomColors.current
    var privateMode by remember { mutableStateOf(state.currentTab?.isPrivate == true) }
    val list = if (privateMode) state.tabs.privateTabs else state.tabs.normalTabs
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(R.string.tabs_title)) },
            navigationIcon = { IconButton(onClick = { vm.show(Screen.BROWSER) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = {
                if (!privateMode && state.tabs.recentlyClosed.isNotEmpty()) {
                    IconButton(onClick = vm::reopenClosedTab) { Icon(Icons.Filled.Restore, stringResource(R.string.reopen_closed_tab)) }
                }
                TextButton(onClick = { vm.closeAllTabs(privateOnly = privateMode) }, enabled = list.isNotEmpty()) { Text(stringResource(R.string.close_all)) }
            },
        )
        if (state.privateSupported) {
            TabRow(selectedTabIndex = if (privateMode) 1 else 0) {
                Tab(selected = !privateMode, onClick = { privateMode = false }, text = { Text(stringResource(R.string.tabs_normal_count, state.tabs.normalTabs.size)) })
                Tab(selected = privateMode, onClick = { privateMode = true }, text = { Text(stringResource(R.string.tabs_private_count, state.tabs.privateTabs.size)) })
            }
        }
        val full = androidx.compose.foundation.lazy.grid.GridItemSpan(Int.MAX_VALUE)
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(160.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(list.size, key = { list[it].id }) { i ->
                val tab = list[i]
                TabCard(tab, tab.id == state.tabs.selectedId, onSelect = { vm.selectTab(tab.id) }, onClose = { vm.closeTab(tab.id) },
                    modifier = Modifier.animateItem())
            }
            if (list.isEmpty()) item(span = { full }) {
                Text(stringResource(if (privateMode) R.string.no_private_tabs else R.string.no_tabs), color = colors.textSecondary, modifier = Modifier.padding(24.dp))
            }
            if (!privateMode && state.tabs.recentlyClosed.isNotEmpty()) {
                item(span = { full }) { Text(stringResource(R.string.recently_closed), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp, start = 4.dp)) }
                items(state.tabs.recentlyClosed.take(10).size, span = { full }) { i ->
                    val closed = state.tabs.recentlyClosed[i]
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { vm.openNewTab(closed.url) }.padding(12.dp)) {
                        Column {
                            Text(closed.title.ifBlank { closed.url }, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(DisplayUrl.format(closed.url), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            androidx.compose.material3.Button(onClick = { vm.openNewTab(isPrivate = privateMode) }, modifier = Modifier.weight(1f).height(48.dp)) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(if (privateMode) R.string.new_private_tab else R.string.new_tab))
            }
        }
    }
}

/** Tab switcher card: title bar with favicon and close button above a preview of the page. */
@Composable
private fun TabCard(tab: TabState, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = LocalFreedomColors.current
    val border = if (selected) (if (tab.isPrivate) colors.private else colors.accent) else colors.outline
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(colors.surfaceRaised)
            .border(if (selected) 2.dp else 1.dp, border, RoundedCornerShape(14.dp)).clickable(onClick = onSelect),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                val icon = tab.favicon
                when {
                    icon != null -> Image(icon.asImageBitmap(), null, Modifier.size(16.dp))
                    tab.isPrivate -> Icon(Icons.Filled.VisibilityOff, null, tint = colors.private, modifier = Modifier.size(16.dp))
                    else -> Icon(Icons.Filled.Public, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                tab.title.ifBlank { if (tab.url.isEmpty()) stringResource(R.string.start_page) else DisplayUrl.format(tab.url) },
                maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Close, stringResource(R.string.close_tab), Modifier.size(18.dp)) }
        }
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f).background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
            val thumb = tab.thumbnail
            when {
                thumb != null -> Image(thumb.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
                tab.url.isEmpty() -> Image(androidx.compose.ui.res.painterResource(if (tab.isPrivate) R.drawable.lumo_mark else R.drawable.lumo_mark), null, Modifier.size(48.dp), alpha = if (tab.isPrivate) 0.4f else 1f)
                else -> Text(DisplayUrl.format(tab.url), style = MaterialTheme.typography.labelSmall, color = colors.textSecondary, maxLines = 3, modifier = Modifier.padding(12.dp))
            }
        }
    }
}
