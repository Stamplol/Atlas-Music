package com.atlas.music.ui.screens

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import com.atlas.music.ui.components.AtlasIconButton
import com.atlas.music.ui.components.TrackCoverArt
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import androidx.compose.runtime.produceState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.compose.ui.platform.LocalContext
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.ui.screens.tools.AudioStudioPage
import com.atlas.music.ui.screens.tools.FocusPage
import com.atlas.music.ui.screens.tools.LibraryCarePage
import com.atlas.music.ui.screens.tools.SleepPage
import com.atlas.music.ui.screens.tools.ToolPage
import com.atlas.music.ui.screens.tools.ToolsHub
import com.atlas.music.util.AudioOutput
import com.atlas.music.util.OutputKind
import com.atlas.music.util.isLandscape
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

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
    initialPage: ToolPage = ToolPage.HUB,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    var page by remember(initialPage) { mutableStateOf(initialPage) }
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

    val title = when (page) {
        ToolPage.HUB -> "Tools"
        ToolPage.AUDIO -> "Sound Studio"
        ToolPage.FOCUS -> "Focus"
        ToolPage.SLEEP -> "Sleep"
        ToolPage.LIBRARY -> "Library care"
    }

    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.ExtraBold) },
            navigationIcon = {
                AtlasIconButton(
                    text = if (page == ToolPage.HUB) "Back to music" else "Back to tools",
                    onClick = { if (page == ToolPage.HUB) onExit() else page = ToolPage.HUB },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = if (page == ToolPage.HUB) "Back to music" else "Back to tools")
                }
            },
            modifier = Modifier.statusBarsPadding(),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        )
        if (page == ToolPage.HUB) {
            PlayerStrip(state = state, viewModel = viewModel, onOpenNowPlaying = onOpenNowPlaying, onOpenQueue = onOpenQueue)
            Spacer(Modifier.height(8.dp))
            ToolsHub(
                state = state,
                onOpenAudio = { page = ToolPage.AUDIO },
                onOpenFocus = { page = ToolPage.FOCUS },
                onOpenSleep = { page = ToolPage.SLEEP },
                onOpenLibrary = { page = ToolPage.LIBRARY },
                onOpenDownloads = onOpenDownloads,
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        } else {
            Box(modifier = Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) {
                when (page) {
                    ToolPage.AUDIO -> AudioStudioPage(state = state, viewModel = viewModel, outputLabel = outputLabel, onShowOutputPicker = { showOutputPicker = true })
                    ToolPage.FOCUS -> FocusPage(state = state, viewModel = viewModel)
                    ToolPage.SLEEP -> SleepPage(state = state, viewModel = viewModel)
                    ToolPage.LIBRARY -> LibraryCarePage(state = state, viewModel = viewModel, onOpenDuplicates = onOpenDuplicates)
                    ToolPage.HUB -> Unit
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
private fun PlayerStrip(
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: ToolsViewModel,
    onOpenNowPlaying: () -> Unit,
    onOpenQueue: () -> Unit,
) {
    val player = state.playerState
    val fraction = if (player.durationMs > 0) (player.positionMs.toFloat() / player.durationMs.toFloat()).coerceIn(0f, 1f) else 0f
    val currentTrack by produceState<TrackEntity?>(null, player.currentTrackId) {
        value = player.currentTrackId?.let { ServiceLocator.musicRepository.getTrack(it) }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(22.dp)).clickable(onClick = onOpenNowPlaying),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                if (currentTrack != null) {
                    TrackCoverArt(
                        track = currentTrack!!,
                        contentDescription = null,
                        size = 46.dp,
                        cornerRadius = 12.dp,
                        showStrip = false,
                    )
                } else {
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
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(player.title.ifBlank { "Nothing playing" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(player.artist.ifBlank { "Tap to browse your library" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                AtlasIconButton(text = if (state.isPlaying) "Pause" else "Play", onClick = viewModel::togglePlayPause) {
                    Icon(
                        if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                AtlasIconButton(text = "Queue", onClick = onOpenQueue) {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Queue", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (player.durationMs > 0) {
                Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(MaterialTheme.colorScheme.surfaceVariant)) {
                    Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction).clip(RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
    }
}

@Composable
private fun MaintenanceFooter(onOpenDownloads: () -> Unit, onOpenSettings: () -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 6.dp)) {
        MaintenanceRow(Icons.AutoMirrored.Filled.QueueMusic, "Download manager", "YouTube · SoundCloud · yt-dlp updates", onOpenDownloads)
        MaintenanceRow(Icons.AutoMirrored.Filled.QueueMusic, "Settings", "Appearance · library folders · widgets", onOpenSettings)
        Spacer(Modifier.height(2.dp))
        Text(
            "Atlas never phones home. Your library, downloads and lyrics stay on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
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
    }
}
