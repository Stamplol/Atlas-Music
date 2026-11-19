// Atlas iOS — Combined AutoEq + graphic EQ + Sound Check DSP curve math.
import Foundation

/// Mirrors Android `EffectiveAudioCurve`.
struct EffectiveAudioCurve: Equatable {
    /// Final per-band EQ gains (dB, 10 bands across ISO 31 Hz .. 16 kHz).
    let totalBandsDb10: [Float]
    /// Underlying headphone AutoEq curve (dB) for the dashed reference plot.
    let headphoneCurveDb10: [Float]
    /// User EQ + Sound Check contribution (dB).
    let userCurveDb10: [Float]
    /// Combined pre-amp gain including automatic anti-clipping headroom.
    let effectivePreampDb: Float
    /// Positive headroom cut applied automatically to avoid digital clipping.
    let autoHeadroomCutDb: Float
}

/// Faithful port of Android `AudioDspMath`. All weights/constants match the source.
enum AudioDsp {
    static let BAND_COUNT = 10
    static let MIN_GAIN_DB: Float = -12
    static let MAX_GAIN_DB: Float = 12
    static let MIN_PREAMP_DB: Float = -12
    static let MAX_PREAMP_DB: Float = 6

    /// Map Sound Check XY pad coordinates (warmth/punch in -1..+1) to 10-band dB offsets.
    static func soundCheckCurveDb(warmth: Float, punch: Float) -> [Float] {
        let w = clamp(warmth, -1, 1)
        let p = clamp(punch, -1, 1)
        let warmthWeights: [Float] = [1.2, 1.8, 2.2, 1.8, 0.8, 0.0, -1.0, -1.8, -2.4, -2.8]
        let punchWeights: [Float] = [3.4, 3.0, 1.6, 0.2, -0.8, 0.2, 1.5, 2.2, 1.2, 0.6]
        return (0..<BAND_COUNT).map { idx in
            clamp(warmthWeights[idx] * w + punchWeights[idx] * p, -6, 6)
        }
    }

    /// Convert 9-band mB gains (EqCurves) into 10 ISO bands in dB.
    static func resample9To10BandDb(_ userBandsMb: [Int]) -> [Float] {
        if userBandsMb.isEmpty { return [Float](repeating: 0, count: BAND_COUNT) }
        let db9 = (0..<9).map { i -> Float in
            let mb = i < userBandsMb.count ? userBandsMb[i] : 0
            return clamp(Float(mb) / 100.0, MIN_GAIN_DB, MAX_GAIN_DB)
        }
        return [
            db9[0] * 0.9, // 31 Hz tracks sub/60 Hz smoothly
            db9[0], db9[1], db9[2], db9[3], db9[4], db9[5], db9[6], db9[7], db9[8],
        ]
    }

    /// Resample 10-band dB gains down to the legacy Equalizer's N bands (in mB).
    static func resample10BandDbToLegacyMb(_ gainsDb10: [Float], targetBandCount: Int,
                                           minMb: Int, maxMb: Int) -> [Int] {
        if targetBandCount <= 0 { return [] }
        return (0..<targetBandCount).map { i in
            let pos = targetBandCount == 1
                ? Float(0)
                : Float(i) * Float(BAND_COUNT - 1) / Float(targetBandCount - 1)
            let lo = clampInt(Int(pos), 0, BAND_COUNT - 1)
            let hi = clampInt(lo + 1, 0, BAND_COUNT - 1)
            let frac = pos - Float(lo)
            let a = lo < gainsDb10.count ? gainsDb10[lo] : 0
            let b = hi < gainsDb10.count ? gainsDb10[hi] : 0
            let db = a * (1 - frac) + b * frac
            return clampInt(Int(db * 100.0), minMb, maxMb)
        }
    }

    /// Combine AutoEq calibration, graphic EQ, Sound Check and preamp with anti-clipping headroom.
    static func computeEffectiveCurve(
        profileId: String?,
        userBandsMb: [Int],
        preampDb: Float,
        soundCheckWarmth: Float,
        soundCheckPunch: Float,
        autoHeadroom: Bool = true
    ) -> EffectiveAudioCurve {
        let hpCurve = HeadphoneProfiles.byID(profileId)?.gainsDb10
            ?? [Float](repeating: 0, count: BAND_COUNT)
        let eqCurve = resample9To10BandDb(userBandsMb)
        let scCurve = soundCheckCurveDb(warmth: soundCheckWarmth, punch: soundCheckPunch)

        let userCombined = (0..<BAND_COUNT).map { i in
            clamp(eqCurve[i] + scCurve[i], MIN_GAIN_DB, MAX_GAIN_DB)
        }
        let total = (0..<BAND_COUNT).map { i in
            clamp(hpCurve[i] + userCombined[i], MIN_GAIN_DB, MAX_GAIN_DB)
        }

        let maxPositivePeak = max(total.max() ?? 0, 0)
        let clampedPreamp = clamp(preampDb, MIN_PREAMP_DB, MAX_PREAMP_DB)
        let cut = autoHeadroom ? maxPositivePeak : 0
        let effectivePreamp = clamp(clampedPreamp - cut, -18, MAX_PREAMP_DB)

        return EffectiveAudioCurve(
            totalBandsDb10: total,
            headphoneCurveDb10: hpCurve,
            userCurveDb10: userCombined,
            effectivePreampDb: effectivePreamp,
            autoHeadroomCutDb: cut
        )
    }

    /// Left/right input gain in dB. Balance > 0 pans right (attenuates left).
    static func channelGainsDb(effectivePreampDb: Float, balance: Float) -> (Float, Float) {
        let b = clamp(balance, -1, 1)
        let leftCut: Float = b > 0 ? abs(b) * 18 : 0
        let rightCut: Float = b < 0 ? abs(b) * 18 : 0
        return (effectivePreampDb - leftCut, effectivePreampDb - rightCut)
    }

    private static func clamp(_ value: Float, _ low: Float, _ high: Float) -> Float {
        min(max(value, low), high)
    }

    private static func clampInt(_ value: Int, _ low: Int, _ high: Int) -> Int {
        min(max(value, low), high)
    }
}
