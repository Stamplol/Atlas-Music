package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.AtlasSettings
import com.atlas.music.di.ServiceLocator
import com.atlas.music.util.ArtworkStyle
import com.atlas.music.util.SortOption
import com.atlas.music.util.ThemeMode
import com.atlas.music.playback.AtlasPlaybackService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    private val settingsRepo = ServiceLocator.settings
    /** null = unknown/loading, false = device has no system audio effects. */
    val effectsAvailable: StateFlow<Boolean?> = AtlasPlaybackService.EffectsStatus

    val settings: StateFlow<AtlasSettings> =
        settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AtlasSettings())

    val lastScanMs: StateFlow<Long> =
        settingsRepo.lastScanMs.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    fun setTheme(mode: ThemeMode) = launch { settingsRepo.setTheme(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { settingsRepo.setDynamicColor(enabled) }
    fun setArtworkStyle(style: ArtworkStyle) = launch { settingsRepo.setArtworkStyle(style) }
    fun setDefaultSort(sort: SortOption) = launch { settingsRepo.setDefaultSort(sort) }
    fun setCrossfade(enabled: Boolean) = launch { settingsRepo.setCrossfade(enabled) }
    fun setCrossfadeSeconds(seconds: Int) = launch { settingsRepo.setCrossfadeSeconds(seconds) }
    fun setEqualizer(enabled: Boolean) = launch { settingsRepo.setEqualizer(enabled) }
    fun setBassBoost(enabled: Boolean) = launch { settingsRepo.setBassBoost(enabled) }
    fun setNormalization(enabled: Boolean) = launch { settingsRepo.setNormalization(enabled) }
    fun setSleepTimerDefault(minutes: Int) = launch { settingsRepo.setSleepTimerDefault(minutes) }
    fun setDownloadFormat(format: String) = launch { settingsRepo.setDownloadFormat(format) }
    fun setAutoImportDownloads(enabled: Boolean) = launch { settingsRepo.setAutoImportDownloads(enabled) }
    fun setAutoCheckYtdlp(enabled: Boolean) = launch { settingsRepo.setAutoCheckYtdlp(enabled) }
    fun setDiagnostics(enabled: Boolean) = launch { settingsRepo.setDiagnostics(enabled) }
    fun setDownloadDestination(uri: String) = launch { settingsRepo.setDownloadDestinationUri(uri) }
    fun setBackgroundMode(id: String) = launch { settingsRepo.setBackgroundMode(id) }
    fun setBackgroundDensity(v: Int) = launch { settingsRepo.setBackgroundDensity(v) }
    fun setBackgroundSpeed(v: Int) = launch { settingsRepo.setBackgroundSpeed(v) }
    fun setBackgroundTrail(v: Int) = launch { settingsRepo.setBackgroundTrail(v) }
    fun setBackgroundReact(enabled: Boolean) = launch { settingsRepo.setBackgroundReact(enabled) }
    fun setSortAlgo(id: String) = launch { settingsRepo.setSortAlgo(id) }
    fun setMusicFolder(uri: String, name: String) = launch { settingsRepo.setMusicFolder(uri, name) }
    fun clearMusicFolder() = launch { settingsRepo.clearMusicFolder() }
    fun setMusicFolderOnly(enabled: Boolean) = launch { settingsRepo.setMusicFolderOnly(enabled) }

    fun addExcludedFolder(folder: String) = launch {
        val current = settings.value.excludedFolders
        val normalized = folder.trim().trim('/').lowercase()
        if (normalized.isNotEmpty()) settingsRepo.setExcludedFolders(current + normalized)
    }

    fun removeExcludedFolder(folder: String) = launch {
        settingsRepo.setExcludedFolders(settings.value.excludedFolders - folder)
    }

    fun rescan() = launch {
        val s = settingsRepo.settings.first()
        ServiceLocator.musicRepository.scan(s.excludedFolders, s.libraryIncludesUnsupported, s.musicFolderUri.takeIf { it.isNotBlank() }, s.musicFolderOnly)
        settingsRepo.setLastScanMs(System.currentTimeMillis())
    }

    suspend fun setMusicFolderAndRescan(uri: String, name: String) {
        settingsRepo.setMusicFolder(uri, name)
        val s = settingsRepo.settings.first()
        ServiceLocator.musicRepository.scan(s.excludedFolders, s.libraryIncludesUnsupported, s.musicFolderUri.takeIf { it.isNotBlank() }, s.musicFolderOnly)
        settingsRepo.setLastScanMs(System.currentTimeMillis())
    }

    suspend fun setMusicFolderOnlyAndRescan(enabled: Boolean) {
        settingsRepo.setMusicFolderOnly(enabled)
        val s = settingsRepo.settings.first()
        ServiceLocator.musicRepository.scan(s.excludedFolders, s.libraryIncludesUnsupported, s.musicFolderUri.takeIf { it.isNotBlank() }, s.musicFolderOnly)
        settingsRepo.setLastScanMs(System.currentTimeMillis())
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { runCatching { block() } }
    }
}
