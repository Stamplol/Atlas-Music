// Atlas iOS — library search across songs, albums, artists and genres; faithful to Android SearchScreen.
import SwiftUI

struct SearchView: View {
    @Environment(MusicRepository.self) private var music
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs

    let onOpenCollection: (String, String) -> Void
    let onPlayAll: ([Track], Int) -> Void

    @State private var query = ""

    init(onOpenCollection: @escaping (String, String) -> Void,
         onPlayAll: @escaping ([Track], Int) -> Void) {
        self.onOpenCollection = onOpenCollection
        self.onPlayAll = onPlayAll
    }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var results: SearchResults {
        query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? SearchResults()
            : music.search(query)
    }

    private var hasAny: Bool {
        let r = results
        return !r.tracks.isEmpty || !r.albums.isEmpty || !r.artists.isEmpty || !r.genres.isEmpty
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Search")
                    .font(AtlasFont.display())
                    .foregroundStyle(colors.text)
                Spacer()
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)

            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass").foregroundStyle(colors.textMuted)
                TextField("Songs, artists, albums, genres…", text: $query)
                    .font(AtlasFont.body())
                    .foregroundStyle(colors.text)
                    .textInputAutocapitalization(.never)
                    .disableAutocorrection(true)
                if !query.isEmpty {
                    Button {
                        query = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(colors.textMuted)
                    }
                    .accessibilityLabel("Clear search")
                    .buttonStyle(.borderless)
                }
            }
            .padding(12)
            .background(colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            .padding(.horizontal, 16)
            .padding(.top, 6)

            if query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                EmptyState(
                    title: "Find anything in your library",
                    body: "Search matches titles, artists, albums, genres and folders. Everything stays on your device."
                )
            } else if !hasAny {
                EmptyState(
                    title: "No matches for \"\(query)\"",
                    body: "Try a different spelling, or check the folder the file lives in."
                )
            } else {
                ScrollView {
                    LazyVStack(alignment: .leading, spacing: 0) {
                        if !results.tracks.isEmpty {
                            SectionHeader("Songs")
                            ForEach(results.tracks, id: \.uri) { track in
                                trackRow(track, list: results.tracks)
                            }
                        }
                        if !results.albums.isEmpty {
                            SectionHeader("Albums")
                            ForEach(results.albums, id: \.self) { album in
                                resultRow(album, subtitle: "Album") { onOpenCollection("album", album) }
                            }
                        }
                        if !results.artists.isEmpty {
                            SectionHeader("Artists")
                            ForEach(results.artists, id: \.self) { artist in
                                resultRow(artist, subtitle: "Artist") { onOpenCollection("artist", artist) }
                            }
                        }
                        if !results.genres.isEmpty {
                            SectionHeader("Genres")
                            ForEach(results.genres, id: \.self) { genre in
                                resultRow(genre, subtitle: "Genre") { onOpenCollection("genre", genre) }
                            }
                        }
                    }
                    .padding(.bottom, 110)
                }
            }
        }
        .background(colors.background.ignoresSafeArea())
    }

    private func trackRow(_ track: Track, list: [Track]) -> some View {
        TrackRow(
            track: track,
            onPlay: { onPlayAll(list, atlasTrackIndex(track, in: list)) },
            onToggleFavorite: { atlasToggleFavorite(track, music: music) },
            onPlayNext: { player.playNext([track]); dialogs.toast("Playing next") },
            onAddToQueue: { player.addToQueue([track]); dialogs.toast("Added to queue") },
            onAddToPlaylist: { dialogs.addToPlaylist(track) },
            onInfo: { dialogs.info(track) },
            onEditTags: { dialogs.editTags(track) }
        )
    }

    private func resultRow(_ title: String, subtitle: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Text(title)
                    .font(AtlasFont.body())
                    .foregroundStyle(colors.text)
                    .lineLimit(1)
                Spacer()
                Text(subtitle)
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }
}
