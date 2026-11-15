package com.atlas.music.ui.components

import com.atlas.music.ui.components.AtlasIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.util.Format

/**
 * The workhorse row. One per track everywhere in the app — song lists, search
 * results, playlists, downloads-import. Tap plays, long-press opens the action sheet.
 */
@androidx.compose.foundation.ExperimentalFoundationApi
@Composable
fun TrackRow(
    track: TrackEntity,
    onPlay: (TrackEntity) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit,
    onRemove: ((TrackEntity) -> Unit)? = null,
    onEditTags: ((TrackEntity) -> Unit)? = null,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(if (compact) 52.dp else 64.dp)
            .combinedClickable(
                onClick = { onPlay(track) },
                onLongClick = { menuOpen = true },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            TrackCoverArt(
                track = track,
                contentDescription = "Album artwork for ${track.album}",
                size = 46.dp,
                cornerRadius = 8.dp,
                showStrip = false,
            )
            Spacer(Modifier.width(12.dp))
        }
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = Format.artistAlbumLine(track.artist, track.album),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = Format.duration(track.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        AtlasIconButton(text = "Toggle favorite", onClick = { onToggleFavorite(track) }) {
            Icon(
                imageVector = if (track.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = if (track.isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (track.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AtlasIconButton(text = "{ menuOpen = true }", onClick = { menuOpen = true }) {
            Icon(
                imageVector = Icons.Filled.MoreVert,
                contentDescription = "Track options",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { menuOpen = false; onPlayNext(track) })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menuOpen = false; onAddToQueue(track) })
            DropdownMenuItem(text = { Text("Add to playlist…") }, onClick = { menuOpen = false; onAddToPlaylist(track) })
            if (onEditTags != null) {
                DropdownMenuItem(text = { Text("Edit tags\u2026") }, onClick = { menuOpen = false; onEditTags(track) })
            }
            DropdownMenuItem(text = { Text("Track info") }, onClick = { menuOpen = false; onShowInfo(track) })
            if (onRemove != null) {
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text("Remove from library", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onRemove(track) },
                )
            }
        }
    }
}
