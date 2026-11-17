package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.ui.components.TrackListContent
import com.atlas.music.util.Format

/** One screen for every "list of tracks" destination: smart playlists, album, artist, genre, folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmartListScreen(
    title: String,
    subtitle: String,
    tracks: List<TrackEntity>,
    onPlay: (TrackEntity, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit,
    onEditTags: (TrackEntity) -> Unit,
    onPlayAll: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalMs = tracks.sumOf { it.durationMs }
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text(
                        title,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            navigationIcon = {
                AtlasIconButton(text = "Back", onClick = onBack) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                androidx.compose.material3.TextButton(onClick = onPlayAll, enabled = tracks.isNotEmpty()) {
                    Text("Play all", style = MaterialTheme.typography.labelLarge)
                }
            },
        )
        Text(
            "${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}  ·  ${Format.totalDuration(totalMs)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        TrackListContent(
            tracks = tracks,
            onPlay = { track, index -> onPlay(track, index) },
            onToggleFavorite = onToggleFavorite,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onAddToPlaylist = onAddToPlaylist,
            onShowInfo = onShowInfo,
        onEditTags = onEditTags,
        )
    }
}
