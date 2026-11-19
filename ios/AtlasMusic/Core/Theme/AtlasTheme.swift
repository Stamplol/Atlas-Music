// Atlas iOS — single-accent color tokens, all curated themes, radii and motion springs.
import SwiftUI

/// The complete Atlas palette. One accent only; every plane is a step toward the gold sun.
struct AtlasColors {
    var background: Color
    var surface: Color
    var surfaceElevated: Color
    var surfaceHighest: Color
    var text: Color
    var textMuted: Color
    var accent: Color
    var accentBright: Color
    var accentDim: Color
    var onAccent: Color
    var error: Color
    var scrim: Color
    var auraTop: Color
    var glow: Color
    var scrimSoft: Color
    var border: Color
}

extension Color {
    /// Android `Color(0xAARRGGBB)` literal, ported without rounding drift.
    init(atlasHex: UInt32) {
        self.init(
            .sRGB,
            red: CGFloat((atlasHex >> 16) & 0xFF) / 255,
            green: CGFloat((atlasHex >> 8) & 0xFF) / 255,
            blue: CGFloat(atlasHex & 0xFF) / 255,
            opacity: CGFloat((atlasHex >> 24) & 0xFF) / 255
        )
    }

    /// Unlabeled convenience spelling of `init(atlasHex:)` used by the iPod shell palettes
    /// (`Color(0xFF……)`). Without this overload those call sites resolve to no `Color` initializer.
    init(_ atlasHex: UInt32) {
        self.init(atlasHex: atlasHex)
    }
}

// MARK: - Exact Android values

private enum AtlasPalette {
    // AtlasDark — "deep observatory", from Color.kt.
    // border mirrors MaterialTheme's `outline` (SurfaceHighest) for ruled rows.
    static let dark = AtlasColors(
        background: Color(atlasHex: 0xFF0B0D12),
        surface: Color(atlasHex: 0xFF12151D),
        surfaceElevated: Color(atlasHex: 0xFF181D29),
        surfaceHighest: Color(atlasHex: 0xFF212839),
        text: Color(atlasHex: 0xFFF4F1E8),
        textMuted: Color(atlasHex: 0xFFA8A79C),
        accent: Color(atlasHex: 0xFFE5A83D),
        accentBright: Color(atlasHex: 0xFFF5C96B),
        accentDim: Color(atlasHex: 0xFF9A7420),
        onAccent: Color(atlasHex: 0xFF241A05),
        error: Color(atlasHex: 0xFFFF8A7A),
        scrim: Color(atlasHex: 0xA6000000),
        auraTop: Color(atlasHex: 0xFF1B2233),
        glow: Color(atlasHex: 0xFFE5A83D),
        scrimSoft: Color(atlasHex: 0x3394E2D5),
        border: Color(atlasHex: 0xFF212839)
    )

    // AtlasLight — "warm paper", from Color.kt.
    static let light = AtlasColors(
        background: Color(atlasHex: 0xFFFAF7EF),
        surface: Color(atlasHex: 0xFFFFFFFF),
        surfaceElevated: Color(atlasHex: 0xFFF0EDE1),
        surfaceHighest: Color(atlasHex: 0xFFE4E1D4),
        text: Color(atlasHex: 0xFF1A1C22),
        textMuted: Color(atlasHex: 0xFF6B6A63),
        accent: Color(atlasHex: 0xFF9A6B12),
        accentBright: Color(atlasHex: 0xFFB78A26),
        accentDim: Color(atlasHex: 0xFF6E4F0D),
        onAccent: Color(atlasHex: 0xFFFFF6DE),
        error: Color(atlasHex: 0xFFB3261E),
        scrim: Color(atlasHex: 0x66000000),
        auraTop: Color(atlasHex: 0xFFE9E4D6),
        glow: Color(atlasHex: 0xFF9A6B12),
        scrimSoft: Color(atlasHex: 0x22F9E2AF),
        border: Color(atlasHex: 0xFFE4E1D4)
    )

    /// Token row from Theme.kt `Pal` — every value is the exact `palFrom` literal.
    struct Tokens {
        let base: UInt32, mantle: UInt32, s0: UInt32, s1: UInt32, s2: UInt32
        let text: UInt32, sub: UInt32, dim: UInt32
        let blue: UInt32, mauve: UInt32, green: UInt32, yellow: UInt32
        let peach: UInt32, red: UInt32, teal: UInt32, border: UInt32
    }

