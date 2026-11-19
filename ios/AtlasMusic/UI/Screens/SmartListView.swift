// Atlas iOS — generic "list of tracks" screen for smart mixes, albums, artists, genres and folders.
import SwiftUI

struct SmartListView: View {
    @Environment(MusicRepository.self) private var music
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs
    @Environment(\.dismiss) private var dismiss

    let title: String
    let subtitle: String?
    let tracks: [Track]
    let onPlayAll: ([Track], Int) -> Void

    init(title: String, subtitle: String? = nil, tracks: [Track],
         onPlayAll: @escaping ([Track], Int) -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.tracks = tracks
        self.onPlayAll = onPlayAll
    }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var totalMs: Int64 { tracks.reduce(Int64(0)) { $0 + $1.durationMs } }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .font(.title3.weight(.semibold))
                        .foregroundStyle(colors.accent)
                }
                .accessibilityLabel("Back")
                VStack(alignment: .leading) {
                    Text(title)
                        .font(AtlasFont.headline())
                        .foregroundStyle(colors.text)
                        .lineLimit(1)
                    if let subtitle, !subtitle.isEmpty {
                        Text(subtitle)
                            .font(AtlasFont.caption())
                            .foregroundStyle(colors.textMuted)
                    }
                }
                Spacer()
                Button("Play all") { onPlayAll(tracks, 0) }
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.accent)
                    .disabled(tracks.isEmpty)
                    .buttonStyle(.borderless)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)

            Text("\(tracks.count) \(tracks.count == 1 ? "track" : "tracks")  ·  \(Format.totalDuration(totalMs))")
                .font(AtlasFont.caption())
                .foregroundStyle(colors.textMuted)
                .padding(.horizontal, 16)
                .padding(.bottom, 4)

            TrackListContent(
                tracks: tracks,
                onPlay: { onPlayAll(tracks, atlasTrackIndex($0, in: tracks)) },
                onToggleFavorite: { atlasToggleFavorite($0, music: music) },
                onPlayNext: { player.playNext([$0]); dialogs.toast("Playing next") },
                onAddToQueue: { player.addToQueue([$0]); dialogs.toast("Added to queue") },
                onAddToPlaylist: { dialogs.addToPlaylist($0) },
                onInfo: { dialogs.info($0) },
                onEditTags: { dialogs.editTags($0) }
            )
        }
        .background(colors.background.ignoresSafeArea())
    }
}
