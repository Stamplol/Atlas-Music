package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.di.ServiceLocator
import com.atlas.music.util.DuplicateDetector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DuplicatesViewModel : ViewModel() {
    private val _groups = MutableStateFlow<List<DuplicateDetector.DuplicateGroup>>(emptyList())
    val groups: StateFlow<List<DuplicateDetector.DuplicateGroup>> = _groups.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _loading.value = true
            _groups.value = ServiceLocator.musicRepository.findDuplicates()
            _loading.value = false
        }
    }
}
