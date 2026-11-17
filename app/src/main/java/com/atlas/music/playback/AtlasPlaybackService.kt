@file:Suppress("DEPRECATION")

package com.atlas.music.playback

import android.app.PendingIntent
import android.content.Intent
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.LibraryResult
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.exoplayer.ExoPlayer
import com.atlas.music.MainActivity
import com.atlas.music.R
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.ServiceLocator
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Background playback engine as a MediaLibraryService. Owns the ExoPlayer, the
 * media session, a small browsable library tree (Android Auto / lock-screen resume),
 * the boundary crossfade, gapless playlist behavior, and system audio effects.
 * UI talks to this only through a MediaController.
 */
class AtlasPlaybackService : MediaLibraryService() {

        @Volatile private var lastMediaItemId: String? = null
    companion object {
        /** Exposed so the UI can honestly say when the device has no EQ support. */
        val EffectsStatus = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
        /** True when 10-band DynamicsProcessing (AutoEq + Preamp + Limiter + Balance) attached cleanly. */
        val DynamicsStatus = kotlinx.coroutines.flow.MutableStateFlow<Boolean>(false)

        const val ACTION_SET_OUTPUT_DEVICE = "com.atlas.music.SET_OUTPUT_DEVICE"
        const val ACTION_SET_FOCUS_VOLUME = "com.atlas.music.SET_FOCUS_VOLUME"
        const val EXTRA_FOCUS_VOLUME = "focus_volume"
        const val ACTION_TOGGLE_FAVORITE = "com.atlas.music.TOGGLE_FAVORITE"
        const val EXTRA_DEVICE_ID = "device_id"

        private const val ROOT = "atlas_root"
        private const val RECENT = "recent"
        private const val FAVORITES = "favorites"
        private const val PLAYLISTS = "playlists"
        private const val PLAYLIST_PREFIX = "playlist:"
        private const val MAX_BROWSE = 50

        /** Sentinel meaning "clear the preferred device" (system-chosen route). */
        const val NULL_DEVICE = Int.MIN_VALUE
    }

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var loudness: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var dynamicsProcessing: DynamicsProcessing? = null

    @Volatile var audioEffectsAvailable: Boolean = true
        private set

    private var crossfadeJob: kotlinx.coroutines.Job? = null
    private var fadeState = FadeState.IDLE
    private enum class FadeState { IDLE, FADING_OUT, FADING_IN }
    private var focusVolume = 1f
    private var latestSettings: com.atlas.music.di.AtlasSettings = com.atlas.music.di.AtlasSettings()
    private var settingsJob: kotlinx.coroutines.Job? = null

