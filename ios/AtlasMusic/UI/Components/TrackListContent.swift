// Atlas iOS — shared ruled track list used by library, search, playlists and smart lists.
import SwiftUI

struct TrackListContent: View {
    private let tracks: [Track]
    private let onPlay: (Track) -> Void
    private let onToggleFavorite: (Track) -> Void
    private let onPlayNext: (Track) -> Void
    private let onAddToQueue: (Track) -> Void
    private let onAddToPlaylist: (Track) -> Void
    private let onInfo: (Track) -> Void
    private let onEditTags: ((Track) -> Void)?
    private let onRemove: ((Track) -> Void)?
    private let emptyTitle: String
    private let emptyBody: String
    private let compact: Bool

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(tracks: [Track], onPlay: @escaping (Track) -> Void,
         onToggleFavorite: @escaping (Track) -> Void, onPlayNext: @escaping (Track) -> Void,
         onAddToQueue: @escaping (Track) -> Void, onAddToPlaylist: @escaping (Track) -> Void,
         onInfo: @escaping (Track) -> Void, onEditTags: ((Track) -> Void)? = nil,
         onRemove: ((Track) -> Void)? = nil, emptyTitle: String = "Nothing here yet",
         emptyBody: String = "", compact: Bool = false) {
        self.tracks = tracks
        self.onPlay = onPlay
        self.onToggleFavorite = onToggleFavorite
        self.onPlayNext = onPlayNext
        self.onAddToQueue = onAddToQueue
        self.onAddToPlaylist = onAddToPlaylist
        self.onInfo = onInfo
        self.onEditTags = onEditTags
        self.onRemove = onRemove
        self.emptyTitle = emptyTitle
        self.emptyBody = emptyBody
        self.compact = compact
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let c = colors
        ZStack {
            c.background.ignoresSafeArea()
            if tracks.isEmpty {
                EmptyState(title: emptyTitle, body: emptyBody)
                    .frame(maxWidth: .infinity, minHeight: 240)
            } else {
                ScrollView(showsIndicators: true) {
                    LazyVStack(spacing: 0) {
                        ForEach(tracks, id: \.uri) { track in
                            TrackRow(
                                track: track,
                                onPlay: { onPlay(track) },
                                onToggleFavorite: { onToggleFavorite(track) },
                                onPlayNext: { onPlayNext(track) },
                                onAddToQueue: { onAddToQueue(track) },
                                onAddToPlaylist: { onAddToPlaylist(track) },
                                onInfo: { onInfo(track) },
                                onEditTags: onEditTags.map { callback in { callback(track) } },
                                onRemove: onRemove.map { callback in { callback(track) } },
                                compact: compact
                            )
                        }
                    }
                    // Clear the docked mini player at the end of the list.
                    .padding(.bottom, 88)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}
