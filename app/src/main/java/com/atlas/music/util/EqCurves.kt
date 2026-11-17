package com.atlas.music.util

/**
 * The full EQ catalog — shared between the service (via Settings band gains), the
 * playback collect path, and the Audio studio sparkbars.
 *
 * Band order runs the usual low → wide → high axis; labels are generic (we don't
 * know the device's exact center frequencies).
 */
object EqCurves {
    /** name -> 9 gain values in mB. Flat is intentionally absent (empty gains = bypass). */
    val CURVES: Map<String, List<Int>> = linkedMapOf(
        "Bass"       to listOf(600, 600, 300, 0, 0, 0, 0, 0, -300),
        "Bass Boost" to listOf(800, 800, 400, 100, 0, 0, 0, 0, -100),
        "Bass Cut"   to listOf(-400, -400, -150, 0, 0, 0, 0, 0, 100),
        "Vocal"      to listOf(-300, -300, 0, 600, 600, 300, 0, 0, 0),
        "Vocal Cut"  to listOf(100, 100, 0, -500, -500, -300, 0, 0, 0),
        "Mid"        to listOf(0, 0, 0, 600, 600, 0, 0, 0, 0),
        "Treble"     to listOf(-300, 0, 0, 0, 300, 600, 600, 600, 600),
        "Treble Cut" to listOf(100, 0, 0, 0, -200, -400, -400, -400, -400),
        "Night"      to listOf(600, 300, 0, -300, -600, -600, -600, -300, 0),
        "Acoustic"   to listOf(100, 100, 100, 0, 300, 400, 500, 500, 400),
        "Rock"       to listOf(400, 300, 100, -100, -100, 0, 200, 300, 400),
        "Jazz"       to listOf(300, 200, 0, 200, 300, 300, 200, 100, 300),
        "Electronic" to listOf(500, 400, 100, -200, -300, 0, 200, 400, 500),
        "Hip-hop"    to listOf(600, 400, 200, -100, -100, 0, 200, 300, 200),
        "Podcast"    to listOf(-400, -200, 0, 600, 600, 400, 100, 0, -100),
        "Classical"  to listOf(300, 200, 0, 0, 0, 0, 100, 200, 300),
    )

    /** Display order — flat first because it's the default. */
    val PRESET_ORDER = listOf("Flat") + CURVES.keys

    fun gainsFor(name: String): List<Int> = CURVES[name] ?: emptyList()

    /** Identify the preset name for persisted [bands], or "Flat" / "Custom". */
    fun matchingPresetName(bands: List<Int>): String {
        if (bands.isEmpty() || bands.all { it == 0 }) return "Flat"
        return CURVES.entries.firstOrNull { (_, curve) ->
            curve.size == bands.size && curve == bands
        }?.key ?: "Custom"
    }

    /** Generic band labels — we don't know the device's exact centers. */
    val MANUAL_BANDS = listOf(
        "Sub", "Bass", "Low-mid", "Mid", "Hi-mid", "Presence", "Brilliance", "Air", "Top",
    )

    /** EQ band indices grouped into the three studio categories. */
    val LOW_END_BANDS = listOf(0, 1, 2)
    val MIDRANGE_BANDS = listOf(3, 4, 5, 6)
    val HIGH_END_BANDS = listOf(7, 8)

    /** mB per full slider deflection. */
    const val BAND_MAX_MB = 600
}
