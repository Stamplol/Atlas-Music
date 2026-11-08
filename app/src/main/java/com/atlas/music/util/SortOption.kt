package com.atlas.music.util

enum class SortOption(val label: String) {
    TITLE("Title"),
    ARTIST("Artist"),
    ALBUM("Album"),
    DURATION("Duration"),
    DATE_ADDED("Date added"),
    YEAR("Year"),
    TRACK_NUMBER("Track number");

    companion object {
        fun fromKey(key: String?): SortOption =
            entries.firstOrNull { it.name == key } ?: TITLE
    }
}

enum class ArtworkStyle(val label: String) {
    LARGE("Large artwork"),
    COMPACT("Compact rows"),
}

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    DARK("Dark"),
    LIGHT("Light"),
    MOCHA("Mocha"),
    LATTE("Latte"),
    GRUVBOX("Gruvbox"),
    TOKYO_NIGHT("Tokyo Night"),
    NORD("Nord"),
    DRACULA("Dracula"),
    KANAGAWA("Kanagawa"),
    ROSE_PINE("Rosé Pine"),
    EVERFOREST("Everforest"),
    OXOCARBON("Oxocarbon");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.name == key } ?: DARK
    }
}
