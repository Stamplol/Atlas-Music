package com.atlas.music.util

import java.net.URI

data class YoutubeLink(
    val videoId: String?,
    val playlistId: String?,
    val isPlaylist: Boolean,
    /** "youtube" or "soundcloud" — drives the provider chip and the license gate. */
    val provider: String = "youtube",
    /** Optional private-set token from SoundCloud private set links (s-xxx). */
    val token: String? = null,
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
    private val SC_HOSTS = setOf("soundcloud.com", "www.soundcloud.com", "m.soundcloud.com")
    private val SC_TRACK = Regex("""^/([\w-]{1,100})/([\w-]{1,100})/?$""")
    private val SC_SET = Regex("""^/([\w-]{1,100})/sets/([\w-]{1,100})(?:/(s-[\w-]{3,64}))?/?$""")
    /** Reserved first path segments on SoundCloud — a track page never starts with these. */
    private val SC_RESERVED = setOf("sets", "tracks", "albums", "likes", "reposts", "spotlight", "comments", "stations", "search")

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
            if (VIDEO_ID.matches(id)) return YoutubeLink(id, null, isPlaylist = false, provider = "youtube")
            return null
        }
        if (SC_HOSTS.contains(host)) {
            val setMatch = SC_SET.find(path)
            if (setMatch != null) {
                val uploader = setMatch.groupValues[1]
                val slug = setMatch.groupValues[2]
                val token = setMatch.groupValues[3].ifBlank { null }
                return YoutubeLink(
                    videoId = null,
                    playlistId = "scset:$uploader/$slug",
                    isPlaylist = true,
                    provider = "soundcloud",
                    token = token,
                )
            }
            val trackMatch = SC_TRACK.find(path)
            if (trackMatch != null) {
                val uploader = trackMatch.groupValues[1]
                val title = trackMatch.groupValues[2]
                if (uploader.lowercase() in SC_RESERVED) return null
                return YoutubeLink(
                    videoId = "sc:$uploader/$title",
                    playlistId = null,
                    isPlaylist = false,
                    provider = "soundcloud",
                )
            }
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
            if (id != null && VIDEO_ID.matches(id)) {
                val list = queryParams["list"]
                val playlistId = if (list != null && PLAYLIST_ID.matches(list)) list else null
                // Watch URLs stay single videos for playback/duplicate purposes; the playlistId
                // rides along only so the UI can offer "download the whole playlist".
                return YoutubeLink(id, playlistId, isPlaylist = false)
            }
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
            link.provider == "soundcloud" && link.isPlaylist -> {
                // stored as scset:{user}/{slug} — put the /sets/ segment back
                val parts = link.playlistId?.removePrefix("scset:")?.split("/", limit = 2) ?: return null
                if (parts.size != 2) return null
                val token = link.token?.let { "/$it" } ?: ""
                "https://soundcloud.com/${parts[0]}/sets/${parts[1]}$token"
            }
            link.provider == "soundcloud" -> {
                "https://soundcloud.com/${link.videoId?.removePrefix("sc:") ?: return null}"
            }
            link.videoId != null -> "https://www.youtube.com/watch?v=${link.videoId}"
            link.playlistId != null -> "https://www.youtube.com/playlist?list=${link.playlistId}"
            else -> null
        }
    }

    /** Canonical playlist URL for batch listing (works for watch+list URLs too). */
    fun canonicalPlaylistUrl(raw: String): String? {
        val link = parse(raw) ?: return null
        return when {
            link.provider == "soundcloud" && link.isPlaylist -> canonicalUrl(raw)
            else -> {
                val playlistId = link.playlistId ?: return null
                "https://www.youtube.com/playlist?list=$playlistId"
            }
        }
    }

    /** "youtube" | "soundcloud" — null for unrecognized links. */
    fun providerOf(raw: String): String? = parse(raw)?.provider
}
