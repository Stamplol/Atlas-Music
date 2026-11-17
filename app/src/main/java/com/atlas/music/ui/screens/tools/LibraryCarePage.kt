package com.atlas.music.ui.screens.tools

import androidx.compose.ui.draw.clip

import androidx.compose.foundation.rememberScrollState

import androidx.compose.foundation.background

import androidx.compose.foundation.clickable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.atlas.music.ui.viewmodel.ToolsUiState
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.Format
import com.atlas.music.util.MediaFormatHelper
import com.atlas.music.util.SortOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LibraryCarePage(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    onOpenDuplicates: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showFix by remember { androidx.compose.runtime.mutableStateOf(false) }
    val allTracks by com.atlas.music.di.ServiceLocator.musicRepository.observeAll(SortOption.TITLE).collectAsState(initial = emptyList())
    val formats = remember(allTracks) {
        allTracks
            .groupingBy { MediaFormatHelper.displayAudioFormatLabel(it.mimeType, it.displayName) }
            .eachCount()
            .toList()
            .sortedByDescending { it.second }
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()),
    ) {
        Spacer(Modifier.height(4.dp))
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                StudioHeader("LIBRARY CARE", trailing = "everything visible")
                Spacer(Modifier.height(8.dp))
                Text(
                    "${state.trackCount} ${if (state.trackCount == 1) "track" else "tracks"} · ${Format.totalDuration(state.totalDurationMs)} · ${Format.bytes(state.totalSizeBytes)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (formats.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    StudioHeader("FORMATS", trailing = if (formats.size == 1) "1 type" else "${formats.size} types")
                    Spacer(Modifier.height(6.dp))
                    formats.forEach { (fmt, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(fmt, style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(58.dp), color = MaterialTheme.colorScheme.onSurface)
                            Text("×$count", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(48.dp))
                            androidx.compose.foundation.layout.Box(modifier = Modifier.weight(1f).height(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
                                androidx.compose.foundation.layout.Box(
                                    modifier = Modifier
                                        .fillMaxWidth(count.toFloat() / formats.maxOf { it.second }.toFloat())
                                        .height(6.dp)
                                        .clip(androidx.compose.foundation.shape.CircleShape)
                                        .background(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                StudioHeader("MISSING INFO", trailing = if (state.unknownArtistCount > 0) "${state.unknownArtistCount} missing" else "all good")
                Spacer(Modifier.height(8.dp))
                if (state.unknownArtistCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.unknownArtistCount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("songs missing artist or album", style = MaterialTheme.typography.titleSmall)
                            Text("Fill in missing tags across these songs in one step.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { showFix = true }, shape = RoundedCornerShape(12.dp)) { Text("Fix") }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(26.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Every track carries artist and album tags.", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).clickable(onClick = onOpenDuplicates),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(18.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    StudioHeader("DUPLICATES")
                    Spacer(Modifier.height(4.dp))
                    Text("Find duplicates", style = MaterialTheme.typography.titleMedium)
                    Text("Same song saved twice — flagged by title, artist, and duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = "Open duplicates", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showFix) {
        com.atlas.music.ui.screens.tools.FixUnknownsDialog(onDismiss = { showFix = false }, onConfirm = { a, al -> viewModel.fixUnknowns(a, al); showFix = false })
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
