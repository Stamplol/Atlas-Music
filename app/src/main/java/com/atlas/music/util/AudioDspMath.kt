package com.atlas.music.util

import kotlin.math.abs

/** Combined real-time DSP output computed for playback and live curve UI. */
data class EffectiveAudioCurve(
    /** Final per-band EQ gains (dB, length 10 across ISO 31 Hz .. 16 kHz). */
    val totalBandsDb10: List<Float>,
    /** Underlying headphone AutoEq curve (dB, length 10) for dashed reference plot. */
    val headphoneCurveDb10: List<Float>,
    /** User EQ + Sound Check contribution (dB, length 10). */
    val userCurveDb10: List<Float>,
    /** Combined pre-amp input gain (dB) including automatic anti-clipping headroom. */
    val effectivePreampDb: Float,
    /** Positive headroom cut applied automatically to avoid digital clipping. */
    val autoHeadroomCutDb: Float,
)

/**
 * Pure DSP math for Audio Studio: combines AutoEq headphone compensation curves,
 * 9-band graphic EQ, Sennheiser/Sony-style Sound Check tilt/punch, automatic peak
 * headroom protection, and stereo channel balance.
 */
object AudioDspMath {

    const val BAND_COUNT = 10
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
    const val MIN_PREAMP_DB = -12f
    const val MAX_PREAMP_DB = 6f

    /** Map Sound Check XY pad coordinates (warmth: -1..+1, punch: -1..+1) to 10-band dB offsets. */
    fun soundCheckCurveDb(warmth: Float, punch: Float): List<Float> {
        val w = warmth.coerceIn(-1f, 1f)
        val p = punch.coerceIn(-1f, 1f)
        // Warmth weights low-mids up (+w) & upper treble down (-w); negative warmth brightens highs.
        // Punch adds sub-bass weight + 3 kHz articulation vs relaxed mid scoop when negative.
        val warmthWeights = floatArrayOf(1.2f, 1.8f, 2.2f, 1.8f, 0.8f, 0.0f, -1.0f, -1.8f, -2.4f, -2.8f)
        val punchWeights  = floatArrayOf(3.4f, 3.0f, 1.6f, 0.2f, -0.8f, 0.2f, 1.5f, 2.2f, 1.2f, 0.6f)
        return List(BAND_COUNT) { idx ->
            (warmthWeights[idx] * w + punchWeights[idx] * p).coerceIn(-6f, 6f)
        }
    }

    /** Convert 9-band mB gains (from EqCurves) into 10 ISO bands in dB. */
    fun resample9To10BandDb(userBandsMb: List<Int>): List<Float> {
        if (userBandsMb.isEmpty()) return List(BAND_COUNT) { 0f }
        // EqCurves 9 bands map approx [60, 120, 250, 500, 1k, 2k, 4k, 8k, 16k] -> expand 31 Hz from 60 Hz.
        val db9 = List(9) { i -> ((userBandsMb.getOrNull(i) ?: 0) / 100f).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB) }
        return listOf(
            db9[0] * 0.9f, // 31 Hz tracks sub/60 Hz smoothly
            db9[0],        // 62 Hz
            db9[1],        // 125 Hz
            db9[2],        // 250 Hz
            db9[3],        // 500 Hz
            db9[4],        // 1 kHz
            db9[5],        // 2 kHz
            db9[6],        // 4 kHz
            db9[7],        // 8 kHz
            db9[8],        // 16 kHz
        )
    }

    /** Resample 10-band dB gains down to Android legacy Equalizer's arbitrary N bands (in mB). */
    fun resample10BandDbToLegacyMb(gainsDb10: List<Float>, targetBandCount: Int, minMb: Int, maxMb: Int): List<Int> {
        if (targetBandCount <= 0) return emptyList()
        return List(targetBandCount) { i ->
            val pos = if (targetBandCount == 1) 0f else i.toFloat() * (BAND_COUNT - 1) / (targetBandCount - 1)
            val lo = pos.toInt().coerceIn(0, BAND_COUNT - 1)
            val hi = (lo + 1).coerceIn(0, BAND_COUNT - 1)
            val frac = pos - lo
            val db = gainsDb10.getOrElse(lo) { 0f } * (1f - frac) + gainsDb10.getOrElse(hi) { 0f } * frac
            (db * 100f).toInt().coerceIn(minMb, maxMb)
        }
    }

    /** Combine headphone AutoEq calibration, graphic EQ, Sound Check, and preamp with anti-clipping headroom. */
    fun computeEffectiveCurve(
        profileId: String?,
        userBandsMb: List<Int>,
        preampDb: Float,
        soundCheckWarmth: Float,
        soundCheckPunch: Float,
        autoHeadroom: Boolean = true,
    ): EffectiveAudioCurve {
        val hpCurve = HeadphoneProfiles.byId(profileId)?.gainsDb10 ?: List(BAND_COUNT) { 0f }
        val eqCurve = resample9To10BandDb(userBandsMb)
        val scCurve = soundCheckCurveDb(soundCheckWarmth, soundCheckPunch)

        val userCombined = List(BAND_COUNT) { i ->
            (eqCurve[i] + scCurve[i]).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }
        val total = List(BAND_COUNT) { i ->
            (hpCurve[i] + userCombined[i]).coerceIn(MIN_GAIN_DB, MAX_GAIN_DB)
        }

        val maxPositivePeak = (total.maxOrNull() ?: 0f).coerceAtLeast(0f)
        val clampedPreamp = preampDb.coerceIn(MIN_PREAMP_DB, MAX_PREAMP_DB)
        val cut = if (autoHeadroom) maxPositivePeak else 0f
        val effectivePreamp = (clampedPreamp - cut).coerceIn(-18f, MAX_PREAMP_DB)

        return EffectiveAudioCurve(
            totalBandsDb10 = total,
            headphoneCurveDb10 = hpCurve,
            userCurveDb10 = userCombined,
            effectivePreampDb = effectivePreamp,
            autoHeadroomCutDb = cut,
        )
    }

    /** Compute left & right channel input gain in dB given [effectivePreampDb] and [balance] in -1f..+1f. */
    fun channelGainsDb(effectivePreampDb: Float, balance: Float): Pair<Float, Float> {
        val b = balance.coerceIn(-1f, 1f)
        // Balance > 0 pans right (attenuates left); Balance < 0 pans left (attenuates right).
        val leftCut = if (b > 0f) abs(b) * 18f else 0f
        val rightCut = if (b < 0f) abs(b) * 18f else 0f
        return (effectivePreampDb - leftCut) to (effectivePreampDb - rightCut)
    }
}
