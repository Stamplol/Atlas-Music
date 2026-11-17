package com.atlas.music.ui.screens.tools

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.atlas.music.util.EqCurves

/** Signature satin gold for the studio — same value as the rest of the app. */
val GOLD = Color(0xFFE5A83D)

@Composable
fun StudioHeader(label: String, trailing: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.weight(1f))
        trailing?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun EqPresetTile(name: String, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val barColor = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
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
                drawLine(barColor, androidx.compose.ui.geometry.Offset(0f, mid), androidx.compose.ui.geometry.Offset(size.width, mid), strokeWidth = 2.dp.toPx())
            } else {
                curves.forEachIndexed { i, gain ->
                    val h = kotlin.math.abs(gain) / EqCurves.BAND_MAX_MB.toFloat() * (mid - 1f)
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
fun FocusRing(
    modifier: Modifier = Modifier,
    sizeDp: androidx.compose.ui.unit.Dp = 150.dp,
    fraction: Float,
    mode: String,
    displayMs: Long,
    stateWord: String,
) {
    val accent = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    androidx.compose.foundation.layout.Box(modifier = modifier.size(sizeDp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 11.dp.toPx()
            val inset = stroke / 2f
            val arcSize = size * (1f - stroke / size.width)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
                size = arcSize,
            )
            if (fraction > 0f) {
                drawArc(
                    color = accent,
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
            Text(mode, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(formatMs(displayMs), style = if (sizeDp >= 150.dp) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(stateWord, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

fun formatMs(ms: Long): String {
    val s = ms / 1000
    val m = s / 60
    val sec = s % 60
    return "%d:%02d".format(m, sec)
}

/** A mini bar chart of focus minutes — one bar per day, today in gold. */
@Composable
fun WeekBars(days: List<Pair<String, Int>>, modifier: Modifier = Modifier) {
    val maxMin = days.maxOfOrNull { it.second }?.coerceAtLeast(1) ?: 1
    val accent = MaterialTheme.colorScheme.primary
    val emptyBarColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        days.forEach { (label, minutes) ->
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                if (minutes > 0) {
                    Text("$minutes", style = MaterialTheme.typography.labelSmall, color = accent)
                }
                Canvas(modifier = Modifier.fillMaxWidth().height(56.dp)) {
                    val barHeight = (minutes.toFloat() / maxMin) * size.height
                    val radius = 4.dp.toPx()
                    drawRoundRect(
                        color = if (minutes > 0) accent else emptyBarColor,
                        topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.25f, size.height - barHeight.coerceAtLeast(2.dp.toPx())),
                        size = androidx.compose.ui.geometry.Size(size.width * 0.5f, barHeight.coerceAtLeast(2.dp.toPx())),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius),
                    )
                }
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.runtime.Composable
fun FixUnknownsDialog(onDismiss: () -> Unit, onConfirm: (String, String) -> Unit) {
    var artist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var album by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Fix unknowns") },
        text = {
            androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier) {
                androidx.compose.material3.Text("Applies to every track still marked Unknown. Saved as user-edited and survive rescans.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedTextField(value = artist, onValueChange = { artist = it }, label = { androidx.compose.material3.Text("Artist name") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 8.dp))
                androidx.compose.material3.OutlinedTextField(value = album, onValueChange = { album = it }, label = { androidx.compose.material3.Text("Album name") }, modifier = androidx.compose.ui.Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(artist, album) }, enabled = artist.isNotBlank() || album.isNotBlank()) { androidx.compose.material3.Text("Apply") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } },
    )
}

@androidx.compose.material3.ExperimentalMaterial3Api
@androidx.compose.runtime.Composable
fun BreakOptionsDialog(focusMinutes: Int, breakMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    var focusState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(focusMinutes.toFloat()) }
    var breakState by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(breakMinutes.toFloat()) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { androidx.compose.material3.Text("Durations") },
        text = {
            androidx.compose.foundation.layout.Column {
                androidx.compose.material3.Text("Focus: ${focusState.toInt()} min")
                androidx.compose.material3.Slider(value = focusState, onValueChange = { focusState = it }, valueRange = 5f..180f, steps = 0)
                androidx.compose.material3.Text("Break: ${breakState.toInt()} min")
                androidx.compose.material3.Slider(value = breakState, onValueChange = { breakState = it }, valueRange = 1f..60f, steps = 0)
            }
        },
        confirmButton = { androidx.compose.material3.TextButton(onClick = { onConfirm(focusState.toInt(), breakState.toInt()) }) { androidx.compose.material3.Text("Save") } },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { androidx.compose.material3.Text("Cancel") } },
    )
}