    private static let mocha = Tokens(
        base: 0x11111B, mantle: 0x181825, s0: 0x1E1E2E, s1: 0x313244, s2: 0x45475A,
        text: 0xCDD6F4, sub: 0xA6ADC8, dim: 0x6C7086,
        blue: 0x89B4FA, mauve: 0xCBA6F7, green: 0xA6E3A1, yellow: 0xF9E2AF,
        peach: 0xFAB387, red: 0xF38BA8, teal: 0x94E2D5, border: 0x313244
    )
    private static let latte = Tokens(
        base: 0xEFF1F5, mantle: 0xE6E9EF, s0: 0xCCD0DA, s1: 0xBCC0CC, s2: 0xACB0BE,
        text: 0x4C4F69, sub: 0x5C5F77, dim: 0x8C8FA1,
        blue: 0x1E66F5, mauve: 0x8839EF, green: 0x40A02B, yellow: 0xDF8E1D,
        peach: 0xFE640B, red: 0xD20F39, teal: 0x179299, border: 0xBCC0CC
    )
    private static let gruvbox = Tokens(
        base: 0x282828, mantle: 0x1D2021, s0: 0x3C3836, s1: 0x504945, s2: 0x665C54,
        text: 0xEBDBB2, sub: 0xD5C4A1, dim: 0x928374,
        blue: 0x83A598, mauve: 0xB16286, green: 0xB8BB26, yellow: 0xFABD2F,
        peach: 0xFE8019, red: 0xFB4934, teal: 0x8EC07C, border: 0x504945
    )
    private static let tokyoNight = Tokens(
        base: 0x1A1B26, mantle: 0x16161E, s0: 0x24283B, s1: 0x414868, s2: 0x565F89,
        text: 0xC0CAF5, sub: 0xA9B1D6, dim: 0x565F89,
        blue: 0x7AA2F7, mauve: 0xBB9AF7, green: 0x9ECE6A, yellow: 0xE0AF68,
        peach: 0xFF9E64, red: 0xF7768E, teal: 0x7DCFFF, border: 0x2F354D
    )
    private static let nord = Tokens(
        base: 0x2E3440, mantle: 0x242933, s0: 0x3B4252, s1: 0x434C5E, s2: 0x4C566A,
        text: 0xECEFF4, sub: 0xD8DEE9, dim: 0x7B88A1,
        blue: 0x81A1C1, mauve: 0xB48EAD, green: 0xA3BE8C, yellow: 0xEBCB8B,
        peach: 0xD08770, red: 0xBF616A, teal: 0x88C0D0, border: 0x434C5E
    )
    private static let dracula = Tokens(
        base: 0x282A36, mantle: 0x21222C, s0: 0x343746, s1: 0x44475A, s2: 0x6272A4,
        text: 0xF8F8F2, sub: 0xE2E2DC, dim: 0x8B8FA8,
        blue: 0x8BE9FD, mauve: 0xBD93F9, green: 0x50FA7B, yellow: 0xF1FA8C,
        peach: 0xFFB86C, red: 0xFF5555, teal: 0x8BE9FD, border: 0x44475A
    )
    private static let kanagawa = Tokens(
        base: 0x1F1F28, mantle: 0x16161D, s0: 0x2A2A37, s1: 0x363646, s2: 0x54546D,
        text: 0xDCD7BA, sub: 0xC8C093, dim: 0x727169,
        blue: 0x7E9CD8, mauve: 0x957FB8, green: 0x98BB6C, yellow: 0xE6C384,
        peach: 0xFF9E3B, red: 0xE46876, teal: 0x7AA89F, border: 0x363646
    )
    private static let rosePine = Tokens(
        base: 0x191724, mantle: 0x1F1D2E, s0: 0x26233A, s1: 0x403D52, s2: 0x524F67,
        text: 0xE0DEF4, sub: 0x908CAA, dim: 0x6E6A86,
        blue: 0x9CCFD8, mauve: 0xEBBCBA, green: 0x9CCFD8, yellow: 0xF6C177,
        peach: 0xEBBCBA, red: 0xEB6F92, teal: 0x3E8FB0, border: 0x403D52
    )
    private static let everforest = Tokens(
        base: 0x2D353B, mantle: 0x232A2E, s0: 0x343F44, s1: 0x3D484D, s2: 0x475258,
        text: 0xD3C6AA, sub: 0xC5B896, dim: 0x859289,
        blue: 0x7FBBB3, mauve: 0xD699B6, green: 0xA7C080, yellow: 0xDBBC7F,
        peach: 0xE69875, red: 0xE67E80, teal: 0x83C092, border: 0x3D484D
    )
    private static let oxocarbon = Tokens(
        base: 0x161616, mantle: 0x0F0F0F, s0: 0x262626, s1: 0x393939, s2: 0x525252,
        text: 0xDDE1E6, sub: 0xC1C7CD, dim: 0x6F6F6F,
        blue: 0x82CFFF, mauve: 0xBE95FF, green: 0x42BE65, yellow: 0xE5B567,
        peach: 0xFF7EB6, red: 0xEE5396, teal: 0x3DDBD9, border: 0x393939
    )

