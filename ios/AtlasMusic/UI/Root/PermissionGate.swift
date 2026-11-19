// Atlas iOS — first-run access gate, faithful to Android MainActivity PermissionGate.
// On iOS there is no READ_MEDIA_AUDIO permission: access comes from the Files app / photo library,
// so the host decides what "grant" means and calls onGranted once access is available.
import SwiftUI

struct PermissionGate: View {
    @Environment(SettingsStore.self) private var settings

    let onGranted: () -> Void

    init(onGranted: @escaping () -> Void) { self.onGranted = onGranted }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    var body: some View {
        VStack(spacing: 0) {
            Spacer()
            Image(systemName: "music.note")
                .font(.system(size: 72))
                .foregroundStyle(colors.accent)
            Text("Atlas Music")
                .font(AtlasFont.display())
                .foregroundStyle(colors.text)
                .padding(.top, 20)
            Text("Ad-less Music. Your library, your rules.")
                .font(AtlasFont.body())
                .foregroundStyle(colors.textMuted)
                .multilineTextAlignment(.center)

            VStack(alignment: .leading, spacing: 6) {
                Text("One permission, one purpose")
                    .font(AtlasFont.title())
                    .foregroundStyle(colors.text)
                Text("Atlas needs access to your audio files to index them. Nothing is uploaded, no account is required, and you can revoke this any time in Settings. On iOS you grant access by choosing your music folder in the Files app.")
                    .font(AtlasFont.body())
                    .foregroundStyle(colors.textMuted)
            }
            .padding(20)
            .background(colors.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
            .padding(.top, 28)

            Button(action: onGranted) {
                Text("Grant music access")
                    .font(AtlasFont.body().weight(.semibold))
                    .foregroundStyle(colors.onAccent)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(colors.accent)
                    .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            }
            .padding(.top, 24)

            Button("I already granted it — check again", action: onGranted)
                .font(AtlasFont.caption())
                .foregroundStyle(colors.accent)
                .padding(.top, 8)
            Spacer()
        }
        .padding(32)
        .background(colors.background.ignoresSafeArea())
    }
}
