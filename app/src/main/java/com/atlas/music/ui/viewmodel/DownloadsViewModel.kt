package com.atlas.music.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.atlas.music.data.db.DownloadStatus
import com.atlas.music.data.db.DownloadTaskEntity
import com.atlas.music.di.ServiceLocator
import com.atlas.music.downloads.FormatOption
import com.atlas.music.downloads.UpdateResult
import com.atlas.music.downloads.YoutubePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadsUiState(
    val url: String = "",
    val previewLoading: Boolean = false,
    val preview: YoutubePreview? = null,
    val previewError: String? = null,
    val format: String = "opus",
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val searchProvider: String = "youtube",
    val searching: Boolean = false,
    val searchError: String? = null,
    val searchResults: List<com.atlas.music.downloads.SearchResultItem> = emptyList(),
    val ytdlpVersion: String = "…",
    val ytdlpAvailable: Boolean = true,
    val checkingUpdate: Boolean = false,
    val updateMessage: String? = null,
    val updating: Boolean = false,
    val updateProgressMessage: String? = null,
    val consentAccepted: Boolean = false,
    /** Watch URLs with a list= param: false = just this video, true = the whole playlist. */
    val playlistMode: Boolean = false,
    val enqueueMessage: String? = null,
)

class DownloadsViewModel : ViewModel() {
    private val manager = ServiceLocator.downloadManager
    private val updater = ServiceLocator.ytdlpUpdater