    /// Mix two ARGB literals in integer space; used only for derived accent shades.
    private static func mix(_ a: UInt32, _ b: UInt32, _ amount: CGFloat) -> UInt32 {
        func channel(_ shift: Int) -> UInt32 {
            let av = CGFloat((a >> shift) & 0xFF)
            let bv = CGFloat((b >> shift) & 0xFF)
            return UInt32(round(av + (bv - av) * amount))
        }
        return (channel(24) << 24) | (channel(16) << 16) | (channel(8) << 8) | channel(0)
    }

    private static func make(_ t: Tokens, light: Bool) -> AtlasColors {
        let accent = t.blue
        return AtlasColors(
            background: Color(atlasHex: t.base),
            surface: Color(atlasHex: t.mantle),
            surfaceElevated: Color(atlasHex: t.s0),
            surfaceHighest: Color(atlasHex: t.s2),
            text: Color(atlasHex: t.text),
            textMuted: Color(atlasHex: t.sub),
            accent: Color(atlasHex: accent),
            accentBright: Color(atlasHex: mix(accent, 0xFFFFFF, 0.28)),
            accentDim: Color(atlasHex: mix(accent, 0xFF000000, 0.42)),
            onAccent: Color(atlasHex: light ? 0xEFF1F5 : 0x11111B),
            error: Color(atlasHex: t.red),
            scrim: Color(atlasHex: light ? 0x66000000 : 0xA6000000),
            auraTop: Color(atlasHex: t.mantle),
            glow: Color(atlasHex: accent),
            scrimSoft: Color(atlasHex: light ? 0x22F9E2AF : 0x3394E2D5),
            border: Color(atlasHex: t.border)
        )
    }

    /// Every non-system `ThemeMode` case, keyed exactly as Theme.kt maps it.
    static let presets: [ThemeMode: AtlasColors] = [
        .mocha: make(mocha, light: false),
        .latte: make(latte, light: true),
        .gruvbox: make(gruvbox, light: false),
        .tokyoNight: make(tokyoNight, light: false),
        .nord: make(nord, light: false),
        .dracula: make(dracula, light: false),
        .kanagawa: make(kanagawa, light: false),
        .rosePine: make(rosePine, light: false),
        .everforest: make(everforest, light: false),
        .oxocarbon: make(oxocarbon, light: false)
    ]
}

/// Resolution used by every reusable component: an explicit environment
/// injection wins; otherwise the persisted settings value is honored, with
/// `.system` following the ambient color scheme.
func atlasResolvedColors(injected: AtlasColors?, scheme: ColorScheme, stored mode: ThemeMode) -> AtlasColors {
    if let injected { return injected }
    switch mode {
    case .system: return scheme == .dark ? AtlasPalette.dark : AtlasPalette.light
    default: return AtlasTheme.colors(for: mode)
    }
}

enum AtlasTheme {
    /// `dynamic` is accepted for Android API parity. iOS exposes no public
    /// Material-You palette extraction, so the curated palette is always used —
    /// an explicit preset also wins on Android, so behavior matches.
    static func colors(for mode: ThemeMode, dynamic: Bool = false) -> AtlasColors {
        if let preset = AtlasPalette.presets[mode] { return preset }
        switch mode {
        case .light: return AtlasPalette.light
        case .system, .dark: return AtlasPalette.dark
        default: return AtlasPalette.dark
        }
    }

    /// Trait-aware resolution for `.system`, for use inside a SwiftUI view.
    static func colors(for mode: ThemeMode, dynamic: Bool = false, colorScheme: ColorScheme) -> AtlasColors {
        guard mode == .system else { return colors(for: mode, dynamic: dynamic) }
        return colorScheme == .dark ? AtlasPalette.dark : AtlasPalette.light
    }
}

// MARK: - Environment injection

private struct AtlasColorsEnvironmentKey: EnvironmentKey {
    static let defaultValue: AtlasColors? = nil
}

extension EnvironmentValues {
    /// Optional override set by the root view. When absent, components resolve
    /// the persisted theme from SettingsStore's `atlas.theme_mode` key.
    var atlasColors: AtlasColors? {
        get { self[AtlasColorsEnvironmentKey.self] }
        set { self[AtlasColorsEnvironmentKey.self] = newValue }
    }
}

extension View {
    /// Inject a preset (or dark/light) palette into every Atlas component.
    func atlasColors(_ colors: AtlasColors) -> some View {
        environment(\.atlasColors, colors)
    }
}

// MARK: - Tokens

enum Radius {
    /// Art thumbnails and small controls.
    static let sm: CGFloat = 10
    /// Rail / grouped cards.
    static let lg: CGFloat = 16
    /// Resume card, cover art, Now Playing chrome.
    static let xl: CGFloat = 24
}

enum AtlasMotion {
    /// MERIDIAN SETTLE — artwork and progress arc.
    static let settle = Spring(duration: 0.45, bounce: 0.18)
    /// Press, chip and small state feedback.
    static let quick = Spring(duration: 0.25, bounce: 0.05)
}
