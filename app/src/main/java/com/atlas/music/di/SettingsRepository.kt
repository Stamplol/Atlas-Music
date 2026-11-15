package com.atlas.music.di

import android.content.Context
import android.content.Intent

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.atlas.music.util.ArtworkStyle
import com.atlas.music.util.SortOption
import com.atlas.music.util.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "atlas_settings")

class SettingsRepository(private val context: Context) {
    private val store = context.settingsDataStore

    private object K {
        val THEME = stringPreferencesKey("theme_mode")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val ARTWORK = stringPreferencesKey("artwork_style")
        val SORT = stringPreferencesKey("default_sort")
        val GAPLESS = booleanPreferencesKey("gapless_enabled")
        val CROSSFADE = booleanPreferencesKey("crossfade_enabled")
        val CROSSFADE_SECS = intPreferencesKey("crossfade_seconds")
        val EQ = booleanPreferencesKey("equalizer_enabled")
        val BASS = booleanPreferencesKey("bass_boost_enabled")
        val BASS_STRENGTH = intPreferencesKey("bass_boost_strength")
        val NORMALIZE = booleanPreferencesKey("normalization_enabled")
        val LOUDNESS_GAIN_MB = intPreferencesKey("loudness_gain_mb")
        val SPEED = floatPreferencesKey("playback_speed")
        val SLEEP_MIN = intPreferencesKey("sleep_timer_default_minutes")
        val EXCLUDED = stringSetPreferencesKey("excluded_folders")
        val MUSIC_FOLDER_URI = stringPreferencesKey("music_folder_uri")
        val MUSIC_FOLDER_NAME = stringPreferencesKey("music_folder_name")
        val MUSIC_FOLDER_ONLY = booleanPreferencesKey("music_folder_only")
        val DL_FORMAT = stringPreferencesKey("download_format")
        val DL_DEST = stringPreferencesKey("download_destination_uri")
        val AUTO_IMPORT = booleanPreferencesKey("auto_import_downloads")
        val AUTO_YTDLP = booleanPreferencesKey("auto_check_ytdlp")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
        val BG_MODE = stringPreferencesKey("background_mode")
        val BG_DENSITY = intPreferencesKey("background_density")
        val BG_SPEED = intPreferencesKey("background_speed")
        val BG_TRAIL = intPreferencesKey("background_trail")
        val BG_REACT = booleanPreferencesKey("background_react")
        val SORT_ALGO = stringPreferencesKey("sort_algo")
        val INCLUDE_UNSUPPORTED = booleanPreferencesKey("library_include_unsupported")
        val EQ_BANDS = stringPreferencesKey("equalizer_band_gains") // comma-separated mB
        val POMODORO_FOCUS = intPreferencesKey("pomodoro_focus_minutes")
        val POMODORO_BREAK = intPreferencesKey("pomodoro_break_minutes")
        val POMODORO_SYNC = booleanPreferencesKey("pomodoro_sync_playback")
        val QUEUE_IDS = stringPreferencesKey(AtlasKeys.QUEUE_IDS)
        val QUEUE_INDEX = intPreferencesKey(AtlasKeys.QUEUE_INDEX)
        val POSITION_MS = longPreferencesKey(AtlasKeys.POSITION_MS)
        val QUEUE_HINT = stringPreferencesKey(AtlasKeys.QUEUE_TITLE_HINT)
        val YTDLP_NOTICE = stringPreferencesKey(AtlasKeys.YTDLP_UPDATE_NOTICE)
        val LAST_SCAN = longPreferencesKey(AtlasKeys.LAST_SCAN_MS)
        val SLEEP_END_AT = longPreferencesKey("sleep_timer_end_at")
        val SLEEP_END_OF_TRACK = booleanPreferencesKey("sleep_timer_end_of_track")
    }

