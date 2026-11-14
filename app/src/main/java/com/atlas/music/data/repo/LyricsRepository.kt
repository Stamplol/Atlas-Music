package com.atlas.music.data.repo

import android.content.Context
import android.net.Uri
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.util.Lyrics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Resolves lyrics for a track: sidecar .lrc file (same stem, next to the audio) → embedded tags.
 * Never hits the network; works fully offline.
 */
class LyricsRepository(private val context: Context) {

    data class LyricsResult(
        val lines: List<com.atlas.music.util.LyricLine>,
        val source: String,
        val raw: Boolean,
    )

    suspend fun lyricsFor(track: TrackEntity): LyricsResult? = withContext(Dispatchers.IO) {
        // 1) Sidecar .lrc — same directory, same filename stem
        val sidecars = candidateSidecars(track)
        for (file in sidecars) {
            val lrc = runCatching {
                if (file.isFile) {
                    val text = file.readText()
                    if (text.isNotBlank()) {
                        val parsed = Lyrics.parse(text)
                        if (parsed.isNotEmpty()) LyricsResult(parsed, "lrc", parsed.all { it.timeMs == 0L }) else null
                    } else null
                } else null
            }.getOrNull()
            if (lrc != null) return@withContext lrc
        }
        // 2) Embedded lyrics via Chaquopy mutagen
        runCatching {
            val bridge = com.atlas.music.downloads.PythonBridge(context)
            if (!bridge.isAvailable()) return@runCatching null
            // SAF content URIs: mutagen can't read them directly — copy to temp
            val working = if (track.filePath.isNotBlank() && File(track.filePath).isFile) {
                    track.filePath
            } else {
                    val temp = File(context.cacheDir, "lyrics-temp/${track.id}.bin").apply { parentFile?.mkdirs() }
                context.contentResolver.openInputStream(Uri.parse(track.uri))?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                }
                temp.absolutePath
            }
            val json = bridge.readEmbeddedLyrics(working)
            if (json.optBoolean("ok", false)) {
                val raw = json.optString("lyrics_raw")
                val parsed = Lyrics.parse(raw)
                    if (parsed.isNotEmpty()) {
                    LyricsResult(parsed, json.optString("source", "embedded"), parsed.all { l -> l.timeMs == 0L })
                } else null
            } else null
        }.getOrNull()
    }

    private fun candidateSidecars(track: TrackEntity): List<File> {
        if (track.filePath.isNotBlank()) {
            val base = track.filePath.substringBeforeLast('.', track.filePath)
                return listOf(File("$base.lrc"), File("$base.lrc".replace(".lrc", ".LRC")))
                    .distinct()
        }
        return emptyList()
    }
}
