package app.freedombrowser.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "bookmarks", indices = [Index("parentId"), Index("url")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 0 = top level. */
    val parentId: Long = 0,
    val title: String,
    /** Null for folders. */
    val url: String?,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val isFolder: Boolean get() = url == null
}

@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)

enum class DownloadStatus { QUEUED, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED }

@Entity(tableName = "downloads", indices = [Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long = -1,
    val downloadedBytes: Long = 0,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    /** content:// URI of the target file once created. */
    val contentUri: String? = null,
    /** SAF tree URI chosen by the user; null = public Downloads folder. */
    val targetTree: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val etag: String? = null,
    val supportsResume: Boolean = false,
    val error: String? = null,
    val isPrivate: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
)

@Entity(tableName = "site_settings")
data class SiteSettingsEntity(
    @PrimaryKey val site: String,
    /** JSON map permission-id -> decision-id (see shared SiteSettings). */
    val permissionsJson: String = "{}",
    val adBlockException: Boolean = false,
)

/** Normal (non-private) tabs persisted for session restore. */
@Entity(tableName = "session_tabs")
data class SessionTabEntity(
    @PrimaryKey val tabId: Long,
    val url: String,
    val title: String,
    val position: Int,
    val selected: Boolean,
    val desktopMode: Boolean,
)
