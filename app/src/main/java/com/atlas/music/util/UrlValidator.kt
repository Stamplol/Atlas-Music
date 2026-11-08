package com.atlas.music.util

import java.net.URI

data class YoutubeLink(
    val videoId: String?,
    val playlistId: String?,
    val isPlaylist: Boolean,
) {
    val stableKey: String get() = videoId ?: playlistId ?: ""
}

object UrlValidator {
    // Deliberately strict: host must be a YouTube host we recognize, scheme https only,
    // and IDs must match YouTube's real ID alphabet. Never pass raw input to yt-dlp.
    private val VIDEO_ID = Regex("^[A-Za-z0-9_-]{10,12}$")
    private val PLAYLIST_ID = Regex("^[A-Za-z0-9_-]{10,64}$")

    private val VIDEO_HOSTS = setOf(
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com",
    )
    private val SHORT_HOSTS = setOf("youtu.be", "www.youtu.be")

    fun parse(raw: String): YoutubeLink? {
        val trimmed = raw.trim()
        if (trimmed.length > 2048) return null
        if (trimmed.any { it.isWhitespace() }) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.lowercase() != "https") return null
        if (uri.userInfo != null) return null
        val host = uri.host?.lowercase() ?: return null
        val path = uri.path.orEmpty()
        val queryParams = uri.query?.split('&')
            ?.mapNotNull { param ->
                val idx = param.indexOf('=')
                if (idx <= 0) null else param.substring(0, idx) to param.substring(idx + 1)
            }
            ?.toMap()
            ?: emptyMap()

        if (SHORT_HOSTS.contains(host)) {
            val id = path.trimStart('/').substringBefore('/')
            if (VIDEO_ID.matches(id)) return YoutubeLink(id, null, isPlaylist = false)
            return null
        }
        if (!VIDEO_HOSTS.contains(host)) return null

        if (path.startsWith("/shorts/")) {
            val id = path.removePrefix("/shorts/").trim('/').substringBefore('/')
            if (VIDEO_ID.matches(id)) return YoutubeLink(id, null, isPlaylist = false)
            return null
        }
        if (path == "/watch" || path.startsWith("/watch")) {
            val id = queryParams["v"]
            if (id != null && VIDEO_ID.matches(id)) return YoutubeLink(id, null, isPlaylist = false)
            return null
        }
        if (path == "/playlist") {
            val list = queryParams["list"]
            if (list != null && PLAYLIST_ID.matches(list)) return YoutubeLink(null, list, isPlaylist = true)
            return null
        }
        return null
    }

    fun isValidYoutubeUrl(raw: String): Boolean = parse(raw) != null

    /** Canonical https URL handed to yt-dlp — never the raw user string. */
    fun canonicalUrl(raw: String): String? {
        val link = parse(raw) ?: return null
        return when {
            link.videoId != null -> "https://www.youtube.com/watch?v=${link.videoId}"
            link.playlistId != null -> "https://www.youtube.com/playlist?list=${link.playlistId}"
            else -> null
        }
    }
}
