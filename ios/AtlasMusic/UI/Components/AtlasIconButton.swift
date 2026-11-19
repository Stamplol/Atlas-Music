// Atlas iOS — icon-only control with long-press tooltip, press feedback and a11y label.
import SwiftUI

struct AtlasIconButton: View {
    private let systemName: String
    private let label: String
    private let action: () -> Void
    private let disabled: Bool
    private let tint: Color?

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark
    @State private var showingTip = false

    init(systemName: String, label: String, action: @escaping () -> Void,
         disabled: Bool = false, tint: Color? = nil) {
        self.systemName = systemName
        self.label = label
        self.action = action
        self.disabled = disabled
        self.tint = tint
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        Button(action: action) {
            Image(systemName: systemName)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(tint ?? c.textMuted)
                .frame(width: 38, height: 38)
                .contentShape(Rectangle())
        }
        .buttonStyle(AtlasIconPressStyle())
        .disabled(disabled)
        .opacity(disabled ? 0.38 : 1)
        .accessibilityLabel(label)
        .overlay(alignment: .bottom) {
            if showingTip && !disabled {
                Text(label)
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.text)
                    .fixedSize()
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(c.surfaceHighest, in: Capsule())
                    .overlay(Capsule().stroke(c.border, lineWidth: 0.5))
                    .offset(y: 30)
                    .transition(.opacity.combined(with: .scale(scale: 0.92)))
                    .allowsHitTesting(false)
                    .zIndex(10)
            }
        }
        .simultaneousGesture(
            LongPressGesture(minimumDuration: 0.4)
                .onEnded { _ in
                    guard !disabled else { return }
                    withAnimation(AtlasMotion.quick) { showingTip = true }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.6) {
                        withAnimation(AtlasMotion.quick) { showingTip = false }
                    }
                }
        )
    }
}

private struct AtlasIconPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.88 : 1)
            .opacity(configuration.isPressed ? 0.6 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
