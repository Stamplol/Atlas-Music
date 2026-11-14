package com.atlas.music

import com.atlas.music.util.UrlValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {
    @Test fun acceptsStandardWatchUrl() {
        assertTrue(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun acceptsShortUrl() {
        assertTrue(UrlValidator.isValidYoutubeUrl("https://youtu.be/dQw4w9WgXcQ"))
    }

    @Test fun acceptsMusicSubdomain() {
        assertTrue(UrlValidator.isValidYoutubeUrl("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun acceptsShorts() {
        assertTrue(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    @Test fun acceptsPlaylistUrl() {
        val link = UrlValidator.parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertNotNull(link)
        assertTrue(link!!.isPlaylist)
        assertNull(link.videoId)
    }

    @Test fun rejectsHttpNotHttps() {
        assertFalse(UrlValidator.isValidYoutubeUrl("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun rejectsNonYoutubeHost() {
        assertFalse(UrlValidator.isValidYoutubeUrl("https://evil.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun rejectsYoutubeLookalike() {
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com.attacker.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun rejectsShellInjectionStrings() {
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ; rm -rf /"))
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/watch?v=`id`"))
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ|cat"))
    }

    @Test fun rejectsEmptyAndGarbage() {
        assertFalse(UrlValidator.isValidYoutubeUrl(""))
        assertFalse(UrlValidator.isValidYoutubeUrl("not a url"))
        assertFalse(UrlValidator.isValidYoutubeUrl("youtube.com/watch?v=dQw4w9WgXcQ")) // scheme required
    }

    @Test fun rejectsOverlongInput() {
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/watch?v=" + "a".repeat(3000)))
    }

    @Test fun canonicalizationDropsTrackingParams() {
        val canonical = UrlValidator.canonicalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=share&feature=web")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", canonical)
    }

    @Test fun canonicalPlaylistKeepsListId() {
        val canonical = UrlValidator.canonicalUrl("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf&si=abc123")
        assertEquals("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", canonical)
    }
}

class PlaylistLinkTest {
    @Test fun watchUrlWithListParamCarriesPlaylistId() {
        val link = UrlValidator.parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertNotNull(link)
        assertEquals("dQw4w9WgXcQ", link!!.videoId)
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", link.playlistId)
        // stays a single video for duplicate detection & playback semantics
        assertFalse(link.isPlaylist)
        assertEquals("dQw4w9WgXcQ", link.stableKey)
    }

    @Test fun playlistOnlyUrlHasNullVideoId() {
        val link = UrlValidator.parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertNotNull(link)
        assertTrue(link!!.isPlaylist)
        assertNull(link.videoId)
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", link.playlistId)
        assertEquals("PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf", link.stableKey)
    }

    @Test fun canonicalUrlStaysVideoForWatchWithList() {
        val canonical = UrlValidator.canonicalUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", canonical)
    }

    @Test fun canonicalPlaylistUrlResolvesBothForms() {
        assertEquals(
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            UrlValidator.canonicalPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
        )
        assertEquals(
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf",
            UrlValidator.canonicalPlaylistUrl("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
        )
    }

    @Test fun canonicalPlaylistUrlNullForPlainVideo() {
        assertNull(UrlValidator.canonicalPlaylistUrl("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test fun rejectsMalformedPlaylistId() {
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/playlist?list=!!!"))
        assertFalse(UrlValidator.isValidYoutubeUrl("https://www.youtube.com/playlist?list=P%27%20OR%201=1--"))
        assertNull(UrlValidator.parse("https://www.youtube.com/playlist?list=<script>alert(1)</script>"))
    }

    @Test fun playlistWithWeirdCasingStillAccepted() {
        // Real YouTube playlist IDs are case-sensitive base64-ish; matching must be case-sensitive
        val link = UrlValidator.parse("https://www.youtube.com/playlist?list=PLRaxlBzk3XxQQSh2CL0OTb1gq")
        assertNotNull(link)
    }
}
