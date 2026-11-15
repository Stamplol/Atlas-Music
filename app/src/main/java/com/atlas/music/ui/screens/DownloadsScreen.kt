package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.atlas.music.data.db.DownloadStatus
import com.atlas.music.data.db.DownloadTaskEntity
import com.atlas.music.ui.viewmodel.DownloadsUiState
import com.atlas.music.util.DownloadStateMachine
import com.atlas.music.util.Format
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    state: DownloadsUiState,
    onUrlChange: (String) -> Unit,
    onFetchPreview: () -> Unit,
    onSetFormat: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onStartDownload: () -> Unit,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearCompleted: () -> Unit,
    onClearHistory: () -> Unit,
    onCheckUpdate: () -> Unit,
    onApplyUpdate: () -> Unit,
    onRestoreBundled: () -> Unit,
    onSetPlaylistMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
    ) {
        item {
            TopAppBar(title = { Text("Downloads", fontWeight = FontWeight.ExtraBold) }, modifier = Modifier.statusBarsPadding(), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        }

        if (!state.ytdlpAvailable) {
            item {
                ErrorBanner("The downloader engine couldn't start on this device. Everything else in Atlas Music still works offline.")
            }
            return@LazyColumn
        }

        item { PasteCard(context = context, state = state, onUrlChange = onUrlChange, onFetchPreview = onFetchPreview) }

        state.previewError?.let { error -> item { ErrorBanner(error) } }
        state.enqueueMessage?.let { message ->
            item {
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }

        state.preview?.let { preview ->
            item {
                PreviewCard(
                    preview = preview,
                    state = state,
                    onSetFormat = onSetFormat,
                    onConsentChange = onConsentChange,
                    onStartDownload = onStartDownload,
                    onSetPlaylistMode = onSetPlaylistMode,
                )
            }
        }

        val active = state.tasks.filter { DownloadStateMachine.isActive(it.status) }
        val finished = state.tasks.filter { DownloadStateMachine.isTerminal(it.status) }

        if (active.isNotEmpty()) {
            item { QueueHeader("In progress", "${active.size} ${if (active.size == 1) "item" else "items"}") }
            items(active, key = { "active-${it.id}" }) { task ->
                DownloadTaskRow(task, onCancel = onCancel, onRetry = onRetry, onDelete = onDelete)
            }
        }

        if (finished.isNotEmpty()) {
            item {
                QueueHeader(
                    "History",
                    "${finished.size} ${if (finished.size == 1) "item" else "items"}",
                    trailing = {
                        Row {
                            TextButton(onClick = onClearCompleted) { Text("Clear completed") }
                        }
                    },
                )
            }
            items(finished.take(50), key = { "done-${it.id}" }) { task ->
                DownloadTaskRow(task, onCancel = onCancel, onRetry = onRetry, onDelete = onDelete)
            }
            item {
                TextButton(onClick = onClearHistory, modifier = Modifier.padding(horizontal = 12.dp)) {
                    Text("Clear all history", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (state.tasks.isEmpty()) {
            item {
                com.atlas.music.ui.components.EmptyState(
                    title = "No downloads yet",
                    body = "Paste a link to a video, track or playlist you're authorized to save, and it will land in your library when it's done.",
                )
            }
        }

        item { YtdlpCard(state, onCheckUpdate, onApplyUpdate, onRestoreBundled) }
        item { LegalNote() }
    }
}

@Composable
private fun PasteCard(
    context: Context,
    state: DownloadsUiState,
    onUrlChange: (String) -> Unit,
    onFetchPreview: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Paste a YouTube or SoundCloud link", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Video, Short, playlist or SoundCloud link…") },
                singleLine = true,
                trailingIcon = {
                    AtlasIconButton(text = "Paste from clipboard", onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.let(onUrlChange)
                    }) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = "Paste from clipboard")
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onFetchPreview,
                enabled = state.url.isNotBlank() && !state.previewLoading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.previewLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.width(10.dp))
                    Text("Reading link…")
                } else {
                    Text("Analyze link")
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: com.atlas.music.downloads.YoutubePreview,
    state: DownloadsUiState,
    onSetFormat: (String) -> Unit,
    onConsentChange: (Boolean) -> Unit,
    onStartDownload: () -> Unit,
    onSetPlaylistMode: (Boolean) -> Unit,
) {
    val wholePlaylist = preview.playlistOnly || (preview.isPlaylist && state.playlistMode)
    val blockedSoundCloud = preview.provider == "soundcloud" && !preview.playlistOnly && !com.atlas.music.util.SoundCloudGate.licenseAllowsDownload(preview.license)
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                AsyncImage(
                    model = preview.thumbnailUrl.takeIf { it.isNotBlank() },
                    contentDescription = "Video thumbnail",
                    modifier = Modifier.size(width = 148.dp, height = 84.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentScale = ContentScale.Crop,
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(preview.title, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(preview.uploader, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        buildString {
                            append(preview.provider.uppercase())
                            if (preview.playlistOnly) {
                                append("  ·  Set  ·  ${preview.playlistCount ?: preview.playlistEntries.size} tracks")
                            } else {
                                append("  ·  ${Format.duration(preview.durationSec * 1000L)}")
                                preview.playlistCount?.let { append("  ·  playlist · $it videos") }
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            if (preview.playlistEntries.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (preview.playlistOnly) "Entire playlist" else "Entire playlist",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            "${preview.playlistEntries.size} track${if (preview.playlistEntries.size == 1) "" else "s"} in \"${preview.playlistTitle.ifBlank { if (preview.provider == "soundcloud") "set" else "playlist" }}\"" +
                                if (preview.provider == "soundcloud") "  ·  each checked at download" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Switch(
                        checked = wholePlaylist,
                        onCheckedChange = onSetPlaylistMode,
                        enabled = !preview.playlistOnly,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Column(modifier = Modifier.padding(start = 4.dp)) {
                    preview.playlistEntries.take(5).forEachIndexed { index, entry ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text("${index + 1}.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(22.dp))
                            Text(entry.title, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (entry.durationSec > 0) {
                                Text(Format.duration(entry.durationSec * 1000L), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    if (preview.playlistEntries.size > 5) {
                        Text("… and ${preview.playlistEntries.size - 5} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 22.dp, top = 2.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Text("Audio format", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormatChip("opus", "Opus · best quality (WebM)", state.format, onSetFormat)
                FormatChip("m4a", "M4A · AAC (iTunes-compatible)", state.format, onSetFormat)
                FormatChip("best", "Original", state.format, onSetFormat)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "Formats come straight from the source — nothing is converted without an encoder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (preview.provider == "soundcloud" && !preview.playlistOnly) {
                Spacer(Modifier.height(2.dp))
                val allowed = com.atlas.music.util.SoundCloudGate.licenseAllowsDownload(preview.license)
                Text(
                    if (allowed) "Licensed for reuse (${com.atlas.music.util.SoundCloudGate.licenseLabel(preview.license)})" else "All rights reserved — not licensed for download",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(8.dp))
            if (blockedSoundCloud) {
                // Hard gate — no consent checkbox can override this.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.error.copy(alpha = 0.10f))
                        .padding(14.dp),
                ) {
                    Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Atlas only downloads SoundCloud tracks the artist licensed for reuse (Creative Commons) or explicitly enabled. This one isn't.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                    Text("Not licensed for download")
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = state.consentAccepted, onCheckedChange = onConsentChange)
                    Text(
                        "I own this content or have permission to download it.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Button(onClick = onStartDownload, enabled = state.consentAccepted, modifier = Modifier.fillMaxWidth()) {
                    Text(if (wholePlaylist) "Download all ${preview.playlistEntries.size} tracks" else "Download audio")
                }
            }
        }
    }
}

@Composable
private fun FormatChip(value: String, label: String, selected: String, onSelect: (String) -> Unit) {
    FilterChip(
        selected = selected == value,
        onClick = { onSelect(value) },
        label = { Text(label) },
    )
}

@Composable
private fun QueueHeader(title: String, subtitle: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            trailing?.invoke()
        }
    }
}

@Composable
private fun DownloadTaskRow(
    task: DownloadTaskEntity,
    onCancel: (Long) -> Unit,
    onRetry: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(task.status)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when (task.status) {
                        DownloadStatus.QUEUED -> "Queued"
                        DownloadStatus.FETCHING_INFO -> "Reading link…"
                        DownloadStatus.DOWNLOADING -> "${Format.speed(task.speedBytesPerSec)}  ·  ${Format.eta(task.etaSeconds)}"
                        DownloadStatus.COMPLETED -> "Saved to your library"
                        DownloadStatus.FAILED -> task.error ?: "Failed"
                        DownloadStatus.CANCELLED -> task.error ?: "Cancelled"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (task.status == DownloadStatus.FAILED) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            when (task.status) {
                DownloadStatus.QUEUED, DownloadStatus.FETCHING_INFO, DownloadStatus.DOWNLOADING ->
                    AtlasIconButton(text = "Cancel download", onClick = { onCancel(task.id) }) { Icon(Icons.Filled.Cancel, contentDescription = "Cancel download") }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED ->
                    AtlasIconButton(text = "Retry download", onClick = { onRetry(task.id) }) { Icon(Icons.Filled.Replay, contentDescription = "Retry download") }
                DownloadStatus.COMPLETED -> Unit
            }
            AtlasIconButton(text = "Remove from history", onClick = { onDelete(task.id) }) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove from history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (task.status == DownloadStatus.DOWNLOADING) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { task.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
private fun StatusIcon(status: DownloadStatus) {
    val tint = when (status) {
        DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
        DownloadStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val icon = when (status) {
        DownloadStatus.COMPLETED -> Icons.Filled.CheckCircle
        DownloadStatus.FAILED -> Icons.Filled.Error
        else -> Icons.Filled.Refresh
    }
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
}

@Composable
private fun YtdlpCard(
    state: DownloadsUiState,
    onCheckUpdate: () -> Unit,
    onApplyUpdate: () -> Unit,
    onRestoreBundled: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("yt-dlp engine", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Installed version: ${state.ytdlpVersion}  ·  bundled with the app, updated only from official PyPI releases with SHA-256 verification.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.updateMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
            state.updateProgressMessage?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCheckUpdate, enabled = !state.checkingUpdate) {
                    if (state.checkingUpdate) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Check for updates")
                }
                Button(onClick = onApplyUpdate, enabled = !state.updating) {
                    if (state.updating) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    else Icon(Icons.Filled.Update, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (state.updating) "Updating…" else "Update now")
                }
            }
            TextButton(onClick = onRestoreBundled) { Text("Restore bundled yt-dlp") }
        }
    }
}

@Composable
private fun LegalNote() {
    Text(
        "Atlas Music downloads only what you're authorized to save. Don't use it to rip content you don't own, to bypass paywalls, or to strip DRM. SoundCloud: Creative Commons-licensed tracks only — everything else is refused. The files you download stay on your device.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun ErrorBanner(message: String) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(10.dp))
        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}
