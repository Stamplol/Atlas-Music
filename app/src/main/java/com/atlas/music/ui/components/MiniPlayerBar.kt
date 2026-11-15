package com.atlas.music.ui.components

import com.atlas.music.ui.components.AtlasIconButton
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.music.util.CoverArtGenerator
import com.atlas.music.util.ProceduralCoverArt
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.atlas.music.playback.PlayerUiState

/** Docked above the bottom bar. Always shows real playback state — never a placeholder. */
@Composable
fun MiniPlayerBar(
    state: PlayerUiState,
    onOpenNowPlaying: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasQueue) return
    val progress = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs.toFloat() else 0f
    androidx.compose.foundation.layout.Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onOpenNowPlaying)
                .padding(horizontal = 12.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            val artError = remember(state.artworkUri) { androidx.compose.runtime.mutableStateOf(false) }
            val seed = remember(state.title, state.artist, state.artworkUri) {
                CoverArtGenerator.hashStr(state.title + "|" + state.artist + "|" + (state.artworkUri?.toString() ?: ""))
            }
            if (state.artworkUri != null && !artError.value) {
                AsyncImage(
                    model = state.artworkUri,
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                    onError = { artError.value = true },
                )
            } else {
                ProceduralCoverArt(
                    seed = seed,
                    title = state.title,
                    ext = "mp3",
                    sizeBytes = 0L,
                    modifier = Modifier.size(44.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                    showStrip = false,
                )
            }
            Spacer(Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.title.ifBlank { "Unknown title" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = state.artist.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            AtlasIconButton(text = "OnTogglePlayPause", onClick = onTogglePlayPause) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
            AtlasIconButton(text = "Next track", onClick = onSkipNext) {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Next track",
                )
            }
        }
        // Progress filament — gold, rounded cap, real position.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(3.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
