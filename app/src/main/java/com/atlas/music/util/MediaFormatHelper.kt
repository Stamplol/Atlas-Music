package com.atlas.music.util

import java.util.Locale

/**
 * Resolves container extensions, Android MediaStore-compatible MIME types,
 * and clean UI format badges for local and downloaded audio files.
 */
object MediaFormatHelper {

    /**
     * Normalize an audio file extension for storage in Android MediaStore.
     *
     * MediaStore.Audio.Media rejects `audio/webm` on Android 13+; WebM audio
     * streams (Opus/Vorbis in EBML) are valid Matroska Audio (`.mka`, `audio/x-matroska`).
     * When [headerBytes] is available, container magic bytes take precedence over
     * a misleading extension.
     */
    fun normalizeDownloadExtension(sourceExtension: String, headerBytes: ByteArray? = null): String {
        detectExtensionFromMagic(headerBytes)?.let { return it }
        return when (val ext = sourceExtension.trim().trimStart('.').lowercase(Locale.ROOT)) {
            "" -> "mka"
            "webm" -> "mka"
            else -> ext
        }
    }

    /** Return an Android MediaStore.Audio.Media-accepted MIME type for [extension]. */
    fun mediaStoreMimeForExtension(extension: String): String =
        when (extension.trim().trimStart('.').lowercase(Locale.ROOT)) {
            "opus", "ogg", "oga" -> "audio/ogg"
            "mka", "webm" -> "audio/x-matroska"
            "m4a", "mp4" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "aac" -> "audio/aac"
            else -> "audio/*"
        }

    /** Human-readable format badge/label derived from [mimeType] or [displayName]. */
    fun displayAudioFormatLabel(mimeType: String?, displayName: String? = null): String {
        val ext = displayName
            ?.substringAfterLast('.', "")
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.takeIf { it.isNotBlank() }

        return when (mimeType.orEmpty().trim().lowercase(Locale.ROOT)) {
            "audio/mpeg", "audio/mp3" -> "MP3"
            "audio/mp4", "audio/m4a", "audio/x-m4a" -> "M4A"
            "audio/x-wav", "audio/wav", "audio/vnd.wave" -> "WAV"
            "audio/flac", "audio/x-flac" -> "FLAC"
            "audio/x-matroska" -> "MKA"
            "audio/webm" -> "WEBM"
            "audio/opus" -> "OPUS"
            "audio/ogg" -> if (ext == "opus") "OPUS" else "OGG"
            "audio/aac", "audio/aacp" -> "AAC"
            else -> {
                val fromMime = mimeType.orEmpty()
                    .substringAfter('/', "")
                    .removePrefix("x-")
                    .uppercase(Locale.ROOT)
                fromMime.ifBlank { ext?.uppercase(Locale.ROOT) ?: "AUDIO" }
            }
        }
    }

    private fun detectExtensionFromMagic(header: ByteArray?): String? {
        if (header == null || header.size < 4) return null
        // EBML / Matroska / WebM: 1A 45 DF A3
        if (header[0] == 0x1A.toByte() &&
            header[1] == 0x45.toByte() &&
            header[2] == 0xDF.toByte() &&
            header[3] == 0xA3.toByte()
        ) {
            return "mka"
        }
        // Ogg container ("OggS")
        if (header[0] == 'O'.code.toByte() &&
            header[1] == 'g'.code.toByte() &&
            header[2] == 'g'.code.toByte() &&
            header[3] == 'S'.code.toByte()
        ) {
            return "ogg"
        }
        // FLAC ("fLaC")
        if (header[0] == 'f'.code.toByte() &&
            header[1] == 'L'.code.toByte() &&
            header[2] == 'a'.code.toByte() &&
            header[3] == 'C'.code.toByte()
        ) {
            return "flac"
        }
        // RIFF / WAV ("RIFF")
        if (header[0] == 'R'.code.toByte() &&
            header[1] == 'I'.code.toByte() &&
            header[2] == 'F'.code.toByte() &&
            header[3] == 'F'.code.toByte()
        ) {
            return "wav"
        }
        // MP4 / M4A ("ftyp" at offset 4..7)
        if (header.size >= 8 &&
            header[4] == 'f'.code.toByte() &&
            header[5] == 't'.code.toByte() &&
            header[6] == 'y'.code.toByte() &&
            header[7] == 'p'.code.toByte()
        ) {
            return "m4a"
        }
        // ID3v2 header ("ID3")
        if (header[0] == 'I'.code.toByte() &&
            header[1] == 'D'.code.toByte() &&
            header[2] == '3'.code.toByte()
        ) {
            return "mp3"
        }
        return null
    }
}
