package com.atlas.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MediaFormatHelperTest {

    @Test
    fun `webm extension normalizes to matroska audio for MediaStore compatibility`() {
        val ext = MediaFormatHelper.normalizeDownloadExtension("webm")
        assertEquals("mka", ext)
        val mime = MediaFormatHelper.mediaStoreMimeForExtension(ext)
        assertEquals("audio/x-matroska", mime)
        assertNotEquals("audio/webm", mime)
    }

    @Test
    fun `webm mime maps to x-matroska instead of unsupported audio webm`() {
        assertEquals("audio/x-matroska", MediaFormatHelper.mediaStoreMimeForExtension("webm"))
        assertEquals("audio/x-matroska", MediaFormatHelper.mediaStoreMimeForExtension("MKA"))
    }

    @Test
    fun `magic bytes override mismatched file extension`() {
        val ebml = byteArrayOf(0x1A, 0x45, 0xDF.toByte(), 0xA3.toByte(), 0x01)
        val ogg = "OggS\u0000".toByteArray()
        val m4a = byteArrayOf(0, 0, 0, 24, 'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte())
        val flac = "fLaC\u0000".toByteArray()
        val wav = "RIFF\u0000".toByteArray()
        val mp3 = "ID3\u0003\u0000".toByteArray()

        assertEquals("mka", MediaFormatHelper.normalizeDownloadExtension("opus", ebml))
        assertEquals("ogg", MediaFormatHelper.normalizeDownloadExtension("webm", ogg))
        assertEquals("m4a", MediaFormatHelper.normalizeDownloadExtension("aac", m4a))
        assertEquals("flac", MediaFormatHelper.normalizeDownloadExtension("bin", flac))
        assertEquals("wav", MediaFormatHelper.normalizeDownloadExtension("bin", wav))
        assertEquals("mp3", MediaFormatHelper.normalizeDownloadExtension("bin", mp3))
    }

    @Test
    fun `displayAudioFormatLabel produces clean badges for mime types and names`() {
        assertEquals("WAV", MediaFormatHelper.displayAudioFormatLabel("audio/x-wav", "sample.wav"))
        assertEquals("MKA", MediaFormatHelper.displayAudioFormatLabel("audio/x-matroska", "song.mka"))
        assertEquals("M4A", MediaFormatHelper.displayAudioFormatLabel("audio/mp4", "song.m4a"))
        assertEquals("MP3", MediaFormatHelper.displayAudioFormatLabel("audio/mpeg", "song.mp3"))
        assertEquals("OPUS", MediaFormatHelper.displayAudioFormatLabel("audio/ogg", "track.opus"))
        assertEquals("OGG", MediaFormatHelper.displayAudioFormatLabel("audio/ogg", "track.ogg"))
        assertEquals("FLAC", MediaFormatHelper.displayAudioFormatLabel("", "album.flac"))
    }
}
