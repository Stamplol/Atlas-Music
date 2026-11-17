package com.atlas.music.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PlayerUiState(
    val connected: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrackId: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: Uri? = null,
    val durationMs: Long = 0,
    val positionMs: Long = 0,
    val bufferedMs: Long = 0,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val playbackSpeed: Float = 1f,
    val hasQueue: Boolean = false,
    val lastError: String? = null,
)

/**
 * UI-side connection to the playback service.
 * Fixes the "tap does nothing" bug: if the controller isn't connected yet,
 * play requests are queued and flushed on connect instead of dropped.
 */
class PlaybackConnection(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _sleepTimerEndAt = MutableStateFlow<Long?>(null)
    val sleepTimerEndAt: StateFlow<Long?> = _sleepTimerEndAt.asStateFlow()

    var controller: MediaController? = null
        private set

    private var connectFuture: ListenableFuture<MediaController>? = null
    private var pendingPlay: Pair<List<TrackEntity>, Int>? = null

    fun connect() {
        if (controller != null) return
        if (connectFuture != null && !connectFuture!!.isDone) return
        val token = SessionToken(context, ComponentName(context, AtlasPlaybackService::class.java))
        connectFuture = MediaController.Builder(context, token)
            .setListener(
                object : MediaController.Listener {
                    override fun onDisconnected(controller: MediaController) {
                        this@PlaybackConnection.controller = null
                        _state.value = _state.value.copy(connected = false, isPlaying = false)
                    }
                },
            )
            .buildAsync()
        connectFuture?.addListener(
            {
                val ctrl = runCatching { connectFuture?.get() }.getOrNull()
                if (ctrl != null) {
                    controller = ctrl
                    ctrl.addListener(playerListener)
                    _state.value = _state.value.copy(connected = true)
                    syncFromPlayer()
                    maybeFireSleepTimer()
                    restorePersistedSleepTimer()
                    pendingPlay?.let { (tracks, idx) ->
                        pendingPlay = null
                        playTracksInternal(tracks, idx)
                    }
                }
            },
            { it.run() },
        )
    }

    fun disconnect() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        connectFuture?.cancel(true)
        connectFuture = null
        _state.value = PlayerUiState(connected = false)
        scope.cancel()
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = syncFromPlayer()
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncFromPlayer()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { syncFromPlayer(); _state.value = _state.value.copy(lastError = null) }
        override fun onRepeatModeChanged(repeatMode: Int) = syncFromPlayer()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncFromPlayer()
        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = syncFromPlayer()
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _state.value = _state.value.copy(
                isPlaying = false,
                isBuffering = false,
                lastError = error.localizedMessage ?: error.errorCodeName,
            )
        }
    }

    private var lastWidgetMs: Long = 0

    private fun syncFromPlayer() {
        val ctrl = controller ?: return
        val item = ctrl.currentMediaItem
        val duration = ctrl.duration
        _state.value = PlayerUiState(
            connected = true,
            isPlaying = ctrl.isPlaying,
            isBuffering = ctrl.playbackState == Player.STATE_BUFFERING,
            currentTrackId = item?.mediaId?.toLongOrNull(),
            title = item?.mediaMetadata?.title?.toString().orEmpty(),
            artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
            album = item?.mediaMetadata?.albumTitle?.toString().orEmpty(),
            artworkUri = item?.mediaMetadata?.artworkUri,
            durationMs = if (duration == C.TIME_UNSET) 0 else duration,
            positionMs = ctrl.currentPosition,
            bufferedMs = ctrl.bufferedPosition,
            repeatMode = ctrl.repeatMode,
            shuffleEnabled = ctrl.shuffleModeEnabled,
            playbackSpeed = ctrl.playbackParameters.speed,
            hasQueue = ctrl.mediaItemCount > 0,
            lastError = if (ctrl.isPlaying) null else _state.value.lastError,
        )
        val now = System.currentTimeMillis()
        if (now - lastWidgetMs > 1000) {
            lastWidgetMs = now
            runCatching { com.atlas.music.widget.AtlasWidgetProvider.updateAll(context) }
            runCatching { com.atlas.music.widget.AtlasSmallWidgetProvider.updateAll(context) }
            runCatching { com.atlas.music.widget.AtlasLargeWidgetProvider.updateAll(context) }
            runCatching { com.atlas.music.widget.StatsWidgetProvider.updateAll(context) }
            runCatching { com.atlas.music.widget.SleepWidgetProvider.updateAll(context) }
        }
    }

    fun tickPosition() {
        val ctrl = controller ?: return
        _state.value = _state.value.copy(
            positionMs = ctrl.currentPosition,
            bufferedMs = ctrl.bufferedPosition,
            hasQueue = ctrl.mediaItemCount > 0,
        )
        maybeFireSleepTimer()
    }

    fun play() {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        ctrl.play()
    }

    fun pause() { controller?.pause() }

    fun togglePlayPause() {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
    }

    fun skipNext() { controller?.seekToNextMediaItem() }

    fun skipPrevious() {
        val ctrl = controller ?: return
        if (ctrl.currentPosition > 3000) ctrl.seekTo(0) else ctrl.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }

    fun cycleRepeatMode() {
        val ctrl = controller ?: return
        ctrl.repeatMode = when (ctrl.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    /** Pomodoro focus duck: lower music while focusing, restore on break. */
    fun setFocusVolume(volume: Float) {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        val command = SessionCommand(AtlasPlaybackService.ACTION_SET_FOCUS_VOLUME, Bundle.EMPTY)
        val args = Bundle().apply { putFloat(AtlasPlaybackService.EXTRA_FOCUS_VOLUME, volume.coerceIn(0f, 1f)) }
        ctrl.sendCustomCommand(command, args)
    }

    fun setPlaybackSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed.coerceIn(0.5f, 2.0f))
    }

    fun playTracks(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        if (controller == null) {
            pendingPlay = tracks to startIndex
            connect()
            return
        }
        playTracksInternal(tracks, startIndex)
    }

    private fun playTracksInternal(tracks: List<TrackEntity>, startIndex: Int) {
        val ctrl = controller ?: return
        if (tracks.isEmpty()) return
        val items = MediaItemFactory.fromTracks(tracks)
        ctrl.setMediaItems(items, startIndex.coerceIn(0, items.lastIndex), 0L)
        _state.value = _state.value.copy(lastError = null)
        ctrl.play()
    }

    fun addToQueue(tracks: List<TrackEntity>) {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        ctrl.addMediaItems(MediaItemFactory.fromTracks(tracks))
    }

    fun playNext(tracks: List<TrackEntity>) {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        val index = (ctrl.currentMediaItemIndex + 1).coerceAtLeast(0)
        ctrl.addMediaItems(index, MediaItemFactory.fromTracks(tracks))
    }

    fun removeQueueItem(index: Int) {
        val ctrl = controller ?: return
        if (index in 0 until ctrl.mediaItemCount) ctrl.removeMediaItem(index)
    }

    fun moveQueueItem(from: Int, to: Int) {
        val ctrl = controller ?: return
        if (from in 0 until ctrl.mediaItemCount && to in 0 until ctrl.mediaItemCount) {
            ctrl.moveMediaItem(from, to)
        }
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        scope.launch { runCatching { ServiceLocator.settings.clearSavedQueue() } }
    }

    fun queueTrackIds(): List<Long> {
        val ctrl = controller ?: return emptyList()
        return (0 until ctrl.mediaItemCount).mapNotNull { ctrl.getMediaItemAt(it).mediaId.toLongOrNull() }
    }

    fun queueSize(): Int = controller?.mediaItemCount ?: 0

    fun setPreferredOutputDevice(deviceId: Int?) {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        val command = SessionCommand(AtlasPlaybackService.ACTION_SET_OUTPUT_DEVICE, Bundle.EMPTY)
        val args = Bundle().apply {
            putInt(AtlasPlaybackService.EXTRA_DEVICE_ID, deviceId ?: AtlasPlaybackService.NULL_DEVICE)
        }
        ctrl.sendCustomCommand(command, args)
    }

    fun playExternalUri(uri: Uri, title: String) {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        val item = MediaItem.Builder()
            .setUri(uri)
            .setMediaId("external:$uri")
            .setMediaMetadata(androidx.media3.common.MediaMetadata.Builder().setTitle(title).build())
            .build()
        ctrl.setMediaItems(listOf(item), 0, 0)
        ctrl.play()
    }

    private fun restorePersistedSleepTimer() {
        scope.launch {
            val saved = runCatching { ServiceLocator.settings.sleepTimerSaved() }.getOrDefault(0L)
            if (saved > System.currentTimeMillis()) _sleepTimerEndAt.value = saved
            else if (saved > 0) ServiceLocator.settings.clearSleepTimerSaved()
        }
    }

    fun toggleFavoriteCurrent() {
        val ctrl = controller
        if (ctrl == null) { connect(); return }
        val command = SessionCommand(AtlasPlaybackService.ACTION_TOGGLE_FAVORITE, Bundle.EMPTY)
        ctrl.sendCustomCommand(command, Bundle.EMPTY)
    }

    fun startSleepTimer(minutes: Int) {
        _sleepTimerEndAt.value = System.currentTimeMillis() + minutes * 60_000L
        val end = _sleepTimerEndAt.value
        scope.launch { end?.let { runCatching { ServiceLocator.settings.saveSleepTimerEndAt(it) } } }
    }

    fun clearSleepTimer() {
        _sleepTimerEndAt.value = null
        scope.launch { runCatching { ServiceLocator.settings.clearSleepTimerSaved() } }
    }

    private fun maybeFireSleepTimer() {
        val end = _sleepTimerEndAt.value ?: return
        if (System.currentTimeMillis() >= end) {
            controller?.pause()
            _sleepTimerEndAt.value = null
            scope.launch { runCatching { ServiceLocator.settings.clearSleepTimerSaved() } }
        }
    }
}
