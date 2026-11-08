package com.atlas.music.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.util.Format
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Bottom sheet inspector — real metadata, honestly labeled. No private file paths shown. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackInfoSheet(
    track: TrackEntity,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onEditTags: ((TrackEntity) -> Unit)? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleLarge)
            Text(track.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (onEditTags != null) {
                androidx.compose.material3.TextButton(onClick = { onEditTags(track) }, modifier = Modifier.padding(top = 8.dp)) { Text("Edit tags\u2026") }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            InfoRow("Album", track.album)
            InfoRow("Genre", track.genre)
            InfoRow("Year", track.year.takeIf { it > 0 }?.toString() ?: "Unknown")
            InfoRow("Track", listOfNotNull(track.discNumber.takeIf { it > 1 }?.let { "Disc $it" }, track.trackNumber.takeIf { it > 0 }?.let { "Track $it" }).joinToString(" · ").ifBlank { "—" })
            InfoRow("Duration", Format.duration(track.durationMs))
            InfoRow("Format", track.mimeType.ifBlank { "audio" }.uppercase().removePrefix("AUDIO/"))
            InfoRow("Size", Format.bytes(track.sizeBytes))
            InfoRow("Folder", track.folder)
            InfoRow("Added", SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(track.dateAddedSec * 1000)))
            InfoRow("Favorite", if (track.isFavorite) "Yes" else "No")
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
            Text(
                "Stored on this device only. Atlas Music never uploads your music or history.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.38f),
        )
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.62f))
    }
}
