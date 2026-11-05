package io.github.adit0132.rockscreen

import android.app.Application
import androidx.room.Room
import io.github.adit0132.rockscreen.data.db.AppDatabase
import io.github.adit0132.rockscreen.data.network.LrcLibApi
import io.github.adit0132.rockscreen.data.repository.LyricsRepository
import io.github.adit0132.rockscreen.worker.CacheEvictionWorker

class RockscreenApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var lyricsRepository: LyricsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = Room.databaseBuilder(this, AppDatabase::class.java, "rockscreen-db")
            .fallbackToDestructiveMigration()
            .build()
        lyricsRepository = LyricsRepository(database, LrcLibApi())
        CacheEvictionWorker.schedule(this)
    }
}
