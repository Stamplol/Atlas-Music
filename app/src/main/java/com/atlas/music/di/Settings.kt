package com.atlas.music.di

import com.atlas.music.util.ArtworkStyle
import com.atlas.music.util.SortOption
import com.atlas.music.util.ThemeMode

data class AtlasSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val dynamicColor: Boolean = false,
    val artworkStyle: ArtworkStyle = ArtworkStyle.LARGE,
    val defaultSort: SortOption = SortOption.TITLE,
    val gaplessEnabled: Boolean = true,
    val crossfadeEnabled: Boolean = false,
    val crossfadeSeconds: Int = 6,
    val equalizerEnabled: Boolean = true,
    val bassBoostEnabled: Boolean = false,
    val normalizationEnabled: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerDefaultMinutes: Int = 30,
    val excludedFolders: Set<String> = emptySet(),
    val musicFolderUri: String = "", // SAF tree URI; empty = automatic (MediaStore everywhere)
    val musicFolderName: String = "",
    val musicFolderOnly: Boolean = true, // when custom folder set, limit scan to it
    val downloadFormat: String = "opus",   // opus | m4a | best
    val downloadDestinationUri: String = "", // SAF tree URI; empty = MediaStore Music/Atlas Music
    val autoImportDownloads: Boolean = true,
    val autoCheckYtdlpUpdates: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val libraryIncludesUnsupported: Boolean = false,
    val backgroundMode: String = "wave",
    val backgroundDensity: Int = 45,
    val backgroundSpeed: Int = 55,
    val backgroundTrail: Int = 72,
    val backgroundReact: Boolean = true,
    val sortAlgo: String = "random",
    val pomodoroFocusMinutes: Int = 25,
    val pomodoroBreakMinutes: Int = 5,
    val pomodoroSyncPlayback: Boolean = true,
    val sleepTimerEndOfTrack: Boolean = false,
)

object AtlasKeys {
    const val QUEUE_IDS = "playback_queue_ids"
    const val QUEUE_INDEX = "playback_queue_index"
    const val POSITION_MS = "playback_position_ms"
    const val QUEUE_TITLE_HINT = "playback_queue_hint"
    const val YTDLP_UPDATE_NOTICE = "ytdlp_update_notice_version"
    const val LAST_SCAN_MS = "library_last_scan_ms"
}
