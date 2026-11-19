// Atlas iOS — docked 64pt player with a real gold progress filament and transport.
import SwiftUI

struct MiniPlayerBar: View {
    private let onOpenNowPlaying: () -> Void

    @Environment(PlayerController.self) private var player
    @Environment(MusicRepository.self) private var music
    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(onOpenNowPlaying: @escaping () -> Void) {
        self.onOpenNowPlaying = onOpenNowPlaying
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        if player.state.hasQueue {
            bar
        }
    }

    private var bar: some View {
        let c = colors
        let state = player.state
        let progress = state.durationMs > 0
            ? min(max(Double(state.positionMs) / Double(state.durationMs), 0), 1)
            : 0
        let track = state.currentTrackURI.flatMap { music.track(uri: $0) }

        return VStack(spacing: 0) {
            Rectangle()
                .fill(c.surfaceElevated)
                .frame(height: 0.5)

            HStack(spacing: 0) {
                Button(action: onOpenNowPlaying) {
                    HStack(spacing: 12) {
                        AlbumArt(track: track, size: 44, corner: Radius.sm, showStrip: false)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(state.title.isEmpty ? "Unknown title" : state.title)
                                .font(AtlasFont.rowTitle())
                                .foregroundStyle(c.text)
                                .lineLimit(1)
                            Text(state.artist.isEmpty ? "Unknown artist" : state.artist)
                                .font(AtlasFont.caption())
                                .foregroundStyle(c.textMuted)
                                .lineLimit(1)
                        }
                        Spacer(minLength: 4)
                        // Space reserved for the trailing controls, which sit above the button.
                        Color.clear.frame(width: 84, height: 44)
                    }
                    .padding(.horizontal, 12)
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Open now playing for \(state.title)")
                .overlay(alignment: .trailing) {
                    HStack(spacing: 0) {
                        AtlasIconButton(
                            systemName: state.isPlaying ? "pause.fill" : "play.fill",
                            label: state.isPlaying ? "Pause" : "Play",
                            action: { player.togglePlayPause() },
                            tint: c.accent
                        )
                        AtlasIconButton(
                            systemName: "forward.fill",
                            label: "Next track",
                            action: { player.skipNext() }
                        )
                    }
                    .padding(.trailing, 8)
                }
            }
            .frame(height: 64)
            .background(c.surface)

            GeometryReader { geometry in
                ZStack(alignment: .leading) {
                    Rectangle().fill(c.surfaceElevated)
                    Rectangle()
                        .fill(c.accent)
                        .frame(width: geometry.size.width * CGFloat(progress))
                }
            }
            .frame(height: 3)
            .accessibilityLabel("Playback progress \(Int(progress * 100))%")
        }
        .animation(AtlasMotion.quick, value: player.state.currentTrackURI)
    }
}
