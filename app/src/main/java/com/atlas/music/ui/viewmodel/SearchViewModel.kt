package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.di.ServiceLocator
import com.atlas.music.data.repo.MusicRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val results: MusicRepository.SearchResults = MusicRepository.SearchResults(),
)

class SearchViewModel : ViewModel() {
    private val repo = ServiceLocator.musicRepository
    private val _state = MutableStateFlow(SearchUiState())
    val state: StateFlow<SearchUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value, searching = value.isNotBlank())
        searchJob?.cancel()
        if (value.isBlank()) {
            _state.value = _state.value.copy(searching = false, results = MusicRepository.SearchResults())
            return
        }
        searchJob = viewModelScope.launch {
            delay(180) // debounce — keeps search responsive on big libraries
            val results = repo.search(value)
            _state.value = _state.value.copy(searching = false, results = results)
        }
    }

    fun clear() { onQueryChange("") }
}
