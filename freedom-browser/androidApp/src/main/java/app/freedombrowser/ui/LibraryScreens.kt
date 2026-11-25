package app.freedombrowser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.freedombrowser.R
import app.freedombrowser.browser.BrowserViewModel
import app.freedombrowser.browser.Screen
import app.freedombrowser.data.BookmarkEntity
import app.freedombrowser.data.BookmarkRepository
import app.freedombrowser.data.DownloadEntity
import app.freedombrowser.data.DownloadStatus
import app.freedombrowser.shared.BookmarkTree
import app.freedombrowser.shared.DisplayUrl
import app.freedombrowser.shared.FileNames
import app.freedombrowser.shared.Formatters
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryScaffold(title: Int, onBack: () -> Unit, actions: @Composable () -> Unit = {}, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        TopAppBar(
            title = { Text(stringResource(title)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            actions = { actions() },
        )
        content()
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, hint: Int) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, placeholder = { Text(stringResource(hint)) },
        trailingIcon = { if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Filled.Close, stringResource(R.string.clear)) } },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun EmptyState(text: Int) {
    Text(stringResource(text), color = LocalFreedomColors.current.textSecondary, modifier = Modifier.padding(32.dp))
}

// ---------------------------------------------------------------------------------- Bookmarks

@Composable
fun BookmarksScreen(lib: LibraryViewModel, vm: BrowserViewModel) {
    val items by lib.bookmarks.collectAsState()
    val stack by lib.folderStack.collectAsState()
    val query by lib.bookmarkQuery.collectAsState()
    var editing by remember { mutableStateOf<BookmarkEntity?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var creatingBookmark by remember { mutableStateOf(false) }
    val back = { if (!lib.upFolder()) vm.show(Screen.BROWSER) }
    BackHandler(onBack = back)
    LibraryScaffold(R.string.bookmarks_title, back, actions = {
        IconButton(onClick = { creatingBookmark = true }) { Icon(Icons.Filled.Bookmark, stringResource(R.string.add_bookmark)) }
        IconButton(onClick = { creatingFolder = true }) { Icon(Icons.Filled.CreateNewFolder, stringResource(R.string.new_folder)) }
    }) {
        SearchField(query, { lib.bookmarkQuery.value = it }, R.string.search_bookmarks)
        if (stack.size > 1 && query.isBlank()) Text(
            stack.drop(1).joinToString(" / ") { it.title }, style = MaterialTheme.typography.labelSmall,
            color = LocalFreedomColors.current.textSecondary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
            if (items.isEmpty()) item { EmptyState(R.string.no_bookmarks) }
            items(items, key = { it.id }) { b ->
                LibraryRow(
                    icon = { Icon(if (b.isFolder) Icons.Filled.Folder else Icons.Filled.Bookmark, null, tint = LocalFreedomColors.current.accent) },
                    title = b.title, subtitle = b.url?.let(DisplayUrl::format),
                    onClick = { if (b.isFolder) lib.openFolder(b) else vm.openNewTab(b.url!!) },
                ) {
                    IconButton(onClick = { editing = b }) { Icon(Icons.Filled.Edit, stringResource(R.string.edit)) }
                    IconButton(onClick = { lib.deleteBookmark(b.id) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
            }
        }
    }
    editing?.let { b -> BookmarkEditDialog(b, lib, onDone = { editing = null }) }
    if (creatingFolder) NameDialog(R.string.new_folder, "", onDismiss = { creatingFolder = false }) { name -> lib.addFolder(name) {}; creatingFolder = false }
    if (creatingBookmark) BookmarkEditDialog(null, lib, onDone = { creatingBookmark = false })
}

@Composable
private fun BookmarkEditDialog(b: BookmarkEntity?, lib: LibraryViewModel, onDone: () -> Unit) {
    val folders by lib.folders.collectAsState()
    var title by remember { mutableStateOf(b?.title.orEmpty()) }
    var url by remember { mutableStateOf(b?.url.orEmpty()) }
    var parent by remember { mutableStateOf(b?.parentId ?: lib.currentFolderId) }
    var error by remember { mutableStateOf<Int?>(null) }
    var folderMenu by remember { mutableStateOf(false) }
    val isFolder = b?.isFolder == true
    val handle: (BookmarkRepository.Result) -> Unit = { r ->
        when (r) {
            is BookmarkRepository.Result.Ok -> onDone()
            is BookmarkRepository.Result.Invalid -> error = when (r.reason) { "cycle" -> R.string.bookmark_err_cycle; "url" -> R.string.bookmark_err_url; else -> R.string.bookmark_err_title }
        }
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text(stringResource(if (b == null) R.string.add_bookmark else R.string.edit)) },
        text = {
            Column {
                OutlinedTextField(title, { title = it }, singleLine = true, label = { Text(stringResource(R.string.title)) })
                if (!isFolder) OutlinedTextField(url, { url = it }, singleLine = true, label = { Text(stringResource(R.string.url)) })
                if (b != null) {
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { folderMenu = true }) {
                        Text(stringResource(R.string.folder) + ": " + (folders.firstOrNull { it.id == parent }?.title ?: stringResource(R.string.bookmarks_root)))
                    }
                    DropdownMenu(expanded = folderMenu, onDismissRequest = { folderMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.bookmarks_root)) }, onClick = { parent = BookmarkTree.ROOT_ID; folderMenu = false })
                        folders.filter { it.id != b.id }.forEach { f -> DropdownMenuItem(text = { Text(f.title) }, onClick = { parent = f.id; folderMenu = false }) }
                    }
                }
                error?.let { Text(stringResource(it), color = LocalFreedomColors.current.danger) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (b == null) lib.addBookmark(title, url, handle) else lib.editBookmark(b.id, title, if (isFolder) null else url, parent, handle)
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDone) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NameDialog(title: Int, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { OutlinedTextField(name, { name = it }, singleLine = true) },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name) }) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

// ------------------------------------------------------------------------------------ History

@Composable
fun HistoryScreen(lib: LibraryViewModel, vm: BrowserViewModel) {
    val items by lib.history.collectAsState()
    val query by lib.historyQuery.collectAsState()
    var showClear by remember { mutableStateOf(false) }
    BackHandler { vm.show(Screen.BROWSER) }
    LibraryScaffold(R.string.history_title, { vm.show(Screen.BROWSER) }, actions = {
        TextButton(onClick = { showClear = true }) { Text(stringResource(R.string.clear_data)) }
    }) {
        SearchField(query, { lib.historyQuery.value = it }, R.string.search_history)
        val dayFormat = remember { DateFormat.getDateInstance(DateFormat.MEDIUM) }
        val timeFormat = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
        LazyColumn {
            if (items.isEmpty()) item { EmptyState(R.string.no_history) }
            var lastDay = ""
            items.forEach { h ->
                val day = dayFormat.format(Date(h.visitedAt))
                if (day != lastDay) {
                    lastDay = day
                    item(key = "d$day") { Text(day, style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp)) }
                }
                item(key = h.id) {
                    LibraryRow(
                        icon = { Text(timeFormat.format(Date(h.visitedAt)), style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary) },
                        title = h.title, subtitle = DisplayUrl.format(h.url), onClick = { vm.openNewTab(h.url) },
                    ) { IconButton(onClick = { lib.deleteHistory(h.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.delete)) } }
                }
            }
        }
    }
    if (showClear) ClearDataDialog(vm, onDismiss = { showClear = false })
}

