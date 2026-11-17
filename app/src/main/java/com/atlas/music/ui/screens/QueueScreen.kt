package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.atlas.music.ui.components.AlbumArt
import com.atlas.music.ui.viewmodel.QueueViewModel
import com.atlas.music.util.Format
import androidx.compose.ui.unit.TextUnit

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    viewModel: QueueViewModel,
    currentTrackId: Long?,
    onClose: () -> Unit,
    onPlayAt: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tracks by viewModel.tracks.collectAsState()
    var showSaveDialog by remember { mutableStateOf(false) }
    var playlistName by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.refresh() }

    androidx.compose.foundation.layout.Box(modifier = modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Queue · ${tracks.size} ${if (tracks.size == 1) "track" else "tracks"}", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            navigationIcon = {
                AtlasIconButton(text = "Close queue", onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close queue") }
            },
            actions = {
                if (tracks.isNotEmpty()) {
                    TextButton(onClick = { playlistName = "Queue · ${tracks.size} tracks"; showSaveDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Text("Save")
                    }
                    TextButton(onClick = viewModel::clear) {
                        Icon(Icons.Filled.ClearAll, contentDescription = null)
                        Text("Clear")
                    }
                }
            },
        )
        if (tracks.isEmpty()) {
            com.atlas.music.ui.components.EmptyState(
                title = "Queue is empty",
                body = "Play something and it will show up here.",
            )
        } else {
            val totalMs = tracks.sumOf { it.durationMs }
            Text(
                "Total: ${Format.totalDuration(totalMs)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
            LazyColumn(
                modifier = Modifier.navigationBarsPadding(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
            ) {
                itemsIndexed(tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                    val isCurrent = track.id == currentTrackId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlayAt(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "%02d".format(index + 1),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp),
                        )
                        com.atlas.music.ui.components.TrackCoverArt(track = track, contentDescription = null, size = 44.dp, cornerRadius = 8.dp, showStrip = false)
                        androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                track.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                "${track.artist}  ·  ${Format.duration(track.durationMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column {
                            AtlasIconButton(
                                text = "Move up",
                                onClick = { viewModel.moveItem(index, (index - 1).coerceAtLeast(0)) },
                                enabled = index > 0,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(18.dp))
                            }
                            AtlasIconButton(
                                text = "Move down",
                                onClick = { viewModel.moveItem(index, (index + 1).coerceAtMost(tracks.lastIndex)) },
                                enabled = index < tracks.lastIndex,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(18.dp))
                            }
                        }
                        AtlasIconButton(text = "Remove from queue", onClick = { viewModel.removeItem(index) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove from queue", modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    } // end of Column (inside Box)

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
    )

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = { Text("Save queue as playlist") },
            text = {
                Column {
                    Text("Every track in the current order becomes a playlist you can play, renumber, and play differently forever.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSaveDialog = false
                    scope.launch {
                        val saved = viewModel.saveAsPlaylist(playlistName)
                        snackbarHostState.showSnackbar(if (saved != null) "Saved as “$saved” playlist" else "Couldn't save the playlist — empty name or empty queue.")
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") } },
        )
    }
    } // end of Box
}
