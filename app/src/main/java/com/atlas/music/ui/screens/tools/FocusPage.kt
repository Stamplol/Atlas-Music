package com.atlas.music.ui.screens.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.atlas.music.ui.viewmodel.ToolsUiState
import com.atlas.music.ui.viewmodel.ToolsViewModel
import com.atlas.music.util.Format
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun FocusPage(
    state: ToolsUiState,
    viewModel: ToolsViewModel,
    modifier: Modifier = Modifier,
) {
    var showBreakOptions by remember { mutableStateOf(false) }
    val totalFocusMs = state.totalMs.takeIf { it > 0 } ?: (state.settings.pomodoroFocusMinutes * 60_000L)
    val displayMs = if (state.totalMs > 0) state.remainingMs else totalFocusMs
    val fraction = if (state.totalMs > 0) (state.remainingMs.toFloat() / state.totalMs.toFloat()).coerceIn(0f, 1f) else 0f
    val stateWord = when {
        state.running -> "REMAINING"
        state.remainingMs > 0 && state.totalMs > 0 -> "PAUSED"
        else -> "READY"
    }
    // Weekly roll-up for the bar chart
    val weekDays = remember(state.sessions) { rollUpLastSevenDays(state.sessions.map { it.startedAtMs to it.durationMinutes }) }
    Column(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        Spacer(Modifier.height(4.dp))
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                StudioHeader(
                    if (state.pomodoroMode == "BREAK") "FOCUS LAB · BREAK" else "FOCUS LAB",
                    trailing = "Today ${state.focusMinutesToday}m · ${state.sessionsToday} done",
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FocusRing(
                        sizeDp = 140.dp,
                        fraction = fraction,
                        mode = state.pomodoroMode,
                        displayMs = displayMs,
                        stateWord = stateWord,
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Whatshot, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (state.streakDays > 0) "${state.streakDays}-day streak" else "Start your streak", style = MaterialTheme.typography.titleSmall)
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(15, 25, 50).forEach { m ->
                                FilterChip(selected = state.settings.pomodoroFocusMinutes == m, onClick = { viewModel.setFocusMinutes(m) }, label = { Text("${m}m") })
                            }
                            TextButton(onClick = { showBreakOptions = true }) { Text("Adjust") }
                        }
                        Spacer(Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Duck music to 35%", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Switch(checked = state.settings.pomodoroSyncPlayback, onCheckedChange = viewModel::setSyncPlayback)
                        }
                    }
                }
                Spacer(Modifier.height(14.dp))
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
                Spacer(Modifier.height(8.dp))
                StudioHeader("THIS WEEK")
                Spacer(Modifier.height(6.dp))
                WeekBars(days = weekDays)
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(shape = RoundedCornerShape(26.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                StudioHeader("LEDGER", trailing = "last sessions")
                Spacer(Modifier.height(8.dp))
                val ledger = state.sessions.take(6)
                if (ledger.isEmpty()) {
                    Text("Nothing logged yet. Finish a focus block and it lands here.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ledger.forEach { s ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 3.dp)) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.size(7.dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (s.kind == "FOCUS") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "${if (s.kind == "FOCUS") "Focus" else "Break"} · ${s.durationMinutes} min · ${SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(s.startedAtMs))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (showBreakOptions) {
        BreakOptionsDialog(
            focusMinutes = state.settings.pomodoroFocusMinutes,
            breakMinutes = state.settings.pomodoroBreakMinutes,
            onDismiss = { showBreakOptions = false },
            onConfirm = { f, b -> viewModel.setFocusMinutes(f); viewModel.setBreakMinutes(b); showBreakOptions = false },
        )
    }
}

private fun rollUpLastSevenDays(sessions: List<Pair<Long, Int>>): List<Pair<String, Int>> {
    val map = LinkedHashMap<Long, Int>()
    val cal = Calendar.getInstance()
    val days = (6 downTo 0).map { offset ->
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, -offset)
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis to SimpleDateFormat("EEE", Locale.getDefault()).format(Date(cal.timeInMillis))
    }
    days.forEach { (dayStart, _) -> map[dayStart] = 0 }
    sessions.forEach { (startedAt, minutes) ->
        val c = Calendar.getInstance().apply { timeInMillis = startedAt }.apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        if (c in map) map[c] = (map[c] ?: 0) + minutes
    }
    return days.map { it.second to (map[it.first] ?: 0) }
}
