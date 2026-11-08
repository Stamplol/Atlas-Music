package com.atlas.music.util

/** Semantic-ish version comparison for yt-dlp release tags (YYYY.MM.DD or YYYY.MM.DD.rev). */
data class YtdlpVersion(val year: Int, val month: Int, val day: Int, val rev: Int = 0) : Comparable<YtdlpVersion> {
    override fun compareTo(other: YtdlpVersion): Int = compareValuesBy(
        this, other,
        { it.year }, { it.month }, { it.day }, { it.rev },
    )
    override fun toString(): String =
        if (rev > 0) "%04d.%02d.%02d.%d".format(year, month, day, rev) else "%04d.%02d.%02d".format(year, month, day)

    companion object {
        private val TAG = Regex("^v?(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{1,3}))?$")

        /** Parse "2025.10.22" / "v2025.10.22" / "2025.10.22.1". Returns null if malformed. */
        fun parse(raw: String): YtdlpVersion? {
            val match = TAG.matchEntire(raw.trim()) ?: return null
            val (y, m, d, r) = match.destructured
            val month = m.toInt(); val day = d.toInt()
            if (month !in 1..12 || day !in 1..31) return null
            return YtdlpVersion(y.toInt(), month, day, r.ifEmpty { "0" }.toInt())
        }
    }
}
