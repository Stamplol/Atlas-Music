// Atlas iOS — Curated Harman-target AutoEq headphone/IEM compensation curves.
import Foundation

/// Mirrors Android `HeadphoneProfile`. Gains are dB across ISO_FREQUENCIES_HZ.
struct HeadphoneProfile: Identifiable, Equatable {
    let id: String
    let brand: String
    let model: String
    let formFactor: String
    let summary: String
    /// 10 gains in dB: [31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k] Hz.
    let gainsDb10: [Float]

    var displayName: String { "\(brand) \(model)" }
}

/// Faithful port of Android `HeadphoneProfiles` (all 32 source profiles).
enum HeadphoneProfiles {
    static let ISO_FREQUENCIES_HZ = [31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000]

    static let PRESETS: [HeadphoneProfile] = [
        HeadphoneProfile(id: "sony_wh1000xm5", brand: "Sony", model: "WH-1000XM5",
            formFactor: "Over-ear ANC", summary: "Tames mid-bass bloom and lifts upper-mid clarity & air",
            gainsDb10: [1.2, -1.8, -3.5, -2.4, 0.4, 1.0, 1.8, 3.2, 2.4, 1.5]),
        HeadphoneProfile(id: "sony_wh1000xm4", brand: "Sony", model: "WH-1000XM4",
            formFactor: "Over-ear ANC", summary: "Cleans thick 100–250 Hz warmth and balances treble detail",
            gainsDb10: [0.5, -3.2, -4.6, -3.0, 0.8, 1.5, 2.6, 3.8, 1.2, 2.0]),
        HeadphoneProfile(id: "sony_wf1000xm5", brand: "Sony", model: "WF-1000XM5",
            formFactor: "True Wireless", summary: "Tightens low-mids and opens vocals around 3–6 kHz",
            gainsDb10: [0.4, -0.6, -1.5, -1.8, 0.2, 0.8, 2.2, 3.6, 2.0, 1.2]),
        HeadphoneProfile(id: "apple_airpods_pro_2", brand: "Apple", model: "AirPods Pro (2nd gen)",
            formFactor: "True Wireless", summary: "Fills sub-bass extension and smooths upper-mid glare",
            gainsDb10: [1.8, 1.1, -0.4, -1.2, 0.0, 0.6, 1.4, -0.8, 1.5, 2.2]),
        HeadphoneProfile(id: "apple_airpods_max", brand: "Apple", model: "AirPods Max",
            formFactor: "Over-ear ANC", summary: "Restores natural 3 kHz vocal body and trims mid-bass hump",
            gainsDb10: [0.8, 0.2, -1.4, -1.0, 0.5, 0.8, 3.4, 2.8, -1.2, 1.6]),
        HeadphoneProfile(id: "apple_airpods_3", brand: "Apple", model: "AirPods (3rd gen)",
            formFactor: "True Wireless", summary: "Compensates open-fit sub-bass rolloff and softens 5 kHz peak",
            gainsDb10: [4.5, 3.2, 1.4, -0.5, 0.0, 0.4, 1.2, -1.8, 0.6, 1.4]),
        HeadphoneProfile(id: "apple_earpods", brand: "Apple", model: "EarPods (USB-C / 3.5mm)",
            formFactor: "Wired Earbud", summary: "Sub-bass boost for open earbuds and smoother 2–4 kHz",
            gainsDb10: [5.0, 3.8, 1.2, -1.4, -0.4, 0.5, -1.2, -0.8, 1.6, 2.2]),
        HeadphoneProfile(id: "sennheiser_hd600", brand: "Sennheiser", model: "HD 600",
            formFactor: "Over-ear Open", summary: "Adds sub-bass extension below 60 Hz while preserving neutral mids",
            gainsDb10: [5.2, 3.8, 1.2, -0.6, -0.4, 0.0, -0.8, -0.4, 0.8, 1.6]),
        HeadphoneProfile(id: "sennheiser_hd650", brand: "Sennheiser", model: "HD 650 / HD 6XX",
            formFactor: "Over-ear Open", summary: "Harman sub-bass lift and gentle 4–8 kHz brilliance",
            gainsDb10: [4.8, 3.0, -0.4, -1.5, -0.2, 0.4, 0.8, 1.8, 1.5, 2.0]),
        HeadphoneProfile(id: "sennheiser_hd800s", brand: "Sennheiser", model: "HD 800 S",
            formFactor: "Over-ear Open", summary: "Rich sub-bass warmth and smooths the 6 kHz treble spike",
            gainsDb10: [5.4, 3.6, 1.0, -0.4, 0.6, 1.0, 2.2, 0.4, -3.8, 0.8]),
        HeadphoneProfile(id: "sennheiser_momentum_4", brand: "Sennheiser", model: "Momentum 4 Wireless",
            formFactor: "Over-ear ANC", summary: "Reduces deep bass emphasis for tighter low-end and vocal focus",
            gainsDb10: [-2.4, -3.2, -1.8, -0.6, 0.8, 1.4, 2.0, 1.6, -0.4, 1.0]),
        HeadphoneProfile(id: "sennheiser_ie200", brand: "Sennheiser", model: "IE 200",
            formFactor: "In-Ear Monitor", summary: "Lifts sub-bass seal and refines 3–6 kHz shimmer",
            gainsDb10: [3.2, 1.8, 0.4, -0.4, 0.2, 0.4, 1.2, -1.0, -1.6, 0.8]),
        HeadphoneProfile(id: "samsung_buds2_pro", brand: "Samsung", model: "Galaxy Buds2 Pro",
            formFactor: "True Wireless", summary: "Smooths 5–8 kHz energy and deepens sub-bass authority",
            gainsDb10: [1.6, 0.6, -0.6, -0.8, 0.2, 0.4, -0.4, -1.8, -2.6, 0.8]),
        HeadphoneProfile(id: "samsung_buds_fe", brand: "Samsung", model: "Galaxy Buds FE",
            formFactor: "True Wireless", summary: "Balances warm mid-bass and opens treble air",
            gainsDb10: [1.2, -0.8, -2.0, -1.2, 0.4, 0.6, 1.2, 0.8, -1.2, 1.8]),
        HeadphoneProfile(id: "ath_m50x", brand: "Audio-Technica", model: "ATH-M50x",
            formFactor: "Over-ear Studio", summary: "Tames mid-bass thump and 10 kHz glare with fuller vocals",
            gainsDb10: [-0.6, -2.2, -3.0, -1.2, 1.0, 1.4, 1.8, 0.6, -2.0, -1.2]),
        HeadphoneProfile(id: "beyer_dt770_80", brand: "Beyerdynamic", model: "DT 770 Pro (80Ω)",
            formFactor: "Over-ear Studio", summary: "Calms the classic Beyer 8 kHz treble peak and warms upper mids",
            gainsDb10: [1.6, 0.4, -0.6, 1.4, 0.4, 0.2, 2.0, -1.2, -4.4, -1.2]),
        HeadphoneProfile(id: "beyer_dt990", brand: "Beyerdynamic", model: "DT 990 Pro",
            formFactor: "Over-ear Open", summary: "Fills deep sub-bass and smooths sharp 6–10 kHz treble",
            gainsDb10: [4.2, 2.0, -1.8, -1.4, 0.6, 1.0, 1.6, -1.4, -5.2, -2.0]),
        HeadphoneProfile(id: "beyer_dt1990", brand: "Beyerdynamic", model: "DT 1990 Pro",
            formFactor: "Over-ear Open", summary: "Adds sub-bass weight and removes 8 kHz analytical bite",
            gainsDb10: [3.8, 1.8, -0.8, -0.6, 0.5, 0.6, 1.8, -0.8, -4.6, -1.4]),
        HeadphoneProfile(id: "bose_qc_ultra", brand: "Bose", model: "QuietComfort Ultra",
            formFactor: "Over-ear ANC", summary: "Refines low-mid warmth and brings forward 2–4 kHz vocals",
            gainsDb10: [0.8, -1.2, -2.6, -1.4, 0.4, 1.2, 2.4, 1.4, -1.6, 1.2]),
        HeadphoneProfile(id: "bose_qc45", brand: "Bose", model: "QuietComfort 45",
            formFactor: "Over-ear ANC", summary: "Smoothes upper-treble sharpness and adds gentle sub weight",
            gainsDb10: [1.8, 0.6, -0.8, -0.6, 0.2, 0.4, 1.0, -0.8, -3.2, -1.0]),
        HeadphoneProfile(id: "moondrop_blessing3", brand: "Moondrop", model: "Blessing 3",
            formFactor: "In-Ear Monitor", summary: "Subtle sub-bass warmth and tamed 3–6 kHz pinna gain",
            gainsDb10: [2.4, 1.2, -0.4, -0.8, 0.0, 0.4, -1.0, -1.4, -0.8, 1.0]),
        HeadphoneProfile(id: "moondrop_chu2", brand: "Moondrop", model: "Chu II",
            formFactor: "In-Ear Monitor", summary: "Smoother upper treble with tight Harman bass balance",
            gainsDb10: [0.8, 0.2, -0.8, -0.6, 0.4, 0.6, 0.4, -1.6, -1.8, 0.6]),
        HeadphoneProfile(id: "moondrop_aria2", brand: "Moondrop", model: "Aria 2",
            formFactor: "In-Ear Monitor", summary: "Polishes 5–8 kHz energy while retaining airy mids",
            gainsDb10: [1.4, 0.6, -0.4, -0.4, 0.2, 0.4, 0.2, -1.4, -2.0, 0.8]),
        HeadphoneProfile(id: "truthear_hexa", brand: "Truthear", model: "Hexa",
            formFactor: "In-Ear Monitor", summary: "Adds gentle sub-bass warmth and smooths 3–8 kHz",
            gainsDb10: [2.6, 1.4, 0.2, -0.2, 0.0, 0.2, -0.8, -1.2, -1.6, 0.4]),
        HeadphoneProfile(id: "truthear_zero_red", brand: "Truthear", model: "ZERO:RED (x Crinacle)",
            formFactor: "In-Ear Monitor", summary: "Sub-bass boost & slight upper-mid presence lift to match Harman",
            gainsDb10: [1.6, 0.8, -0.2, -0.4, 0.2, 0.6, 1.0, 0.6, -0.8, 0.8]),
        HeadphoneProfile(id: "kz_zsn_pro", brand: "KZ", model: "ZSN Pro",
            formFactor: "In-Ear Monitor", summary: "Calms aggressive V-shaped 2–8 kHz peaks for fatigue-free listening",
            gainsDb10: [-0.4, -1.2, -1.0, 0.8, 1.4, 0.6, -2.8, -3.6, -3.4, -1.0]),
        HeadphoneProfile(id: "tinhifi_t2", brand: "TinHiFi", model: "T2",
            formFactor: "In-Ear Monitor", summary: "Brings sub-bass warmth into neutral monitors and softens 2.5 kHz",
            gainsDb10: [4.6, 2.8, 0.8, -0.6, 0.0, 0.2, -1.6, 0.4, -1.2, 1.4]),
        HeadphoneProfile(id: "koss_porta_pro", brand: "Koss", model: "Porta Pro",
            formFactor: "On-ear Classic", summary: "Cleans warm 120–250 Hz mid-bass and opens treble detail",
            gainsDb10: [3.8, 0.4, -2.8, -2.4, 0.2, 1.2, 2.6, 3.2, 2.4, 2.0]),
        HeadphoneProfile(id: "koss_ksc75", brand: "Koss", model: "KSC75",
            formFactor: "On-ear Classic", summary: "Lifts sub-bass warmth and softens 5 kHz bright bite",
            gainsDb10: [5.4, 3.6, 1.0, -0.6, -0.4, 0.4, -1.0, -2.2, -0.6, 1.2]),
        HeadphoneProfile(id: "philips_shp9500", brand: "Philips", model: "SHP9500",
            formFactor: "Over-ear Open", summary: "Strong sub-bass extension and tamed 6 kHz grain",
            gainsDb10: [5.6, 3.8, 1.4, -0.4, 0.4, 0.8, 0.6, -1.8, -2.6, 0.6]),
        HeadphoneProfile(id: "hifiman_sundara", brand: "HIFIMAN", model: "Sundara (2020)",
            formFactor: "Over-ear Planar", summary: "Linear sub-bass planar shelf and balanced 2 kHz vocal dip",
            gainsDb10: [4.2, 2.4, 0.6, 0.0, 0.2, 0.4, 2.2, -0.6, -1.8, 0.4]),
        HeadphoneProfile(id: "harman_universal", brand: "Harman", model: "2019 Universal Contour",
            formFactor: "Universal Reference", summary: "Balanced studio warmth with crisp vocal presence and gentle sub shelf",
            gainsDb10: [2.5, 1.8, 0.5, -0.5, 0.0, 0.4, 1.2, 1.6, 0.8, 0.4]),
    ]

    static func byID(_ id: String?) -> HeadphoneProfile? {
        guard let id, !id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return PRESETS.first { $0.id == id }
    }

    /// Android spelling alias.
    static func byId(_ id: String?) -> HeadphoneProfile? { byID(id) }

    static func search(_ query: String) -> [HeadphoneProfile] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return PRESETS }
        return PRESETS.filter {
            $0.brand.lowercased().contains(q) ||
            $0.model.lowercased().contains(q) ||
            $0.formFactor.lowercased().contains(q)
        }
    }
}
