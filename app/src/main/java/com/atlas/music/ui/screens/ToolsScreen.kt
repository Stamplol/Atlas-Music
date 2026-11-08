package com.atlas.music.ui.screens

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.ui.platform.LocalContext
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.AudioOutput
import com.atlas.music.util.Format
import com.atlas.music.util.OutputKind
import com.atlas.music.util.isLandscape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private enum class ToolsSection(val label: String) {
    TIMER("Timer"),
    SOUND("Sound"),
    LIBRARY("Library"),
    FILES("Files"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    viewModel: ToolsViewModel,
    onExit: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenDuplicates: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var section by remember { mutableStateOf(ToolsSection.TIMER) }
    var outputs by remember { mutableStateOf(AudioOutput.listOutputs(context)) }
    var showOutputPicker by remember { mutableStateOf(false) }
    var preferredDeviceId by remember { mutableStateOf<Int?>(null) }
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>?) {
                outputs = AudioOutput.listOutputs(context)
            }
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>?) {
                outputs = AudioOutput.listOutputs(context)
            }
        }
        am.registerAudioDeviceCallback(callback, null)
        try { kotlinx.coroutines.awaitCancellation() } finally { am.unregisterAudioDeviceCallback(callback) }
    }
    val outputLabel = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }?.name
        ?: outputs.firstOrNull()?.name ?: "Speaker"
    val outputKind = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }?.kind
        ?: outputs.firstOrNull()?.kind ?: OutputKind.SPEAKER

    if (isLandscape()) {
        ToolsLandscapeDashboard(
            state = state,
            viewModel = viewModel,
            outputLabel = outputLabel,
            outputKind = outputKind,
            onShowOutputPicker = { showOutputPicker = true },
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenDownloads = onOpenDownloads,
            onExit = onExit,
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Tools", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back to music") } },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            Text(
                "Productivity, integrated with playback — timers, sound, library, files.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
            )
            // Integrated player header (always visible in Tools)
            PlayerHeaderCard(state = state, viewModel = viewModel, onOpenNowPlaying = onOpenNowPlaying, onOpenQueue = onOpenQueue, outputLabel = outputLabel, outputKind = outputKind, onShowOutputPicker = { showOutputPicker = true })
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ToolsSection.entries.forEach { s ->
                    FilterChip(selected = section == s, onClick = { section = s }, label = { Text(s.label) })
                }
            }
            Spacer(Modifier.height(4.dp))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (section) {
                    ToolsSection.TIMER -> TimerSection(state = state, viewModel = viewModel)
                    ToolsSection.SOUND -> SoundSection(state = state, viewModel = viewModel, outputLabel = outputLabel, outputKind = outputKind, onShowOutputPicker = { showOutputPicker = true })
                    ToolsSection.LIBRARY -> LibrarySection(state = state, viewModel = viewModel, onOpenDuplicates = onOpenDuplicates)
                    ToolsSection.FILES -> FilesSection(state = state, onOpenDownloads = onOpenDownloads, onOpenSettings = onOpenSettings)
                }
            }
        }
    }

    if (showOutputPicker) {
        OutputPickerSheet(
            outputs = outputs,
            preferredDeviceId = preferredDeviceId,
            onSelect = { device -> preferredDeviceId = device?.id; viewModel.setPreferredOutput(device?.id); showOutputPicker = false },
            onDismiss = { showOutputPicker = false },
        )
    }
}

