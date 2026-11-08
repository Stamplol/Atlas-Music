package com.atlas.music.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.atlas.music.data.db.TrackEntity

/** Single source of truth for building playable queue items from library tracks. */
object MediaItemFactory {
    const val EXTRA_TRACK_ID = "atlas_track_id"
    const val EXTRA_FAVORITE = "atlas_favorite"
    const val EXTRA_MIME = "atlas_mime"
    const val EXTRA_SIZE = "atlas_size"
    const val EXTRA_GENRE = "atlas_genre"
    const val EXTRA_YEAR = "atlas_year"
    const val EXTRA_FOLDER = "atlas_folder"
    const val EXTRA_DURATION = "atlas_duration"

    fun albumArtUri(albumId: Long): Uri =
        Uri.parse("content://media/external/audio/albumart/$albumId")

    fun fromTrack(track: TrackEntity): MediaItem {
        val extras = Bundle().apply {
            putLong(EXTRA_TRACK_ID, track.id)
            putBoolean(EXTRA_FAVORITE, track.isFavorite)
            putString(EXTRA_MIME, track.mimeType)
            putLong(EXTRA_SIZE, track.sizeBytes)
            putString(EXTRA_GENRE, track.genre)
            putInt(EXTRA_YEAR, track.year)
            putString(EXTRA_FOLDER, track.folder)
            putLong(EXTRA_DURATION, track.durationMs)
        }
        return MediaItem.Builder()
            .setUri(track.uri)
            .setMediaId(track.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setAlbumTitle(track.album)
                    .setArtworkUri(albumArtUri(track.albumId))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    fun fromTracks(tracks: List<TrackEntity>): List<MediaItem> = tracks.map(::fromTrack)
}
