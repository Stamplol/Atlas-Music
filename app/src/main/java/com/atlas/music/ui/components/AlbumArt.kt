package com.atlas.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.playback.MediaItemFactory
import com.atlas.music.util.CoverArtGenerator
import com.atlas.music.util.ProceduralCoverArt
import com.atlas.music.util.TrackProceduralCover

private fun artworkUri(albumId: Long) = MediaItemFactory.albumArtUri(albumId)

/** Album art with checksum-seeded procedural fallback (me-when-music covers). */
@Composable
fun AlbumArt(
    albumId: Long,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerRadius: Dp = 10.dp,
    showStrip: Boolean = false,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    val shape = RoundedCornerShape(cornerRadius)
    // Album-level seed (fallback when we don't have a track). Still uniquely per album.
    val seed = remember(albumId) { CoverArtGenerator.hashStr("album:$albumId") }
    var error by remember(albumId) { mutableStateOf(false) }

    Box(
        modifier = sized.clip(shape),
    ) {
        if (albumId != 0L && !error) {
            AsyncImage(
                model = artworkUri(albumId),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { error = true },
            )
            if (error) {
                // Transparent so we fall through to procedural below
            }
        }
        if (error || albumId == 0L) {
            ProceduralCoverArt(
                seed = seed,
                title = contentDescription ?: "—",
                ext = "mp3",
                sizeBytes = 0L,
                modifier = Modifier.fillMaxSize(),
                showStrip = showStrip,
            )
        }
    }
}

/**
 * Per-track cover — seed = file content checksum. This is the correct source
 * of uniqueness: same bytes → same art, anywhere on the device.
 */
@Composable
fun TrackCoverArt(
    track: TrackEntity,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp? = null,
    cornerRadius: Dp = 10.dp,
    showStrip: Boolean = true,
) {
    val sized = if (size != null) modifier.size(size) else modifier
    val shape = RoundedCornerShape(cornerRadius)
    var error by remember(track.uri, track.albumId) { mutableStateOf(false) }

    Box(modifier = sized.clip(shape)) {
        if (track.albumId != 0L && !error) {
            AsyncImage(
                model = artworkUri(track.albumId),
                contentDescription = contentDescription ?: track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { error = true },
            )
        }
        if (track.albumId == 0L || error) {
            TrackProceduralCover(
                track = track,
                modifier = Modifier.fillMaxSize(),
                showStrip = showStrip,
            )
        }
    }
}