@Composable
private fun PlayerHeaderCard(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
    outputLabel: String,
    outputKind: OutputKind,
    onShowOutputPicker: () -> Unit,
) {
    val player = state.playerState
    val uiScope = androidx.compose.runtime.rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    if (player.currentTrackId != null) {
                        Text(player.title.firstOrNull()?.uppercase() ?: "-", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onBackground)
                    Text(player.artist.ifBlank { "Tap play from your library" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (player.durationMs > 0) {
                        Slider(value = player.positionMs.toFloat(), onValueChange = {}, enabled = false, valueRange = 0f..player.durationMs.toFloat(), modifier = Modifier.fillMaxWidth().height(28.dp))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = viewModel::toggleShuffle) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (player.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                IconButton(onClick = viewModel::skipPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
                IconButton(onClick = viewModel::togglePlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (state.isPlaying) "Pause" else "Play", tint = MaterialTheme.colorScheme.primary) }
                IconButton(onClick = viewModel::skipNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
                IconButton(onClick = viewModel::cycleRepeatMode) {
                    Icon(when (player.repeatMode) { Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne; Player.REPEAT_MODE_ALL -> Icons.Filled.Repeat; else -> Icons.Filled.Repeat }, contentDescription = "Repeat", tint = if (player.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { uiScope.launch { viewModel.toggleFavoriteCurrent() } }) { Icon(if (false) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, contentDescription = "Favorite") }
                IconButton(onClick = onOpenQueue) { Icon(Icons.Filled.QueueMusic, contentDescription = "Queue") }
                TextButton(onClick = onShowOutputPicker) { Text(outputLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                TextButton(onClick = onOpenNowPlaying) { Text("Open player") }
            }
        }
    }
}

@Composable
private fun TimerSection(state: com.atlas.music.ui.viewmodel.ToolsUiState, viewModel: ToolsViewModel) {
    var showBreakOptions by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(8.dp))
                    Text("Focus timer", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.weight(1f))
                    Text("Today ${state.focusMinutesToday}m · ${state.sessionsToday} done · ${state.streakDays}d streak", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(15, 25, 50).forEach { m -> FilterChip(selected = state.settings.pomodoroFocusMinutes == m, onClick = { viewModel.setFocusMinutes(m) }, label = { Text("$m min") }) }
                    TextButton(onClick = { showBreakOptions = true }) { Text("Adjust…") }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = state.pomodoroMode == "FOCUS", onClick = { viewModel.switchMode("FOCUS") }, label = { Text("Focus") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = state.pomodoroMode == "BREAK", onClick = { viewModel.switchMode("BREAK") }, label = { Text("Break") })
                    Spacer(Modifier.weight(1f))
                    Text(formatMs(state.remainingMs.takeIf { it > 0 } ?: (if (state.pomodoroMode == "BREAK") state.settings.pomodoroBreakMinutes * 60_000L else state.settings.pomodoroFocusMinutes * 60_000L)), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                }
                if (state.totalMs > 0) { Spacer(Modifier.height(6.dp)); Slider(value = (state.remainingMs.toFloat() / state.totalMs.toFloat()).coerceIn(0f, 1f), onValueChange = {}, enabled = false) }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { if (state.running) viewModel.pausePomodoro() else if (state.remainingMs > 0 && state.totalMs > 0) viewModel.resumePomodoro() else viewModel.startPomodoro() }) {
                        Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(6.dp)); Text(if (state.running) "Pause" else "Start")
                    }
                    TextButton(onClick = viewModel::resetPomodoro) { Text("Reset") }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Duck music while focusing (35% vol)", modifier = Modifier.weight(1f)); Switch(checked = state.settings.pomodoroSyncPlayback, onCheckedChange = viewModel::setSyncPlayback) }
                if (state.lastEvent != null) Text(state.lastEvent!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                if (state.sessions.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp)); Text("SESSION LEDGER", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    state.sessions.take(5).forEach { s -> Text("${if (s.kind == "FOCUS") "Focus" else "Break"} · ${s.durationMinutes} min · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.startedAtMs))}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
                val rem = state.sleepRemainingMs
                Spacer(Modifier.height(4.dp))
                Text(if (rem != null && rem > 0) "Paused in ${formatMs(rem)}" else "No timer running", color = if (rem != null && rem > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 30, 60).forEach { m -> FilterChip(selected = false, onClick = { viewModel.startSleepTimer(m) }, label = { Text("$m min") }) }
                    if (rem != null && rem > 0) TextButton(onClick = viewModel::clearSleepTimer) { Text("Cancel") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showBreakOptions) {
        BreakOptionsDialog(focusMinutes = state.settings.pomodoroFocusMinutes, breakMinutes = state.settings.pomodoroBreakMinutes, onDismiss = { showBreakOptions = false }, onConfirm = { f, b -> viewModel.setFocusMinutes(f); viewModel.setBreakMinutes(b); showBreakOptions = false })
    }
}

@Composable
private fun SoundSection(state: com.atlas.music.ui.viewmodel.ToolsUiState, viewModel: ToolsViewModel, outputLabel: String, outputKind: OutputKind, onShowOutputPicker: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("EQ presets", style = MaterialTheme.typography.titleLarge)
                Text("Applies instantly to the playback session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Flat", "Bass", "Vocal").forEach { preset -> FilterChip(selected = false, onClick = { viewModel.applyEqPreset(preset) }, label = { Text(preset) }) } }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) { listOf("Treble", "Night", "Mid").forEach { preset -> FilterChip(selected = false, onClick = { viewModel.applyEqPreset(preset) }, label = { Text(preset) }) } }
                if (state.lastEvent?.startsWith("EQ:") == true) Text(state.lastEvent!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(8.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Playback speed", style = MaterialTheme.typography.titleLarge)
                Text("Now ${"%.1f".format(state.playerState.playbackSpeed)}× — drag, it applies immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(value = state.playerState.playbackSpeed, onValueChange = { viewModel.setPlaybackSpeed(it) }, valueRange = 0.5f..2f, steps = 14)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Audio output", modifier = Modifier.weight(1f))
                    TextButton(onClick = onShowOutputPicker) { Text(outputLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LibrarySection(state: com.atlas.music.ui.viewmodel.ToolsUiState, viewModel: ToolsViewModel, onOpenDuplicates: () -> Unit) {
    var showFix by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Library stats", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(6.dp))
                Text("${state.trackCount} tracks · ${Format.totalDuration(state.totalDurationMs)} · ${Format.bytes(state.totalSizeBytes)}", style = MaterialTheme.typography.bodyLarge)
                Text("${state.unknownArtistCount} tracks need artist/album tags", style = MaterialTheme.typography.bodyMedium, color = if (state.unknownArtistCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { showFix = true }) { Text("Fix unknowns") }
            }
        }
        Spacer(Modifier.height(8.dp))
        ListItem(headlineContent = { Text("Find duplicates") }, supportingContent = { Text("Same song saved twice, flagging wasted space") }, trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }, modifier = Modifier.clickable(onClick = onOpenDuplicates), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        Spacer(Modifier.height(24.dp))
    }
    if (showFix) {
        FixUnknownsDialog(onDismiss = { showFix = false }, onConfirm = { a, al -> viewModel.fixUnknowns(a, al); showFix = false })
    }
}

@Composable
private fun FilesSection(state: com.atlas.music.ui.viewmodel.ToolsUiState, onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        ListItem(headlineContent = { Text("Download manager") }, supportingContent = { Text("YouTube downloads, yt-dlp updates, history") }, trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }, modifier = Modifier.clickable(onClick = onOpenDownloads), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        ListItem(headlineContent = { Text("Album artwork style") }, supportingContent = { Text("Settings → Appearance") }, trailingContent = { Icon(Icons.Filled.ChevronRight, contentDescription = null) }, modifier = Modifier.clickable(onClick = onOpenSettings), colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToolsLandscapeDashboard(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    outputLabel: String,
    outputKind: OutputKind,
    onShowOutputPicker: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenDownloads: () -> Unit,
    onExit: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onExit) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back") }
                Text("Atlas Tools — table", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date()), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onOpenNowPlaying) { Text("Player") }
                TextButton(onClick = onOpenDownloads) { Text("Downloads") }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Left column: Now playing + Pomodoro
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.playerState.title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(state.playerState.artist.ifBlank { "Start music from Library" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = viewModel::skipPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
                                IconButton(onClick = viewModel::togglePlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = viewModel::skipNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
                                IconButton(onClick = viewModel::toggleShuffle) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                IconButton(onClick = viewModel::cycleRepeatMode) { Icon(Icons.Filled.Repeat, contentDescription = "Repeat", tint = if (state.playerState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                            Text("${Format.duration(state.playerState.positionMs)} / ${Format.duration(state.playerState.durationMs)}  ·  ${outputLabel}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(state.pomodoroMode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(formatMs(state.remainingMs.takeIf { it > 0 } ?: (if (state.pomodoroMode == "BREAK") state.settings.pomodoroBreakMinutes * 60_000L else state.settings.pomodoroFocusMinutes * 60_000L)), style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                            Text("Today ${state.focusMinutesToday}m · ${state.sessionsToday} done · ${state.streakDays}d streak", style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { if (state.running) viewModel.pausePomodoro() else if (state.remainingMs > 0 && state.totalMs > 0) viewModel.resumePomodoro() else viewModel.startPomodoro() }) { Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null); Spacer(Modifier.width(4.dp)); Text(if (state.running) "Pause" else "Start") }
                                TextButton(onClick = viewModel::resetPomodoro) { Text("Reset") }
                            }
                        }
                    }
                }
                // Right column: Sleep + Sound + Stats
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("SLEEP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text(state.sleepRemainingMs?.let { "Paused in ${formatMs(it)}" } ?: "No timer", style = MaterialTheme.typography.headlineSmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                                listOf(10, 30, 60).forEach { m -> FilterChip(selected = false, onClick = { viewModel.startSleepTimer(m) }, label = { Text("$m") }) }
                                TextButton(onClick = viewModel::clearSleepTimer) { Text("Cancel") }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text("SPEED ${"%.1f".format(state.playerState.playbackSpeed)}×", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
                            Slider(value = state.playerState.playbackSpeed, onValueChange = { viewModel.setPlaybackSpeed(it) }, valueRange = 0.5f..2f, steps = 14)
                        }
                    }
                    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("SOUND", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("Flat", "Bass", "Vocal", "Night").forEach { preset -> FilterChip(selected = false, onClick = { viewModel.applyEqPreset(preset) }, label = { Text(preset) }) }
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(outputLabel, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = onShowOutputPicker) { Text("Change output") }
                            if (state.lastEvent?.startsWith("EQ:") == true) Text(state.lastEvent!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("LIBRARY", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${state.trackCount} tracks", style = MaterialTheme.typography.headlineSmall)
                            Text("${Format.totalDuration(state.totalDurationMs)} · ${Format.bytes(state.totalSizeBytes)}", style = MaterialTheme.typography.bodyMedium)
                            Text("${state.unknownArtistCount} unknowns", style = MaterialTheme.typography.bodyMedium, color = if (state.unknownArtistCount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = onOpenDownloads) { Text("Downloads") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FixUnknownsDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var artist by remember { mutableStateOf("") }
    var album by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fix unknowns") },
        text = {
            Column {
                Text("Applies to every track still marked Unknown. Saved as user-edited and survive rescans.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { Text("Artist name") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                OutlinedTextField(value = album, onValueChange = { album = it }, label = { Text("Album name") }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(artist, album) }, enabled = artist.isNotBlank() || album.isNotBlank()) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun BreakOptionsDialog(focusMinutes: Int, breakMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var focusState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(focusMinutes.toFloat()) }
    var breakState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(breakMinutes.toFloat()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Durations") },
        text = {
            Column {
                Text("Focus: ${focusState.toInt()} min")
                Slider(value = focusState, onValueChange = { focusState = it }, valueRange = 5f..180f, steps = 0)
                Text("Break: ${breakState.toInt()} min")
                Slider(value = breakState, onValueChange = { breakState = it }, valueRange = 1f..60f, steps = 0)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(focusState.toInt(), breakState.toInt()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}
