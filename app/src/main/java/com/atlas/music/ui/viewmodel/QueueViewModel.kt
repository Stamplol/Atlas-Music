package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class QueueViewModel : ViewModel() {
    private val _tracks = MutableStateFlow<List<TrackEntity>>(emptyList())
    val tracks: StateFlow<List<TrackEntity>> = _tracks.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            val connection = ServiceLocator.playbackConnection
            val ids = connection.queueTrackIds()
            _tracks.value = if (ids.isEmpty()) emptyList() else ServiceLocator.musicRepository.getTracks(ids).let { list ->
                val index = ids.withIndex().associate { it.value to it.index }
                list.sortedBy { index[it.id] ?: Int.MAX_VALUE }
            }
        }
    }

    fun removeItem(index: Int) {
        ServiceLocator.playbackConnection.removeQueueItem(index)
        refresh()
    }

    fun moveItem(from: Int, to: Int) {
        ServiceLocator.playbackConnection.moveQueueItem(from, to)
        refresh()
    }

    fun clear() {
        ServiceLocator.playbackConnection.clearQueue()
        refresh()
    }
}
