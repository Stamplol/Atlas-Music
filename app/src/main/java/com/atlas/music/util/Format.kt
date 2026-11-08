package com.atlas.music.util

object Format {
    fun duration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val totalSec = ms / 1000
        val minutes = totalSec / 60
        val seconds = totalSec % 60
        val hours = minutes / 60
        return if (hours > 0) "%d:%02d:%02d".format(hours, minutes % 60, seconds)
        else "%d:%02d".format(minutes, seconds)
    }

    fun eta(seconds: Long): String = when {
        seconds <= 0 -> "calculating…"
        seconds < 60 -> "${seconds}s left"
        seconds < 3600 -> "${seconds / 60}m ${seconds % 60}s left"
        else -> "${seconds / 3600}h ${(seconds % 3600) / 60}m left"
    }

    fun speed(bytesPerSec: Long): String = when {
        bytesPerSec <= 0 -> "—"
        bytesPerSec < 1024 -> "${bytesPerSec} B/s"
        bytesPerSec < 1024 * 1024 -> "%.1f KB/s".format(bytesPerSec / 1024.0)
        else -> "%.1f MB/s".format(bytesPerSec / (1024.0 * 1024.0))
    }

    fun bytes(bytes: Long): String = when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }

    fun totalDuration(ms: Long): String {
        val hours = ms / 3_600_000
        val minutes = (ms % 3_600_000) / 60_000
        return if (hours > 0) "${hours} hr ${minutes} min" else "${minutes} min"
    }

    fun artistAlbumLine(artist: String, album: String): String = listOf(artist, album)
        .filter { it.isNotBlank() }
        .joinToString("  ·  ")
}
