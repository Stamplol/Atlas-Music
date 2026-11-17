package com.atlas.music.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {
    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    fun observeAll(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE")
    suspend fun getAll(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun byId(id: Long): TrackEntity?

    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun byIds(ids: List<Long>): List<TrackEntity>

    @Query("SELECT isFavorite FROM tracks WHERE id = :id")
    fun observeFavorite(id: Long): kotlinx.coroutines.flow.Flow<Boolean?>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE")
    fun observeFavorites(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE folder = :folder ORDER BY title COLLATE NOCASE")
    fun observeFolder(folder: String): Flow<List<TrackEntity>>

    @Query("SELECT DISTINCT folder FROM tracks ORDER BY folder COLLATE NOCASE")
    fun observeFolders(): Flow<List<String>>

    @Query("SELECT folder, COUNT(*) as count FROM tracks GROUP BY folder ORDER BY count DESC")
    fun observeFolderCounts(): Flow<List<FolderCount>>

    @Query("SELECT DISTINCT artist FROM tracks WHERE artist != '' ORDER BY artist COLLATE NOCASE")
    fun observeArtists(): Flow<List<String>>

    @Query("SELECT artist as name, COUNT(*) as count FROM tracks WHERE artist != '' GROUP BY artist ORDER BY artist COLLATE NOCASE")
    fun observeArtistCounts(): Flow<List<NameCount>>

    @Query("SELECT DISTINCT album FROM tracks WHERE album != '' ORDER BY album COLLATE NOCASE")
    fun observeAlbums(): Flow<List<String>>

    @Query("SELECT album as name, COUNT(*) as count FROM tracks WHERE album != '' GROUP BY album ORDER BY album COLLATE NOCASE")
    fun observeAlbumCounts(): Flow<List<NameCount>>

    @Query("SELECT DISTINCT genre FROM tracks WHERE genre != '' ORDER BY genre COLLATE NOCASE")
    fun observeGenres(): Flow<List<String>>

    @Query("SELECT genre as name, COUNT(*) as count FROM tracks WHERE genre != '' GROUP BY genre ORDER BY genre COLLATE NOCASE")
    fun observeGenreCounts(): Flow<List<NameCount>>

    @Query("SELECT * FROM tracks WHERE album = :album ORDER BY discNumber, trackNumber, title COLLATE NOCASE")
    fun observeAlbumTracks(album: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE artist = :artist ORDER BY album COLLATE NOCASE, discNumber, trackNumber")
    fun observeArtistTracks(artist: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE genre = :genre ORDER BY title COLLATE NOCASE")
    fun observeGenreTracks(genre: String): Flow<List<TrackEntity>>

    @Query(
        """SELECT tracks.* FROM tracks
           INNER JOIN play_events ON play_events.trackId = tracks.id
           GROUP BY tracks.id
           ORDER BY MAX(play_events.playedAtMs) DESC"""
    )
    fun observeRecentlyPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks ORDER BY dateAddedSec DESC, id DESC")
    fun observeRecentlyAdded(): Flow<List<TrackEntity>>

    @Query(
        """SELECT tracks.* FROM tracks
           INNER JOIN play_events ON play_events.trackId = tracks.id
           GROUP BY tracks.id
           ORDER BY COUNT(play_events.id) DESC"""
    )
    fun observeMostPlayed(): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks")
    fun observeTrackCount(): Flow<Int>

    @Query("SELECT COALESCE(SUM(durationMs), 0) FROM tracks")
    fun observeTotalDurationMs(): Flow<Long>

    @Query("SELECT COALESCE(SUM(sizeBytes), 0) FROM tracks")
    fun observeTotalSizeBytes(): Flow<Long>

    @Query("SELECT uri FROM tracks")
    suspend fun allUris(): List<String>

    @Query("SELECT fingerprint, COUNT(*) as count FROM tracks GROUP BY fingerprint HAVING count > 1")
    suspend fun duplicateFingerprints(): List<FingerprintCount>

    @Query("SELECT * FROM tracks WHERE fingerprint = :fingerprint ORDER BY dateAddedSec")
    suspend fun tracksWithFingerprint(fingerprint: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tracks: List<TrackEntity>): List<Long>

    @Update
    suspend fun update(track: TrackEntity)

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM tracks WHERE uri NOT IN (:keepUris)")
    suspend fun deleteMissing(keepUris: List<String>)

    @Query("DELETE FROM tracks")
    suspend fun clearAll()

    @Query("SELECT * FROM tracks WHERE title LIKE '%' || :q || '%' COLLATE NOCASE OR artist LIKE '%' || :q || '%' COLLATE NOCASE OR album LIKE '%' || :q || '%' COLLATE NOCASE OR genre LIKE '%' || :q || '%' COLLATE NOCASE OR folder LIKE '%' || :q || '%' COLLATE NOCASE LIMIT :limit")
    suspend fun searchTracks(q: String, limit: Int): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE artist = 'Unknown artist' OR album = 'Unknown album'")
    suspend fun unknownCount(): Int

    @Query("SELECT * FROM tracks WHERE uri = :uri")
    suspend fun byUri(uri: String): TrackEntity?
}

data class FolderCount(val folder: String, val count: Int)
data class FingerprintCount(val fingerprint: String, val count: Int)
data class NameCount(val name: String, val count: Int)

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun byId(id: Long): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observeById(id: Long): Flow<PlaylistEntity?>

    @Insert
    suspend fun insert(playlist: PlaylistEntity): Long

    @Update
    suspend fun update(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT trackId FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY position")
    fun observePlaylistTrackIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT COUNT(*) FROM playlist_tracks WHERE playlistId = :playlistId")
    fun observePlaylistSize(playlistId: Long): Flow<Int>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun nextPosition(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTrack(entry: PlaylistTrackEntity): Long

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun removeTrack(playlistId: Long, trackId: Long)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId")
    suspend fun clearPlaylist(playlistId: Long)

    @Query("DELETE FROM playlist_tracks WHERE trackId = :trackId")
    suspend fun removeTrackEverywhere(trackId: Long)

    @Query("UPDATE playlist_tracks SET position = :position WHERE playlistId = :playlistId AND trackId = :trackId")
    suspend fun setPosition(playlistId: Long, trackId: Long, position: Int)
}

@Dao
interface PlayEventDao {
    @Insert
    suspend fun insert(event: PlayEventEntity)

    @Query("DELETE FROM play_events")
    suspend fun clearAll()

    @Query("DELETE FROM play_events WHERE trackId = :trackId")
    suspend fun deleteForTrack(trackId: Long)

    @Query("SELECT COUNT(*) FROM play_events")
    fun observeCount(): Flow<Int>
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE status IN ('QUEUED','FETCHING_INFO','DOWNLOADING') ORDER BY createdAtMs ASC LIMIT 1")
    suspend fun nextActive(): DownloadTaskEntity?

    @Query("SELECT * FROM download_tasks WHERE id = :id")
    suspend fun byId(id: Long): DownloadTaskEntity?

    @Insert
    suspend fun insert(task: DownloadTaskEntity): Long

    @Update
    suspend fun update(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status, error = :error, completedAtMs = :completedAt WHERE id = :id")
    suspend fun setStatus(id: Long, status: DownloadStatus, error: String?, completedAt: Long?)

    @Query("UPDATE download_tasks SET status = 'FAILED', error = :message, completedAtMs = :now WHERE status IN ('FETCHING_INFO', 'DOWNLOADING')")
    suspend fun markInterruptedFailed(message: String, now: Long): Int

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE status = 'QUEUED')")
    suspend fun hasQueued(): Boolean

    @Query("UPDATE download_tasks SET progress = :progress, bytesDownloaded = :downloaded, bytesTotal = :total, speedBytesPerSec = :speed, etaSeconds = :eta WHERE id = :id")
    suspend fun setProgress(id: Long, progress: Float, downloaded: Long, total: Long, speed: Long, eta: Long)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM download_tasks WHERE status = 'COMPLETED'")
    suspend fun clearCompleted()

    @Query("DELETE FROM download_tasks")
    suspend fun clearHistory()

    @Query("SELECT COUNT(*) FROM download_tasks WHERE status = 'COMPLETED'")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE videoId = :videoId AND status != 'FAILED' AND status != 'CANCELLED')")
    suspend fun hasActiveOrDone(videoId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM download_tasks WHERE outputPath = :path)")
    suspend fun hasOutputPath(path: String): Boolean
}

@Dao
interface PomodoroDao {
    @Insert
    suspend fun insert(session: PomodoroSessionEntity)

    @Query("SELECT * FROM pomodoro_sessions ORDER BY startedAtMs DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<PomodoroSessionEntity>>

    @Query("SELECT COALESCE(SUM(durationMinutes), 0) FROM pomodoro_sessions WHERE kind = 'FOCUS' AND completed = 1 AND startedAtMs >= :sinceMs")
    suspend fun focusMinutesSince(sinceMs: Long): Int

    @Query("SELECT * FROM pomodoro_sessions WHERE kind = 'FOCUS' AND completed = 1 ORDER BY startedAtMs DESC")
    fun observeFocusDone(): kotlinx.coroutines.flow.Flow<List<PomodoroSessionEntity>>

    @Query("DELETE FROM pomodoro_sessions WHERE startedAtMs < :beforeMs")
    suspend fun clearBefore(beforeMs: Long)
}
