// Atlas iOS — uppercase mono section eyebrow with an optional text action.
import SwiftUI

struct SectionHeader: View {
    private let title: String
    private let actionLabel: String?
    private let action: (() -> Void)?

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(_ title: String, actionLabel: String? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.actionLabel = actionLabel
        self.action = action
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        HStack(alignment: .center) {
            Text(title.uppercased())
                .font(AtlasFont.eyebrow())
                .kerning(1.4)
                .foregroundStyle(c.accent.opacity(0.9))
                .lineLimit(1)
            Spacer(minLength: 8)
            if let actionLabel, let action {
                Button(action: action) {
                    Text(actionLabel)
                        .font(AtlasFont.label())
                        .foregroundStyle(c.textMuted)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                }
                .buttonStyle(SectionActionPressStyle())
                .accessibilityLabel(actionLabel)
            }
        }
        .padding(EdgeInsets(top: 14, leading: 16, bottom: 8, trailing: 8))
    }
}

private struct SectionActionPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.55 : 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
