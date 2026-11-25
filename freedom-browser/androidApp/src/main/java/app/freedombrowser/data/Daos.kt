package app.freedombrowser.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks WHERE parentId = :parentId ORDER BY url IS NOT NULL, position, title COLLATE NOCASE")
    fun children(parentId: Long): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url IS NULL ORDER BY title COLLATE NOCASE")
    fun folders(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url IS NOT NULL AND (title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%') ORDER BY title COLLATE NOCASE LIMIT 200")
    fun search(q: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): BookmarkEntity?

    @Query("SELECT url FROM bookmarks WHERE url IS NOT NULL")
    fun allUrls(): Flow<List<String>>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun get(id: Long): BookmarkEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks WHERE parentId = :parentId")
    suspend fun nextPosition(parentId: Long): Int

    @Insert suspend fun insert(entity: BookmarkEntity): Long
    @Update suspend fun update(entity: BookmarkEntity)

    @Query("SELECT id FROM bookmarks WHERE parentId = :parentId")
    suspend fun childIds(parentId: Long): List<Long>

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteOne(id: Long)

    /** Deletes a node and, for folders, its entire subtree. */
    @Transaction
    suspend fun deleteRecursive(id: Long) {
        for (child in childIds(id)) deleteRecursive(child)
        deleteOne(id)
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun recent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :q || '%' OR url LIKE '%' || :q || '%' ORDER BY visitedAt DESC LIMIT :limit")
    fun search(q: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT 1")
    suspend fun latest(): HistoryEntity?

    @Insert suspend fun insert(entity: HistoryEntity): Long

    @Query("UPDATE history SET title = :title WHERE id = :id")
    suspend fun updateTitle(id: Long, title: String)

    @Query("DELETE FROM history WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM history WHERE visitedAt >= :cutoff") suspend fun deleteSince(cutoff: Long)
    @Query("SELECT COUNT(*) FROM history") suspend fun count(): Int
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun all(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id") suspend fun get(id: Long): DownloadEntity?
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED','RUNNING')") suspend fun active(): List<DownloadEntity>
    @Insert suspend fun insert(entity: DownloadEntity): Long
    @Update suspend fun update(entity: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :bytes, totalBytes = :total WHERE id = :id")
    suspend fun progress(id: Long, bytes: Long, total: Long)

    @Query("DELETE FROM downloads WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED','FAILED','CANCELLED')") suspend fun clearFinished()
    @Query("DELETE FROM downloads WHERE isPrivate = 1 AND status IN ('COMPLETED','FAILED','CANCELLED')") suspend fun clearPrivateFinished()
}

@Dao
interface SiteSettingsDao {
    @Query("SELECT * FROM site_settings ORDER BY site") fun all(): Flow<List<SiteSettingsEntity>>
    @Query("SELECT * FROM site_settings WHERE site = :site") suspend fun get(site: String): SiteSettingsEntity?
    @Upsert suspend fun upsert(entity: SiteSettingsEntity)
    @Query("DELETE FROM site_settings WHERE site = :site") suspend fun delete(site: String)
    @Query("DELETE FROM site_settings") suspend fun clear()
}

@Dao
interface SessionDao {
    @Query("SELECT * FROM session_tabs ORDER BY position") suspend fun all(): List<SessionTabEntity>
    @Query("DELETE FROM session_tabs") suspend fun clear()
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(tabs: List<SessionTabEntity>)

    @Transaction
    suspend fun replace(tabs: List<SessionTabEntity>) {
        clear()
        insertAll(tabs)
    }
}
