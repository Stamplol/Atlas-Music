package com.atlas.music.util

/** Derives a displayable folder path from MediaStore RELATIVE_PATH and/or a file path. */
object LibraryFolder {
    /** e.g. "Music/FLAC/" -> "music/flac" (lower-cased, trailing slash trimmed). */
    fun normalize(relativePath: String?): String {
        if (relativePath.isNullOrBlank()) return "unknown"
        return relativePath.trim().trimEnd('/').lowercase().ifBlank { "unknown" }
    }

    /** Display label with original casing preserved when available. */
    fun label(folder: String): String = folder.ifBlank { "unknown" }

    /** True when `folder` is inside any excluded prefix (e.g. "podcasts"). */
    fun isExcluded(folder: String, excluded: Set<String>): Boolean {
        val f = folder.lowercase()
        return excluded.any { ex ->
            val e = ex.trim().lowercase().trimEnd('/')
            e.isNotEmpty() && (f == e || f.startsWith("$e/"))
        }
    }
}
