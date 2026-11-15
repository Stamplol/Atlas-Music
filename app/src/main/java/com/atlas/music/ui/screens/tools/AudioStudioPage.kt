package com.atlas.music.ui.screens.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardColors
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.ui.viewmodel.ToolsUiState
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.EqCurves
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier

@Composable
fun AudioStudioPage(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    outputLabel: String,
    onShowOutputPicker: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activePreset = state.lastEvent?.removePrefix("EQ: ")?.takeIf { state.lastEvent.startsWith("EQ:") }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ---------- PRESETS ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("PRESETS", trailing = "${EqCurves.PRESET_ORDER.size - 1} curves")
                Text(
                    "Flat returns the device to zero — no bands touched.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EqCurves.PRESET_ORDER.forEach { preset ->
                        EqPresetTile(name = preset, selected = activePreset == preset, onClick = { viewModel.applyEqPreset(preset) })
                    }
                }
            }
        }

        // ---------- LOW END ----------
        BandGroupCard(
            title = "LOW END",
            caption = "Sub weight, punch, warmth — feel first, detail later.",
            indices = EqCurves.LOW_END_BANDS,
            state = state,
            viewModel = viewModel,
        )

        // ---------- MIDRANGE ----------
        BandGroupCard(
            title = "MIDRANGE",
            caption = "Body, focus, presence — vocals and the instruments that carry the tune.",
            indices = EqCurves.MIDRANGE_BANDS,
            state = state,
            viewModel = viewModel,
        )

        // ---------- HIGH END ----------
        BandGroupCard(
            title = "HIGH END",
            caption = "Air, brilliance, shine — sparkle without the dental buzz.",
            indices = EqCurves.HIGH_END_BANDS,
            state = state,
            viewModel = viewModel,
        )

        // ---------- DYNAMICS & LOUDNESS ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("DYNAMICS", trailing = "loudness & bass")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = if (state.settings.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Bass boost", style = MaterialTheme.typography.titleSmall)
                        Text(if (state.settings.bassBoostEnabled) "+${state.settings.bassBoostStrength}% low-frequency weight" else "Off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.settings.bassBoostEnabled, onCheckedChange = viewModel::setBassBoost)
                }
                if (state.settings.bassBoostEnabled) {
                    Slider(value = state.settings.bassBoostStrength.toFloat(), onValueChange = { viewModel.setBassBoostStrength(it.toInt()) }, valueRange = 0f..100f, steps = 100)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BlurOn, contentDescription = null, tint = if (state.settings.normalizationEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Volume normalization", style = MaterialTheme.typography.titleSmall)
                        val gainDb = state.settings.loudnessGainMb / 100.0
                        Text("+${"%.1f".format(gainDb)} dB target across the library", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = state.settings.normalizationEnabled, onCheckedChange = viewModel::setNormalization)
                }
                if (state.settings.normalizationEnabled) {
                    Slider(value = state.settings.loudnessGainMb.toFloat(), onValueChange = { viewModel.setLoudnessGainMb(it.toInt()) }, valueRange = 0f..1000f, steps = 19)
                }
            }
        }

        // ---------- TRANSITIONS ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("TRANSITIONS", trailing = "gapless ↔ crossfade")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crossfade", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (state.settings.crossfadeEnabled) "Fading across ${state.settings.crossfadeSeconds}s" else "Gapless — hard cut, true DJ flow",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.settings.crossfadeEnabled, onCheckedChange = viewModel::setCrossfade)
                }
                if (state.settings.crossfadeEnabled) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                        listOf(2, 5, 8, 12).forEach { s ->
                            FilterChip(selected = state.settings.crossfadeSeconds == s, onClick = { viewModel.setCrossfadeSeconds(s) }, label = { Text("${s}s") })
                        }
                    }
                }
            }
        }

        // ---------- PLAYBACK ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("PLAYBACK")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Speed", style = MaterialTheme.typography.titleSmall)
                        Text("Tempo without pitch shift — 0.5×–2.0×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("%.1f×".format(state.playerState.playbackSpeed), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                Slider(value = state.playerState.playbackSpeed, onValueChange = { viewModel.setPlaybackSpeed(it) }, valueRange = 0.5f..2f, steps = 14)
            }
        }

        // ---------- OUTPUT ----------
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                StudioHeader("OUTPUT")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Audio output", style = MaterialTheme.typography.titleSmall)
                        Text(outputLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    TextButton(onClick = onShowOutputPicker) { Text("Change", color = MaterialTheme.colorScheme.primary) }
                }
            }
        }

        // ---------- RESET ----------
        Button(
            onClick = viewModel::resetAudio,
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(14.dp),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer),
        ) {
            Icon(Icons.Filled.Cached, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Reset audio — everything back to flat")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BandGroupCard(
    title: String,
    caption: String,
    indices: List<Int>,
    state: com.atlas.music.ui.viewmodel.ToolsUiState,
    viewModel: com.atlas.music.ui.viewmodel.ToolsViewModel,
) {
    val bands = remember(state.equalizerBands) {
        List(9) { i -> state.equalizerBands.getOrNull(i) ?: 0 }
    }
    Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            StudioHeader(title)
            Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            indices.forEach { i ->
                val label = EqCurves.MANUAL_BANDS[i]
                val gain = bands[i]
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                    Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp), color = MaterialTheme.colorScheme.onSurface)
                    Slider(
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
                        modifier = Modifier.width(56.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Manual bands ride on your device's equalizer — exact centers and maximum curve vary by hardware.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}
