package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.di.ServiceLocator
import com.atlas.music.playback.PlayerUiState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel : ViewModel() {
    val connection = ServiceLocator.playbackConnection
    val state: StateFlow<PlayerUiState> = connection.state
    val sleepTimerEndAt: StateFlow<Long?> = connection.sleepTimerEndAt

    init {
        // Position ticker — keeps scrubber honest without hammering the main thread.
        viewModelScope.launch {
            while (true) {
                connection.tickPosition()
                kotlinx.coroutines.delay(1000)
            }
        }
    }

    fun tick() = connection.tickPosition()
    fun togglePlayPause() = connection.togglePlayPause()
    fun skipNext() = connection.skipNext()
    fun skipPrevious() = connection.skipPrevious()
    fun seekTo(ms: Long) = connection.seekTo(ms)
    fun toggleShuffle() = connection.toggleShuffle()
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun setPlaybackSpeed(speed: Float) {
        connection.setPlaybackSpeed(speed)
        viewModelScope.launch { ServiceLocator.settings.setPlaybackSpeed(speed) }
    }
    fun startSleepTimer(minutes: Int) = connection.startSleepTimer(minutes)
    fun clearSleepTimer() = connection.clearSleepTimer()

    suspend fun toggleFavoriteCurrent() {
        val id = connection.state.value.currentTrackId ?: return
        val track = ServiceLocator.musicRepository.getTrack(id) ?: return
        ServiceLocator.musicRepository.setFavorite(id, !track.isFavorite)
    }
}
