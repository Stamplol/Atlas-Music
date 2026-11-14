package com.atlas.music.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.C
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import com.atlas.music.AtlasApplication
import com.atlas.music.R
import com.google.common.collect.ImmutableList

/**
 * Premium Media3 notification — Meridian Settle edition.
 * - Satins Gold large cover (checksum-seeded, gradient) as large icon
 * - Title = track, text = Artist · Album (plus format/bitrate hint from extras)
 * - Full control deck in expanded view: Prev / Play-Pause / Next / Shuffle / Repeat / Favorite heart
 *   Compact view keeps Prev / Play-Pause / Next thumb-reachable.
 * - Lockscreen public, gold-tinted small icon, no sound (channel LOW).
 */
class AtlasNotificationProvider(context: Context) : DefaultMediaNotificationProvider(
    context,
    { DefaultMediaNotificationProvider.DEFAULT_NOTIFICATION_ID },
    AtlasApplication.CHANNEL_PLAYBACK,
    R.string.app_name,
) {
    init {
        setSmallIcon(R.drawable.ic_notification_small)
    }

    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence =
        metadata.title?.toString()?.trim()?.ifBlank { "Unknown title" } ?: "Unknown title"

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence {
        val artist = metadata.artist?.toString()?.trim()?.ifBlank { "Unknown artist" } ?: "Unknown artist"
        val album = metadata.albumTitle?.toString()?.trim()?.ifBlank { "" } ?: ""
        val extras = metadata.extras
        val parts = mutableListOf<String>()
        if (artist.isNotBlank()) parts.add(artist)
        if (album.isNotBlank()) parts.add(album)
        // Rich hint: year • format (FLAC/MP3…) instead of a second line that would truncate.
        val year = extras?.getInt(MediaItemFactory.EXTRA_YEAR, 0) ?: 0
        if (year > 0) parts.add(year.toString())
        val mime = extras?.getString(MediaItemFactory.EXTRA_MIME, "")?.lowercase().orEmpty()
        val fmt = when {
            mime.contains("flac") -> "FLAC"
            mime.contains("ogg") || mime.contains("opus") -> "OPUS"
            mime.contains("wav") -> "WAV"
            mime.contains("mpeg") || mime.contains("mp3") -> "MP3"
            mime.contains("mp4") || mime.contains("m4a") -> "M4A"
            mime.contains("aac") -> "AAC"
            else -> null
        }
        if (fmt != null) parts.add(fmt)
        return parts.joinToString("  ·  ")
    }

    override fun getMediaButtons(
        session: MediaSession,
        playerCommands: Player.Commands,
        customLayout: ImmutableList<CommandButton>,
        showPauseButton: Boolean,
    ): ImmutableList<CommandButton> {
        val builder = ImmutableList.Builder<CommandButton>()
        val player = session.player
        val itemCount = runCatching { player.mediaItemCount }.getOrDefault(1)
        val index = runCatching { player.currentMediaItemIndex }.getOrDefault(0)
        val hasNext = itemCount > 1 && index < itemCount - 1
        val hasPrev = itemCount > 1 && index > 0
        val isFavorite = runCatching {
            val id = player.currentMediaItem?.mediaId?.toLongOrNull()
            AtlasNotificationState.isFavorite
        }.getOrDefault(false)

        fun compactExtras(index: Int): Bundle = Bundle().apply {
            putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, index)
        }
        val noCompact = Bundle().apply {
            putInt(DefaultMediaNotificationProvider.COMMAND_KEY_COMPACT_VIEW_INDEX, C.INDEX_UNSET)
        }

        if (hasPrev && playerCommands.containsAny(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS)) {
            builder.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                    .setIconResId(androidx.media3.ui.R.drawable.exo_icon_previous)
                    .setDisplayName("Previous")
                    .setExtras(compactExtras(0))
                    .build()
            )
        }
        if (playerCommands.contains(Player.COMMAND_PLAY_PAUSE)) {
            builder.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
                    .setIconResId(
                        if (showPauseButton) androidx.media3.ui.R.drawable.exo_icon_pause
                        else androidx.media3.ui.R.drawable.exo_icon_play
                    )
                    .setDisplayName(if (showPauseButton) "Pause" else "Play")
                    .setExtras(compactExtras(1))
                    .build()
            )
        }
        if (hasNext && playerCommands.containsAny(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_NEXT)) {
            builder.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .setIconResId(androidx.media3.ui.R.drawable.exo_icon_next)
                    .setDisplayName("Next")
                    .setExtras(compactExtras(1))
                    .build()
            )
        }
        // Shuffle (dynamic on/off icon)
        if (playerCommands.contains(Player.COMMAND_SET_SHUFFLE_MODE)) {
            builder.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_SHUFFLE_MODE)
                    .setIconResId(
                        if (player.shuffleModeEnabled) androidx.media3.ui.R.drawable.exo_icon_shuffle_on
                        else androidx.media3.ui.R.drawable.exo_icon_shuffle_off
                    )
                    .setDisplayName(if (player.shuffleModeEnabled) "Shuffle on" else "Shuffle off")
                    .setExtras(noCompact)
                    .build()
            )
        }
        // Repeat (off / all / one)
        if (playerCommands.contains(Player.COMMAND_SET_REPEAT_MODE)) {
            builder.add(
                CommandButton.Builder()
                    .setPlayerCommand(Player.COMMAND_SET_REPEAT_MODE)
                    .setIconResId(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ALL -> androidx.media3.ui.R.drawable.exo_icon_repeat_all
                            Player.REPEAT_MODE_ONE -> androidx.media3.ui.R.drawable.exo_icon_repeat_one
                            else -> androidx.media3.ui.R.drawable.exo_icon_repeat_off
                        }
                    )
                    .setDisplayName(
                        when (player.repeatMode) {
                            Player.REPEAT_MODE_ALL -> "Repeat all"
                            Player.REPEAT_MODE_ONE -> "Repeat one"
                            else -> "Repeat off"
                        }
                    )
                    .setExtras(noCompact)
                    .build()
            )
        }
        // Favorite heart — custom session command (handled by service).
        builder.add(
            CommandButton.Builder()
                .setSessionCommand(
                    SessionCommand(
                        AtlasPlaybackService.ACTION_TOGGLE_FAVORITE,
                        Bundle.EMPTY,
                    )
                )
                .setIconResId(if (isFavorite) R.drawable.notif_heart_filled else R.drawable.notif_heart_unfilled)
                .setDisplayName(if (isFavorite) "Unlike" else "Like")
                .setExtras(noCompact)
                .build()
        )
        // Any additional custom layout buttons the session supplied (e.g. future).
        for (button in customLayout) {
            if (button.sessionCommand != null) builder.add(button)
        }
        return builder.build()
    }
}