    val settings: Flow<AtlasSettings> = store.data.map { p ->
        AtlasSettings(
            themeMode = ThemeMode.fromKey(p[K.THEME]),
            dynamicColor = p[K.DYNAMIC] ?: false,
            artworkStyle = ArtworkStyle.entries.firstOrNull { it.name == p[K.ARTWORK] } ?: ArtworkStyle.LARGE,
            defaultSort = SortOption.fromKey(p[K.SORT]),
            gaplessEnabled = p[K.GAPLESS] ?: true,
            crossfadeEnabled = p[K.CROSSFADE] ?: false,
            crossfadeSeconds = (p[K.CROSSFADE_SECS] ?: 6).coerceIn(2, 12),
            equalizerEnabled = p[K.EQ] ?: true,
            bassBoostEnabled = p[K.BASS] ?: false,
            bassBoostStrength = (p[K.BASS_STRENGTH] ?: 60).coerceIn(0, 100),
            normalizationEnabled = p[K.NORMALIZE] ?: false,
            loudnessGainMb = (p[K.LOUDNESS_GAIN_MB] ?: 400).coerceIn(0, 1000),
            playbackSpeed = (p[K.SPEED] ?: 1.0f).coerceIn(0.5f, 2.0f),
            sleepTimerDefaultMinutes = (p[K.SLEEP_MIN] ?: 30).coerceIn(5, 180),
            excludedFolders = p[K.EXCLUDED] ?: emptySet(),
            musicFolderUri = p[K.MUSIC_FOLDER_URI] ?: "",
            musicFolderName = p[K.MUSIC_FOLDER_NAME] ?: "",
            musicFolderOnly = p[K.MUSIC_FOLDER_ONLY] ?: true,
            downloadFormat = p[K.DL_FORMAT] ?: "opus",
            downloadDestinationUri = p[K.DL_DEST] ?: "",
            autoImportDownloads = p[K.AUTO_IMPORT] ?: true,
            autoCheckYtdlpUpdates = p[K.AUTO_YTDLP] ?: true,
            diagnosticsEnabled = p[K.DIAGNOSTICS] ?: false,
            libraryIncludesUnsupported = p[K.INCLUDE_UNSUPPORTED] ?: false,
            backgroundMode = p[K.BG_MODE] ?: "wave",
            backgroundDensity = (p[K.BG_DENSITY] ?: 45).coerceIn(1, 100),
            backgroundSpeed = (p[K.BG_SPEED] ?: 55).coerceIn(1, 100),
            backgroundTrail = (p[K.BG_TRAIL] ?: 72).coerceIn(1, 95),
            backgroundReact = p[K.BG_REACT] ?: true,
            sortAlgo = p[K.SORT_ALGO] ?: "random",
            sleepTimerEndOfTrack = p[K.SLEEP_END_OF_TRACK] ?: false,
            pomodoroFocusMinutes = (p[K.POMODORO_FOCUS] ?: 25).coerceIn(5, 180),
            pomodoroBreakMinutes = (p[K.POMODORO_BREAK] ?: 5).coerceIn(1, 60),
            pomodoroSyncPlayback = p[K.POMODORO_SYNC] ?: true,
        )
    }

    suspend fun setTheme(mode: ThemeMode) = store.edit { it[K.THEME] = mode.name }
    suspend fun setDynamicColor(enabled: Boolean) = store.edit { it[K.DYNAMIC] = enabled }
    suspend fun setArtworkStyle(style: ArtworkStyle) = store.edit { it[K.ARTWORK] = style.name }
    suspend fun setDefaultSort(sort: SortOption) = store.edit { it[K.SORT] = sort.name }
    suspend fun setGapless(enabled: Boolean) = store.edit { it[K.GAPLESS] = enabled }
    suspend fun setCrossfade(enabled: Boolean) = store.edit { it[K.CROSSFADE] = enabled }
    suspend fun setCrossfadeSeconds(seconds: Int) = store.edit { it[K.CROSSFADE_SECS] = seconds.coerceIn(2, 12) }
    suspend fun setEqualizer(enabled: Boolean) = store.edit { it[K.EQ] = enabled }
    suspend fun setBassBoost(enabled: Boolean) = store.edit { it[K.BASS] = enabled }
    suspend fun setNormalization(enabled: Boolean) = store.edit { it[K.NORMALIZE] = enabled }
    suspend fun setBassBoostStrength(strength: Int) = store.edit { it[K.BASS_STRENGTH] = strength.coerceIn(0, 100) }
    suspend fun setLoudnessGainMb(gainMb: Int) = store.edit { it[K.LOUDNESS_GAIN_MB] = gainMb.coerceIn(0, 1000) }
    suspend fun resetAudio() = store.edit {
        it[K.EQ] = false
        it[K.BASS] = false
        it[K.BASS_STRENGTH] = 60
        it[K.NORMALIZE] = false
        it[K.LOUDNESS_GAIN_MB] = 400
        it[K.CROSSFADE] = false
        it[K.SPEED] = 1.0f
        it[K.EQ_BANDS] = ""
    }
    suspend fun setPlaybackSpeed(speed: Float) = store.edit { it[K.SPEED] = speed.coerceIn(0.5f, 2.0f) }
    suspend fun setSleepTimerDefault(minutes: Int) = store.edit { it[K.SLEEP_MIN] = minutes.coerceIn(5, 180) }
    suspend fun setExcludedFolders(folders: Set<String>) = store.edit { it[K.EXCLUDED] = folders }
    suspend fun setMusicFolder(uri: String, name: String) {
        val old = store.data.first()[K.MUSIC_FOLDER_URI]
        if (old != null && old != uri) releasePersistableUriPermission(old)
        store.edit { it[K.MUSIC_FOLDER_URI] = uri; it[K.MUSIC_FOLDER_NAME] = name }
    }
    suspend fun clearMusicFolder() {
        val old = store.data.first()[K.MUSIC_FOLDER_URI]
        store.edit { it.remove(K.MUSIC_FOLDER_URI); it.remove(K.MUSIC_FOLDER_NAME) }
        releasePersistableUriPermission(old)
    }
    suspend fun setMusicFolderOnly(enabled: Boolean) = store.edit { it[K.MUSIC_FOLDER_ONLY] = enabled }
    suspend fun setDownloadFormat(format: String) = store.edit { it[K.DL_FORMAT] = format }
    suspend fun setDownloadDestinationUri(uri: String) {
        val old = store.data.first()[K.DL_DEST]
        if (old != null && old != uri) releasePersistableUriPermission(old)
        store.edit { it[K.DL_DEST] = uri }
    }
    suspend fun setAutoImportDownloads(enabled: Boolean) = store.edit { it[K.AUTO_IMPORT] = enabled }
    suspend fun setAutoCheckYtdlp(enabled: Boolean) = store.edit { it[K.AUTO_YTDLP] = enabled }
    suspend fun setDiagnostics(enabled: Boolean) = store.edit { it[K.DIAGNOSTICS] = enabled }
    suspend fun setBackgroundMode(id: String) = store.edit { it[K.BG_MODE] = id }
    suspend fun setBackgroundDensity(v: Int) = store.edit { it[K.BG_DENSITY] = v.coerceIn(1, 100) }
    suspend fun setBackgroundSpeed(v: Int) = store.edit { it[K.BG_SPEED] = v.coerceIn(1, 100) }
    suspend fun setBackgroundTrail(v: Int) = store.edit { it[K.BG_TRAIL] = v.coerceIn(1, 95) }
    suspend fun setBackgroundReact(enabled: Boolean) = store.edit { it[K.BG_REACT] = enabled }
    suspend fun setSortAlgo(id: String) = store.edit { it[K.SORT_ALGO] = id }
    suspend fun setPomodoroFocus(minutes: Int) = store.edit { it[K.POMODORO_FOCUS] = minutes.coerceIn(5, 180) }
    suspend fun setPomodoroBreak(minutes: Int) = store.edit { it[K.POMODORO_BREAK] = minutes.coerceIn(1, 60) }
    suspend fun setPomodoroSync(enabled: Boolean) = store.edit { it[K.POMODORO_SYNC] = enabled }
    suspend fun setIncludeUnsupported(enabled: Boolean) = store.edit { it[K.INCLUDE_UNSUPPORTED] = enabled }

