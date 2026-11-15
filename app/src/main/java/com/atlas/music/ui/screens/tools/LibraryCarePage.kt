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
    val sizeGb = state.totalSizeBytes / 1e9
    val formats = remember(allTracks) {
        allTracks.groupingBy { it.uri.substringAfterLast('.', "unk").lowercase().take(4) }.eachCount().toList().sortedByDescending { it.second }
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
                    "${state.trackCount} tracks · ${Format.totalDuration(state.totalDurationMs)} · ${"%.1f".format(sizeGb)} GB",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                if (formats.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    StudioHeader("FORMATS", trailing = "${formats.size} types")
                    Spacer(Modifier.height(6.dp))
                    formats.forEach { (ext, count) ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                            Text(".$ext", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(52.dp), color = MaterialTheme.colorScheme.onSurface)
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
                StudioHeader("TAGS", trailing = if (state.unknownArtistCount > 0) "${state.unknownArtistCount} unknowns" else "all good")
                Spacer(Modifier.height(8.dp))
                if (state.unknownArtistCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${state.unknownArtistCount}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("tracks need artist or album", style = MaterialTheme.typography.titleSmall)
                            Text("One tap fixes every one of them as user-edited tags.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickableRow(onOpenDuplicates)
                .padding(horizontal = 6.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Find duplicates", style = MaterialTheme.typography.titleLarge)
                Text("Same song saved twice — flagged by fingerprint of title/artist/duration", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Open duplicates", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showFix) {
        com.atlas.music.ui.screens.tools.FixUnknownsDialog(onDismiss = { showFix = false }, onConfirm = { a, al -> viewModel.fixUnknowns(a, al); showFix = false })
    }
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.clickable(onClick = onClick)
