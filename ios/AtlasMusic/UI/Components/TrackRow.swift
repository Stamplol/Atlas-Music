// Atlas iOS — ruled workhorse row: tap to play, favorite, and a full action menu.
import SwiftUI

struct TrackRow: View {
    private let track: Track
    private let onPlay: () -> Void
    private let onToggleFavorite: () -> Void
    private let onPlayNext: () -> Void
    private let onAddToQueue: () -> Void
    private let onAddToPlaylist: () -> Void
    private let onInfo: () -> Void
    private let onEditTags: (() -> Void)?
    private let onRemove: (() -> Void)?
    private let compact: Bool

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(track: Track, onPlay: @escaping () -> Void, onToggleFavorite: @escaping () -> Void,
         onPlayNext: @escaping () -> Void, onAddToQueue: @escaping () -> Void,
         onAddToPlaylist: @escaping () -> Void, onInfo: @escaping () -> Void,
         onEditTags: (() -> Void)? = nil, onRemove: (() -> Void)? = nil, compact: Bool = false) {
        self.track = track
        self.onPlay = onPlay
        self.onToggleFavorite = onToggleFavorite
        self.onPlayNext = onPlayNext
        self.onAddToQueue = onAddToQueue
        self.onAddToPlaylist = onAddToPlaylist
        self.onInfo = onInfo
        self.onEditTags = onEditTags
        self.onRemove = onRemove
        self.compact = compact
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        HStack(spacing: 0) {
            Button(action: onPlay) {
                HStack(spacing: 12) {
                    if !compact {
                        AlbumArt(track: track, size: 46, corner: Radius.sm, showStrip: false)
                    }
                    VStack(alignment: .leading, spacing: 2) {
                        Text(track.displayTitle)
                            .font(AtlasFont.rowTitle())
                            .foregroundStyle(c.text)
                            .lineLimit(1)
                        Text(Format.artistAlbumLine(artist: track.displayArtist, album: track.displayAlbum))
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted)
                            .lineLimit(1)
                    }
                    Spacer(minLength: 4)
                    Text(Format.duration(track.durationMs))
                        .font(AtlasFont.mono(size: 11))
                        .monospacedDigit()
                        .foregroundStyle(c.textMuted)
                }
                .padding(.leading, 12)
                .padding(.vertical, 8)
            }
            .buttonStyle(AtlasRowPressStyle())
            .accessibilityLabel("Play \(track.displayTitle) by \(track.displayArtist)")

            AtlasIconButton(
                systemName: track.isFavorite ? "heart.fill" : "heart",
                label: track.isFavorite ? "Remove from favorites" : "Add to favorites",
                action: onToggleFavorite,
                tint: track.isFavorite ? c.accent : c.textMuted
            )

            Menu {
                Button("Play next", action: onPlayNext)
                Button("Add to queue", action: onAddToQueue)
                Button("Add to playlist…", action: onAddToPlaylist)
                if let onEditTags {
                    Button("Edit tags…", action: onEditTags)
                }
                Button("Track info", action: onInfo)
                if let onRemove {
                    Divider()
                    Button("Remove from library", role: .destructive, action: onRemove)
                }
            } label: {
                Image(systemName: "ellipsis")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(c.textMuted)
                    .frame(width: 34, height: 38)
                    .contentShape(Rectangle())
            }
            .menuStyle(.borderlessButton)
            .accessibilityLabel("More options for \(track.displayTitle)")
            .padding(.trailing, 8)
        }
        .frame(height: compact ? 52 : 64)
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(c.border.opacity(0.55))
                .frame(height: 0.5)
                .padding(.leading, compact ? 12 : 68)
        }
    }
}

private struct AtlasRowPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.985 : 1)
            .opacity(configuration.isPressed ? 0.72 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
