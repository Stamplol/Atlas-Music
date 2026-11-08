package com.atlas.music.util

import com.atlas.music.data.db.TrackEntity
import java.util.Locale

object DuplicateDetector {
    /** Normalized title+artist+duration fingerprint. Tracks within 2s count as identical. */
    fun fingerprint(title: String, artist: String, durationMs: Long): String {
        val normTitle = normalize(title)
        val normArtist = normalize(artist)
        // Rounded (not floored) 2s buckets so neighbors on a bucket boundary still match.
        val durationBucket = kotlin.math.round(durationMs / 2000.0).toInt()
        return "$normTitle|$normArtist|$durationBucket"
    }

    fun normalize(value: String): String {
        var text = value.lowercase(Locale.ROOT).trim()
        // Strip common junk: feat. clauses, (official video), [remastered], etc.
        text = Regex("\\((?:feat|ft|with)[^)]*\\)").replace(text, "")
        text = Regex("\\[[^]]*]").replace(text, "")
        text = Regex("\\([^)]*(?:official|video|audio|lyric|remaster|edit|version)[^)]*\\)").replace(text, "")
        text = text.replace(Regex("[^a-z0-9 ]"), " ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    data class DuplicateGroup(val fingerprint: String, val tracks: List<TrackEntity>, val totalSizeBytes: Long)

    fun findDuplicates(tracks: List<TrackEntity>): List<DuplicateGroup> = tracks
        .groupBy { it.fingerprint }
        .filterValues { it.size > 1 }
        .map { (fp, group) ->
            DuplicateGroup(fp, group.sortedBy { it.dateAddedSec }, group.sumOf { it.sizeBytes })
        }
        .sortedByDescending { it.tracks.size }

    /** Bytes that would be freed by keeping one track per duplicate group. */
    fun wastedBytes(groups: List<DuplicateGroup>): Long = groups.sumOf { group ->
        group.tracks.drop(1).sumOf { it.sizeBytes }
    }
}
