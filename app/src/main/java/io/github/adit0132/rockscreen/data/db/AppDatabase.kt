package io.github.adit0132.rockscreen.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [CachedLyrics::class],
    version = 2, // 2: Unicode-aware cache keys (old keys collided for non-Latin titles)
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lyricsDao(): LyricsDao
}
