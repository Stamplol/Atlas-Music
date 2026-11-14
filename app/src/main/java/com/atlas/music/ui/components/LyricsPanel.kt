package com.atlas.music.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.data.repo.LyricsRepository
import com.atlas.music.di.ServiceLocator
import com.atlas.music.util.Format
import com.atlas.music.util.LyricLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Full-height lyric panel — offline sidecar LRC or embedded USLT via mutagen.
 * Highlights the current line; raw (unsynced) lyrics render as simple text.
 */
@Composable
fun LyricsPanel(
    track: TrackEntity,
    positionMs: Long,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    // Three-state: loading → loaded(lines) / empty (null from repo)
    var loaded by remember(track.id) { mutableStateOf<List<LyricLine>?>(null) }
    var source by remember(track.id) { mutableStateOf("") }
    var done by remember(track.id) { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(track.id) {
        if (done) return@LaunchedEffect
        done = false
        val res = withContext(Dispatchers.IO) {
            ServiceLocator.lyricsRepository.lyricsFor(track)
        }
        loaded = res?.lines ?: emptyList()
        source = res?.source ?: ""
        done = true
    }

    LaunchedEffect(positionMs, loaded) {
        val list = loaded ?: return@LaunchedEffect
        if (list.size < 2 || list.all { it.timeMs == 0L }) return@LaunchedEffect
        val idx = list.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        listState.animateScrollToItem(idx.coerceAtLeast(0))
    }

    Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Close lyrics") }
            Spacer(Modifier.weight(1f))
            Text("LYRICS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {}) { Text(source.ifBlank { "unknown" }) }
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !done -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Reading lyrics…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                loaded == null || loaded!!.isEmpty() -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center, modifier = Modifier.fillMaxSize().padding(32.dp)) {
                    Text("No lyrics found", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "This track doesn't ship embedded lyrics and there's no .lrc file next to it.\nDrop a file named like the track (e.g. ${track.displayName.substringBeforeLast('.')}.lrc) into the same folder and hit rescan.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> {
                    val list = loaded!!
                    val raw = list.all { it.timeMs == 0L }
                    // Locate current line
                    val activeIdx = if (raw) -1 else list.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        itemsIndexed(list) { index, line ->
                            val isCurrent = index == activeIdx
                            Text(
                                text = line.text,
                                style = if (raw) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleLarge,
                                fontSize = if (isCurrent) 22.sp else 16.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth().clickable { }.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
