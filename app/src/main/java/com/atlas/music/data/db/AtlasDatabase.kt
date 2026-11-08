package com.atlas.music.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackEntity::class,
        PlayEventEntity::class,
        DownloadTaskEntity::class,
        PomodoroSessionEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(DownloadTypeConverters::class)
abstract class AtlasDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playEventDao(): PlayEventDao
    abstract fun downloadDao(): DownloadDao
    abstract fun pomodoroDao(): PomodoroDao
}

class DownloadTypeConverters {
    @androidx.room.TypeConverter
    fun statusToString(status: DownloadStatus): String = status.name

    @androidx.room.TypeConverter
    fun stringToStatus(value: String): DownloadStatus =
        runCatching { DownloadStatus.valueOf(value) }.getOrDefault(DownloadStatus.FAILED)
}
