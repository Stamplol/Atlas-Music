package com.atlas.music.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One row per audio file found on the device (or imported from a download). */
@Entity(
    tableName = "tracks",
    indices = [
        Index("uri", unique = true),
        Index("title"),
        Index("artist"),
        Index("album"),
        Index("genre"),
        Index("folder"),
        Index("dateAddedSec"),
        Index("fingerprint"),
    ],
)
data class TrackEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** content:// media URI — stable across reboots, unique per file. */
    val uri: String,
    val filePath: String,
    val displayName: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val discNumber: Int,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    /** Lower-cased top-level folder, e.g. "music/flac". */
    val folder: String,
    val albumId: Long,
    /** title|artist|durationSec fingerprint for duplicate detection. */
    val fingerprint: String,
    val mimeType: String = "",
    val isFavorite: Boolean = false,
    /** True after the user edits tags here — scans must preserve those fields. */
    val userEdited: Boolean = false,
)

/** A user-created playlist. */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
)

/** Ordered membership of a playlist. */
@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    indices = [Index("trackId"), Index("position")],
)
data class PlaylistTrackEntity(
    val playlistId: Long,
    val trackId: Long,
    val position: Int,
    val addedAtMs: Long = System.currentTimeMillis(),
)

/** Listening history — local only, never leaves the device. */
@Entity(tableName = "play_events", indices = [Index("trackId"), Index("playedAtMs")])
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val playedAtMs: Long = System.currentTimeMillis(),
)

enum class DownloadStatus { QUEUED, FETCHING_INFO, DOWNLOADING, COMPLETED, FAILED, CANCELLED }

/** One row per download attempt; survives restarts. */
@Entity(tableName = "download_tasks", indices = [Index("createdAtMs"), Index("status")])
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val videoId: String,
    val title: String,
    val uploader: String,
    val thumbnailUrl: String,
    val format: String,          // opus | m4a | best
    val status: DownloadStatus,
    val progress: Float = 0f,    // 0..1
    val bytesDownloaded: Long = 0,
    val bytesTotal: Long = 0,
    val speedBytesPerSec: Long = 0,
    val etaSeconds: Long = 0,
    val outputPath: String? = null,
    val error: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null,
)

enum class SmartPlaylist(val label: String) {
    MOST_PLAYED("Most played"),
    RECENTLY_PLAYED("Recently played"),
    RECENTLY_ADDED("Recently added"),
    FAVORITES("Favorites"),
}


/** Completed focus/break sessions — local ledger for streaks and totals. */
@androidx.room.Entity(tableName = "pomodoro_sessions", indices = [androidx.room.Index("startedAtMs")])
data class PomodoroSessionEntity(
    @androidx.room.PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startedAtMs: Long,
    val endedAtMs: Long,
    val durationMinutes: Int,
    /** FOCUS or BREAK */
    val kind: String,
    val completed: Boolean = true,
)