    /** Equalizer band gains in millibels, one per band, comma-separated. */
    val equalizerBands: Flow<List<Int>> = store.data.map { p ->
        p[K.EQ_BANDS]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
    }

    suspend fun setEqualizerBands(gainsMb: List<Int>) =
        store.edit { it[K.EQ_BANDS] = gainsMb.joinToString(",") }

    // ---- Playback state persistence (queue + position) ----

    data class SavedQueue(val ids: List<Long>, val index: Int, val positionMs: Long, val hint: String?)

    suspend fun saveQueue(ids: List<Long>, index: Int, positionMs: Long, hint: String? = null) =
        store.edit {
            it[K.QUEUE_IDS] = ids.joinToString(",")
            it[K.QUEUE_INDEX] = index
            it[K.POSITION_MS] = positionMs
            if (hint != null) it[K.QUEUE_HINT] = hint
        }

    suspend fun loadQueue(): SavedQueue? {
        val p = store.data.first()
        val idsRaw = p[K.QUEUE_IDS] ?: return null
        val ids = idsRaw.split(',').mapNotNull { it.trim().toLongOrNull() }
        if (ids.isEmpty()) return null
        return SavedQueue(ids, p[K.QUEUE_INDEX] ?: 0, p[K.POSITION_MS] ?: 0L, p[K.QUEUE_HINT])
    }

    suspend fun clearSavedQueue() = store.edit {
        it.remove(K.QUEUE_IDS); it.remove(K.QUEUE_INDEX); it.remove(K.POSITION_MS); it.remove(K.QUEUE_HINT)
    }

    suspend fun setYtdlpUpdateNotice(version: String?) =
        store.edit { if (version == null) it.remove(K.YTDLP_NOTICE) else it[K.YTDLP_NOTICE] = version }

    suspend fun ytdlpUpdateNotice(): String? = store.data.first()[K.YTDLP_NOTICE]

    suspend fun setLastScanMs(ms: Long) = store.edit { it[K.LAST_SCAN] = ms }

    suspend fun saveSleepTimerEndAt(endAt: Long) = store.edit { it[K.SLEEP_END_AT] = endAt }
    suspend fun clearSleepTimerSaved() = store.edit { it.remove(K.SLEEP_END_AT) }
    suspend fun sleepTimerSaved(): Long = store.data.first()[K.SLEEP_END_AT] ?: 0L
    suspend fun setSleepTimerEndOfTrack(enabled: Boolean) = store.edit { it[K.SLEEP_END_OF_TRACK] = enabled }


    suspend fun releasePersistableUriPermission(uriString: String?) {
        if (uriString.isNullOrBlank()) return
        runCatching {
            context.contentResolver.releasePersistableUriPermission(
                android.net.Uri.parse(uriString),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.onFailure { android.util.Log.w("AtlasSettings", "releasePersistableUriPermission failed", it) }
    }
    val lastScanMs: Flow<Long> = store.data.map { it[K.LAST_SCAN] ?: 0L }
}
