package app.freedombrowser.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter fun fromStatus(s: DownloadStatus): String = s.name
    @TypeConverter fun toStatus(s: String): DownloadStatus = DownloadStatus.valueOf(s)
}

@Database(
    entities = [BookmarkEntity::class, HistoryEntity::class, DownloadEntity::class, SiteSettingsEntity::class, SessionTabEntity::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class FreedomDatabase : RoomDatabase() {
    abstract fun bookmarks(): BookmarkDao
    abstract fun history(): HistoryDao
    abstract fun downloads(): DownloadDao
    abstract fun siteSettings(): SiteSettingsDao
    abstract fun session(): SessionDao

    companion object {
        fun create(context: Context): FreedomDatabase =
            Room.databaseBuilder(context, FreedomDatabase::class.java, "freedom.db").build()
    }
}
