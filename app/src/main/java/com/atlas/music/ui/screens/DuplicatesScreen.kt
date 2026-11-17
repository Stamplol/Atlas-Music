package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.ui.components.AlbumArt
import com.atlas.music.ui.components.EmptyState
import com.atlas.music.ui.viewmodel.DuplicatesViewModel
import com.atlas.music.util.DuplicateDetector
import com.atlas.music.util.Format

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    viewModel: DuplicatesViewModel,
    onBack: () -> Unit,
    onRemoveFromLibrary: (com.atlas.music.data.db.TrackEntity) -> Unit,
    onKeepOnly: (com.atlas.music.data.db.TrackEntity, List<com.atlas.music.data.db.TrackEntity>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val groups by viewModel.groups.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val wasted = DuplicateDetector.wastedBytes(groups)

    Column(modifier = modifier.fillMaxWidth()) {
        TopAppBar(
            title = { Text("Duplicates", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) },
            navigationIcon = { AtlasIconButton(text = "Back", onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        if (!loading && groups.isEmpty()) {
            EmptyState(
                title = "No duplicates found",
                body = "Every track in your library looks distinct — same title, same artist, same duration only once.",
            )
            return
        }
        Text(
            "${groups.size} duplicate ${if (groups.size == 1) "group" else "groups"}  ·  up to ${Format.bytes(wasted)} reclaimable",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
            items(groups, key = { it.fingerprint }) { group ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            group.tracks.first().title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${group.tracks.first().artist}  ·  ${group.tracks.size} copies  ·  ${Format.bytes(group.totalSizeBytes)} total",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.size(8.dp))
                        group.tracks.forEach { track ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                com.atlas.music.ui.components.TrackCoverArt(track = track, contentDescription = null, size = 40.dp, cornerRadius = 6.dp, showStrip = false)
                                androidx.compose.foundation.layout.Spacer(Modifier.size(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(track.folder, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        "${Format.bytes(track.sizeBytes)}  ·  added ${java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(track.dateAddedSec * 1000))}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                OutlinedButton(onClick = { onKeepOnly(track, group.tracks) }) {
                                    Text("Keep this")
                                }
                                AtlasIconButton(text = "Remove from library", onClick = { onRemoveFromLibrary(track) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Remove from library", modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
