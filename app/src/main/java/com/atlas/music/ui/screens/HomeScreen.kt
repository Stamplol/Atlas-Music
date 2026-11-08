@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.atlas.music.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.music.R
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.ui.components.AlbumArt
import com.atlas.music.ui.components.EmptyState
import com.atlas.music.ui.components.SectionHeader
import com.atlas.music.ui.components.TrackRow
import com.atlas.music.ui.viewmodel.HomeUiState
import com.atlas.music.util.Format

@Composable
fun HomeScreen(
    state: HomeUiState,
    onRescan: () -> Unit,
    onOpenTrack: (TrackEntity, List<TrackEntity>, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit,
    onEditTags: (TrackEntity) -> Unit,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = androidx.compose.runtime.remember { SnackbarHostState() }
    var entered by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val entranceAlpha by animateFloatAsState(targetValue = if (entered) 1f else 0f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "home-entrance")
    LaunchedEffect(state.lastScanMessage, state.error) {
        val message = state.error ?: state.lastScanMessage
        if (message != null) snackbarHostState.showSnackbar(message)
    }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
        // Ambient aurora — faint gold light bleeding from the top, like a heading glow.
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0f),
                        ),
                    ),
                ),
        ) {}
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp, top = 8.dp),
        ) {
            item { HomeHeader(state = state, onRescan = onRescan) }

            if (state.trackCount == 0 && !state.scanning) {
                item {
                    EmptyState(
                        title = "Your library is quiet",
                        body = "Grant access to your music, then scan. Atlas Music plays what you already own — nothing leaves your device.",
                        actionLabel = "Scan library",
                        onAction = onRescan,
                    )
                }
                return@LazyColumn
            }

            state.recentlyPlayed.firstOrNull()?.let { resume ->
                item { androidx.compose.foundation.layout.Box(modifier = Modifier.graphicsLayer { alpha = entranceAlpha; translationY = (1f - entranceAlpha) * 18f }) { ResumeHero(track = resume, onPlay = { onOpenTrack(resume, listOf(resume), 0) }) } }
            }

            if (state.recentlyPlayed.isNotEmpty()) {
                item {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.graphicsLayer { alpha = entranceAlpha; translationY = (1f - entranceAlpha) * 24f }) {
                    SectionHeader("Jump back in", actionLabel = "See all", onAction = onOpenLibrary)
                    HorizontalTrackRail(
                        tracks = state.recentlyPlayed.take(12),
                        onPlay = { track -> onOpenTrack(track, state.recentlyPlayed, state.recentlyPlayed.indexOf(track)) },
                    )
                    }
                }
            }

            if (state.recentlyAdded.isNotEmpty()) {
                item { SectionHeader("Recently added") }
                items(state.recentlyAdded.take(5), key = { "recent-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { onOpenTrack(track, state.recentlyAdded, state.recentlyAdded.indexOf(it)) },
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShowInfo = onShowInfo,
                        onEditTags = onEditTags,
                    )
                }
            }

            if (state.favorites.isNotEmpty()) {
                item { SectionHeader("Favorites", actionLabel = "See all", onAction = onOpenLibrary) }
                items(state.favorites.take(4), key = { "fav-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { onOpenTrack(track, state.favorites, state.favorites.indexOf(it)) },
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShowInfo = onShowInfo,
                        onEditTags = onEditTags,
                    )
                }
            }

            item { LibraryStats(state) }
        }
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun HomeHeader(state: HomeUiState, onRescan: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Atlas Music", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Text("AD-LESS MUSIC.", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onRescan) {
            if (state.scanning) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Rescan library")
            }
        }
    }
}

@Composable
private fun ResumeHero(track: TrackEntity, onPlay: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(vertical = 6.dp)
            .shadow(elevation = 18.dp, shape = RoundedCornerShape(22.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                com.atlas.music.ui.components.TrackCoverArt(
                    track = track,
                    contentDescription = null,
                    size = 92.dp,
                    cornerRadius = 16.dp,
                    showStrip = false,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "RESUME",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Resume playback", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp))
            }
        }
    }
}

@Composable
private fun HorizontalTrackRail(tracks: List<TrackEntity>, onPlay: (TrackEntity) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tracks.forEach { track ->
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (pressed) 0.94f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium,
                ),
                label = "rail-press",
            )
            Column(
                modifier = Modifier
                    .width(132.dp)
                    .scale(scale)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(
                        interactionSource = interactionSource,
                        indication = androidx.compose.material3.ripple(bounded = true, radius = 90.dp),
                    ) { onPlay(track) },
            ) {
                com.atlas.music.ui.components.TrackCoverArt(
                    track = track,
                    contentDescription = null,
                    modifier = Modifier
                        .size(132.dp)
                        .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp)),
                    cornerRadius = 16.dp,
                    showStrip = false,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    track.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontFamily = com.atlas.music.ui.theme.SpaceGrotesk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    track.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun LibraryStats(state: HomeUiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.weight(1f)) { StatPill("${state.trackCount}", "tracks") }
        Box(modifier = Modifier.weight(1f)) { StatPill(Format.totalDuration(state.totalDurationMs), "of music") }
    }
}

@Composable
private fun StatPill(value: String, label: String) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, fontFamily = com.atlas.music.ui.theme.JetBrainsMono)
            Text(label.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

