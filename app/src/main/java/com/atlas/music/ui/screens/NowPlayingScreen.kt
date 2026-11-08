package com.atlas.music.ui.screens

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.TextButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import com.atlas.music.R
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.playback.PlayerUiState
import com.atlas.music.ui.theme.AtlasDark
import com.atlas.music.util.AudioOutput
import com.atlas.music.util.isLandscape
import com.atlas.music.util.Format
import com.atlas.music.util.OutputKind
import androidx.media3.common.Player

private val ProgressTrackAlpha = 0.32f

/** The signature screen. Orbit arc behind the art + reactive output routing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    track: TrackEntity?,
    isFavorite: Boolean,
    sleepTimerEndAt: Long?,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onToggleFavorite: () -> Unit,
    onStartSleepTimer: (Int) -> Unit,
    onClearSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenInfo: () -> Unit,
    onSetSpeed: (Float) -> Unit,
    onSetOutputDevice: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var outputs by remember { mutableStateOf(AudioOutput.listOutputs(context)) }
    var showOutputPicker by remember { mutableStateOf(false) }
    var preferredDeviceId by remember { mutableStateOf<Int?>(null) }

    // React to headphones / DAC / Bluetooth plug and unplug in real time.
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
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            am.unregisterAudioDeviceCallback(callback)
        }
    }
    val outputLabel = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }?.name
        ?: outputs.firstOrNull()?.name
        ?: "Speaker"
    val outputKind = outputs.firstOrNull { it.kind != OutputKind.SPEAKER && it.kind != OutputKind.EARPIECE }?.kind
        ?: outputs.firstOrNull()?.kind
        ?: OutputKind.SPEAKER

    var seekPreviewMs by remember(state.currentTrackId) { mutableStateOf<Long?>(null) }
    val effectivePosition = seekPreviewMs ?: state.positionMs
    val duration = state.durationMs
    // Tracks with no metadata report 0:00 — keep the slider at 0, not full.
    val fraction = if (duration <= 0) 0f else (effectivePosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)

    // Signature motion: the art settles with a spring whenever the track changes.
    val artScale by animateFloatAsState(
        targetValue = if (state.isPlaying) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "artwork-settle",
    )
    val landscape = isLandscape()
    val toolsVm: com.atlas.music.ui.viewmodel.ToolsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val toolsState by toolsVm.state.collectAsState()
    var showToolsPanel by remember { mutableStateOf(false) }
    val hasActiveTool = toolsState.running || sleepTimerEndAt != null
    LaunchedEffect(hasActiveTool) { if (hasActiveTool) showToolsPanel = true }
    // Immersive: hide Android status + nav bars while landscape player is up (swipe to peek).
    DisposableEffect(landscape) {
        val activity = context as? android.app.Activity
        val controller = activity?.let { WindowInsetsControllerCompat(it.window, it.window.decorView) }
        if (landscape && controller != null) {
            controller.hide(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars())
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Background engine (bg.js port): animated dunes flowing behind everything.
        // The blur stays as a quiet veil so the art still feels lit by itself.
        val bgSettings by com.atlas.music.di.ServiceLocator.settings.settings.collectAsState(initial = com.atlas.music.di.AtlasSettings())
        com.atlas.music.ui.components.AnimatedBackground(
            mode = com.atlas.music.ui.components.backgroundModeFromId(bgSettings.backgroundMode),
            density = bgSettings.backgroundDensity,
            speed = bgSettings.backgroundSpeed,
            trail = bgSettings.backgroundTrail,
            react = bgSettings.backgroundReact,
            sortAlgo = bgSettings.sortAlgo,
            isPlaying = state.isPlaying,
            accent = MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxSize(),
        )
        // Blurred artwork veil — the art is the lighting, the sim is the texture.
        AsyncImage(
            model = state.artworkUri,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(90.dp),
            contentScale = ContentScale.Crop,
            alpha = 0.38f,
        )
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0x330B0D12),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.62f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
        )

        if (landscape) {
            Row(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                // LEFT: Active tool (if any) or Art + Meridian
                if (showToolsPanel) {
                    Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(horizontal = 8.dp, vertical = 10.dp)) {
                        ToolsActivePanel(
                            toolsState = toolsState,
                            sleepEndAt = sleepTimerEndAt,
                            onStartPomodoro = { toolsVm.startPomodoro() },
                            onPausePomodoro = { toolsVm.pausePomodoro() },
                            onResumePomodoro = { toolsVm.resumePomodoro() },
                            onResetPomodoro = { toolsVm.resetPomodoro() },
                            onApplyEq = { toolsVm.applyEqPreset(it) },
                            onStartSleep = onStartSleepTimer,
                            onClearSleep = onClearSleepTimer,
                            onShowOutputPicker = { showOutputPicker = true },
                            onClosePanel = { showToolsPanel = false },
                        )
                    }
                } else Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        val artSize = minOf(maxWidth, maxHeight) * 0.82f
                        MeridianArcLandscape(fraction = fraction, accent = MaterialTheme.colorScheme.primary, orbit = Color.White.copy(alpha = 0.10f), modifier = Modifier.fillMaxSize())
                        Box(
                            modifier = Modifier
                                .size(artSize)
                                .shadow(elevation = 28.dp, shape = RoundedCornerShape(24.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                                .scale(artScale)
                                .clip(RoundedCornerShape(24.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (track != null) {
                                com.atlas.music.ui.components.TrackCoverArt(
                                    track = track,
                                    contentDescription = "Album artwork for ${state.album}",
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadius = 24.dp,
                                    showStrip = true,
                                )
                            } else {
                                Icon(
                                    imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                    modifier = Modifier.fillMaxSize(0.48f),
                                )
                                AsyncImage(
                                    model = state.artworkUri,
                                    contentDescription = "Album artwork for ${state.album}",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                )
                            }
                        }
                    }
                }
                // RIGHT: controls stack (vertically centered, thumb-reachable)
                Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
                    Row(modifier = Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close now playing", tint = Color.White)
                        }
                        Spacer(Modifier.weight(1f))
                        Text("NOW PLAYING", style = MaterialTheme.typography.titleSmall, color = Color.White.copy(alpha = 0.65f))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onOpenInfo) {
                            Icon(Icons.Filled.GraphicEq, contentDescription = "Track information", tint = Color.White)
                        }
                    }
                    val albumEyebrow = state.album.takeIf { it.isNotBlank() && it != "Unknown album" }
                    albumEyebrow?.let {
                        Text(it.uppercase(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        state.title.ifBlank { "Unknown title" },
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull(
                            state.artist.ifBlank { "Unknown artist" },
                            track?.genre?.takeIf { it.isNotBlank() && it != "Unknown genre" },
                            track?.year?.takeIf { it > 0 }?.toString(),
                        ).joinToString("   ·   "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Slider(
                        value = effectivePosition.toFloat(),
                        onValueChange = { seekPreviewMs = it.toLong() },
                        onValueChangeFinished = { seekPreviewMs?.let(onSeekTo); seekPreviewMs = null },
                        enabled = duration > 0,
                        valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                        colors = androidx.compose.material3.SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                        ),
                    )
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(Format.duration(effectivePosition), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), fontFamily = com.atlas.music.ui.theme.JetBrainsMono)
                        Spacer(Modifier.weight(1f))
                        Text(Format.duration(state.durationMs), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), fontFamily = com.atlas.music.ui.theme.JetBrainsMono)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        IconButton(onClick = onToggleShuffle) {
                            Icon(Icons.Filled.Shuffle, contentDescription = "Toggle shuffle", tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f))
                        }
                        IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        Box(
                            modifier = Modifier.size(72.dp).shadow(elevation = 16.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)).clip(CircleShape).background(MaterialTheme.colorScheme.primary).clickable(onClick = onTogglePlayPause),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(36.dp),
                            )
                        }
                        IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                        IconButton(onClick = onCycleRepeatMode) {
                            when (state.repeatMode) {
                                Player.REPEAT_MODE_ONE -> Icon(Icons.Filled.RepeatOne, contentDescription = "Repeat one track", tint = MaterialTheme.colorScheme.primary)
                                Player.REPEAT_MODE_ALL -> Icon(Icons.Filled.Repeat, contentDescription = "Repeat queue", tint = MaterialTheme.colorScheme.primary)
                                else -> Icon(Icons.Filled.Repeat, contentDescription = "Repeat off", tint = Color.White.copy(alpha = 0.65f))
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = onToggleFavorite) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                                tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                            )
                        }
                        SleepTimerControl(endAt = sleepTimerEndAt, defaultMinutes = 30, onStart = onStartSleepTimer, onClear = onClearSleepTimer)
                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.Filled.QueueMusic, contentDescription = "Open queue", tint = Color.White)
                        }
                    }
                    // Output row compact for landscape
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = outputIcon(outputKind),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).size(30.dp).padding(6.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White.copy(alpha = 0.08f)).clickable { showOutputPicker = true }.padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(outputLabel, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (state.playbackSpeed != 1f) {
                            Spacer(Modifier.width(8.dp))
                            Text("%.1fx".format(state.playbackSpeed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            // Floating SVG tools toggle — bottom right (landscape only), plain icon
            Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 20.dp)) {
                IconButton(onClick = { showToolsPanel = !showToolsPanel }) {
                    Icon(
                        imageVector = ImageVector.vectorResource(R.drawable.ic_tools_toggle),
                        contentDescription = "Toggle tools",
                        tint = if (showToolsPanel || hasActiveTool) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (hasActiveTool && !showToolsPanel) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(MaterialTheme.colorScheme.primary).align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp),
                    ) {}
                }
            }
        } else LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 10.dp),
        ) {
            item {
                Row(modifier = Modifier.statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = "Close now playing", tint = Color.White)
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "NOW PLAYING",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.65f),
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onOpenInfo) {
                        Icon(Icons.Filled.GraphicEq, contentDescription = "Track information", tint = Color.White)
                    }
                }
            }

            item {
                Spacer(Modifier.height(2.dp))
                // ---- The Meridian: tilted orbit behind the art, gold dot rides progress ----
                Box(
                    modifier = Modifier.fillMaxWidth().height(310.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    MeridianArc(
                        fraction = fraction,
                        modifier = Modifier.fillMaxSize(),
                        accent = MaterialTheme.colorScheme.primary,
                        orbit = Color.White.copy(alpha = 0.10f),
                    )
                    Box(
                        modifier = Modifier
                            .size(272.dp)
                            .shadow(elevation = 32.dp, shape = RoundedCornerShape(28.dp), spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                            .scale(artScale)
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (track != null) {
                            com.atlas.music.ui.components.TrackCoverArt(
                                track = track,
                                contentDescription = "Album artwork for ${state.album}",
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 28.dp,
                                showStrip = true,
                            )
                        } else {
                            Icon(
                                imageVector = ImageVector.vectorResource(R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxSize(0.48f),
                            )
                            AsyncImage(
                                model = state.artworkUri,
                                contentDescription = "Album artwork for ${state.album}",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            item {
                val albumEyebrow = state.album.takeIf { it.isNotBlank() && it != "Unknown album" }
                albumEyebrow?.let {
                    Text(
                        it.uppercase(),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    state.title.ifBlank { "Unknown title" },
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    listOfNotNull(
                        state.artist.ifBlank { "Unknown artist" },
                        track?.genre?.takeIf { it.isNotBlank() && it != "Unknown genre" },
                        track?.year?.takeIf { it > 0 }?.toString(),
                    ).joinToString("   ·   "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            item {
                Spacer(Modifier.height(10.dp))
                Slider(
                    value = effectivePosition.toFloat(),
                    onValueChange = { seekPreviewMs = it.toLong() },
                    onValueChangeFinished = {
                        seekPreviewMs?.let(onSeekTo)
                        seekPreviewMs = null
                    },
                    enabled = duration > 0,
                    valueRange = 0f..duration.coerceAtLeast(1).toFloat(),
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = Color.White.copy(alpha = 0.32f),
                    ),
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        Format.duration(effectivePosition),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = com.atlas.music.ui.theme.JetBrainsMono,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        Format.duration(state.durationMs),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.7f),
                        fontFamily = com.atlas.music.ui.theme.JetBrainsMono,
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    IconButton(onClick = onToggleShuffle) {
                        Icon(
                            Icons.Filled.Shuffle,
                            contentDescription = "Toggle shuffle",
                            tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.65f),
                        )
                    }
                    IconButton(onClick = onSkipPrevious, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipPrevious, contentDescription = "Previous track", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(76.dp)
                            .shadow(elevation = 16.dp, shape = CircleShape, spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable(onClick = onTogglePlayPause),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    IconButton(onClick = onSkipNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Next track", tint = Color.White, modifier = Modifier.size(32.dp))
                    }
                    IconButton(onClick = onCycleRepeatMode) {
                        when (state.repeatMode) {
                            Player.REPEAT_MODE_ONE ->
                                Icon(Icons.Filled.RepeatOne, contentDescription = "Repeat one track", tint = MaterialTheme.colorScheme.primary)
                            Player.REPEAT_MODE_ALL ->
                                Icon(Icons.Filled.Repeat, contentDescription = "Repeat queue", tint = MaterialTheme.colorScheme.primary)
                            else ->
                                Icon(Icons.Filled.Repeat, contentDescription = "Repeat off", tint = Color.White.copy(alpha = 0.65f))
                        }
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                            tint = if (isFavorite) MaterialTheme.colorScheme.primary else Color.White,
                        )
                    }
                    SleepTimerControl(
                        endAt = sleepTimerEndAt,
                        defaultMinutes = 30,
                        onStart = onStartSleepTimer,
                        onClear = onClearSleepTimer,
                    )
                    IconButton(onClick = onOpenQueue) {
                        Icon(Icons.Filled.QueueMusic, contentDescription = "Open queue", tint = Color.White)
                    }
                }
            }

            item {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = outputIcon(outputKind),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .size(30.dp)
                            .padding(6.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { showOutputPicker = true }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            outputLabel,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.9f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    if (state.playbackSpeed != 1f) {
                        Text(
                            "%.1f×".format(state.playbackSpeed),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable { onSetSpeed(1f) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(48.dp)) }
        }

        if (showOutputPicker) {
            OutputPickerSheet(
                outputs = outputs,
                preferredDeviceId = preferredDeviceId,
                onSelect = { device ->
                    preferredDeviceId = device?.id
                    onSetOutputDevice(device?.id)
                    showOutputPicker = false
                },
                onDismiss = { showOutputPicker = false },
            )
        }
    }
}

@Composable
private fun MeridianArc(
    fraction: Float,
    accent: Color,
    orbit: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.52f
        val ry = size.width * 0.24f
        val tilt = -10f

        // Draw in a tilted, origin-centered space.
        drawContext.canvas.save()
        drawContext.canvas.translate(cx, cy)
        drawContext.canvas.rotate(tilt)
        val topLeft = androidx.compose.ui.geometry.Offset(-rx, -ry)
        val arcSize = androidx.compose.ui.geometry.Size(rx * 2, ry * 2)
        drawOval(
            color = orbit,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f), 0f),
            ),
        )
        // Progress arc — gold, the one loud thing.
        val sweep = 360f * fraction
        if (sweep > 0.5f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 2.5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            // Gold dot at the leading edge of the arc.
            val angleRad = Math.toRadians((-90f + sweep).toDouble())
            drawCircle(
                color = accent,
                radius = 4.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    kotlin.math.cos(angleRad).toFloat() * rx,
                    kotlin.math.sin(angleRad).toFloat() * ry,
                ),
            )
        }
        drawContext.canvas.restore()
    }
}

@Composable
private fun MeridianArcLandscape(
    fraction: Float,
    accent: Color,
    orbit: Color,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val rx = size.width * 0.46f
        val ry = size.height * 0.32f
        val tilt = -24f
        drawContext.canvas.save()
        drawContext.canvas.translate(cx, cy)
        drawContext.canvas.rotate(tilt)
        val topLeft = androidx.compose.ui.geometry.Offset(-rx, -ry)
        val arcSize = androidx.compose.ui.geometry.Size(rx * 2, ry * 2)
        drawOval(
            color = orbit,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 18f), 0f)),
        )
        val sweep = 360f * fraction
        if (sweep > 0.5f) {
            drawArc(
                color = accent,
                startAngle = -90f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
            )
            val angleRad = Math.toRadians((-90f + sweep).toDouble())
            drawCircle(
                color = accent,
                radius = 6.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(
                    kotlin.math.cos(angleRad).toFloat() * rx,
                    kotlin.math.sin(angleRad).toFloat() * ry,
                ),
            )
        }
        drawContext.canvas.restore()
    }
}

@Composable
internal fun outputIcon(kind: OutputKind): ImageVector = when (kind) {
    OutputKind.SPEAKER -> Icons.Filled.Speaker
    OutputKind.WIRED -> Icons.Filled.Headphones
    OutputKind.BLUETOOTH -> Icons.Filled.Bluetooth
    OutputKind.USB_DAC -> Icons.Filled.Usb
    OutputKind.HDMI -> Icons.Filled.Cast
    else -> Icons.Filled.Speaker
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OutputPickerSheet(
    outputs: List<com.atlas.music.util.OutputDevice>,
    preferredDeviceId: Int?,
    onSelect: (com.atlas.music.util.OutputDevice?) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
            Text("Audio output", style = MaterialTheme.typography.titleLarge)
            Text(
                "Pick a route — DAC, Bluetooth or your built-in speaker. Atlas Music keeps the choice until you change it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .clickable { onSelect(null) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Speaker, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(12.dp))
                Text("System default", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                if (preferredDeviceId == null) {
                    Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                }
            }
            outputs.forEach { output ->
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (preferredDeviceId == output.id) MaterialTheme.colorScheme.secondaryContainer else Color.White.copy(alpha = 0.04f))
                        .clickable { onSelect(output) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(outputIcon(output.kind), contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(output.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (preferredDeviceId == output.id) {
                        Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            if (outputs.isEmpty()) {
                Text(
                    "No removable outputs detected. Plug in a USB DAC or pair Bluetooth and they'll appear here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SleepTimerControl(
    endAt: Long?,
    defaultMinutes: Int,
    onStart: (Int) -> Unit,
    onClear: () -> Unit,
) {
    if (endAt != null) {
        val remainingMs = (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        val minutes = remainingMs / 60_000 + 1
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                .clickable(onClick = onClear)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("$minutes min — tap to cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    } else {
        IconButton(onClick = { onStart(defaultMinutes) }) {
            Icon(Icons.Filled.Timer, contentDescription = "Start sleep timer ($defaultMinutes min)", tint = Color.White)
        }
    }
}


/** Left-pane tools when a tool is active (landscape player). Shared ViewModel with Tools tab. */
@Composable
private fun ToolsActivePanel(
    toolsState: com.atlas.music.ui.viewmodel.ToolsUiState,
    sleepEndAt: Long?,
    onStartPomodoro: () -> Unit,
    onPausePomodoro: () -> Unit,
    onResumePomodoro: () -> Unit,
    onResetPomodoro: () -> Unit,
    onApplyEq: (String) -> Unit,
    onStartSleep: (Int) -> Unit,
    onClearSleep: () -> Unit,
    onShowOutputPicker: () -> Unit,
    onClosePanel: () -> Unit,
) {
    androidx.compose.foundation.layout.Column(modifier = Modifier.fillMaxSize().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 16.dp)) {
        androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("TOOLS", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            TextButton(onClick = onClosePanel) { Text("Cover") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Focus — ${toolsState.pomodoroMode}", style = MaterialTheme.typography.titleMedium)
        Text(
            formatMs(toolsState.remainingMs.takeIf { it > 0 } ?: (if (toolsState.pomodoroMode == "BREAK") toolsState.settings.pomodoroBreakMinutes * 60_000L else toolsState.settings.pomodoroFocusMinutes * 60_000L)),
            style = MaterialTheme.typography.displaySmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (toolsState.running) {
                TextButton(onClick = onPausePomodoro) { Text("Pause") }
            } else if (toolsState.remainingMs > 0 && toolsState.totalMs > 0) {
                TextButton(onClick = onResumePomodoro) { Text("Resume") }
            } else {
                TextButton(onClick = onStartPomodoro) { Text("Start") }
            }
            TextButton(onClick = onResetPomodoro) { Text("Reset") }
        }
        Text("Today ${toolsState.focusMinutesToday}m · ${toolsState.sessionsToday} done", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.65f))
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.1f))
        Text("Sleep", style = MaterialTheme.typography.titleMedium)
        val sleepRem = sleepEndAt?.let { (it - System.currentTimeMillis()).coerceAtLeast(0L) }
        Text(if (sleepRem != null && sleepRem > 0) "Paused in ${formatMs(sleepRem)}" else "No timer", color = MaterialTheme.colorScheme.primary)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(10, 30, 60).forEach { m -> androidx.compose.material3.FilterChip(selected = false, onClick = { onStartSleep(m) }, label = { Text("$m") }) }
            if (sleepRem != null && sleepRem > 0) TextButton(onClick = onClearSleep) { Text("Cancel") }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = 0.1f))
        Text("Sound", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("Flat", "Bass", "Vocal", "Night").forEach { preset -> androidx.compose.material3.FilterChip(selected = false, onClick = { onApplyEq(preset) }, label = { Text(preset) }) }
        }
        TextButton(onClick = onShowOutputPicker) { Text("Audio output") }
        if (toolsState.lastEvent != null) Text(toolsState.lastEvent!!, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
    }
}

private fun formatMs(ms: Long): String {
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
