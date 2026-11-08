@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.atlas.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.atlas.music.data.db.TrackEntity
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.atlas.music.di.ServiceLocator
import com.atlas.music.util.ArtworkStyle
import kotlinx.coroutines.flow.map

/** Shared track list used across library tabs, smart lists, search, playlists. */
@Composable
fun TrackListContent(
    tracks: List<TrackEntity>,
    onPlay: (TrackEntity, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit,
    onRemove: ((TrackEntity) -> Unit)? = null,
    onEditTags: ((TrackEntity) -> Unit)? = null,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(bottom = 96.dp),
    modifier: Modifier = Modifier,
) {
    val compact by ServiceLocator.settings.settings
        .map { it.artworkStyle == ArtworkStyle.COMPACT }
        .collectAsState(initial = false)
    if (tracks.isEmpty()) {
        EmptyState(
            title = "Nothing here yet",
            body = "Scan your library or adjust the current filter.",
            modifier = modifier.height(240.dp),
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = contentPadding, verticalArrangement = Arrangement.Top) {
        items(tracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                onPlay = { onPlay(track, tracks.indexOf(track)) },
                onToggleFavorite = onToggleFavorite,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onAddToPlaylist = onAddToPlaylist,
                onShowInfo = onShowInfo,
                onRemove = onRemove,
                onEditTags = onEditTags,
                compact = compact,
            )
        }
    }
}
