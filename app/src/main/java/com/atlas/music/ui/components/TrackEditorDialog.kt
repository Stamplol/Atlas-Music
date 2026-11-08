package com.atlas.music.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.atlas.music.data.db.TrackEntity

/**
 * Tag editor — fix "Unknown artist/album" and tidy the library.
 * Saves to the Atlas DB immediately; best-effort rewrites embedded file tags
 * when the source is a writable file (SAF-only files keep file tags unchanged).
 */
@Composable
fun TrackEditorDialog(
    track: TrackEntity,
    onDismiss: () -> Unit,
    onSave: (title: String, artist: String, album: String, genre: String, year: Int, trackNumber: Int, discNumber: Int) -> Unit,
    onReset: (() -> Unit)? = null,
) {
    var title by remember(track.id) { mutableStateOf(track.title) }
    var artist by remember(track.id) { mutableStateOf(track.artist) }
    var album by remember(track.id) { mutableStateOf(track.album) }
    var genre by remember(track.id) { mutableStateOf(track.genre) }
    var yearText by remember(track.id) { mutableStateOf(track.year.takeIf { it > 0 }?.toString() ?: "") }
    var trackText by remember(track.id) { mutableStateOf(track.trackNumber.takeIf { it > 0 }?.toString() ?: "") }
    var discText by remember(track.id) { mutableStateOf(track.discNumber.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit tags", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                EditorField(label = "Title", value = title, onValueChange = { title = it }, singleLine = true)
                EditorField(label = "Artist", value = artist, onValueChange = { artist = it }, singleLine = true)
                EditorField(label = "Album", value = album, onValueChange = { album = it }, singleLine = true)
                EditorField(label = "Genre", value = genre, onValueChange = { genre = it }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        EditorField(label = "Year", value = yearText, onValueChange = { yearText = it.filter { c -> c.isDigit() }.take(4) }, singleLine = true)
                    }
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        EditorField(label = "Track #", value = trackText, onValueChange = { trackText = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true)
                    }
                    androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f)) {
                        EditorField(label = "Disc #", value = discText, onValueChange = { discText = it.filter { c -> c.isDigit() }.take(2) }, singleLine = true)
                    }
                }
                Text(
                    "Saved to your Atlas library. When the file is writable, the embedded tags are rewritten too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (onReset != null && track.userEdited) {
                    TextButton(onClick = onReset, modifier = Modifier.padding(top = 4.dp)) { Text("Reset to file values") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        title.trim(),
                        artist.trim(),
                        album.trim(),
                        genre.trim(),
                        yearText.toIntOrNull() ?: 0,
                        trackText.toIntOrNull() ?: 0,
                        discText.toIntOrNull() ?: 1,
                    )
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun EditorField(label: String, value: String, onValueChange: (String) -> Unit, singleLine: Boolean) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = singleLine,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
