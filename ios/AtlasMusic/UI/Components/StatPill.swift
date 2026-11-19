// Atlas iOS — compact capsule stat: mono value over a muted caption label.
import SwiftUI

struct StatPill: View {
    private let value: String
    private let label: String

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(value: String, label: String) {
        self.value = value
        self.label = label
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        HStack(spacing: 6) {
            Text(value)
                .font(AtlasFont.mono(size: 13))
                .monospacedDigit()
                .foregroundStyle(c.accent)
            Text(label)
                .font(AtlasFont.caption())
                .foregroundStyle(c.textMuted)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 7)
        .background(c.surfaceElevated, in: Capsule())
        .overlay(Capsule().stroke(c.border, lineWidth: 0.5))
        .accessibilityElement(children: .combine)
    }
}
