// Atlas iOS — Tools tab host: player strip, hub navigation and the four tool workspaces.
import SwiftUI
import Foundation

/// Hosts the tools hub and its subpages, sharing ToolsViewModel with Now Playing.
struct ToolsView: View {
    private let onOpenNowPlaying: () -> Void
    private let onOpenDownloads: () -> Void
    private let onOpenDuplicates: () -> Void

    @Environment(PlayerController.self) private var player
    @Environment(MusicRepository.self) private var music
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected
    @Environment(\.dismiss) private var dismiss

    @State private var page: ToolPage
    @State private var showQueue = false
    @State private var showSettings = false

    init(initialPage: ToolPage = .hub,
         onOpenNowPlaying: @escaping () -> Void,
         onOpenDownloads: @escaping () -> Void,
         onOpenDuplicates: @escaping () -> Void) {
        self.onOpenNowPlaying = onOpenNowPlaying
        self.onOpenDownloads = onOpenDownloads
        self.onOpenDuplicates = onOpenDuplicates
        _page = State(initialValue: initialPage)
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var title: String {
        switch page {
        case .hub: return "Tools"
        case .audio: return "Sound Studio"
        case .focus: return "Focus"
        case .sleep: return "Sleep"
        case .library: return "Library care"
        }
    }

    var body: some View {
        let c = colors
        VStack(spacing: 0) {
            header(c)
            if page == .hub {
                playerStrip(c)
                ToolsHub(
                    onOpen: { page = $0 },
                    onOpenDownloads: onOpenDownloads,
                    onOpenSettings: { showSettings = true }
                )
            } else {
                Group {
                    switch page {
                    case .audio: AudioStudioView()
                    case .focus: FocusView()
                    case .sleep: SleepView()
                    case .library: LibraryCareView(onOpenDuplicates: onOpenDuplicates)
                    case .hub: EmptyView()
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }
        .background(c.background)
        .sheet(isPresented: $showQueue) { QueueView(onDismiss: { showQueue = false }) }
        .sheet(isPresented: $showSettings) { quickSettings(c) }
    }

    private func header(_ c: AtlasColors) -> some View {
        HStack(spacing: 8) {
            AtlasIconButton(
                systemName: page == .hub ? "chevron.left" : "arrow.left",
                label: page == .hub ? "Back to music" : "Back to tools",
                action: { if page == .hub { dismiss() } else { page = .hub } }
            )
            Text(title)
                .font(AtlasFont.headline())
                .fontWeight(.bold)
                .foregroundStyle(c.text)
                .lineLimit(1)
            Spacer(minLength: 8)
            if page == .hub {
                AtlasIconButton(systemName: "music.note", label: "Now playing", action: onOpenNowPlaying,
                                tint: c.accent)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 6)
    }

    @ViewBuilder
    private func playerStrip(_ c: AtlasColors) -> some View {
        let current = player.state.currentTrackURI.flatMap { music.track(uri: $0) }
        let fraction: Double = player.state.durationMs > 0
            ? min(max(Double(player.state.positionMs) / Double(player.state.durationMs), 0), 1)
            : 0

        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Button { onOpenNowPlaying() } label: {
                    AlbumArt(track: current, size: 46, corner: 12, showStrip: false)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open now playing")

                Button { onOpenNowPlaying() } label: {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(player.state.title.isEmpty ? "Nothing playing" : player.state.title)
                            .font(AtlasFont.rowTitle())
                            .foregroundStyle(c.text).lineLimit(1)
                        Text(player.state.artist.isEmpty ? "Tap to browse your library" : player.state.artist)
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .buttonStyle(.plain)

                AtlasIconButton(
                    systemName: player.state.isPlaying ? "pause.fill" : "play.fill",
                    label: player.state.isPlaying ? "Pause" : "Play",
                    action: { player.togglePlayPause() },
                    tint: c.accent
                )
                AtlasIconButton(systemName: "list.bullet.rectangle", label: "Queue",
                                action: { showQueue = true }, tint: c.textMuted)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 10)

            if player.state.durationMs > 0 {
                GeometryReader { geo in
                    ZStack(alignment: .leading) {
                        Capsule().fill(c.surfaceElevated)
                        Capsule().fill(c.accent).frame(width: geo.size.width * CGFloat(fraction))
                    }
                }
                .frame(height: 3)
                .padding(.horizontal, 12)
                .padding(.bottom, 8)
            }
        }
        .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        .padding(.horizontal, 16)
        .padding(.top, 6)
    }

    /// Compact settings surface so the hub's Settings tile always performs real work even when
    /// ToolsView is presented outside a tab bar with no settings navigation callback.
    private func quickSettings(_ c: AtlasColors) -> some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    ToolCard {
                        StudioHeader("APPEARANCE")
                        Picker("Theme", selection: Binding(
                            get: { settings.themeMode },
                            set: { settings.themeMode = $0 }
                        )) {
                            ForEach(ThemeMode.allCases) { mode in Text(mode.label).tag(mode) }
                        }
                        .pickerStyle(.menu)
                        .tint(c.accent)
                        Toggle("Dynamic color", isOn: Binding(
                            get: { settings.dynamicColor }, set: { settings.dynamicColor = $0 }
                        )).tint(c.accent)
                        Picker("Artwork", selection: Binding(
                            get: { settings.artworkStyle }, set: { settings.artworkStyle = $0 }
                        )) {
                            ForEach(ArtworkStyle.allCases) { style in Text(style.label).tag(style) }
                        }
                        .pickerStyle(.menu)
                        .tint(c.accent)
                    }
                    ToolCard {
                        StudioHeader("BACKGROUNDS")
                        Picker("Mode", selection: Binding(
                            get: { settings.backgroundMode },
                            set: { settings.backgroundMode = $0 }
                        )) {
                            ForEach(BackgroundMode.allCases) { mode in Text(mode.label).tag(mode.rawValue) }
                        }
                        .pickerStyle(.menu)
                        .tint(c.accent)
                        Toggle("React to music", isOn: Binding(
                            get: { settings.backgroundReact }, set: { settings.backgroundReact = $0 }
                        )).tint(c.accent)
                    }
                    ToolCard {
                        StudioHeader("PLAYBACK")
                        Toggle("Gapless", isOn: Binding(
                            get: { settings.gaplessEnabled }, set: { settings.gaplessEnabled = $0 }
                        )).tint(c.accent)
                        Toggle("Crossfade", isOn: Binding(
                            get: { settings.crossfadeEnabled }, set: {
                                settings.crossfadeEnabled = $0
                                player.applyAudioSettings()
                            }
                        )).tint(c.accent)
                        Stepper("Sleep default: \(settings.sleepTimerDefaultMinutes) min", value: Binding(
                            get: { settings.sleepTimerDefaultMinutes },
                            set: { settings.sleepTimerDefaultMinutes = $0 }
                        ), in: 5...180)
                    }
                }
                .padding(16)
            }
            .background(c.background)
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { showSettings = false }.foregroundStyle(c.accent)
                }
            }
        }
        .presentationDetents([.large])
    }
}
