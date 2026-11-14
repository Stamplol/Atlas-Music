package com.atlas.music.util

/** One synced lyric line, in LRC / embedded format. Raw-only lyrics are returned as a single-line list. */
data class LyricLine(val timeMs: Long, val text: String)

/**
 * LRC parser — `[mm:ss.xx]` timestamps (also accepts `[mm:ss]`).
 * Handles multi-timestamps on one line (ignored — we take the first).
 * Multiline plain-text lyrics (no timestamps) are returned with timeMs = 0 — the UI shows them as static text.
 */
object Lyrics {
    private val TIME_REGEX = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(raw: String): List<LyricLine> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()
        val hasTime = TIME_REGEX.containsMatchIn(trimmed)
        if (!hasTime) {
            // plain text
            return listOf(LyricLine(0, trimmed.split("\n").joinToString("\n").trim()))
        }
        val lines = mutableListOf<LyricLine>()
        trimmed.lineSequence().forEach { rawLine ->
            val matches = TIME_REGEX.findAll(rawLine)
            val timestamps = matches.map { m ->
                val min = m.groupValues[1].toInt()
                val sec = m.groupValues[2].toInt()
                val frac = m.groupValues[3].padEnd(3, '0').take(3).toLong()
                min * 60_000L + sec * 1000L + frac
            }.toList()
            if (timestamps.isEmpty()) return@forEach
            val lyric = TIME_REGEX.replace(rawLine, "").trim()
            for (t in timestamps) {
                if (lyric.isNotBlank()) lines.add(LyricLine(t, lyric))
            }
        }
        return lines.sortedBy { it.timeMs }
    }
}
