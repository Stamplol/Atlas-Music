package com.atlas.music.util

/**
 * Curated Harman-target AutoEq calibration curves for popular headphones and IEMs
 * across 10 ISO frequencies (31 Hz to 16 kHz, values in dB).
 */
data class HeadphoneProfile(
    val id: String,
    val brand: String,
    val model: String,
    val formFactor: String,
    val summary: String,
    /** 10 gains in dB: [31, 62, 125, 250, 500, 1k, 2k, 4k, 8k, 16k] Hz */
    val gainsDb10: List<Float>,
) {
    val displayName: String get() = "$brand $model"
}

object HeadphoneProfiles {

    val ISO_FREQUENCIES_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)

    val PRESETS: List<HeadphoneProfile> = listOf(
        HeadphoneProfile(
            id = "sony_wh1000xm5",
            brand = "Sony",
            model = "WH-1000XM5",
            formFactor = "Over-ear ANC",
            summary = "Tames mid-bass bloom and lifts upper-mid clarity & air",
            gainsDb10 = listOf(1.2f, -1.8f, -3.5f, -2.4f, 0.4f, 1.0f, 1.8f, 3.2f, 2.4f, 1.5f),
        ),
        HeadphoneProfile(
            id = "sony_wh1000xm4",
            brand = "Sony",
            model = "WH-1000XM4",
            formFactor = "Over-ear ANC",
            summary = "Cleans thick 100–250 Hz warmth and balances treble detail",
            gainsDb10 = listOf(0.5f, -3.2f, -4.6f, -3.0f, 0.8f, 1.5f, 2.6f, 3.8f, 1.2f, 2.0f),
        ),
        HeadphoneProfile(
            id = "sony_wf1000xm5",
            brand = "Sony",
            model = "WF-1000XM5",
            formFactor = "True Wireless",
            summary = "Tightens low-mids and opens vocals around 3–6 kHz",
            gainsDb10 = listOf(0.4f, -0.6f, -1.5f, -1.8f, 0.2f, 0.8f, 2.2f, 3.6f, 2.0f, 1.2f),
        ),
        HeadphoneProfile(
            id = "apple_airpods_pro_2",
            brand = "Apple",
            model = "AirPods Pro (2nd gen)",
            formFactor = "True Wireless",
            summary = "Fills sub-bass extension and smooths upper-mid glare",
            gainsDb10 = listOf(1.8f, 1.1f, -0.4f, -1.2f, 0.0f, 0.6f, 1.4f, -0.8f, 1.5f, 2.2f),
        ),
        HeadphoneProfile(
            id = "apple_airpods_max",
            brand = "Apple",
            model = "AirPods Max",
            formFactor = "Over-ear ANC",
            summary = "Restores natural 3 kHz vocal body and trims mid-bass hump",
            gainsDb10 = listOf(0.8f, 0.2f, -1.4f, -1.0f, 0.5f, 0.8f, 3.4f, 2.8f, -1.2f, 1.6f),
        ),
        HeadphoneProfile(
            id = "apple_airpods_3",
            brand = "Apple",
            model = "AirPods (3rd gen)",
            formFactor = "True Wireless",
            summary = "Compensates open-fit sub-bass rolloff and softens 5 kHz peak",
            gainsDb10 = listOf(4.5f, 3.2f, 1.4f, -0.5f, 0.0f, 0.4f, 1.2f, -1.8f, 0.6f, 1.4f),
        ),
        HeadphoneProfile(
            id = "apple_earpods",
            brand = "Apple",
            model = "EarPods (USB-C / 3.5mm)",
            formFactor = "Wired Earbud",
            summary = "Sub-bass boost for open earbuds and smoother 2–4 kHz",
            gainsDb10 = listOf(5.0f, 3.8f, 1.2f, -1.4f, -0.4f, 0.5f, -1.2f, -0.8f, 1.6f, 2.2f),
        ),
        HeadphoneProfile(
            id = "sennheiser_hd600",
            brand = "Sennheiser",
            model = "HD 600",
            formFactor = "Over-ear Open",
            summary = "Adds sub-bass extension below 60 Hz while preserving neutral mids",
            gainsDb10 = listOf(5.2f, 3.8f, 1.2f, -0.6f, -0.4f, 0.0f, -0.8f, -0.4f, 0.8f, 1.6f),
        ),
        HeadphoneProfile(
            id = "sennheiser_hd650",
            brand = "Sennheiser",
            model = "HD 650 / HD 6XX",
            formFactor = "Over-ear Open",
            summary = "Harman sub-bass lift and gentle 4–8 kHz brilliance",
            gainsDb10 = listOf(4.8f, 3.0f, -0.4f, -1.5f, -0.2f, 0.4f, 0.8f, 1.8f, 1.5f, 2.0f),
        ),
        HeadphoneProfile(
            id = "sennheiser_hd800s",
            brand = "Sennheiser",
            model = "HD 800 S",
            formFactor = "Over-ear Open",
            summary = "Rich sub-bass warmth and smooths the 6 kHz treble spike",
            gainsDb10 = listOf(5.4f, 3.6f, 1.0f, -0.4f, 0.6f, 1.0f, 2.2f, 0.4f, -3.8f, 0.8f),
        ),
        HeadphoneProfile(
            id = "sennheiser_momentum_4",
            brand = "Sennheiser",
            model = "Momentum 4 Wireless",
            formFactor = "Over-ear ANC",
            summary = "Reduces deep bass emphasis for tighter low-end and vocal focus",
            gainsDb10 = listOf(-2.4f, -3.2f, -1.8f, -0.6f, 0.8f, 1.4f, 2.0f, 1.6f, -0.4f, 1.0f),
        ),
        HeadphoneProfile(
            id = "sennheiser_ie200",
            brand = "Sennheiser",
            model = "IE 200",
            formFactor = "In-Ear Monitor",
            summary = "Lifts sub-bass seal and refines 3–6 kHz shimmer",
            gainsDb10 = listOf(3.2f, 1.8f, 0.4f, -0.4f, 0.2f, 0.4f, 1.2f, -1.0f, -1.6f, 0.8f),
        ),
        HeadphoneProfile(
            id = "samsung_buds2_pro",
            brand = "Samsung",
            model = "Galaxy Buds2 Pro",
            formFactor = "True Wireless",
            summary = "Smooths 5–8 kHz energy and deepens sub-bass authority",
            gainsDb10 = listOf(1.6f, 0.6f, -0.6f, -0.8f, 0.2f, 0.4f, -0.4f, -1.8f, -2.6f, 0.8f),
        ),
        HeadphoneProfile(
            id = "samsung_buds_fe",
            brand = "Samsung",
            model = "Galaxy Buds FE",
            formFactor = "True Wireless",
            summary = "Balances warm mid-bass and opens treble air",
            gainsDb10 = listOf(1.2f, -0.8f, -2.0f, -1.2f, 0.4f, 0.6f, 1.2f, 0.8f, -1.2f, 1.8f),
        ),
        HeadphoneProfile(
            id = "ath_m50x",
            brand = "Audio-Technica",
            model = "ATH-M50x",
            formFactor = "Over-ear Studio",
            summary = "Tames mid-bass thump and 10 kHz glare with fuller vocals",
            gainsDb10 = listOf(-0.6f, -2.2f, -3.0f, -1.2f, 1.0f, 1.4f, 1.8f, 0.6f, -2.0f, -1.2f),
        ),
        HeadphoneProfile(
            id = "beyer_dt770_80",
            brand = "Beyerdynamic",
            model = "DT 770 Pro (80Ω)",
            formFactor = "Over-ear Studio",
            summary = "Calms the classic Beyer 8 kHz treble peak and warms upper mids",
            gainsDb10 = listOf(1.6f, 0.4f, -0.6f, 1.4f, 0.4f, 0.2f, 2.0f, -1.2f, -4.4f, -1.2f),
        ),
        HeadphoneProfile(
            id = "beyer_dt990",
            brand = "Beyerdynamic",
            model = "DT 990 Pro",
            formFactor = "Over-ear Open",
            summary = "Fills deep sub-bass and smooths sharp 6–10 kHz treble",
            gainsDb10 = listOf(4.2f, 2.0f, -1.8f, -1.4f, 0.6f, 1.0f, 1.6f, -1.4f, -5.2f, -2.0f),
        ),
        HeadphoneProfile(
            id = "beyer_dt1990",
            brand = "Beyerdynamic",
            model = "DT 1990 Pro",
            formFactor = "Over-ear Open",
            summary = "Adds sub-bass weight and removes 8 kHz analytical bite",
            gainsDb10 = listOf(3.8f, 1.8f, -0.8f, -0.6f, 0.5f, 0.6f, 1.8f, -0.8f, -4.6f, -1.4f),
        ),
        HeadphoneProfile(
            id = "bose_qc_ultra",
            brand = "Bose",
            model = "QuietComfort Ultra",
            formFactor = "Over-ear ANC",
            summary = "Refines low-mid warmth and brings forward 2–4 kHz vocals",
            gainsDb10 = listOf(0.8f, -1.2f, -2.6f, -1.4f, 0.4f, 1.2f, 2.4f, 1.4f, -1.6f, 1.2f),
        ),
        HeadphoneProfile(
            id = "bose_qc45",
            brand = "Bose",
            model = "QuietComfort 45",
            formFactor = "Over-ear ANC",
            summary = "Smoothes upper-treble sharpness and adds gentle sub weight",
            gainsDb10 = listOf(1.8f, 0.6f, -0.8f, -0.6f, 0.2f, 0.4f, 1.0f, -0.8f, -3.2f, -1.0f),
        ),
        HeadphoneProfile(
            id = "moondrop_blessing3",
            brand = "Moondrop",
            model = "Blessing 3",
            formFactor = "In-Ear Monitor",
            summary = "Subtle sub-bass warmth and tamed 3–6 kHz pinna gain",
            gainsDb10 = listOf(2.4f, 1.2f, -0.4f, -0.8f, 0.0f, 0.4f, -1.0f, -1.4f, -0.8f, 1.0f),
        ),
        HeadphoneProfile(
            id = "moondrop_chu2",
            brand = "Moondrop",
            model = "Chu II",
            formFactor = "In-Ear Monitor",
            summary = "Smoother upper treble with tight Harman bass balance",
            gainsDb10 = listOf(0.8f, 0.2f, -0.8f, -0.6f, 0.4f, 0.6f, 0.4f, -1.6f, -1.8f, 0.6f),
        ),
        HeadphoneProfile(
            id = "moondrop_aria2",
            brand = "Moondrop",
            model = "Aria 2",
            formFactor = "In-Ear Monitor",
            summary = "Polishes 5–8 kHz energy while retaining airy mids",
            gainsDb10 = listOf(1.4f, 0.6f, -0.4f, -0.4f, 0.2f, 0.4f, 0.2f, -1.4f, -2.0f, 0.8f),
        ),
        HeadphoneProfile(
            id = "truthear_hexa",
            brand = "Truthear",
            model = "Hexa",
            formFactor = "In-Ear Monitor",
            summary = "Adds gentle sub-bass warmth and smooths 3–8 kHz",
            gainsDb10 = listOf(2.6f, 1.4f, 0.2f, -0.2f, 0.0f, 0.2f, -0.8f, -1.2f, -1.6f, 0.4f),
        ),
        HeadphoneProfile(
            id = "truthear_zero_red",
            brand = "Truthear",
            model = "ZERO:RED (x Crinacle)",
            formFactor = "In-Ear Monitor",
            summary = "Sub-bass boost & slight upper-mid presence lift to match Harman",
            gainsDb10 = listOf(1.6f, 0.8f, -0.2f, -0.4f, 0.2f, 0.6f, 1.0f, 0.6f, -0.8f, 0.8f),
        ),
        HeadphoneProfile(
            id = "kz_zsn_pro",
            brand = "KZ",
            model = "ZSN Pro",
            formFactor = "In-Ear Monitor",
            summary = "Calms aggressive V-shaped 2–8 kHz peaks for fatigue-free listening",
            gainsDb10 = listOf(-0.4f, -1.2f, -1.0f, 0.8f, 1.4f, 0.6f, -2.8f, -3.6f, -3.4f, -1.0f),
        ),
        HeadphoneProfile(
            id = "tinhifi_t2",
            brand = "TinHiFi",
            model = "T2",
            formFactor = "In-Ear Monitor",
            summary = "Brings sub-bass warmth into neutral monitors and softens 2.5 kHz",
            gainsDb10 = listOf(4.6f, 2.8f, 0.8f, -0.6f, 0.0f, 0.2f, -1.6f, 0.4f, -1.2f, 1.4f),
        ),
        HeadphoneProfile(
            id = "koss_porta_pro",
            brand = "Koss",
            model = "Porta Pro",
            formFactor = "On-ear Classic",
            summary = "Cleans warm 120–250 Hz mid-bass and opens treble detail",
            gainsDb10 = listOf(3.8f, 0.4f, -2.8f, -2.4f, 0.2f, 1.2f, 2.6f, 3.2f, 2.4f, 2.0f),
        ),
        HeadphoneProfile(
            id = "koss_ksc75",
            brand = "Koss",
            model = "KSC75",
            formFactor = "On-ear Classic",
            summary = "Lifts sub-bass warmth and softens 5 kHz bright bite",
            gainsDb10 = listOf(5.4f, 3.6f, 1.0f, -0.6f, -0.4f, 0.4f, -1.0f, -2.2f, -0.6f, 1.2f),
        ),
        HeadphoneProfile(
            id = "philips_shp9500",
            brand = "Philips",
            model = "SHP9500",
            formFactor = "Over-ear Open",
            summary = "Strong sub-bass extension and tamed 6 kHz grain",
            gainsDb10 = listOf(5.6f, 3.8f, 1.4f, -0.4f, 0.4f, 0.8f, 0.6f, -1.8f, -2.6f, 0.6f),
        ),
        HeadphoneProfile(
            id = "hifiman_sundara",
            brand = "HIFIMAN",
            model = "Sundara (2020)",
            formFactor = "Over-ear Planar",
            summary = "Linear sub-bass planar shelf and balanced 2 kHz vocal dip",
            gainsDb10 = listOf(4.2f, 2.4f, 0.6f, 0.0f, 0.2f, 0.4f, 2.2f, -0.6f, -1.8f, 0.4f),
        ),
        HeadphoneProfile(
            id = "harman_universal",
            brand = "Harman",
            model = "2019 Universal Contour",
            formFactor = "Universal Reference",
            summary = "Balanced studio warmth with crisp vocal presence and gentle sub shelf",
            gainsDb10 = listOf(2.5f, 1.8f, 0.5f, -0.5f, 0.0f, 0.4f, 1.2f, 1.6f, 0.8f, 0.4f),
        ),
    )

    fun byId(id: String?): HeadphoneProfile? =
        if (id.isNullOrBlank()) null else PRESETS.firstOrNull { it.id == id }

    fun search(query: String): List<HeadphoneProfile> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return PRESETS
        return PRESETS.filter {
            it.brand.lowercase().contains(q) ||
                it.model.lowercase().contains(q) ||
                it.formFactor.lowercase().contains(q)
        }
    }
}
