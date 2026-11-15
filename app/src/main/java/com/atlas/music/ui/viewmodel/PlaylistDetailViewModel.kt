package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.PlaylistEntity
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PlaylistDetailState(
    val playlist: PlaylistEntity? = null,
    val tracks: List<TrackEntity> = emptyList(),
    val loaded: Boolean = false,
)

class PlaylistDetailViewModel(private val playlistId: Long) : ViewModel() {
    private val playlists = ServiceLocator.playlistRepository
    private val _state = MutableStateFlow(PlaylistDetailState())
    val state: StateFlow<PlaylistDetailState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            // Playlist row + ordered track ids; re-resolve full entities when either changes.
            playlists.observePlaylistTrackIds(playlistId).collect { ids ->
                val tracks = ServiceLocator.musicRepository.getTracks(ids).let { list ->
                    val index = ids.withIndex().associate { it.value to it.index }
                    list.sortedBy { index[it.id] ?: Int.MAX_VALUE }
                }
                _state.value = _state.value.copy(tracks = tracks, loaded = true)
            }
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(playlist = ServiceLocator.database.playlistDao().byId(playlistId))
        }
    }

    fun rename(name: String) { viewModelScope.launch { playlists.rename(playlistId, name) } }

    fun removeTrack(track: TrackEntity) { viewModelScope.launch { playlists.removeTrack(playlistId, track.id) } }

    fun delete() { viewModelScope.launch { playlists.delete(playlistId) } }

    fun moveTrack(trackId: Long, toIndex: Int) { viewModelScope.launch { playlists.moveTrack(playlistId, trackId, toIndex) } }

    /**
     * Batch-add tracks already in the library — the Flow above picks them up and
     * re-resolves automatically, so no manual refresh is needed here.
     */
    fun addTracks(ids: Set<Long>) {
        if (ids.isEmpty()) return
        viewModelScope.launch { ids.forEach { playlists.addTrack(playlistId, it) } }
    }
}
