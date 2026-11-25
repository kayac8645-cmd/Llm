package app.freedombrowser.data

import app.freedombrowser.shared.BookmarkTree
import app.freedombrowser.shared.HistoryRange
import app.freedombrowser.shared.PermissionDecision
import app.freedombrowser.shared.SitePermission
import app.freedombrowser.shared.SiteSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

class BookmarkRepository(private val dao: BookmarkDao) {
    fun children(parentId: Long) = dao.children(parentId)
    fun folders() = dao.folders()
    fun search(q: String) = dao.search(q)
    fun bookmarkedUrls(): Flow<Set<String>> = dao.allUrls().map { it.toSet() }
    suspend fun findByUrl(url: String) = dao.findByUrl(url)

    sealed class Result {
        data class Ok(val id: Long) : Result()
        data class Invalid(val reason: String) : Result()
    }

    suspend fun addBookmark(title: String, url: String, parentId: Long = BookmarkTree.ROOT_ID): Result {
        val t = BookmarkTree.validateTitle(title) ?: url.take(300)
        val u = BookmarkTree.validateUrl(url) ?: return Result.Invalid("url")
        dao.findByUrl(u)?.let { return Result.Ok(it.id) }
        return Result.Ok(dao.insert(BookmarkEntity(parentId = parentId, title = t, url = u, position = dao.nextPosition(parentId))))
    }

    suspend fun addFolder(title: String, parentId: Long = BookmarkTree.ROOT_ID): Result {
        val t = BookmarkTree.validateTitle(title) ?: return Result.Invalid("title")
        return Result.Ok(dao.insert(BookmarkEntity(parentId = parentId, title = t, url = null, position = dao.nextPosition(parentId))))
    }

    /** Edits title/url and moves the node. Rejects moves that would nest a folder inside itself. */
    suspend fun edit(id: Long, title: String, url: String?, parentId: Long): Result {
        val existing = dao.get(id) ?: return Result.Invalid("missing")
        val t = BookmarkTree.validateTitle(title) ?: return Result.Invalid("title")
        val u = if (existing.isFolder) null else (BookmarkTree.validateUrl(url.orEmpty()) ?: return Result.Invalid("url"))
        if (existing.isFolder) {
            val parents = HashMap<Long, Long>()
            suspend fun parentOf(node: Long): Long? = parents[node] ?: dao.get(node)?.parentId?.also { parents[node] = it }
            var cursor: Long? = parentId
            var guard = 0
            while (cursor != null && cursor != BookmarkTree.ROOT_ID) {
                if (cursor == id) return Result.Invalid("cycle")
                cursor = parentOf(cursor)
                if (++guard > 10_000) return Result.Invalid("cycle")
            }
        }
        val position = if (parentId != existing.parentId) dao.nextPosition(parentId) else existing.position
        dao.update(existing.copy(title = t, url = u, parentId = parentId, position = position))
        return Result.Ok(id)
    }

    suspend fun delete(id: Long) = dao.deleteRecursive(id)
}

/**
 * Browsing history. Private tabs must never reach this class's [record] — it additionally refuses
 * private visits defensively.
 */
class HistoryRepository(private val dao: HistoryDao, private val now: () -> Long = System::currentTimeMillis) {
    fun recent(limit: Int = 500) = dao.recent(limit)
    fun search(q: String, limit: Int = 500) = if (q.isBlank()) dao.recent(limit) else dao.search(q.trim(), limit)

    suspend fun record(url: String, title: String, isPrivate: Boolean) {
        if (isPrivate) return
        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        val latest = dao.latest()
        // Collapse reloads / redirects to the same URL within a short window.
        if (latest != null && latest.url == url && now() - latest.visitedAt < 30_000) {
            if (title.isNotBlank() && title != latest.title) dao.updateTitle(latest.id, title)
            return
        }
        dao.insert(HistoryEntity(url = url, title = title.ifBlank { url }, visitedAt = now()))
    }

    suspend fun updateTitle(url: String, title: String, isPrivate: Boolean) {
        if (isPrivate || title.isBlank()) return
        val latest = dao.latest() ?: return
        if (latest.url == url) dao.updateTitle(latest.id, title)
    }

    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun clear(range: HistoryRange) = dao.deleteSince(range.cutoff(now()))
}

class SiteSettingsRepository(private val dao: SiteSettingsDao) {
    private val json = Json { ignoreUnknownKeys = true }

    fun all(): Flow<List<SiteSettings>> = dao.all().map { list -> list.map(::toModel) }

    suspend fun get(site: String): SiteSettings = dao.get(site)?.let(::toModel) ?: SiteSettings(site)

    suspend fun setPermission(site: String, permission: SitePermission, decision: PermissionDecision) =
        save(get(site).with(permission, decision))

    suspend fun setAdBlockException(site: String, exempt: Boolean) = save(get(site).copy(adBlockException = exempt))

    suspend fun reset(site: String) = dao.delete(site)
    suspend fun clearAll() = dao.clear()

    private suspend fun save(s: SiteSettings) {
        if (s.isDefault) dao.delete(s.site)
        else dao.upsert(SiteSettingsEntity(s.site, json.encodeToString(s.permissions), s.adBlockException))
    }

    private fun toModel(e: SiteSettingsEntity) = SiteSettings(
        site = e.site,
        permissions = runCatching { json.decodeFromString<Map<String, String>>(e.permissionsJson) }.getOrDefault(emptyMap()),
        adBlockException = e.adBlockException,
    )
}
