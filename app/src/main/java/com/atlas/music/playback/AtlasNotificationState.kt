package com.atlas.music.playback

/** Process-wide playback state for notification buttons (favorite heart). */
object AtlasNotificationState {
    @Volatile var isFavorite: Boolean = false
    @Volatile var trackId: Long = -1L
}
