package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Checkbox
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.ui.components.AlbumArt
import com.atlas.music.ui.components.EmptyState
import com.atlas.music.ui.viewmodel.PlaylistDetailState
import com.atlas.music.util.Format

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: com.atlas.music.ui.viewmodel.PlaylistDetailViewModel,
    onBack: () -> Unit,
    onPlayAll: (List<com.atlas.music.data.db.TrackEntity>) -> Unit,
    onToggleFavorite: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onAddToPlaylist: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onShowInfo: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onEditTags: (com.atlas.music.data.db.TrackEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var renaming by remember { mutableStateOf(false) }
    var renameInput by remember(state.playlist?.name) { mutableStateOf(state.playlist?.name ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    val playlist = state.playlist
    val totalMs = state.tracks.sumOf { it.durationMs }

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = {
                Column {
                    Text(playlist?.name ?: "Playlist", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${state.tracks.size} tracks  ·  ${Format.totalDuration(totalMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            navigationIcon = {
                AtlasIconButton(text = "Back", onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
            },
            actions = {
                AtlasIconButton(text = "Add tracks", onClick = { showPicker = true }) { Icon(Icons.Filled.Add, contentDescription = "Add tracks") }
                AtlasIconButton(text = "Rename playlist", onClick = { renaming = true }) { Icon(Icons.Filled.Edit, contentDescription = "Rename playlist") }
                AtlasIconButton(text = "Delete playlist", onClick = { confirmDelete = true }) { Icon(Icons.Filled.Delete, contentDescription = "Delete playlist") }
            },
        )
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            androidx.compose.material3.Button(onClick = { onPlayAll(state.tracks) }, enabled = state.tracks.isNotEmpty()) {
                Text("Play all")
            }
        }
        if (state.loaded && state.tracks.isEmpty()) {
            EmptyState(
                title = "Nothing in this playlist yet",
                body = "Long-press any track and choose “Add to playlist”.",
            )
            return
        }
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)) {
            itemsIndexed(state.tracks, key = { index, track -> "$index-${track.id}" }) { index, track ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "%02d".format(index + 1),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    com.atlas.music.ui.components.TrackCoverArt(track = track, contentDescription = null, size = 44.dp, cornerRadius = 8.dp, showStrip = false)
                    androidx.compose.foundation.layout.Spacer(Modifier.size(12.dp))
                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${track.artist}  ·  ${Format.duration(track.durationMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Column {
                        AtlasIconButton(text = "{ viewModel.moveTrack(track.id, (index - 1).coerceAtLeast(0)) }, modifier = Modifier.size(32.dp)", onClick = { viewModel.moveTrack(track.id, (index - 1).coerceAtLeast(0)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "Move up", modifier = Modifier.size(16.dp))
                        }
                        AtlasIconButton(text = "{ viewModel.moveTrack(track.id, (index + 1).coerceAtMost(state.tracks.lastIndex)) }, modifier = Modifier.size(32.dp)", onClick = { viewModel.moveTrack(track.id, (index + 1).coerceAtMost(state.tracks.lastIndex)) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.ArrowDownward, contentDescription = "Move down", modifier = Modifier.size(16.dp))
                        }
                    }
                    AtlasIconButton(text = "Edit tags", onClick = { onEditTags(track) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit tags", modifier = Modifier.size(18.dp))
                    }
                    AtlasIconButton(text = "{ viewModel.removeTrack(track) }", onClick = { viewModel.removeTrack(track) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove from playlist", modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }

    if (renaming) {
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename playlist") },
            text = {
                OutlinedTextField(value = renameInput, onValueChange = { renameInput = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = { renameInput.trim().takeIf { it.isNotEmpty() }?.let(viewModel::rename); renaming = false }) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete playlist?") },
            text = { Text("“${playlist?.name}” will be deleted. The audio files stay on your device.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete(); confirmDelete = false; onBack() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )


        if (showPicker) {
            AddTracksDialog(
                onDismiss = { showPicker = false },
                existingIds = state.tracks.map { it.id }.toSet(),
                onAdd = { ids ->
                    viewModel.addTracks(ids)
                    showPicker = false
                },
            )
        }
    }
}

@Composable
private fun AddTracksDialog(
    onDismiss: () -> Unit,
    existingIds: Set<Long>,
    onAdd: (Set<Long>) -> Unit,
) {
    val library by com.atlas.music.di.ServiceLocator.musicRepository.observeAll(com.atlas.music.util.SortOption.TITLE).collectAsState(initial = emptyList())
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    val candidates = remember(library, existingIds) { library.filter { it.id !in existingIds } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add tracks to this playlist") },
        text = {
            Column(modifier = Modifier.height(420.dp).fillMaxWidth()) {
                if (candidates.isEmpty()) {
                    Text(
                        "Everything in your library is already in this playlist. Nice.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        itemsIndexed(candidates, key = { _, t -> t.id }) { _, track ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    selected = if (track.id in selected) selected - track.id else selected + track.id
                                }.padding(vertical = 4.dp),
                            ) {
                                Checkbox(checked = track.id in selected, onCheckedChange = { checked ->
                                    selected = if (checked) selected + track.id else selected - track.id
                                })
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${track.artist} · ${track.album}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(selected) }, enabled = selected.isNotEmpty()) { Text("Add ${selected.size}") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
