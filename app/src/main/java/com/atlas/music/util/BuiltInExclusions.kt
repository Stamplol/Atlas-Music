package com.atlas.music.util

/**
 * Junk MediaStore can't be trusted to filter itself — untagged audio often gets IS_MUSIC = NULL
 * and the IS_RINGTONE/NOTIFICATION/ALARM flags are equally unreliable on most OEMs.
 * These folder-level patterns are always skipped, in addition to the user's own exclusions.
 *
 * Deliberately narrow: matches whole-ish tokens, not arbitrary substrings of legitimate titles,
 * and never excludes Atlas's own finished output (Music/Atlas Music/) on purpose.
 */
object BuiltInExclusions {

    private val PATTERNS: List<Regex> = listOf(
        // Messaging apps — voice notes, audio attachments, "audio messages" (opaque path segments)
        Regex("whatsapp"),
        Regex("telegram"),
        Regex("signal"),
        Regex("viber"),
        Regex("threema"),
        Regex("discord"),
        Regex("messenger"),
        // Voice recorders / dictaphones
        Regex("recorder"),
        Regex("recordings"),
        Regex("dictaphone"),
        Regex("call\\s*record"),
        Regex("voice[ _-]?memo"),
        Regex("memos"),
        // System sounds — flags are usually NULL, so the IS_* filters pass them; path always wins
        Regex("ringtones?"),
        Regex("notifications?"),
        Regex("alarms?"),
        Regex("notification[ _-]?sounds?"),
        Regex("alarm[ _-]?sounds?"),
        // Camera / archive audio — video soundtrack exports, not a music library
        Regex("(^|/)dcim(/|$)"),
        Regex("(^|/)movies(/|$)"),
        Regex("(^|/)pictures(/|$)"),
        Regex("screen[ _-]?recordings?"),
        // Atlas's own transient artifacts, should one ever leak into a shared folder
        Regex("\\.atlas\\b"),
        Regex("atlas[ _-]?temp"),
    )

    fun isBuiltInExcluded(folder: String): Boolean {
        val haystack = folder.lowercase()
        return PATTERNS.any { it.containsMatchIn(haystack) }
    }

    /** Short, legible hint for the Settings copy. */
    const val SUMMARY = "Messaging apps (WhatsApp/Telegram/Signal), recorders and memos, ringtones/notifications/alarms, DCIM/Movies/Pictures, Atlas temp"
}
