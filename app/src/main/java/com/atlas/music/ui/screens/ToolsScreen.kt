package com.atlas.music.ui.screens

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.AudioOutput
import com.atlas.music.util.EqCurves
import com.atlas.music.util.Format
import com.atlas.music.util.OutputKind
import com.atlas.music.util.isLandscape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val GOLD = Color(0xFFE5A83D)

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
    var outputs by remember { mutableStateOf(AudioOutput.listOutputs(context)) }
    var showOutputPicker by remember { mutableStateOf(false) }
    var preferredDeviceId by remember { mutableStateOf<Int?>(null) }

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
    val headphones = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }
    val outputLabel = headphones?.name ?: outputs.firstOrNull()?.name ?: "Speaker"
    val outputKind = headphones?.kind ?: outputs.firstOrNull()?.kind ?: OutputKind.SPEAKER

    if (isLandscape()) {
        ToolsLandscapeDashboard(
            state = state,
            viewModel = viewModel,
            outputLabel = outputLabel,
            onShowOutputPicker = { showOutputPicker = true },
            onOpenNowPlaying = onOpenNowPlaying,
            onOpenDownloads = onOpenDownloads,
            onExit = onExit,
        )
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("Tools", fontWeight = FontWeight.ExtraBold) },
                navigationIcon = { IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to music") } },
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, top = 2.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item { PlayerStrip(state = state, viewModel = viewModel, onOpenNowPlaying = onOpenNowPlaying, onOpenQueue = onOpenQueue) }
                item { FocusLabCard(state = state, viewModel = viewModel) }
                item { SoundCard(state = state, viewModel = viewModel, outputLabel = outputLabel, onShowOutputPicker = { showOutputPicker = true }) }
                item { SleepCard(state = state, viewModel = viewModel) }
                item { LibraryCareCard(state = state, viewModel = viewModel, onOpenDuplicates = onOpenDuplicates) }
                item { MaintenanceFooter(onOpenDownloads = onOpenDownloads, onOpenSettings = onOpenSettings) }
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

// ---------------- Portrait pieces ----------------

@Composable
private fun PlayerStrip(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val player = state.playerState
    val fraction = if (player.durationMs > 0) (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpenNowPlaying),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Box(
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    if (player.currentTrackId != null) {
                        Text(player.title.firstOrNull()?.uppercase() ?: "-", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(player.artist.ifBlank { "Tap to browse your library" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = viewModel::togglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (player.durationMs > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(label: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun FocusLabCard(state: com.atlas.music.ui.viewmodel.ToolsUiState, viewModel: ToolsViewModel) {
    var showAdjust by remember { mutableStateOf(false) }
    val totalFocusMs = state.totalMs.takeIf { it > 0 } ?: (state.settings.pomodoroFocusMinutes * 60_000L)
    val displayMs = if (state.totalMs > 0) state.remainingMs else totalFocusMs
    val fraction = if (state.totalMs > 0) (state.remainingMs.toFloat() / state.totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val stateWord = when {
        state.running -> "REMAINING"
        state.remainingMs > 0 && state.totalMs > 0 -> "PAUSED"
        else -> "READY"
    }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader(
                if (state.pomodoroMode == "BREAK") "FOCUS LAB · BREAK" else "FOCUS LAB",
                trailing = "Today ${state.focusMinutesToday}m · ${state.sessionsToday} done",
            )
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The ring — gold arc burns down over time.
                Box(modifier = Modifier.size(136.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val stroke = 11.dp.toPx()
                        val inset = stroke / 2f
                        val arcSize = size * (1f - stroke / size.width)
                        drawArc(
                            color = Color(0x14FFFFFF),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke, cap = StrokeCap.Round),
                            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                            size = arcSize,
                        )
                        if (fraction > 0f) {
                            drawArc(
                                color = GOLD,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                                size = arcSize,
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.pomodoroMode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text(formatMs(displayMs), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        Text(stateWord, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (state.streakDays > 0) "${state.streakDays}-day streak" else "Start your streak",
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(15, 25, 50).forEach { m ->
                            FilterChip(
                                selected = state.settings.pomodoroFocusMinutes == m,
                                onClick = { viewModel.setFocusMinutes(m) },
                                label = { Text("${m}m") },
                            )
                        }
                        TextButton(onClick = { showAdjust = true }) { Text("…") }
                    }
                    Spacer(Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duck music to 35%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Switch(checked = state.settings.pomodoroSyncPlayback, onCheckedChange = viewModel::setSyncPlayback)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = {
                        if (state.running) viewModel.pausePomodoro()
                        else if (state.remainingMs > 0 && state.totalMs > 0) viewModel.resumePomodoro()
                        else viewModel.startPomodoro()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.running) "Pause" else if (state.remainingMs > 0 && state.totalMs > 0) "Resume" else "Start")
                }
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = viewModel::resetPomodoro) { Text("Reset") }
            }
            state.lastEvent?.takeIf { it.startsWith("Focus") || it.startsWith("Break") }?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
            }
            // Ledger — completed sessions only, FOCUS and BREAK marked with a dot.
            val ledger = state.sessions.take(4)
            if (ledger.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("LEDGER", style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                ledger.forEach { s ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Box(modifier = Modifier.size(6.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (s.kind == "FOCUS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${if (s.kind == "FOCUS") "Focus" else "Break"} · ${s.durationMinutes} min · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(s.startedAtMs))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
    if (showAdjust) {
        BreakOptionsDialog(
            focusMinutes = state.settings.pomodoroFocusMinutes,
            breakMinutes = state.settings.pomodoroBreakMinutes,
            onDismiss = { showAdjust = false },
            onConfirm = { f, b -> viewModel.setFocusMinutes(f); viewModel.setBreakMinutes(b); showAdjust = false },
        )
    }
}

@Composable
private fun SoundCard(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    outputLabel: String,
    onShowOutputPicker: () -> Unit,
) {
    val activePreset = state.lastEvent?.removePrefix("EQ: ")?.takeIf { state.lastEvent.startsWith("EQ:") }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader("SOUND", trailing = "live this session")
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EqCurves.PRESET_ORDER.forEach { preset ->
                    EqPresetTile(
                        name = preset,
                        selected = activePreset == preset,
                        onClick = { viewModel.applyEqPreset(preset) },
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Speed", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                Text("%.1f×".format(state.playerState.playbackSpeed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
            Slider(
                value = state.playerState.playbackSpeed,
                onValueChange = { viewModel.setPlaybackSpeed(it) },
                valueRange = 0.5f..2f,
                steps = 14,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Output", style = MaterialTheme.typography.titleSmall)
                    Text(outputLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = onShowOutputPicker) { Text("Change", color = MaterialTheme.colorScheme.primary) }
            }
        }
    }
}

@Composable
private fun EqPresetTile(name: String, selected: Boolean, onClick: () -> Unit) {
    val barColor = if (selected) GOLD else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) GOLD.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Canvas(modifier = Modifier.size(width = 96.dp, height = 38.dp)) {
            val curves = EqCurves.gainsFor(name)
            val flat = curves.isEmpty()
            val n = 9
            val slot = size.width / n
            val bw = slot * 0.62f
            val mid = size.height / 2f
            if (flat) {
                // Flat: one hairline instead of inventing fake bars
                drawLine(barColor, androidx.compose.ui.geometry.Offset(0f, mid), androidx.compose.ui.geometry.Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            } else {
                curves.forEachIndexed { i, gain ->
                    val h = kotlin.math.abs(gain) / 600f * (mid - 1f)
                    val x = i * slot + (slot - bw) / 2f
                    if (gain >= 0) {
                        drawRoundRect(barColor, androidx.compose.ui.geometry.Offset(x, mid - h), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2f))
                    } else {
                        drawRoundRect(barColor, androidx.compose.ui.geometry.Offset(x, mid), androidx.compose.ui.geometry.Size(bw, h), androidx.compose.ui.geometry.CornerRadius(bw / 2f))
                    }
                }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SleepCard(state: com.atlas.music.ui.viewmodel.ToolsUiState, viewModel: ToolsViewModel) {
    val remaining = state.sleepRemainingMs
    val active = remaining != null && remaining > 0
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader("SLEEP", trailing = if (active) "active" else null)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (active) "Paused in ${formatMs(remaining)}" else "Ends playback",
                        style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                        color = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        if (state.settings.sleepTimerEndOfTrack) "Stops when the current track ends" else "Pick a duration below",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (active) TextButton(onClick = viewModel::clearSleepTimer) { Text("Cancel") }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 30, 60).forEach { m ->
                    FilterChip(selected = false, onClick = { viewModel.startSleepTimer(m) }, label = { Text("${m}m") })
                }
            }
            Spacer(Modifier.height(10.dp))
            // "End of track" as the first-class citizen it deserves to be.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (state.settings.sleepTimerEndOfTrack) 1f else 0.45f))
                    .clickable { viewModel.setSleepTimerEndOfTrack(!state.settings.sleepTimerEndOfTrack) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Stop at end of track", style = MaterialTheme.typography.bodyLarge)
                    Text("Atlas pauses when this song ends — no half-song wakeups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.settings.sleepTimerEndOfTrack, onCheckedChange = viewModel::setSleepTimerEndOfTrack)
            }
        }
    }
}

@Composable
private fun LibraryCareCard(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    onOpenDuplicates: () -> Unit,
) {
    var showFix by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            SectionHeader("LIBRARY CARE", trailing = "the part nobody checks")
            Spacer(Modifier.height(8.dp))
            Text(
                "${state.trackCount} tracks · ${Format.totalDuration(state.totalDurationMs)} · ${Format.bytes(state.totalSizeBytes)}",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            if (state.unknownArtistCount > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(GOLD.copy(alpha = 0.10f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text("${state.unknownArtistCount}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("tracks need tags", style = MaterialTheme.typography.bodyMedium)
                        Text("Unknown artist or album — one tap fixes all of them", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(onClick = { showFix = true }, shape = RoundedCornerShape(12.dp)) { Text("Fix") }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Every track carries artist and album tags.", style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onOpenDuplicates)
                    .padding(horizontal = 6.dp, vertical = 12.dp),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Find duplicates", style = MaterialTheme.typography.bodyLarge)
                    Text("Same song saved twice — flagged by size and length", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (showFix) {
        FixUnknownsDialog(onDismiss = { showFix = false }, onConfirm = { a, al -> viewModel.fixUnknowns(a, al); showFix = false })
    }
}

@Composable
private fun MaintenanceFooter(onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit) {
    Column {
        MaintenanceRow(Icons.Filled.Download, "Download manager", "YouTube links · yt-dlp updates", onOpenDownloads)
        MaintenanceRow(Icons.Filled.Settings, "Settings", "Appearance · library folders · widgets", onOpenSettings)
        Spacer(Modifier.height(2.dp))
        Text(
            "Atlas never phones home. Your library, downloads and lyrics all stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 6.dp),
        )
    }
}

@Composable
private fun MaintenanceRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, caption: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------- Landscape table ----------------

@Composable
private fun ToolsLandscapeDashboard(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    outputLabel: String,
    onShowOutputPicker: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenDownloads: () -> Unit,
    onExit: () -> Unit,
) {
    val clockTickState = remember { androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { clockTickState.longValue = System.currentTimeMillis(); kotlinx.coroutines.delay(1000) } }
    val clockTick = clockTickState.longValue
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("Tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(remember(clockTick) { Date() }), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(remember(clockTick) { Date() }), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = onOpenNowPlaying) { Text("Player") }
            TextButton(onClick = onOpenDownloads) { Text("Downloads") }
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Left: playing + sound
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.weight(1.05f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        SectionHeader("PLAYING", trailing = outputLabel)
                        Spacer(Modifier.height(4.dp))
                        Text(state.playerState.title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(state.playerState.artist.ifBlank { "Start music from Library" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(onClick = onOpenNowPlaying),
                        ) {
                            IconButton(onClick = viewModel::skipPrevious) { Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous") }
                            IconButton(onClick = viewModel::togglePlayPause) { Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp)) }
                            IconButton(onClick = viewModel::skipNext) { Icon(Icons.Filled.SkipNext, contentDescription = "Next") }
                            IconButton(onClick = viewModel::toggleShuffle) { Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = if (state.playerState.shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) }
                            IconButton(onClick = viewModel::cycleRepeatMode) {
                                Icon(
                                    when (state.playerState.repeatMode) {
                                        Player.REPEAT_MODE_ONE -> Icons.Filled.RepeatOne
                                        else -> Icons.Filled.Repeat
                                    },
                                    contentDescription = "Repeat",
                                    tint = if (state.playerState.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        Text("${Format.duration(state.playerState.positionMs)} / ${Format.duration(state.playerState.durationMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("SOUND", trailing = "%.1f×".format(state.playerState.playbackSpeed))
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            EqCurves.PRESET_ORDER.take(5).forEach { preset ->
                                FilterChip(
                                    selected = state.lastEvent == "EQ: $preset",
                                    onClick = { viewModel.applyEqPreset(preset) },
                                    label = { Text(preset) },
                                )
                            }
                        }
                        Slider(value = state.playerState.playbackSpeed, onValueChange = { viewModel.setPlaybackSpeed(it) }, valueRange = 0.5f..2f, steps = 14)
                        TextButton(onClick = onShowOutputPicker) { Text("Output: $outputLabel") }
                    }
                }
            }
            // Right: focus + library
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Card(modifier = Modifier.weight(1.6f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        val totalFocusMs = state.settings.pomodoroFocusMinutes * 60_000L
                        val displayMs = if (state.totalMs > 0) state.remainingMs else totalFocusMs
                        SectionHeader("FOCUS", trailing = "${state.streakDays}d streak · ${state.focusMinutesToday}m today")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(formatMs(displayMs), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Button(onClick = { if (state.running) viewModel.pausePomodoro() else if (state.remainingMs > 0 && state.totalMs > 0) viewModel.resumePomodoro() else viewModel.startPomodoro() }, modifier = Modifier.height(44.dp)) {
                                Icon(if (state.running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(4.dp)); Text(if (state.running) "Pause" else "Start")
                            }
                            Spacer(Modifier.width(2.dp))
                            TextButton(onClick = viewModel::resetPomodoro) { Text("Reset") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 25, 50).forEach { m -> FilterChip(selected = state.settings.pomodoroFocusMinutes == m, onClick = { viewModel.setFocusMinutes(m) }, label = { Text("${m}m") }) }
                            Spacer(Modifier.weight(1f))
                            Text("Stop at end of track", style = MaterialTheme.typography.bodySmall, modifier = Modifier.align(Alignment.CenterVertically))
                            Switch(checked = state.settings.sleepTimerEndOfTrack, onCheckedChange = viewModel::setSleepTimerEndOfTrack)
                        }
                    }
                }
                Card(modifier = Modifier.weight(1f).fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SectionHeader("LIBRARY", trailing = "${state.unknownArtistCount} unknowns")
                        Text("${state.trackCount} tracks", style = MaterialTheme.typography.headlineSmall)
                        Text("${Format.totalDuration(state.totalDurationMs)} · ${Format.bytes(state.totalSizeBytes)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

// ---------------- Shared dialogs ----------------

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
