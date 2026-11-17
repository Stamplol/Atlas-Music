package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.NameCount
import com.atlas.music.data.db.PlaylistEntity
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import com.atlas.music.util.SortOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LibraryUiState(
    val tracks: List<TrackEntity> = emptyList(),
    val folders: List<com.atlas.music.data.db.FolderCount> = emptyList(),
    val albumCounts: List<NameCount> = emptyList(),
    val artistCounts: List<NameCount> = emptyList(),
    val genreCounts: List<NameCount> = emptyList(),
    val playlists: List<PlaylistEntity> = emptyList(),
    val sort: SortOption = SortOption.TITLE,
    val scanning: Boolean = false,
    val scanMessage: String? = null,
)

class LibraryViewModel : ViewModel() {
    private val repo = ServiceLocator.musicRepository
    private val playlists = ServiceLocator.playlistRepository

    private val _sort = MutableStateFlow(SortOption.TITLE)
    val sort: StateFlow<SortOption> = _sort.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val tracks: Flow<List<TrackEntity>> = _sort.flatMapLatest { repo.observeAll(it) }
    val folderCounts = repo.observeFolderCounts()
    val folders = repo.observeFolders()
    val albums = repo.observeAlbums()
    val artists = repo.observeArtists()
    val genres = repo.observeGenres()
    val playlistList = playlists.observePlaylists()

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching { ServiceLocator.settings.settings.first().defaultSort }.getOrNull()?.let { savedSort ->
                _sort.value = savedSort
            }
        }
        viewModelScope.launch {
            _sort.collect { currentSort ->
                _state.value = _state.value.copy(sort = currentSort)
            }
        }
        viewModelScope.launch {
            folderCounts.collect { folders -> _state.value = _state.value.copy(folders = folders) }
        }
        viewModelScope.launch {
            repo.observeAlbumCounts().collect { _state.value = _state.value.copy(albumCounts = it) }
        }
        viewModelScope.launch {
            repo.observeArtistCounts().collect { _state.value = _state.value.copy(artistCounts = it) }
        }
        viewModelScope.launch {
            repo.observeGenreCounts().collect { _state.value = _state.value.copy(genreCounts = it) }
        }
        viewModelScope.launch {
            playlistList.collect { _state.value = _state.value.copy(playlists = it) }
        }
    }

    fun setSort(option: SortOption) { _sort.value = option }

    fun folderTracks(folder: String) = repo.observeFolderTracks(folder)
    fun albumTracks(album: String) = repo.observeAlbumTracks(album)
    fun artistTracks(artist: String) = repo.observeArtistTracks(artist)
    fun genreTracks(genre: String) = repo.observeGenreTracks(genre)

    fun createPlaylist(name: String, onCreated: (Long) -> Unit) {
        viewModelScope.launch {
            runCatching { playlists.create(name) }
                .onSuccess { onCreated(it) }
        }
    }

    fun deletePlaylist(id: Long) { viewModelScope.launch { playlists.delete(id) } }
}
