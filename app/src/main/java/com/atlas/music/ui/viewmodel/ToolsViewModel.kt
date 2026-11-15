package com.atlas.music.ui.viewmodel

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.PomodoroSessionEntity
import com.atlas.music.di.AtlasSettings
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.max

data class ToolsUiState(
    val settings: AtlasSettings = AtlasSettings(),
    val pomodoroMode: String = "FOCUS", // FOCUS | BREAK | IDLE
    val running: Boolean = false,
    val remainingMs: Long = 0L,
    val totalMs: Long = 0L,
    val focusMinutesToday: Int = 0,
    val sessionsToday: Int = 0,
    val streakDays: Int = 0,
    val sessions: List<PomodoroSessionEntity> = emptyList(),
    val sleepRemainingMs: Long? = null,
    val isPlaying: Boolean = false,
    val currentTitle: String = "",
    val lastEvent: String? = null,
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0L,
    val totalSizeBytes: Long = 0L,
    val unknownArtistCount: Int = 0,
    val playerState: com.atlas.music.playback.PlayerUiState = com.atlas.music.playback.PlayerUiState(),
)

class ToolsViewModel : ViewModel() {
    private val settingsRepo = ServiceLocator.settings
    private val db = ServiceLocator.database

    private val _state = MutableStateFlow(ToolsUiState())
    val state: StateFlow<ToolsUiState> = _state

    private var tickerJob: Job? = null
    private var endAtMs: Long = 0L
    private var pausedRemainingMs: Long = 0L

