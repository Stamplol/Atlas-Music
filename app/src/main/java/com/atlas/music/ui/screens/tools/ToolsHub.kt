package com.atlas.music.ui.screens.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.QueuePlayNext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.util.EqCurves

/** Which page of the tools hub is open. */
enum class ToolPage { HUB, AUDIO, FOCUS, SLEEP, LIBRARY }

@Composable
fun ToolsHub(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    onOpenAudio: () -> Unit,
    onOpenFocus: () -> Unit,
    onOpenSleep: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val wide = com.atlas.music.util.isLandscape()
    val gridSpec = GridCells.Fixed(if (wide) 3 else 2)
    LazyVerticalGrid(
        columns = gridSpec,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxCurrentLineSpan) }) {
            AudioStudioTile(state = state, onClick = onOpenAudio)
        }
        item { FocusTile(state = state, onClick = onOpenFocus) }
        item { SleepTile(state = state, onClick = onOpenSleep) }
        item { LibraryGearTile(state = state, onClick = onOpenLibrary) }
        item { DownloadsTile(onClick = onOpenDownloads) }
        item(span = { GridItemSpan(maxCurrentLineSpan) }) {
            SettingsGearTile(onClick = onOpenSettings)
        }
    }
}

@Composable
private fun AudioStudioTile(state: com.atlas.music.ui.viewmodel.ToolsUiState, onClick: () -> Unit) {
    val activePreset = state.lastEvent?.removePrefix("EQ: ")?.takeIf { state.lastEvent.startsWith("EQ:") } ?: "Flat"
    val bands = if (state.equalizerBands.size == 9) state.equalizerBands else EqCurves.gainsFor(activePreset) + List(9 - EqCurves.gainsFor(activePreset).size) { 0 }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(18.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("AUDIO STUDIO", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(activePreset, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "EQ · Bass · Loudness · Stage",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Live EQ sparkbars, the tile's signature
            Canvas(modifier = Modifier.size(width = 108.dp, height = 56.dp)) {
                val flat = bands.all { it == 0 }
                val n = 9
                val slot = size.width / n
                val bw = slot * 0.6f
                val mid = size.height / 2f
                if (flat) {
                    drawLine(GOLD.copy(alpha = 0.6f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
                } else {
                    bands.forEachIndexed { i, gain ->
                        val h = kotlin.math.abs(gain) / EqCurves.BAND_MAX_MB.toFloat() * (mid - 1f)
                        val x = i * slot + (slot - bw) / 2f
                        val top = if (gain >= 0) mid - h else mid
                        drawRoundRect(GOLD, Offset(x, top), androidx.compose.ui.geometry.Size(bw, h.coerceAtLeast(2f)), CornerRadius(bw / 2f))
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusTile(state: com.atlas.music.ui.viewmodel.ToolsUiState, onClick: () -> Unit) {
    val total = state.settings.pomodoroFocusMinutes
    val display = if (state.totalMs > 0) state.remainingMs else total * 60_000L
    val fraction = if (state.totalMs > 0) (state.remainingMs.toFloat() / state.totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("FOCUS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            FocusRing(
                sizeDp = 96.dp,
                fraction = fraction,
                mode = state.pomodoroMode,
                displayMs = display,
                stateWord = if (state.running) "REMAINING" else if (state.remainingMs > 0 && state.totalMs > 0) "PAUSED" else "READY",
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Today ${state.focusMinutesToday}m · ${state.streakDays}d streak",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SleepTile(state: com.atlas.music.ui.viewmodel.ToolsUiState, onClick: () -> Unit) {
    val remaining = state.sleepRemainingMs
    val active = remaining != null && remaining > 0
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("SLEEP", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Icon(Icons.Filled.Bedtime, contentDescription = null, tint = if (active) GOLD else MaterialTheme.colorScheme.outline, modifier = Modifier.size(34.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                if (active) "Paused in ${formatMs(remaining!!)}" else "No timer",
                style = MaterialTheme.typography.titleMedium,
                color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(if (state.settings.sleepTimerEndOfTrack) "Stops at end of track" else "Pick a duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun LibraryGearTile(state: com.atlas.music.ui.viewmodel.ToolsUiState, onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("LIBRARY", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Icon(Icons.Filled.LibraryMusic, contentDescription = null, tint = GOLD, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
            Text("${state.trackCount} tracks", style = MaterialTheme.typography.titleMedium)
            Text(
                if (state.unknownArtistCount > 0) "${state.unknownArtistCount} need tags" else "All tracks tagged",
                style = MaterialTheme.typography.bodySmall,
                color = if (state.unknownArtistCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadsTile(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("DOWNLOADS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Icon(Icons.Filled.QueuePlayNext, contentDescription = null, tint = GOLD, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(12.dp))
            Text("YouTube · SoundCloud", style = MaterialTheme.typography.titleMedium)
            Text("Queue an authorized link", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsGearTile(onClick: () -> Unit) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Filled.Settings, contentDescription = null, tint = GOLD, modifier = Modifier.size(30.dp))
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("SETTINGS", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text("Appearance · library folders · widgets · sort", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
