package com.atlas.music.ui.screens.tools

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Balance
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.SurroundSound
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlas.music.playback.AtlasPlaybackService
import com.atlas.music.ui.viewmodel.ToolsUiState
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.AudioDspMath
import com.atlas.music.util.EffectiveAudioCurve
import com.atlas.music.util.EqCurves
import com.atlas.music.util.HeadphoneProfiles
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AudioStudioPage(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    outputLabel: String,
    onShowOutputPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = state.settings
    val dynamicsAttached by AtlasPlaybackService.DynamicsStatus.collectAsState()
    val effectsAvailable by AtlasPlaybackService.EffectsStatus.collectAsState()
    var showHeadphoneSheet by remember { mutableStateOf(false) }
    var showManualBands by remember { mutableStateOf(false) }

    val selectedHeadphone = remember(s.headphoneProfileId) {
        HeadphoneProfiles.byId(s.headphoneProfileId)
    }
    val effectiveCurve = remember(
        s.headphoneProfileId,
        state.equalizerBands,
        s.preampDb,
        s.soundCheckWarmth,
        s.soundCheckPunch,
        s.autoHeadroomEnabled,
    ) {
        AudioDspMath.computeEffectiveCurve(
            profileId = s.headphoneProfileId,
            userBandsMb = state.equalizerBands,
            preampDb = s.preampDb,
            soundCheckWarmth = s.soundCheckWarmth,
            soundCheckPunch = s.soundCheckPunch,
            autoHeadroom = s.autoHeadroomEnabled,
        )
    }
    val activePreset = EqCurves.matchingPresetName(state.equalizerBands)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (effectsAvailable == false) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Android audio effects aren't supported on this audio route — hardware output will play flat.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        // ---------- 1. OUTPUT & MASTER SOUND ENGINE ----------
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader(
                    "SOUND ENGINE",
                    trailing = if (!s.equalizerEnabled) "bypassed" else if (dynamicsAttached) "10-band DSP + limiter" else "hardware EQ",
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.GraphicEq,
                        contentDescription = null,
                        tint = if (s.equalizerEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (s.equalizerEnabled) "Sound enhancement on" else "Direct bypass (flat)",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            if (s.equalizerEnabled) "Tap switch to A/B compare with raw audio" else "All headphones, EQ, bass & stage bypassed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = s.equalizerEnabled,
                        onCheckedChange = viewModel::setEqualizerEnabled,
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Playing through", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(outputLabel, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onShowOutputPicker) { Text("Switch output") }
                }
            }
        }

        // ---------- 2. LIVE FREQUENCY RESPONSE + AUTOEQ HEADPHONE CALIBRATION ----------
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader(
                    "FREQUENCY RESPONSE",
                    trailing = if (s.equalizerEnabled) "headroom ${"%+.1f".format(effectiveCurve.effectivePreampDb)} dB" else "bypassed",
                )
                Spacer(Modifier.height(10.dp))
                FrequencyResponseGraph(
                    curve = effectiveCurve,
                    enabled = s.equalizerEnabled,
                    hasHeadphoneProfile = selectedHeadphone != null,
                )
                Spacer(Modifier.height(10.dp))
                // Headphone AutoEq calibration row (Wavelet-style)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .clickable { showHeadphoneSheet = true }
                        .padding(14.dp),
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = null,
                        tint = if (selectedHeadphone != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selectedHeadphone?.displayName ?: "Choose your headphones (AutoEq)",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            selectedHeadphone?.summary ?: "Match Sony, Apple, Sennheiser, Moondrop & 30+ models to the Harman target",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (selectedHeadphone != null) {
                        IconButton(onClick = { viewModel.setHeadphoneProfile("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear headphone model", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ---------- 3. FIND YOUR SOUND — INTERACTIVE XY PAD ----------
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        StudioHeader("FIND YOUR SOUND", trailing = if (abs(s.soundCheckWarmth) > 0.05f || abs(s.soundCheckPunch) > 0.05f) "custom feel" else "neutral")
                        Text(
                            "Drag anywhere — no technical dB math needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (abs(s.soundCheckWarmth) > 0.05f || abs(s.soundCheckPunch) > 0.05f) {
                        TextButton(onClick = viewModel::resetSoundCheck) { Text("Center") }
                    }
                }
                Spacer(Modifier.height(10.dp))
                SoundCheckXyPad(
                    warmth = s.soundCheckWarmth,
                    punch = s.soundCheckPunch,
                    onUpdate = viewModel::setSoundCheck,
                )
            }
        }

        // ---------- 4. SOUNDSTAGE, BASS, BALANCE & PROTECTION ----------
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("SPACE & DYNAMICS", trailing = "headphones")
                Spacer(Modifier.height(10.dp))

                // Soundstage Width (Virtualizer)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.SurroundSound,
                        contentDescription = null,
                        tint = if (s.virtualizerStrength > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Soundstage width", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.virtualizerStrength > 0) "${s.virtualizerStrength}% wider stereo image" else "Off — natural stereo width",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Slider(
                    value = s.virtualizerStrength.toFloat(),
                    onValueChange = { viewModel.setVirtualizerStrength(it.toInt()) },
                    valueRange = 0f..100f,
                )

                // Bass Weight
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = if (s.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sub-bass boost", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.bassBoostEnabled) "+${s.bassBoostStrength}% deep low-end punch" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.bassBoostEnabled, onCheckedChange = viewModel::setBassBoost)
                }
                if (s.bassBoostEnabled) {
                    Slider(
                        value = s.bassBoostStrength.toFloat(),
                        onValueChange = { viewModel.setBassBoostStrength(it.toInt()) },
                        valueRange = 0f..100f,
                    )
                }

                Spacer(Modifier.height(4.dp))

                // Left/Right Ear Balance
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Balance,
                        contentDescription = null,
                        tint = if (abs(s.channelBalance) > 0.03f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Left / Right balance", style = MaterialTheme.typography.titleSmall)
                        val balPct = (abs(s.channelBalance) * 100).toInt()
                        Text(
                            when {
                                s.channelBalance < -0.04f -> "$balPct% toward Left ear"
                                s.channelBalance > 0.04f -> "$balPct% toward Right ear"
                                else -> "Centered"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (abs(s.channelBalance) > 0.03f) {
                        TextButton(onClick = { viewModel.setChannelBalance(0f) }) { Text("Center") }
                    }
                }
                CenterZeroSlider(
                    value = s.channelBalance,
                    onValueChange = { v -> viewModel.setChannelBalance(if (abs(v) < 0.06f) 0f else v) },
                    valueRange = -1f..1f,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))

                // Clipping Limiter + Auto Headroom
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = if (s.limiterEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Peak limiter & headroom", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.limiterEnabled) "Protects loud boosts from crackling (${"%.1f".format(effectiveCurve.autoHeadroomCutDb)} dB headroom trim)" else "Limiter off — raw digital peaks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.limiterEnabled, onCheckedChange = viewModel::setLimiterEnabled)
                }

                Spacer(Modifier.height(6.dp))

                // Loudness boost for quiet tracks
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BlurOn, contentDescription = null, tint = if (s.normalizationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Quiet-track loudness lift", style = MaterialTheme.typography.titleSmall)
                        val gainDb = s.loudnessGainMb / 100.0
                        Text(
                            if (s.normalizationEnabled) "+${"%.1f".format(gainDb)} dB gentle gain lift" else "Off",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.normalizationEnabled, onCheckedChange = viewModel::setNormalization)
                }
                if (s.normalizationEnabled) {
                    Slider(
                        value = s.loudnessGainMb.toFloat(),
                        onValueChange = { viewModel.setLoudnessGainMb(it.toInt()) },
                        valueRange = 0f..1000f,
                        steps = 19,
                    )
                }
            }
        }

        // ---------- 5. SIGNATURE EQ PRESETS & EXPANDABLE MANUAL BANDS ----------
        Card(
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("SOUND PRESETS", trailing = activePreset)
                Text(
                    "Instant tonal curves — stacks cleanly with your headphone profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EqCurves.PRESET_ORDER.forEach { preset ->
                        EqPresetTile(name = preset, selected = activePreset == preset, onClick = { viewModel.applyEqPreset(preset) })
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { showManualBands = !showManualBands }
                        .padding(vertical = 6.dp),
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fine-tune manual bands", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (showManualBands) "Tap to collapse 9 graphic bands & preamp" else "Expand for per-band sliders (60 Hz – 16 kHz)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(
                        imageVector = if (showManualBands) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (showManualBands) "Hide manual bands" else "Show manual bands",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                AnimatedVisibility(visible = showManualBands) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                        ManualBandSliders(state = state, viewModel = viewModel)
                    }
                }
            }
        }

        // ---------- 6. TRANSITIONS & SPEED ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("PLAYBACK FEEL", trailing = "transitions & speed")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crossfade between tracks", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (s.crossfadeEnabled) "Smoothly blends across ${s.crossfadeSeconds}s" else "Gapless — zero pause between songs",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.crossfadeEnabled, onCheckedChange = viewModel::setCrossfade)
                }
                if (s.crossfadeEnabled) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf(2, 5, 8, 12).forEach { sec ->
                            FilterChip(selected = s.crossfadeSeconds == sec, onClick = { viewModel.setCrossfadeSeconds(sec) }, label = { Text("${sec}s") })
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Playback speed", style = MaterialTheme.typography.titleSmall)
                        Text("Tempo without changing pitch", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%.1f×".format(state.playerState.playbackSpeed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = state.playerState.playbackSpeed, onValueChange = { viewModel.setPlaybackSpeed(it) }, valueRange = 0.5f..2f, steps = 14)
            }
        }

        // ---------- 7. RESET ----------
        Button(
            onClick = viewModel::resetAudio,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        ) {
            Icon(Icons.Filled.Cached, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Reset all sound settings to flat")
        }
        Spacer(Modifier.navigationBarsPadding().height(24.dp))
    }

    if (showHeadphoneSheet) {
        HeadphonePickerSheet(
            selectedId = s.headphoneProfileId,
            onSelect = { profileId ->
                viewModel.setHeadphoneProfile(profileId)
                showHeadphoneSheet = false
            },
            onDismiss = { showHeadphoneSheet = false },
        )
    }
}