// ---------------------------------------------------------------------------------- Downloads

@Composable
fun DownloadsScreen(lib: LibraryViewModel, vm: BrowserViewModel, actions: PageActions) {
    val items by lib.downloads.collectAsState()
    val speeds by lib.speeds.collectAsState()
    var confirmOpen by remember { mutableStateOf<DownloadEntity?>(null) }
    BackHandler { vm.show(Screen.BROWSER) }
    LibraryScaffold(R.string.downloads_title, { vm.show(Screen.BROWSER) }, actions = {
        TextButton(onClick = lib::clearFinished, enabled = items.any { it.status in FINISHED }) { Text(stringResource(R.string.clear_history)) }
    }) {
        LazyColumn {
            if (items.isEmpty()) item { EmptyState(R.string.no_downloads) }
            items(items, key = { it.id }) { d -> DownloadRow(d, speeds[d.id] ?: 0, lib, actions, onOpen = { if (FileNames.isPotentiallyDangerous(d.fileName, d.mimeType)) confirmOpen = d else actions.openDownload(d) }) }
        }
    }
    confirmOpen?.let { d ->
        AlertDialog(
            onDismissRequest = { confirmOpen = null },
            title = { Text(stringResource(R.string.download_dangerous_title)) },
            text = { Text(stringResource(R.string.open_dangerous_body, d.fileName)) },
            confirmButton = { TextButton(onClick = { confirmOpen = null; actions.openDownload(d) }) { Text(stringResource(R.string.open)) } },
            dismissButton = { TextButton(onClick = { confirmOpen = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private val FINISHED = setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED)

@Composable
private fun DownloadRow(d: DownloadEntity, speed: Long, lib: LibraryViewModel, actions: PageActions, onOpen: () -> Unit) {
    val colors = LocalFreedomColors.current
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().clickable(enabled = d.status == DownloadStatus.COMPLETED, onClick = onOpen).padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (FileNames.isPotentiallyDangerous(d.fileName, d.mimeType)) Icons.Filled.Warning else Icons.Filled.InsertDriveFile, null,
                tint = if (d.status == DownloadStatus.FAILED) colors.danger else colors.accent)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                val status = when (d.status) {
                    DownloadStatus.QUEUED -> stringResource(R.string.dl_queued)
                    DownloadStatus.RUNNING -> listOf(
                        "${Formatters.bytes(d.downloadedBytes)} / ${Formatters.bytes(d.totalBytes)}", Formatters.speed(speed),
                        Formatters.eta(d.totalBytes - d.downloadedBytes, speed),
                    ).filter { it.isNotEmpty() }.joinToString(" · ")
                    DownloadStatus.PAUSED -> stringResource(R.string.dl_paused, Formatters.bytes(d.downloadedBytes))
                    DownloadStatus.COMPLETED -> Formatters.bytes(d.totalBytes) + (if (d.isPrivate) " · " + stringResource(R.string.private_tab) else "")
                    DownloadStatus.FAILED -> stringResource(R.string.dl_failed, d.error.orEmpty())
                    DownloadStatus.CANCELLED -> stringResource(R.string.dl_cancelled)
                }
                Text(status, style = MaterialTheme.typography.labelSmall, color = if (d.status == DownloadStatus.FAILED) colors.danger else colors.textSecondary, maxLines = 1)
            }
            when (d.status) {
                DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                    if (d.supportsResume) IconButton(onClick = { lib.pause(d.id) }) { Icon(Icons.Filled.Pause, stringResource(R.string.pause)) }
                    IconButton(onClick = { lib.cancel(d.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = { lib.resume(d.id) }) { Icon(Icons.Filled.PlayArrow, stringResource(R.string.resume)) }
                    IconButton(onClick = { lib.cancel(d.id) }) { Icon(Icons.Filled.Close, stringResource(R.string.cancel)) }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = { lib.retry(d.id) }) { Icon(Icons.Filled.Refresh, stringResource(R.string.retry)) }
                    IconButton(onClick = { lib.remove(d.id, false) }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = { actions.shareDownload(d) }) { Icon(Icons.Filled.Share, stringResource(R.string.share)) }
                    Column {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Filled.Delete, stringResource(R.string.delete)) }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.remove_from_list)) }, onClick = { menu = false; lib.remove(d.id, false) })
                            DropdownMenuItem(text = { Text(stringResource(R.string.delete_file)) }, onClick = { menu = false; lib.remove(d.id, true) })
                        }
                    }
                }
            }
        }
        if (d.status == DownloadStatus.RUNNING || d.status == DownloadStatus.PAUSED) {
            val pct = Formatters.percent(d.downloadedBytes, d.totalBytes)
            if (pct >= 0) LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp), drawStopIndicator = {})
            else LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp))
        }
    }
}

@Composable
fun LibraryRow(icon: @Composable () -> Unit, title: String, subtitle: String?, onClick: () -> Unit, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.size(width = 44.dp, height = 24.dp), contentAlignment = Alignment.CenterStart) { icon() }
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall, color = LocalFreedomColors.current.textSecondary)
        }
        trailing()
    }
}
