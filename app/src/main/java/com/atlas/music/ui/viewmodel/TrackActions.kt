package com.atlas.music.ui.viewmodel

import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Shared track operations so every screen behaves identically. */
object TrackActions {
    fun play(tracks: List<TrackEntity>, startIndex: Int = 0) {
        ServiceLocator.playbackConnection.playTracks(tracks, startIndex)
    }

    suspend fun toggleFavorite(track: TrackEntity) = withContext(Dispatchers.IO) {
        ServiceLocator.musicRepository.setFavorite(track.id, !track.isFavorite)
    }

    fun playNext(track: TrackEntity) = ServiceLocator.playbackConnection.playNext(listOf(track))

    fun addToQueue(track: TrackEntity) = ServiceLocator.playbackConnection.addToQueue(listOf(track))

    suspend fun addToPlaylist(playlistId: Long, track: TrackEntity) =
        ServiceLocator.playlistRepository.addTrack(playlistId, track.id)

    suspend fun removeFromLibrary(track: TrackEntity) =
        ServiceLocator.musicRepository.deleteTrack(track.id)

    data class TagUpdateResult(val dbOk: Boolean, val fileOk: Boolean, val fileError: String? = null)

    suspend fun updateTags(
        track: TrackEntity,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        discNumber: Int,
    ): TagUpdateResult = withContext(Dispatchers.IO) {
        ServiceLocator.musicRepository.updateTags(
            track.id, title, artist, album, genre, year, trackNumber, discNumber,
        )
        // Best-effort: rewrite embedded tags when the source is a real file (not SAF-only).
        val path = track.filePath
        var fileOk = false
        var fileError: String? = null
        if (path.isNotBlank()) {
            val f = java.io.File(path)
            if (f.isFile) {
                val bridge = com.atlas.music.downloads.PythonBridge(ServiceLocator.appContext)
                if (bridge.isAvailable()) {
                    val res = bridge.writeTags(path, title, artist, album, genre, year, trackNumber, discNumber)
                    fileOk = res.optBoolean("ok", false)
                    if (!fileOk) fileError = res.optString("error", "Tag write failed")
                } else {
                    fileError = "Python runtime unavailable"
                }
            } else {
                fileError = "File not directly writable (saved to Atlas library only)"
            }
        } else {
            fileError = "File not directly writable (saved to Atlas library only)"
        }
        TagUpdateResult(dbOk = true, fileOk = fileOk, fileError = fileError)
    }

    suspend fun resetTags(track: TrackEntity) = withContext(Dispatchers.IO) {
        ServiceLocator.musicRepository.resetTagsToFile(track.id)
    }
}
