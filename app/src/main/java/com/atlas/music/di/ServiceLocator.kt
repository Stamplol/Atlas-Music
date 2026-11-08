package com.atlas.music.di

import android.content.Context
import androidx.room.Room
import com.atlas.music.data.db.AtlasDatabase
import com.atlas.music.data.repo.MusicRepository
import com.atlas.music.data.repo.PlaylistRepository
import com.atlas.music.downloads.DownloadManager
import com.atlas.music.downloads.YtdlpUpdater
import com.atlas.music.playback.PlaybackConnection

/**
 * Manual dependency wiring. Single-process app, so a small object graph beats
 * a DI framework here — fewer moving parts, same clarity.
 */
object ServiceLocator {
    @Volatile private var initialized = false

    lateinit var appContext: Context
        private set
    lateinit var database: AtlasDatabase
        private set
    lateinit var settings: SettingsRepository
        private set
    lateinit var musicRepository: MusicRepository
        private set
    lateinit var playlistRepository: PlaylistRepository
        private set
    lateinit var downloadManager: DownloadManager
        private set
    lateinit var ytdlpUpdater: YtdlpUpdater
        private set
    lateinit var playbackConnection: PlaybackConnection
        private set

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            appContext = context.applicationContext
            database = Room.databaseBuilder(appContext, AtlasDatabase::class.java, "atlas_music.db")
                .fallbackToDestructiveMigration() // v1 only; bump intentionally later
                .build()
            settings = SettingsRepository(appContext)
            musicRepository = MusicRepository(appContext, database)
            playlistRepository = PlaylistRepository(database)
            downloadManager = DownloadManager(appContext, database, musicRepository, settings)
            ytdlpUpdater = YtdlpUpdater(appContext, settings)
            playbackConnection = PlaybackConnection(appContext).apply { connect() }
            initialized = true
        }
    }
}
