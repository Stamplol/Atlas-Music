package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val recentlyAdded: List<TrackEntity> = emptyList(),
    val favorites: List<TrackEntity> = emptyList(),
    val trackCount: Int = 0,
    val totalDurationMs: Long = 0,
    val scanning: Boolean = false,
    val lastScanMessage: String? = null,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val repo = ServiceLocator.musicRepository
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                repo.observeRecentlyPlayed(),
                repo.observeRecentlyAdded(),
                repo.observeFavorites(),
                repo.observeTrackCount(),
                repo.observeTotalDurationMs(),
            ) { recent, added, favs, count, total -> HomeUiState(false, recent, added, favs, count, total) }
                .collect { _state.value = it.copy(scanning = _state.value.scanning, lastScanMessage = _state.value.lastScanMessage, error = _state.value.error) }
        }
    }

    fun rescan() {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true, lastScanMessage = null, error = null)
        viewModelScope.launch {
            runCatching {
                val settings = ServiceLocator.settings.settings.first()
                ServiceLocator.musicRepository.scan(settings.excludedFolders, settings.libraryIncludesUnsupported, settings.musicFolderUri.takeIf { it.isNotBlank() }, settings.musicFolderOnly)
            }.onSuccess { result ->
                ServiceLocator.settings.setLastScanMs(System.currentTimeMillis())
                _state.value = _state.value.copy(
                    scanning = false,
                    lastScanMessage = "Scan complete — ${result.added} new, ${result.updated} updated, ${result.removed} removed.",
                )
            }.onFailure { e ->
                android.util.Log.e("AtlasScan", "scan failed", e)
                _state.value = _state.value.copy(scanning = false, error = e.message ?: "Scan failed.")
            }
        }
    }

    fun dismissMessage() { _state.value = _state.value.copy(lastScanMessage = null, error = null) }
}