    private val _state = MutableStateFlow(DownloadsUiState(ytdlpVersion = manager.installedYtdlpVersion(), ytdlpAvailable = manager.isDownloaderAvailable()))
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val defaultFmt = runCatching { ServiceLocator.settings.settings.first().downloadFormat }.getOrDefault("opus")
            if (defaultFmt in listOf("opus", "m4a", "best")) {
                _state.value = _state.value.copy(format = defaultFmt)
            }
            withContext(Dispatchers.IO) { runCatching { manager.recoverInterrupted() } }
        }
        viewModelScope.launch {
            manager.tasks.collect { tasks -> _state.value = _state.value.copy(tasks = tasks) }
        }
    }

    fun onUrlChange(value: String) {
        _state.value = _state.value.copy(url = value, preview = null, previewError = null, enqueueMessage = null, searchError = null)
        // Typing clears stale results, but never while a search is in flight
        if (!_state.value.searching) _state.value = _state.value.copy(searchResults = emptyList())
    }

    fun setSearchProvider(provider: String) {
        _state.value = _state.value.copy(searchProvider = provider, searchResults = emptyList(), searchError = null)
    }

    /** Search (plain query) or analyze-link (http(s) URL) — same field, same button, magic by content. */
    fun submitSearchOrUrl() {
        val raw = _state.value.url.trim()
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            fetchPreview()
            return
        }
        if (raw.isBlank()) { _state.value = _state.value.copy(searchError = "Type an artist/song, or paste a link."); return }
        _state.value = _state.value.copy(searching = true, searchError = null, searchResults = emptyList(), previewError = null, preview = null)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { manager.search(raw, _state.value.searchProvider) }
            _state.value = _state.value.copy(
                searching = false,
                searchError = result.error,
                searchResults = result.entries,
            )
        }
    }

    fun startDownloadFromResult(item: com.atlas.music.downloads.SearchResultItem) {
        val s = _state.value
        if (!s.consentAccepted) {
            _state.value = s.copy(searchError = "Please confirm you're authorized to download this content.")
            return
        }
        viewModelScope.launch {
            runCatching { manager.enqueue(item.url, item.title, item.uploader, item.thumbnailUrl, s.format) }
                .onSuccess {
                    _state.value = _state.value.copy(enqueueMessage = "Queued “${item.title.take(60)}”.", searchError = null)
                }.onFailure { e ->
                    _state.value = _state.value.copy(searchError = e.message ?: "Couldn't queue ${item.title.take(40)}.")
                }
        }
    }

    fun setFormat(format: String) { _state.value = _state.value.copy(format = format) }
    fun setConsentAccepted(accepted: Boolean) { _state.value = _state.value.copy(consentAccepted = accepted) }
    fun setPlaylistMode(enabled: Boolean) { _state.value = _state.value.copy(playlistMode = enabled) }

    fun fetchPreview() {
        val url = _state.value.url.trim()
        if (url.isBlank()) { _state.value = _state.value.copy(previewError = "Paste a YouTube link first."); return }
        _state.value = _state.value.copy(previewLoading = true, previewError = null, preview = null)
        viewModelScope.launch {
            val preview = withContext(Dispatchers.IO) { manager.preview(url) }
            _state.value = _state.value.copy(
                previewLoading = false,
                preview = if (preview.ok) preview else null,
                previewError = preview.error,
                playlistMode = preview.ok && preview.isPlaylist,
                enqueueMessage = null,
            )
        }
    }

    fun startDownload() {
        val s = _state.value
        val preview = s.preview ?: return
        if (!s.consentAccepted) {
            _state.value = s.copy(previewError = "Please confirm you're authorized to download this content.")
            return
        }
        val wholePlaylist = preview.playlistOnly || (preview.isPlaylist && s.playlistMode)
        viewModelScope.launch {
            if (wholePlaylist) {
                runCatching {
                    manager.enqueuePlaylist(s.url, s.format)
                }.onSuccess { result ->
                    val queued = result.enqueued
                    val extras = buildList {
                        if (result.duplicates > 0) add("${result.duplicates} already in your downloads")
                        if (result.failed > 0) add("${result.failed} couldn't be read")
                    }.joinToString(", ").let { if (it.isNotBlank()) " ($it)" else "" }
                    _state.value = _state.value.copy(
                        url = "",
                        preview = null,
                        consentAccepted = false,
                        enqueueMessage = if (queued > 0) "Queued $queued video${if (queued == 1) "" else "s"} from the playlist$extras."
                        else "Nothing new to queue$extras.",
                    )
                }.onFailure { e ->
                    _state.value = _state.value.copy(previewError = e.message ?: "Couldn't start the playlist download.")
                }
            } else {
                runCatching {
                    manager.enqueue(s.url, preview.title, preview.uploader, preview.thumbnailUrl, s.format)
                }.onSuccess {
                    _state.value = _state.value.copy(url = "", preview = null, consentAccepted = false, enqueueMessage = null)
                }.onFailure { e ->
                    _state.value = _state.value.copy(previewError = e.message ?: "Couldn't start the download.")
                }
            }
        }
    }

    fun cancel(taskId: Long) { viewModelScope.launch { manager.cancel(taskId) } }
    fun retry(taskId: Long) { viewModelScope.launch { manager.retry(taskId) } }
    fun delete(taskId: Long) { viewModelScope.launch { manager.deleteTask(taskId) } }
    fun clearCompleted() { viewModelScope.launch { manager.clearCompleted() } }
    fun clearHistory() { viewModelScope.launch { manager.clearHistory() } }

    fun checkYtdlpUpdate() {
        _state.value = _state.value.copy(checkingUpdate = true, updateMessage = null)
        viewModelScope.launch {
            val result = updater.checkForUpdate()
            _state.value = _state.value.copy(
                checkingUpdate = false,
                updateMessage = result.fold(
                    onSuccess = { check ->
                        if (check.updateAvailable) "yt-dlp ${check.latestVersion} is available (you have ${check.currentVersion})."
                        else "yt-dlp is up to date (${check.currentVersion})."
                    },
                    onFailure = { e -> e.message ?: "Couldn't check for updates. You may be offline — downloads still work." },
                ),
            )
        }
    }

    fun applyYtdlpUpdate() {
        _state.value = _state.value.copy(updating = true, updateProgressMessage = "Downloading yt-dlp from PyPI…")
        viewModelScope.launch {
            val result = updater.applyUpdate()
            val message = when (result) {
                is UpdateResult.Success -> "yt-dlp updated to ${result.version}."
                is UpdateResult.Failed -> result.message
            }
            _state.value = _state.value.copy(
                updating = false,
                updateProgressMessage = message,
                ytdlpVersion = manager.installedYtdlpVersion(),
            )
        }
    }

    fun restoreBundledYtdlp() {
        viewModelScope.launch {
            updater.restoreBundled()
            _state.value = _state.value.copy(updateProgressMessage = "Restored the bundled yt-dlp ${manager.installedYtdlpVersion()}.", ytdlpVersion = manager.installedYtdlpVersion())
        }
    }
}
