package com.atlas.music

import com.atlas.music.util.YtdlpVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtdlpVersionTest {
    @Test fun parsesPlainReleaseTag() {
        assertEquals(YtdlpVersion(2025, 10, 22), YtdlpVersion.parse("2025.10.22"))
    }

    @Test fun parsesPrefixedTag() {
        assertEquals(YtdlpVersion(2025, 10, 22), YtdlpVersion.parse("v2025.10.22"))
    }

    @Test fun parsesRevisionTag() {
        assertEquals(YtdlpVersion(2025, 10, 22, 1), YtdlpVersion.parse("2025.10.22.1"))
    }

    @Test fun rejectsGarbage() {
        assertNull(YtdlpVersion.parse("latest"))
        assertNull(YtdlpVersion.parse(""))
        assertNull(YtdlpVersion.parse("2025.13.40"))
        assertNull(YtdlpVersion.parse("v1.2"))
    }

    @Test fun orderingWorksAcrossRevisionsAndMonths() {
        assertTrue(YtdlpVersion.parse("2025.10.22")!! > YtdlpVersion.parse("2025.10.21")!!)
        assertTrue(YtdlpVersion.parse("2026.01.01")!! > YtdlpVersion.parse("2025.12.31")!!)
        assertTrue(YtdlpVersion.parse("2025.10.22.1")!! > YtdlpVersion.parse("2025.10.22")!!)
        assertFalse(YtdlpVersion.parse("2025.10.22")!! > YtdlpVersion.parse("2025.10.22")!!)
    }

    @Test fun toStringRoundTrips() {
        assertEquals("2025.10.22", YtdlpVersion.parse("v2025.10.22").toString())
        assertEquals("2025.10.22.3", YtdlpVersion.parse("2025.10.22.3").toString())
    }
}
