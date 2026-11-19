// Atlas iOS — Space Grotesk display / JetBrains Mono metadata, registered from bundled TTFs.
import SwiftUI
import CoreText

fileprivate enum AtlasFontFace: String, CaseIterable {
    case grotesk400 = "space_grotesk_400"
    case grotesk500 = "space_grotesk_500"
    case grotesk600 = "space_grotesk_600"
    case grotesk700 = "space_grotesk_700"
    case mono400 = "jetbrains_mono_400"
    case mono500 = "jetbrains_mono_500"
    case mono600 = "jetbrains_mono_600"
    case mono700 = "jetbrains_mono_700"

    var isMono: Bool { rawValue.hasPrefix("jetbrains") }
}

/// Registers the eight copied fonts once and resolves their real PostScript
/// names, so `Font.custom` hits the exact weight file instead of a synthesized one.
private final class AtlasFontRegistry {
    let names: [AtlasFontFace: String]

    init() {
        var resolved: [AtlasFontFace: String] = [:]
        for face in AtlasFontFace.allCases {
            guard let url = Bundle.main.url(forResource: face.rawValue, withExtension: "ttf"),
                  let data = try? Data(contentsOf: url),
                  let provider = CGDataProvider(data: data as CFData),
                  let cgFont = CGFont(provider) else { continue }

            var error: Unmanaged<CFError>?
            // A "font already registered" error is harmless (e.g. hot reload).
            _ = CTFontManagerRegisterGraphicsFont(cgFont, &error)
            error?.release()

            let ctFont = CTFontCreateWithGraphicsFont(cgFont, 12, nil, nil)
            if let psName = CTFontCopyPostScriptName(ctFont) as String? {
                resolved[face] = psName
            }
        }
        names = resolved
    }
}

enum AtlasFont {
    private static let registry = AtlasFontRegistry()

    /// Explicit registration entry point; normally called lazily by the helpers below.
    static func registerFonts() { _ = registry }

    private static func face(_ face: AtlasFontFace, _ size: CGFloat) -> Font {
        if let name = registry.names[face] { return Font.custom(name, size: size) }
        return face.isMono ? .system(size: size, design: .monospaced) : .system(size: size)
    }

    // Display / headline — Space Grotesk, tracked tight in source Type.kt.
    static func display() -> Font { face(.grotesk700, 34) }       // displaySmall
    static func headline() -> Font { face(.grotesk700, 26) }     // headlineMedium
    static func title() -> Font { face(.grotesk600, 20) }        // titleLarge
    static func rowTitle() -> Font { face(.grotesk500, 16) }     // titleMedium
    static func body() -> Font { face(.grotesk400, 15) }         // bodyLarge
    static func caption() -> Font { face(.grotesk400, 11) }      // bodySmall
    static func small() -> Font { face(.grotesk400, 13) }        // bodyMedium

    // JetBrains Mono — eyebrows, durations, counters, file paths.
    /// Callers apply `.kerning(1.4)` for the eyebrow tracking used on Android.
    static func eyebrow() -> Font { face(.mono600, 11) }
    static func mono(size: CGFloat = 11) -> Font { face(.mono600, size) }
    static func label() -> Font { face(.mono600, 12) }           // labelLarge
}
