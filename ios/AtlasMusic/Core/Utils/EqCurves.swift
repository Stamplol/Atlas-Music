// Atlas iOS — The 16-preset graphic EQ catalog shared by playback and Audio Studio.
import Foundation

/// Faithful port of Android `EqCurves`. Gains are in milli-bels (mB), low -> high bands.
enum EqCurves {
    /// Kotlin `linkedMapOf` insertion order, preserved explicitly for `PRESET_ORDER`.
    static let CURVE_LIST: [(name: String, gains: [Int])] = [
        ("Bass",       [600, 600, 300, 0, 0, 0, 0, 0, -300]),
        ("Bass Boost", [800, 800, 400, 100, 0, 0, 0, 0, -100]),
        ("Bass Cut",   [-400, -400, -150, 0, 0, 0, 0, 0, 100]),
        ("Vocal",      [-300, -300, 0, 600, 600, 300, 0, 0, 0]),
        ("Vocal Cut",  [100, 100, 0, -500, -500, -300, 0, 0, 0]),
        ("Mid",        [0, 0, 0, 600, 600, 0, 0, 0, 0]),
        ("Treble",     [-300, 0, 0, 0, 300, 600, 600, 600, 600]),
        ("Treble Cut", [100, 0, 0, 0, -200, -400, -400, -400, -400]),
        ("Night",      [600, 300, 0, -300, -600, -600, -600, -300, 0]),
        ("Acoustic",   [100, 100, 100, 0, 300, 400, 500, 500, 400]),
        ("Rock",       [400, 300, 100, -100, -100, 0, 200, 300, 400]),
        ("Jazz",       [300, 200, 0, 200, 300, 300, 200, 100, 300]),
        ("Electronic", [500, 400, 100, -200, -300, 0, 200, 400, 500]),
        ("Hip-hop",    [600, 400, 200, -100, -100, 0, 200, 300, 200]),
        ("Podcast",    [-400, -200, 0, 600, 600, 400, 100, 0, -100]),
        ("Classical",  [300, 200, 0, 0, 0, 0, 100, 200, 300]),
    ]

    static let CURVES: [String: [Int]] = Dictionary(uniqueKeysWithValues: CURVE_LIST.map { ($0.name, $0.gains) })

    /// Display order — Flat first because it's the default.
    static let PRESET_ORDER: [String] = ["Flat"] + CURVE_LIST.map { $0.name }

    /// Flat is intentionally absent from CURVES (empty gains = bypass).
    static func gainsFor(_ name: String) -> [Int] { CURVES[name] ?? [] }

    /// Identify the preset name for persisted bands, or "Flat" / "Custom".
    static func matchingPresetName(_ bands: [Int]) -> String {
        if bands.isEmpty || bands.allSatisfy({ $0 == 0 }) { return "Flat" }
        for entry in CURVE_LIST where entry.gains == bands { return entry.name }
        return "Custom"
    }

    /// Generic band labels — the device's exact center frequencies are unknown.
    static let MANUAL_BANDS = [
        "Sub", "Bass", "Low-mid", "Mid", "Hi-mid", "Presence", "Brilliance", "Air", "Top",
    ]

    /// EQ band indices grouped into the three studio categories.
    static let LOW_END_BANDS = [0, 1, 2]
    static let MIDRANGE_BANDS = [3, 4, 5, 6]
    static let HIGH_END_BANDS = [7, 8]

    /// mB per full slider deflection.
    static let BAND_MAX_MB = 600
}
