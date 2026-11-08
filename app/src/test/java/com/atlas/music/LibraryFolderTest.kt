package com.atlas.music

import com.atlas.music.util.LibraryFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryFolderTest {
    @Test fun normalizesCaseAndSlashes() {
        assertEquals("music/flac", LibraryFolder.normalize("Music/FLAC/"))
        assertEquals("downloads", LibraryFolder.normalize("Downloads"))
    }

    @Test fun blankBecomesUnknown() {
        assertEquals("unknown", LibraryFolder.normalize(null))
        assertEquals("unknown", LibraryFolder.normalize("   "))
    }

    @Test fun excludedPrefixMatchesNestedFolders() {
        val excluded = setOf("podcasts")
        assertTrue(LibraryFolder.isExcluded("podcasts", excluded))
        assertTrue(LibraryFolder.isExcluded("podcasts/tech", excluded))
        assertFalse(LibraryFolder.isExcluded("music/podcasts", excluded))
        assertFalse(LibraryFolder.isExcluded("podcastsx", excluded))
    }
}
