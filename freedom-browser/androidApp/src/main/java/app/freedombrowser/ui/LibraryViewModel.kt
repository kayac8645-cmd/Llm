package app.freedombrowser.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.freedombrowser.AppContainer
import app.freedombrowser.data.BookmarkEntity
import app.freedombrowser.data.BookmarkRepository
import app.freedombrowser.data.DownloadEntity
import app.freedombrowser.data.HistoryEntity
import app.freedombrowser.shared.BookmarkTree
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Bookmarks, history and downloads lists. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(private val c: AppContainer) : ViewModel() {
    val folderStack = MutableStateFlow(listOf(BookmarkEntity(id = BookmarkTree.ROOT_ID, title = "", url = null)))
    val bookmarkQuery = MutableStateFlow("")
    val bookmarks: StateFlow<List<BookmarkEntity>> = combineQuery()
    val folders: StateFlow<List<BookmarkEntity>> = c.bookmarks.folders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyQuery = MutableStateFlow("")
    val history: StateFlow<List<HistoryEntity>> = historyQuery.flatMapLatest { c.history.search(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloads: StateFlow<List<DownloadEntity>> = c.downloads.downloads.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val speeds = c.downloads.speeds

    private fun combineQuery(): StateFlow<List<BookmarkEntity>> =
        kotlinx.coroutines.flow.combine(folderStack, bookmarkQuery) { stack, q -> stack.last().id to q }
            .flatMapLatest { (folder, q) -> if (q.isBlank()) c.bookmarks.children(folder) else c.bookmarks.search(q.trim()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun openFolder(folder: BookmarkEntity) { folderStack.value = folderStack.value + folder }
    fun upFolder(): Boolean {
        if (folderStack.value.size <= 1) return false
        folderStack.value = folderStack.value.dropLast(1)
        return true
    }
    val currentFolderId: Long get() = folderStack.value.last().id

    fun addFolder(title: String, onResult: (BookmarkRepository.Result) -> Unit) = viewModelScope.launch { onResult(c.bookmarks.addFolder(title, currentFolderId)) }
    fun addBookmark(title: String, url: String, onResult: (BookmarkRepository.Result) -> Unit) = viewModelScope.launch { onResult(c.bookmarks.addBookmark(title, url, currentFolderId)) }
    fun editBookmark(id: Long, title: String, url: String?, parentId: Long, onResult: (BookmarkRepository.Result) -> Unit) =
        viewModelScope.launch { onResult(c.bookmarks.edit(id, title, url, parentId)) }
    fun deleteBookmark(id: Long) = viewModelScope.launch { c.bookmarks.delete(id) }

    fun deleteHistory(id: Long) = viewModelScope.launch { c.history.delete(id) }

    fun pause(id: Long) = c.downloads.pause(id)
    fun resume(id: Long) = c.downloads.resume(id)
    fun cancel(id: Long) = c.downloads.cancel(id)
    fun retry(id: Long) = c.downloads.retry(id)
    fun remove(id: Long, deleteFile: Boolean) = c.downloads.remove(id, deleteFile)
    fun clearFinished() = c.downloads.clearFinished()
}
