// Atlas iOS — centered loading/empty placeholder with a real optional action.
import SwiftUI

struct EmptyState: View {
    private let title: String
    private let message: String
    private let actionLabel: String?
    private let action: (() -> Void)?

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(title: String, body: String, actionLabel: String? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.message = body
        self.actionLabel = actionLabel
        self.action = action
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        VStack(spacing: 12) {
            Image(systemName: "music.note.list")
                .font(.system(size: 52, weight: .light))
                .foregroundStyle(c.textMuted.opacity(0.7))
                .accessibilityHidden(true)
            Text(title)
                .font(AtlasFont.title())
                .foregroundStyle(c.text)
                .multilineTextAlignment(.center)
            Text(message)
                .font(AtlasFont.small())
                .foregroundStyle(c.textMuted)
                .multilineTextAlignment(.center)
            if let actionLabel, let action {
                Button(action: action) {
                    Text(actionLabel)
                        .font(AtlasFont.body())
                        .foregroundStyle(c.onAccent)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 10)
                        .background(c.accent, in: Capsule())
                }
                .buttonStyle(EmptyActionPressStyle())
                .accessibilityLabel(actionLabel)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(32)
    }
}

private struct EmptyActionPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
            .opacity(configuration.isPressed ? 0.82 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