@Composable
private fun FrequencyResponseGraph(
    curve: EffectiveAudioCurve,
    enabled: Boolean,
    hasHeadphoneProfile: Boolean,
) {
    val accent = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val refColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val zeroLineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.24f)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(142.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                val maxDb = 12f
                fun dbToY(db: Float): Float = (midY - (db / maxDb) * (midY * 0.85f)).coerceIn(4f, h - 4f)

                // Horizontal dB lines (-6, 0, +6 dB)
                listOf(-6f, 6f).forEach { db ->
                    val y = dbToY(db)
                    drawLine(gridColor, Offset(0f, y), Offset(w, y), strokeWidth = 1.dp.toPx())
                }
                drawLine(zeroLineColor, Offset(0f, midY), Offset(w, midY), strokeWidth = 1.2.dp.toPx())

                // Vertical octave columns
                val n = curve.totalBandsDb10.size
                for (i in 0 until n) {
                    val x = if (n <= 1) 0f else w * i / (n - 1).toFloat()
                    drawLine(gridColor, Offset(x, 0f), Offset(x, h), strokeWidth = 1.dp.toPx())
                }

                // Dashed headphone AutoEq reference curve
                if (hasHeadphoneProfile) {
                    val refPath = buildSmoothCurvePath(curve.headphoneCurveDb10, w, ::dbToY)
                    drawPath(
                        path = refPath,
                        color = refColor,
                        style = Stroke(
                            width = 1.6.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
                        ),
                    )
                }

                // Main active combined frequency response curve + gradient fill
                val points = if (enabled) curve.totalBandsDb10 else List(10) { 0f }
                val strokePath = buildSmoothCurvePath(points, w, ::dbToY)
                val fillPath = Path().apply {
                    addPath(strokePath)
                    lineTo(w, midY)
                    lineTo(0f, midY)
                    close()
                }
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(accent.copy(alpha = 0.28f), accent.copy(alpha = 0.04f)),
                    ),
                )
                drawPath(
                    path = strokePath,
                    color = accent,
                    style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )

                // Band dots
                points.forEachIndexed { idx, db ->
                    val x = w * idx / (points.size - 1).toFloat()
                    val y = dbToY(db)
                    drawCircle(accent, radius = 3.dp.toPx(), center = Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k").forEach { hz ->
                Text(
                    hz,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

private fun buildSmoothCurvePath(
    bandsDb: List<Float>,
    width: Float,
    dbToY: (Float) -> Float,
): Path {
    val path = Path()
    if (bandsDb.isEmpty()) return path
    val pts = bandsDb.mapIndexed { idx, db ->
        val x = if (bandsDb.size == 1) 0f else width * idx / (bandsDb.size - 1).toFloat()
        Offset(x, dbToY(db))
    }
    path.moveTo(pts.first().x, pts.first().y)
    for (i in 0 until pts.lastIndex) {
        val p0 = pts[i]
        val p1 = pts[i + 1]
        val midX = (p0.x + p1.x) / 2f
        path.cubicTo(midX, p0.y, midX, p1.y, p1.x, p1.y)
    }
    return path
}

@Composable
private fun SoundCheckXyPad(
    warmth: Float,
    punch: Float,
    onUpdate: (Float, Float) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    val surfaceBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    val axisColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f)

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Punchy", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Bright",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.Center,
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(176.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(surfaceBg)
                    .border(1.dp, axisColor, RoundedCornerShape(18.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val w = ((offset.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                            val p = (1f - (offset.y / size.height) * 2f).coerceIn(-1f, 1f)
                            onUpdate(if (abs(w) < 0.08f) 0f else w, if (abs(p) < 0.08f) 0f else p)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val w = ((change.position.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                            val p = (1f - (change.position.y / size.height) * 2f).coerceIn(-1f, 1f)
                            onUpdate(if (abs(w) < 0.08f) 0f else w, if (abs(p) < 0.08f) 0f else p)
                        }
                    },
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    drawLine(axisColor, Offset(cx, 0f), Offset(cx, size.height), strokeWidth = 1.dp.toPx())
                    drawLine(axisColor, Offset(0f, cy), Offset(size.width, cy), strokeWidth = 1.dp.toPx())

                    val dotX = ((warmth + 1f) / 2f) * size.width
                    val dotY = ((1f - punch) / 2f) * size.height
                    drawCircle(accent.copy(alpha = 0.20f), radius = 22.dp.toPx(), center = Offset(dotX, dotY))
                    drawCircle(accent, radius = 9.dp.toPx(), center = Offset(dotX, dotY))
                }
            }
            Text(
                "Warm",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(46.dp),
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text("Relaxed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CenterZeroSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val trackBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    val span = (valueRange.endInclusive - valueRange.start).coerceAtLeast(1e-4f)
    val midFrac = ((0f - valueRange.start) / span).coerceIn(0f, 1f)
    val valFrac = ((value - valueRange.start) / span).coerceIn(0f, 1f)

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(14.dp),
        ) {
            val cy = size.height / 2f
            val trackH = 6.dp.toPx()
            drawRoundRect(
                color = trackBg,
                topLeft = Offset(0f, cy - trackH / 2f),
                size = androidx.compose.ui.geometry.Size(size.width, trackH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
            )
            val startX = size.width * minOf(midFrac, valFrac)
            val endX = size.width * maxOf(midFrac, valFrac)
            if (endX - startX > 1f) {
                drawRoundRect(
                    color = primary,
                    topLeft = Offset(startX, cy - trackH / 2f),
                    size = androidx.compose.ui.geometry.Size(endX - startX, trackH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(trackH / 2f),
                )
            }
            val midX = size.width * midFrac
            drawLine(
                color = primary.copy(alpha = 0.65f),
                start = Offset(midX, cy - 6.dp.toPx()),
                end = Offset(midX, cy + 6.dp.toPx()),
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                activeTrackColor = androidx.compose.ui.graphics.Color.Transparent,
                inactiveTrackColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ManualBandSliders(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
) {
    val s = state.settings
    val bands = remember(state.equalizerBands) {
        List(9) { i -> state.equalizerBands.getOrNull(i) ?: 0 }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Manual preamp offset
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("Preamp", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurface)
            CenterZeroSlider(
                value = s.preampDb,
                onValueChange = viewModel::setPreampDb,
                valueRange = AudioDspMath.MIN_PREAMP_DB..AudioDspMath.MAX_PREAMP_DB,
                modifier = Modifier.weight(1f).height(34.dp),
            )
            Text(
                "%+.1f dB".format(s.preampDb),
                style = MaterialTheme.typography.labelMedium,
                color = if (abs(s.preampDb) > 0.1f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(60.dp),
                textAlign = TextAlign.End,
            )
        }

        EqCurves.MANUAL_BANDS.forEachIndexed { i, label ->
            val gain = bands[i]
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurface)
                CenterZeroSlider(
                    value = gain.toFloat(),
                    onValueChange = { v ->
                        val next = bands.toMutableList()
                        next[i] = v.toInt().coerceIn(-EqCurves.BAND_MAX_MB, EqCurves.BAND_MAX_MB)
                        viewModel.setCustomBands(next)
                    },
                    valueRange = -EqCurves.BAND_MAX_MB.toFloat()..EqCurves.BAND_MAX_MB.toFloat(),
                    modifier = Modifier.weight(1f).height(34.dp),
                )
                Text(
                    "%+.1f dB".format(gain / 100.0),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (gain != 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(60.dp),
                    textAlign = TextAlign.End,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeadphonePickerSheet(
    selectedId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by remember { mutableStateOf("") }
    val filtered = remember(query) { HeadphoneProfiles.search(query) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp).padding(horizontal = 20.dp)) {
            Text("Choose your headphones", style = MaterialTheme.typography.titleLarge)
            Text(
                "Harman-target calibration profiles (${HeadphoneProfiles.PRESETS.size} models)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search brand or model (e.g. Sony, AirPods, HD 600)") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            LazyColumn(
                modifier = Modifier.weight(1f, fill = false).navigationBarsPadding(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (query.isBlank()) {
                    item {
                        HeadphoneOptionRow(
                            title = "None (Use default headphone tuning)",
                            subtitle = "Leaves frequency response calibrated only by your EQ and Sound Check",
                            badge = "Default",
                            selected = selectedId.isBlank(),
                            onClick = { onSelect("") },
                        )
                    }
                }
                items(filtered, key = { it.id }) { profile ->
                    HeadphoneOptionRow(
                        title = profile.displayName,
                        subtitle = profile.summary,
                        badge = profile.formFactor,
                        selected = selectedId == profile.id,
                        onClick = { onSelect(profile.id) },
                    )
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

@Composable
private fun HeadphoneOptionRow(
    title: String,
    subtitle: String,
    badge: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp))
                Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.surface) {
                    Text(badge, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (selected) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Filled.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
