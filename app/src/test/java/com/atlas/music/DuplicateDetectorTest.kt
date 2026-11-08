package com.atlas.music

import com.atlas.music.data.db.TrackEntity
import com.atlas.music.util.DuplicateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateDetectorTest {
    private fun track(title: String, artist: String, durationMs: Long, id: Long = 0, size: Long = 1_000_000) =
        TrackEntity(
            id = id,
            uri = "content://x/$id",
            filePath = "/music/$title.mp3",
            displayName = "$title.mp3",
            title = title,
            artist = artist,
            album = "Album",
            albumArtist = artist,
            genre = "Rock",
            year = 2001,
            trackNumber = 1,
            discNumber = 1,
            durationMs = durationMs,
            sizeBytes = size,
            dateAddedSec = id,
            dateModifiedSec = 0,
            folder = "music",
            albumId = 1,
            fingerprint = DuplicateDetector.fingerprint(title, artist, durationMs),
        )

    @Test fun sameSongDifferentCaseIsDuplicate() {
        val a = track("Mr. Brightside", "The Killers", 222_000)
        val b = track("MR. BRIGHTSIDE", "the killers", 221_500)
        // within 2s bucket — treated as the same recording
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test fun featClauseIsNormalized() {
        val a = track("Blinding Lights", "The Weeknd", 200_000)
        val b = track("Blinding Lights (feat. Someone)", "The Weeknd", 200_000)
        assertEquals(a.fingerprint, b.fingerprint)
    }

    @Test fun differentDurationsAreNotDuplicates() {
        val a = track("Song", "Artist", 100_000)
        val b = track("Song", "Artist", 200_000)
        assertTrue(a.fingerprint != b.fingerprint)
    }

    @Test fun findDuplicatesGroupsCorrectly() {
        val tracks = listOf(
            track("Song A", "Artist", 100_000, id = 1),
            track("Song A", "Artist", 100_500, id = 2, size = 2_000_000),
            track("Song A (Official Video)", "Artist", 100_000, id = 3),
            track("Song B", "Artist", 180_000, id = 4),
        )
        val groups = DuplicateDetector.findDuplicates(tracks)
        assertEquals(1, groups.size)
        assertEquals(3, groups[0].tracks.size)
        assertEquals(2_000_000L + 1_000_000L + 1_000_000L, groups[0].totalSizeBytes)
    }

    @Test fun wastedBytesCountsAllButOnePerGroup() {
        val tracks = listOf(
            track("Song A", "Artist", 100_000, id = 1, size = 1_000_000),
            track("Song A", "Artist", 100_000, id = 2, size = 3_000_000),
        )
        val groups = DuplicateDetector.findDuplicates(tracks)
        assertEquals(3_000_000L, DuplicateDetector.wastedBytes(groups))
    }
}
