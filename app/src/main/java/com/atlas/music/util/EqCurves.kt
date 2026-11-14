package com.atlas.music.util

/**
 * 9-band EQ gain curves in mB — the single source of truth for both the playback service
 * (applied via Settings -> equalizer band gains) and the Tools sparkbars.
 * Band order runs the usual low -> wide -> high axis: [60, 150, 400, 1k, 2.4k, 6k, 9k] spirit,
 * kept abstract here since the service receives raw band indices.
 */
object EqCurves {
    /** name -> 9 gain values in mB. Flat is intentionally absent (empty gain = bypass). */
    val CURVES: Map<String, List<Int>> = linkedMapOf(
        "Bass" to listOf(600, 600, 300, 0, 0, 0, 0, 0, -300),
        "Vocal" to listOf(-300, -300, 0, 600, 600, 300, 0, 0, 0),
        "Treble" to listOf(-300, 0, 0, 0, 300, 600, 600, 600, 600),
        "Mid" to listOf(0, 0, 0, 600, 600, 0, 0, 0, 0),
        "Night" to listOf(600, 300, 0, -300, -600, -600, -600, -300, 0),
    )

    /** Display order for the preset row, flat first because it's the default. */
    val PRESET_ORDER = listOf("Flat") + CURVES.keys

    fun gainsFor(name: String): List<Int> = CURVES[name] ?: emptyList()
}
