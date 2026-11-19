// Atlas iOS — Sleep timer: countdown, one-tap durations, end-of-track and default minutes.
import SwiftUI
import Foundation

/// Sleep controls. The countdown mirrors PlayerController's live sleepTimerEndAt.
struct SleepView: View {
    @Environment(ToolsViewModel.self) private var toolsVM
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var remainingMs: Int64 {
        guard let end = player.sleepTimerEndAt else { return 0 }
        return max(0, Int64(end.timeIntervalSinceNow))
    }

    private var isActive: Bool { remainingMs > 0 }

    var body: some View {
        let c = colors
        ScrollView(showsIndicators: false) {
            VStack(spacing: 12) {
                statusCard(c)
                endOfTrackCard(c)
                defaultCard(c)
            }
            .padding(16)
        }
    }

    private func statusCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("SLEEP", trailing: isActive ? "counting down" : nil)
            HStack(spacing: 12) {
                Image(systemName: "bed.double.fill")
                    .font(.system(size: 30))
                    .foregroundStyle(isActive ? c.accent : c.textMuted)
                VStack(alignment: .leading, spacing: 3) {
                    Text(isActive ? "Paused in \(atlasFormatMs(remainingMs))" : "Nothing running")
                        .font(isActive ? AtlasFont.headline() : AtlasFont.title())
                        .foregroundStyle(isActive ? c.accent : c.text)
                        .lineLimit(1)
                    Text(isActive
                         ? "Drift off — Atlas pauses when time's up"
                         : "No timer tonight")
                        .font(AtlasFont.small())
                        .foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                if isActive {
                    Button("Cancel") { toolsVM.clearSleepTimer() }
                        .font(AtlasFont.label())
                        .foregroundStyle(c.accent)
                }
            }
            .padding(.top, 6)

            Divider().background(c.border.opacity(0.5)).padding(.vertical, 8)
            StudioHeader("DURATION")
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 62), spacing: 8)], spacing: 8) {
                ForEach([10, 20, 30, 60, 90], id: \.self) { minutes in
                    AtlasFilterChip("\(minutes)m") { toolsVM.startSleepTimer(minutes: minutes) }
                }
            }
        }
    }

    private func endOfTrackCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("STOP AT END OF TRACK")
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("Finish this track, then silence")
                        .font(AtlasFont.rowTitle())
                        .foregroundStyle(c.text)
                    Text("Atlas pauses exactly when the current song ends — never mid-chorus.")
                        .font(AtlasFont.small())
                        .foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                Toggle("", isOn: Binding(
                    get: { settings.sleepTimerEndOfTrack },
                    set: {
                        settings.sleepTimerEndOfTrack = $0
                        player.applyAudioSettings()
                    }
                )).labelsHidden().tint(c.accent)
            }
            .padding(.top, 6)
        }
    }

    private func defaultCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("DEFAULT")
            Text("The sleep button in Now Playing uses this duration.")
                .font(AtlasFont.caption())
                .foregroundStyle(c.textMuted)
            LazyVGrid(columns: [GridItem(.adaptive(minimum: 62), spacing: 8)], spacing: 8) {
                ForEach([10, 15, 30, 45, 60], id: \.self) { minutes in
                    AtlasFilterChip("\(minutes)m", selected: settings.sleepTimerDefaultMinutes == minutes) {
                        settings.sleepTimerDefaultMinutes = minutes
                    }
                }
            }
            .padding(.top, 4)
        }
    }
}
