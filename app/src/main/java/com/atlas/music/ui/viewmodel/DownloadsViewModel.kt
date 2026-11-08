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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DownloadsUiState(
    val url: String = "",
    val previewLoading: Boolean = false,
    val preview: YoutubePreview? = null,
    val previewError: String? = null,
    val format: String = "opus",
    val tasks: List<DownloadTaskEntity> = emptyList(),
    val ytdlpVersion: String = "…",
    val ytdlpAvailable: Boolean = true,
    val checkingUpdate: Boolean = false,
    val updateMessage: String? = null,
    val updating: Boolean = false,
    val updateProgressMessage: String? = null,
    val consentAccepted: Boolean = false,
)

class DownloadsViewModel : ViewModel() {
    private val manager = ServiceLocator.downloadManager
    private val updater = ServiceLocator.ytdlpUpdater

    private val _state = MutableStateFlow(DownloadsUiState(ytdlpVersion = manager.installedYtdlpVersion(), ytdlpAvailable = manager.isDownloaderAvailable()))
    val state: StateFlow<DownloadsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            manager.tasks.collect { tasks -> _state.value = _state.value.copy(tasks = tasks) }
        }
    }

    fun onUrlChange(value: String) {
        _state.value = _state.value.copy(url = value, preview = null, previewError = null)
    }

    fun setFormat(format: String) { _state.value = _state.value.copy(format = format) }
    fun setConsentAccepted(accepted: Boolean) { _state.value = _state.value.copy(consentAccepted = accepted) }

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
        viewModelScope.launch {
            runCatching {
                manager.enqueue(s.url, preview.title, preview.uploader, preview.thumbnailUrl, s.format)
            }.onSuccess {
                _state.value = _state.value.copy(url = "", preview = null, consentAccepted = false)
            }.onFailure { e ->
                _state.value = _state.value.copy(previewError = e.message ?: "Couldn't start the download.")
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