    override fun onCreate() {
        super.onCreate()
        // Deterministic audio session: effects attach to this player only, never the global mix.
        val audioManager = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
        val generatedId = runCatching { audioManager.generateAudioSessionId() }.getOrDefault(0)
        val audioSessionId = if (generatedId != 0) generatedId else 0
        val sessionPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        if (audioSessionId != 0) {
            runCatching { sessionPlayer.setAudioSessionId(audioSessionId) }
        }
        // If we couldn't generate one, effects will attach to whatever the player picks.

        sessionPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    // Queue finished on its own — the outgoing track is still owed a play event
                    // (and a save; otherwise a kill now restores the listen position at 0:00).
                    lastMediaItemId?.toLongOrNull()?.let { trackId ->
                        scope.launch { ServiceLocator.musicRepository.recordPlay(trackId) }
                    }
                    scope.launch { saveQueueState() }
                }
            }

                        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // Flawless skip: bad / missing file → next playable, never dead-stop mid-queue.
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                    error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND) {
                    if (sessionPlayer.mediaItemCount > 1) {
                        sessionPlayer.seekToNextMediaItem()
                    }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // The outgoing item — credit it with the play when it ended on its own.
                // Previously the NEW item got the credit, which double-counted successors
                // and starved finished tracks (found in the polish pass).
                val outgoingId = lastMediaItemId
                lastMediaItemId = mediaItem?.mediaId
                scope.launch {
                    saveQueueState()
                    val id = mediaItem?.mediaId?.toLongOrNull()
                    val fav = id?.let { ServiceLocator.musicRepository.getTrack(it)?.isFavorite } ?: false
                    AtlasNotificationState.isFavorite = fav
                    AtlasNotificationState.trackId = id ?: -1L
                    runCatching { mediaLibrarySession?.let { onUpdateNotification(it, true) } }
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    if (outgoingId != null) {
                        outgoingId.toLongOrNull()?.let { trackId ->
                            scope.launch { ServiceLocator.musicRepository.recordPlay(trackId) }
                        }
                    }
                    if (latestSettings.sleepTimerEndOfTrack) {
                        sessionPlayer.pause()
                        sessionPlayer.seekTo(0L)
                        scope.launch { runCatching { ServiceLocator.settings.setSleepTimerEndOfTrack(false) } }
                        return
                    }
                }
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_SEEK
                ) {
                    // Crossfade: new track starts from silence and rises gently.
                    maybeStartFadeIn(sessionPlayer)
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) scope.launch { saveQueueState() }
            }
        })

        player = sessionPlayer
        setMediaNotificationProvider(AtlasNotificationProvider(this))
        mediaLibrarySession = MediaLibrarySession.Builder(this, sessionPlayer, libraryCallback)
            .setBitmapLoader(AtlasBitmapLoader(this))
            .setSessionActivity(sessionActivity())
            .build()
        attachAudioEffects(sessionPlayer, audioSessionId)
        settingsJob = scope.launch {
            ServiceLocator.settings.settings.collect { latestSettings = it }
        }
        scope.launch { applyAudioSettings(sessionPlayer) }
        startCrossfadeWatcher(sessionPlayer)
        scope.launch { restoreSavedQueue(sessionPlayer) }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep music playing when the app is swiped away if something is playing.
        val current = player
        if (current == null || !current.isPlaying) {
            runCatching { kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.withTimeout(750) { saveQueueState() } } }
            super.onTaskRemoved(rootIntent)
        }
    }

    override fun onDestroy() {
        runCatching { kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { kotlinx.coroutines.withTimeout(750) { saveQueueState() } } }
        settingsJob?.cancel()
        crossfadeJob?.cancel()
        equalizer?.release(); bassBoost?.release(); loudness?.release()
        virtualizer?.release(); dynamicsProcessing?.release()
        equalizer = null; bassBoost = null; loudness = null
        virtualizer = null; dynamicsProcessing = null
        mediaLibrarySession?.run {
            player.release()
            release()
        }
        mediaLibrarySession = null
        player = null
        scope.cancel()
        super.onDestroy()
    }

    private fun sessionActivity(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    // ---------- Media library tree (Android Auto / lock-screen resume) ----------

    private fun <T> completeAsync(block: suspend () -> T): ListenableFuture<T> {
        val future = com.google.common.util.concurrent.SettableFuture.create<T>()
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                future.set(block())
            } catch (t: Throwable) {
                future.setException(t)
            }
        }
        return future
    }

    private val libraryCallback = object : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(getString(R.string.app_name))
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .build()
                )
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(root, params)
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return completeAsync {
                val items = buildChildren(parentId, params)
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return completeAsync {
                val id = mediaId.toLongOrNull()
                val track = id?.let { ServiceLocator.musicRepository.getTrack(it) }
                if (track != null) {
                    LibraryResult.ofItem(playableItem(track), null)
                } else {
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                }
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return completeAsync {
                val results = ServiceLocator.musicRepository.search(query).tracks.take(pageSize)
                LibraryResult.ofItemList(
                    ImmutableList.copyOf(results.map(::playableItem)),
                    params,
                )
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: MediaLibraryService.LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> =
            Futures.immediateFuture(LibraryResult.ofVoid(params))

        // onSetMediaItems left as the default: browse-tree items already carry URIs.

        override fun onCustomCommand(
            session: MediaSession,
            controllerInfo: MediaSession.ControllerInfo,
            command: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            if (command.customAction == ACTION_SET_OUTPUT_DEVICE) {
                val deviceId = args.getInt(EXTRA_DEVICE_ID, NULL_DEVICE)
                val device = if (deviceId == NULL_DEVICE) null else findOutputDeviceById(deviceId)
                player?.setPreferredAudioDevice(device)
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (command.customAction == ACTION_SET_FOCUS_VOLUME) {
                focusVolume = args.getFloat(EXTRA_FOCUS_VOLUME, 1f).coerceIn(0f, 1f)
                player?.volume = effectiveVolume()
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
            if (command.customAction == ACTION_TOGGLE_FAVORITE) {
                val currentId = player?.currentMediaItem?.mediaId?.toLongOrNull()
                if (currentId != null) {
                    scope.launch {
                        val track = ServiceLocator.musicRepository.getTrack(currentId)
                        if (track != null) {
                            val next = !track.isFavorite
                            ServiceLocator.musicRepository.setFavorite(currentId, next)
                            AtlasNotificationState.isFavorite = next
                            AtlasNotificationState.trackId = currentId
                            runCatching { mediaLibrarySession?.let { onUpdateNotification(it, true) } }
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            }
            return super.onCustomCommand(session, controllerInfo, command, args)
        }

    }

    private suspend fun buildChildren(parentId: String, params: MediaLibraryService.LibraryParams?): List<MediaItem> {
        val recentParams = params?.isRecent == true
        return when (parentId) {
            ROOT -> {
                if (recentParams) {
                    recentlyPlayedItems(MAX_BROWSE)
                } else {
                    listOf(
                        browsableItem(RECENT, "Recently played"),
                        browsableItem(FAVORITES, "Favorites"),
                        browsableItem(PLAYLISTS, "Playlists"),
                    )
                }
            }
            RECENT -> recentlyPlayedItems(MAX_BROWSE)
            FAVORITES -> ServiceLocator.musicRepository.observeFavorites().first().take(MAX_BROWSE).map(::playableItem)
            PLAYLISTS -> ServiceLocator.database.playlistDao().observePlaylists().first()
                .map { playlist ->
                    MediaItem.Builder()
                        .setMediaId(PLAYLIST_PREFIX + playlist.id)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(playlist.name)
                                .setIsBrowsable(true)
                                .setIsPlayable(false)
                                .build()
                        )
                        .build()
                }
            else -> {
                if (parentId.startsWith(PLAYLIST_PREFIX)) {
                    val playlistId = parentId.removePrefix(PLAYLIST_PREFIX).toLongOrNull()
                    if (playlistId != null) {
                        val ids = ServiceLocator.database.playlistDao().observePlaylistTrackIds(playlistId).first()
                        ServiceLocator.musicRepository.getTracks(ids)
                            .let { tracks -> val index = ids.withIndex().associate { it.value to it.index }; tracks.sortedBy { index[it.id] ?: Int.MAX_VALUE } }
                            .map(::playableItem)
                    } else emptyList()
                } else emptyList()
            }
        }
    }

    private suspend fun recentlyPlayedItems(limit: Int): List<MediaItem> =
        ServiceLocator.musicRepository.observeRecentlyPlayed().first().take(limit).map(::playableItem)

    private fun browsableItem(id: String, title: String): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setIsBrowsable(true)
                .setIsPlayable(false)
                .build()
        )
        .build()

    private fun playableItem(track: TrackEntity): MediaItem {
        val item = MediaItemFactory.fromTrack(track)
        return item.buildUpon()
            .setMediaMetadata(
                item.mediaMetadata.buildUpon()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun findOutputDeviceById(deviceId: Int): android.media.AudioDeviceInfo? {
        val audioManager = getSystemService(android.content.Context.AUDIO_SERVICE) as android.media.AudioManager
        return audioManager.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
            .firstOrNull { it.id == deviceId }
    }

    private fun effectiveVolume(): Float = focusVolume.coerceIn(0f, 1f)

    // ---------- Equalizer / DynamicsProcessing / Virtualizer / Bass / Loudness ----------

    private val isoBandCutoffsHz = floatArrayOf(
        44f, 88f, 177f, 355f, 710f, 1420f, 2840f, 5680f, 11360f, 22000f,
    )

    private fun attachAudioEffects(exoPlayer: ExoPlayer, audioSessionId: Int) {
        val effectiveId = if (audioSessionId != 0) audioSessionId else exoPlayer.audioSessionId
        var attachedAny = false

        // Prefer 10-band DynamicsProcessing (AutoEq, preamp headroom, L/R balance, peak limiter).
        runCatching {
            val cfg = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                false, 0,
                false, 0,
                true, com.atlas.music.util.AudioDspMath.BAND_COUNT,
                true,
            ).build()
            DynamicsProcessing(0, effectiveId, cfg).apply {
                enabled = false
                for (i in 0 until com.atlas.music.util.AudioDspMath.BAND_COUNT) {
                    setPostEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, isoBandCutoffsHz[i], 0f))
                }
                setLimiterAllChannelsTo(
                    DynamicsProcessing.Limiter(true, true, 0, 2.0f, 60.0f, 10.0f, -0.5f, 0f)
                )
            }
        }.onSuccess {
            dynamicsProcessing = it
            DynamicsStatus.value = true
            attachedAny = true
        }.onFailure { e ->
            android.util.Log.w("AtlasAudio", "DynamicsProcessing unavailable, falling back to Equalizer: ${e.message}")
            DynamicsStatus.value = false
        }

        // Legacy Equalizer as fallback (when DynamicsProcessing isn't attached).
        if (dynamicsProcessing == null) {
            runCatching {
                Equalizer(0, effectiveId).apply { enabled = false }
            }.onSuccess {
                equalizer = it
                attachedAny = true
            }
        }

        runCatching { BassBoost(0, effectiveId).apply { enabled = false } }
            .onSuccess { bassBoost = it; attachedAny = true }
        runCatching { LoudnessEnhancer(effectiveId).apply { enabled = false } }
            .onSuccess { loudness = it; attachedAny = true }
        runCatching { Virtualizer(0, effectiveId).apply { enabled = false } }
            .onSuccess { virtualizer = it }

        audioEffectsAvailable = attachedAny
        EffectsStatus.value = attachedAny
    }

    private suspend fun applyAudioSettings(exoPlayer: ExoPlayer) {
        combine(
            ServiceLocator.settings.settings,
            ServiceLocator.settings.equalizerBands,
        ) { settings, gains -> settings to gains }.collect { (settings, gains) ->
            val masterActive = settings.equalizerEnabled
            val effectiveCurve = com.atlas.music.util.AudioDspMath.computeEffectiveCurve(
                profileId = settings.headphoneProfileId,
                userBandsMb = gains,
                preampDb = settings.preampDb,
                soundCheckWarmth = settings.soundCheckWarmth,
                soundCheckPunch = settings.soundCheckPunch,
                autoHeadroom = settings.autoHeadroomEnabled,
            )
            val (leftDb, rightDb) = com.atlas.music.util.AudioDspMath.channelGainsDb(
                effectivePreampDb = effectiveCurve.effectivePreampDb,
                balance = settings.channelBalance,
            )

            dynamicsProcessing?.let { dp ->
                runCatching {
                    val needDp = masterActive || kotlin.math.abs(settings.channelBalance) > 0.01f || kotlin.math.abs(settings.preampDb) > 0.1f
                    dp.enabled = needDp
                    if (needDp) {
                        dp.setInputGainbyChannel(0, leftDb)
                        dp.setInputGainbyChannel(1, rightDb)
                        val gains10 = if (masterActive) effectiveCurve.totalBandsDb10 else List(10) { 0f }
                        for (i in 0 until com.atlas.music.util.AudioDspMath.BAND_COUNT) {
                            dp.setPostEqBandAllChannelsTo(
                                i,
                                DynamicsProcessing.EqBand(true, isoBandCutoffsHz[i], gains10[i]),
                            )
                        }
                        dp.setLimiterAllChannelsTo(
                            DynamicsProcessing.Limiter(
                                true,
                                settings.limiterEnabled,
                                0,
                                2.0f,
                                60.0f,
                                10.0f,
                                -0.5f,
                                0f,
                            )
                        )
                    }
                }.onFailure { android.util.Log.w("AtlasAudio", "dp update failed: ${it.message}") }
            }

            equalizer?.let { eq ->
                runCatching {
                    if (masterActive) {
                        eq.enabled = true
                        val range = eq.bandLevelRange
                        val minMb = range[0].toInt()
                        val maxMb = range[1].toInt()
                        val bands = eq.numberOfBands.toInt()
                        val mapped = com.atlas.music.util.AudioDspMath.resample10BandDbToLegacyMb(
                            effectiveCurve.totalBandsDb10, bands, minMb, maxMb,
                        )
                        for (i in 0 until bands) {
                            eq.setBandLevel(i.toShort(), mapped[i].toShort())
                        }
                    } else {
                        eq.enabled = false
                    }
                }
            }

            bassBoost?.let { bb ->
                runCatching {
                    if (masterActive && settings.bassBoostEnabled && bb.strengthSupported) {
                        bb.enabled = true
                        bb.setStrength((settings.bassBoostStrength * 10).coerceIn(0, 1000).toShort())
                    } else {
                        bb.enabled = false
                    }
                }
            }

            virtualizer?.let { virt ->
                runCatching {
                    if (masterActive && settings.virtualizerStrength > 0 && virt.strengthSupported) {
                        virt.enabled = true
                        virt.setStrength((settings.virtualizerStrength * 10).coerceIn(0, 1000).toShort())
                    } else {
                        virt.enabled = false
                    }
                }
            }

            loudness?.let { le ->
                runCatching {
                    if (masterActive && settings.normalizationEnabled) {
                        le.enabled = true
                        le.setTargetGain(settings.loudnessGainMb)
                    } else {
                        le.enabled = false
                    }
                }
            }
        }
    }

    // ---------- Gapless + boundary crossfade ----------

    private fun startCrossfadeWatcher(exoPlayer: ExoPlayer) {
        crossfadeJob?.cancel()
        crossfadeJob = scope.launch {
            while (isActive) {
                delay(200)
                val settings = latestSettings
                val duration = exoPlayer.duration.takeIf { it > 0 }
                val remaining = if (duration != null) duration - exoPlayer.currentPosition else Long.MAX_VALUE
                // Sleep timer "end of track": works regardless of crossfade or queue position.
                if (settings.sleepTimerEndOfTrack && exoPlayer.isPlaying && remaining in 1..600) {
                    exoPlayer.pause()
                    exoPlayer.volume = effectiveVolume()
                    fadeState = FadeState.IDLE
                    scope.launch { runCatching { ServiceLocator.settings.setSleepTimerEndOfTrack(false) } }
                    continue
                }
                if (!settings.crossfadeEnabled) {
                    if (exoPlayer.volume != effectiveVolume()) exoPlayer.volume = effectiveVolume()
                    fadeState = FadeState.IDLE
                    continue
                }
                if (duration == null) continue
                val fadeMs = settings.crossfadeSeconds * 1000L
                val hasNextTrack = (exoPlayer.repeatMode != Player.REPEAT_MODE_ONE &&
                    exoPlayer.currentMediaItemIndex < exoPlayer.mediaItemCount - 1) ||
                    exoPlayer.repeatMode == Player.REPEAT_MODE_ALL
                if (exoPlayer.isPlaying && remaining in 1..fadeMs && hasNextTrack) {
                    fadeState = FadeState.FADING_OUT
                    val fraction = remaining.toFloat() / fadeMs.toFloat()
                    exoPlayer.volume = (fraction.coerceIn(0f, 1f) * effectiveVolume())
                } else if (fadeState == FadeState.FADING_OUT && remaining > fadeMs) {
                    exoPlayer.volume = effectiveVolume()
                    fadeState = FadeState.IDLE
                }
            }
        }
    }

    private fun maybeStartFadeIn(exoPlayer: ExoPlayer) {
        scope.launch {
            val settings = latestSettings
            if (!settings.crossfadeEnabled) {
                exoPlayer.volume = effectiveVolume()
                return@launch
            }
            val fadeMs = settings.crossfadeSeconds * 1000L
            fadeState = FadeState.FADING_IN
            exoPlayer.volume = 0f
            val steps = (fadeMs / 100L).coerceAtLeast(1)
            for (i in 1..steps) {
                delay(100)
                if (fadeState != FadeState.FADING_IN) return@launch
                exoPlayer.volume = (i.toFloat() / steps).coerceIn(0f, 1f) * effectiveVolume()
            }
            fadeState = FadeState.IDLE
        }
    }

    // ---------- Queue save / restore ----------

    private suspend fun saveQueueState() {
        val exoPlayer = player ?: return
        val ids = (0 until exoPlayer.mediaItemCount)
            .mapNotNull { index -> exoPlayer.getMediaItemAt(index).mediaId.toLongOrNull() }
        if (ids.isEmpty()) return
        ServiceLocator.settings.saveQueue(
            ids = ids,
            index = exoPlayer.currentMediaItemIndex.coerceAtLeast(0),
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
            hint = exoPlayer.currentMediaItem?.mediaMetadata?.title?.toString(),
        )
    }

    private suspend fun restoreSavedQueue(exoPlayer: ExoPlayer) {
        val saved = ServiceLocator.settings.loadQueue() ?: return
        val tracks = ServiceLocator.musicRepository.getTracks(saved.ids)
        if (tracks.isEmpty()) return
        val trackIndex = tracks.associateBy { it.id }
        val items = saved.ids.mapNotNull { id -> trackIndex[id]?.let(MediaItemFactory::fromTrack) }
        if (items.isEmpty()) return
        val restoredIndex = saved.index.coerceIn(0, items.lastIndex)
        val restoredTrackId = saved.ids.getOrNull(restoredIndex)
        // Clamp saved position into the restored track: a position saved at/past the end of a
        // short sample (or after a crash at 00:00) yields a dead restore that never plays.
        val restoredDuration = restoredTrackId?.let { trackIndex[it]?.durationMs ?: 0L } ?: 0L
        val safePosition = if (restoredDuration > 0) saved.positionMs.coerceIn(0L, restoredDuration - 500L) else 0L
        exoPlayer.setMediaItems(items, restoredIndex, safePosition)
        // Seed notification heart state from restored track
        val restoredId = saved.ids.getOrNull(restoredIndex)
        AtlasNotificationState.trackId = restoredId ?: -1L
        AtlasNotificationState.isFavorite = restoredId?.let { trackIndex[it]?.isFavorite } ?: false
        exoPlayer.prepare()
        exoPlayer.pause() // restoration never autoplays
    }
}