    init {
        viewModelScope.launch {
            val settingsFlow = settingsRepo.settings
            val sessionsFlow = db.pomodoroDao().observeAll()
            val sleepFlow = ServiceLocator.playbackConnection.sleepTimerEndAt
            val playFlow = ServiceLocator.playbackConnection.state
            val repo = ServiceLocator.musicRepository
            combine(settingsFlow, sessionsFlow, sleepFlow, playFlow) { settings, sessions, sleepEnd, play ->
                val todayStart = startOfToday()
                val today = sessions.filter { it.startedAtMs >= todayStart && it.kind == "FOCUS" && it.completed }
                val focusMin = today.sumOf { it.durationMinutes }
                val streak = computeStreak(sessions)
                ToolsUiState(
                    settings = settings,
                    pomodoroMode = _state.value.pomodoroMode,
                    running = _state.value.running,
                    remainingMs = _state.value.remainingMs,
                    totalMs = _state.value.totalMs,
                    focusMinutesToday = focusMin,
                    sessionsToday = today.size,
                    streakDays = streak,
                    sessions = sessions,
                    sleepRemainingMs = sleepEnd?.let { max(0L, it - System.currentTimeMillis()) },
                    isPlaying = play.isPlaying,
                    currentTitle = play.title,
                    lastEvent = _state.value.lastEvent,
                    playerState = play,
                    // Carry the sibling collectors' values forward — rebuilding the state
                    // from scratch here zeroed the library stats on every emission.
                    trackCount = _state.value.trackCount,
                    totalDurationMs = _state.value.totalDurationMs,
                    totalSizeBytes = _state.value.totalSizeBytes,
                    unknownArtistCount = _state.value.unknownArtistCount,
                )
            }.collect { _state.value = it }
        }
        viewModelScope.launch {
            val repo = ServiceLocator.musicRepository
            combine(repo.observeTrackCount(), repo.observeTotalDurationMs(), repo.observeTotalSizeBytes()) { count, dur, size ->
                Triple(count, dur, size)
            }.collect { (count, dur, size) ->
                _state.value = _state.value.copy(trackCount = count, totalDurationMs = dur, totalSizeBytes = size)
            }
        }
        viewModelScope.launch {
            while (true) {
                val unknown = runCatching { ServiceLocator.musicRepository.unknownCount() }.getOrDefault(0)
                _state.value = _state.value.copy(unknownArtistCount = unknown)
                kotlinx.coroutines.delay(15_000)
            }
        }
        // Sleep countdown refresh
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val end = ServiceLocator.playbackConnection.sleepTimerEndAt.value
                val rem = end?.let { max(0L, it - System.currentTimeMillis()) }
                if (_state.value.sleepRemainingMs != rem) {
                    _state.value = _state.value.copy(sleepRemainingMs = rem)
                }
                if (_state.value.running) {
                    val rem2 = max(0L, endAtMs - System.currentTimeMillis())
                    _state.value = _state.value.copy(remainingMs = rem2)
                    if (rem2 <= 0L) finishSegment()
                }
            }
        }
    }

    fun setFocusMinutes(minutes: Int) = viewModelScope.launch { settingsRepo.setPomodoroFocus(minutes) }
    fun setBreakMinutes(minutes: Int) = viewModelScope.launch { settingsRepo.setPomodoroBreak(minutes) }
    fun setSyncPlayback(enabled: Boolean) = viewModelScope.launch { settingsRepo.setPomodoroSync(enabled) }
    fun setSleepTimerEndOfTrack(enabled: Boolean) = viewModelScope.launch { settingsRepo.setSleepTimerEndOfTrack(enabled) }

    fun startPomodoro() {
        val s = _state.value.settings
        val total = if (_state.value.pomodoroMode == "BREAK") s.pomodoroBreakMinutes * 60_000L else s.pomodoroFocusMinutes * 60_000L
        endAtMs = System.currentTimeMillis() + total
        persistWidgetTimer(endAtMs, _state.value.pomodoroMode)
        _state.value = _state.value.copy(running = true, totalMs = total, remainingMs = total, lastEvent = null)
        if (s.pomodoroSyncPlayback && _state.value.pomodoroMode == "FOCUS") {
            ServiceLocator.playbackConnection.setFocusVolume(0.35f)
        }
    }

    fun pausePomodoro() {
        if (!_state.value.running) return
        pausedRemainingMs = max(0L, endAtMs - System.currentTimeMillis())
        _state.value = _state.value.copy(running = false, remainingMs = pausedRemainingMs)
        persistWidgetTimer(0L, _state.value.pomodoroMode)
    }

    fun resumePomodoro() {
        if (_state.value.running) return
        endAtMs = System.currentTimeMillis() + pausedRemainingMs
        _state.value = _state.value.copy(running = true)
        persistWidgetTimer(endAtMs, _state.value.pomodoroMode)
    }

    fun resetPomodoro() {
        _state.value = _state.value.copy(running = false, remainingMs = _state.value.totalMs, lastEvent = null)
        persistWidgetTimer(0L, _state.value.pomodoroMode)
        viewModelScope.launch {
            if (_state.value.settings.pomodoroSyncPlayback) ServiceLocator.playbackConnection.setFocusVolume(1f)
        }
    }

    fun switchMode(mode: String) {
        _state.value = _state.value.copy(pomodoroMode = mode, running = false, remainingMs = 0L, totalMs = 0L)
        persistWidgetTimer(0L, mode)
    }

    private fun finishSegment() {
        val mode = _state.value.pomodoroMode
        val totalMin = (_state.value.totalMs / 60_000L).toInt()
        val now = System.currentTimeMillis()
        viewModelScope.launch {
            db.pomodoroDao().insert(
                PomodoroSessionEntity(startedAtMs = endAtMs - _state.value.totalMs, endedAtMs = now, durationMinutes = max(1, totalMin), kind = mode, completed = true)
            )
            persistWidgetTimer(0L, if (mode == "FOCUS") "BREAK" else "FOCUS")
            _state.value = _state.value.copy(
                running = false,
                pomodoroMode = if (mode == "FOCUS") "BREAK" else "FOCUS",
                remainingMs = 0L,
                totalMs = 0L,
                lastEvent = if (mode == "FOCUS") "Focus complete — take a break" else "Break over — back to focus",
            )
            if (mode == "FOCUS" && _state.value.settings.pomodoroSyncPlayback) {
                ServiceLocator.playbackConnection.setFocusVolume(1f)
            }
            postTimerNotification(
                title = if (mode == "FOCUS") "Focus complete" else "Break over",
                body = if (mode == "FOCUS") "$totalMin min focus logged. Breathe. Stretch." else "Time for the next focus block.",
            )
            com.atlas.music.widget.PomodoroWidgetProvider.updateAll(ServiceLocator.appContext)
            com.atlas.music.widget.ToolsWidgetProvider.updateAll(ServiceLocator.appContext)
        }
    }

    private fun startOfToday(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun computeStreak(sessions: List<PomodoroSessionEntity>): Int {
        val focus = sessions.filter { it.kind == "FOCUS" && it.completed }
        if (focus.isEmpty()) return 0
        val days = focus.map {
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = it.startedAtMs
            listOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
        }.toSet()
        var streak = 0
        val cal = java.util.Calendar.getInstance()
        // Streak counts consecutive days ending today (or yesterday if today empty)
        var check = cal.timeInMillis
        if (days.none { it[0] == cal.get(java.util.Calendar.YEAR) && it[1] == cal.get(java.util.Calendar.DAY_OF_YEAR) }) {
            cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
            if (days.none { it[0] == cal.get(java.util.Calendar.YEAR) && it[1] == cal.get(java.util.Calendar.DAY_OF_YEAR) }) return 0
        }
        while (true) {
            val key = listOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
            if (key in days) { streak++; cal.add(java.util.Calendar.DAY_OF_YEAR, -1) } else break
        }
        return streak
    }

    private fun persistWidgetTimer(endAt: Long, mode: String) {
        try {
            ServiceLocator.appContext.getSharedPreferences("atlas_pomodoro", Context.MODE_PRIVATE)
                .edit().putLong("end_at", endAt).putString("mode", mode).apply()
        } catch (e: Exception) {}
    }

    private fun postTimerNotification(title: String, body: String) {
        try {
            val ctx: Context = ServiceLocator.appContext
            val manager = ctx.getSystemService(NotificationManager::class.java)
            val notif = androidx.core.app.NotificationCompat.Builder(ctx, com.atlas.music.AtlasApplication.CHANNEL_PLAYBACK)
                .setSmallIcon(com.atlas.music.R.drawable.ic_notification_small)
                .setContentTitle(title)
                .setContentText(body)
                .setAutoCancel(true)
                .build()
            manager.notify(4101, notif)
        } catch (e: Exception) {
            android.util.Log.w("Pomodoro", "notify failed", e)
        }
    }

    // ---- Audio passthrough ----
    fun startSleepTimer(minutes: Int) = ServiceLocator.playbackConnection.startSleepTimer(minutes)
    fun clearSleepTimer() = ServiceLocator.playbackConnection.clearSleepTimer()
    fun setCrossfade(enabled: Boolean) = viewModelScope.launch { settingsRepo.setCrossfade(enabled) }
    fun setCrossfadeSeconds(seconds: Int) = viewModelScope.launch { settingsRepo.setCrossfadeSeconds(seconds) }
    fun setNormalization(enabled: Boolean) = viewModelScope.launch { settingsRepo.setNormalization(enabled) }

    // ---- Player integration ----
    fun togglePlayPause() = ServiceLocator.playbackConnection.togglePlayPause()
    fun skipNext() = ServiceLocator.playbackConnection.skipNext()
    fun skipPrevious() = ServiceLocator.playbackConnection.skipPrevious()
    fun toggleShuffle() = ServiceLocator.playbackConnection.toggleShuffle()
    fun cycleRepeatMode() = ServiceLocator.playbackConnection.cycleRepeatMode()
    fun seekTo(ms: Long) = ServiceLocator.playbackConnection.seekTo(ms)
    fun setPlaybackSpeed(speed: Float) {
        ServiceLocator.playbackConnection.setPlaybackSpeed(speed)
        viewModelScope.launch { ServiceLocator.settings.setPlaybackSpeed(speed) }
    }
    fun setPreferredOutput(deviceId: Int?) = ServiceLocator.playbackConnection.setPreferredOutputDevice(deviceId)
    fun toggleFavoriteCurrent() = ServiceLocator.playbackConnection.toggleFavoriteCurrent()

    // ---- Unknowns fixer (batch) ----
    fun fixUnknowns(artist: String, album: String) {
        viewModelScope.launch {
            val cleanArtist = artist.trim().ifBlank { "Unknown artist" }
            val cleanAlbum = album.trim().ifBlank { "Unknown album" }
            val all = runCatching { ServiceLocator.musicRepository.observeAll(com.atlas.music.util.SortOption.TITLE).first() }.getOrNull() ?: emptyList()
            all.filter { it.artist == "Unknown artist" || it.album == "Unknown album" }.forEach { t ->
                val repo = ServiceLocator.musicRepository
                repo.updateTags(
                    t.id,
                    t.title,
                    if (t.artist == "Unknown artist") cleanArtist else t.artist,
                    if (t.album == "Unknown album") cleanAlbum else t.album,
                    t.genre, t.year, t.trackNumber, t.discNumber,
                )
            }
            _state.value = _state.value.copy(lastEvent = "Fixed unknowns → $cleanArtist")
        }
    }

    // ---- EQ presets (band gains in mB, applied by service) ----
    fun applyEqPreset(name: String) {
        val bands = com.atlas.music.util.EqCurves.gainsFor(name)
        viewModelScope.launch {
            if (bands.isEmpty()) {
                // Flat = bypass the equalizer entirely, don't feed the chain zeroed gains.
                settingsRepo.setEqualizer(false)
                settingsRepo.setEqualizerBands(emptyList())
            } else {
                settingsRepo.setEqualizer(true)
                settingsRepo.setEqualizerBands(bands)
            }
            _state.value = _state.value.copy(lastEvent = "EQ: $name")
        }
    }
}
