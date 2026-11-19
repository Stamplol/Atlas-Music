// Atlas iOS — six-tab library browser; faithful to Android LibraryScreen.
import SwiftUI

struct LibraryView: View {
    @Environment(MusicRepository.self) private var music
    @Environment(PlaylistRepository.self) private var playlists
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs
    @Environment(TabNavigationState.self) private var tabNav

    let onOpenPlaylist: (Playlist) -> Void
    let onOpenCollection: (String, String) -> Void
    let onOpenDuplicates: () -> Void
    let onPlayAll: ([Track], Int) -> Void

    @State private var sort: SortOption = .title
    @State private var showSort = false
    @State private var showCreate = false
    @State private var newPlaylistName = ""

    private static let tabs = ["Songs", "Albums", "Artists", "Genres", "Folders", "Playlists"]

    init(onOpenPlaylist: @escaping (Playlist) -> Void,
         onOpenCollection: @escaping (String, String) -> Void,
         onOpenDuplicates: @escaping () -> Void,
         onPlayAll: @escaping ([Track], Int) -> Void) {
        self.onOpenPlaylist = onOpenPlaylist
        self.onOpenCollection = onOpenCollection
        self.onOpenDuplicates = onOpenDuplicates
        self.onPlayAll = onPlayAll
        self._sort = State(initialValue: .title)
    }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var tracks: [Track] { music.allTracks(sort: sort) }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Library")
                    .font(AtlasFont.display())
                    .foregroundStyle(colors.text)
                Spacer()
                if tabNav.libraryTab == 0 {
                    Button {
                        showSort = true
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .foregroundStyle(colors.accent)
                    }
                    .accessibilityLabel("Sort library")
                    .buttonStyle(.borderless)
                    .popover(isPresented: $showSort) { sortMenu }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .padding(.bottom, 6)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 18) {
                    ForEach(Self.tabs.indices, id: \.self) { index in
                        Button {
                            tabNav.libraryTab = index
                        } label: {
                            Text(Self.tabs[index])
                                .font(AtlasFont.caption())
                                .foregroundStyle(tabNav.libraryTab == index ? colors.accent : colors.textMuted)
                                .padding(.vertical, 8)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 16)
            }
            .background(colors.surface)

            switch tabNav.libraryTab {
            case 0: songsTab
            case 1: countList(items: music.albums(), emptyTitle: "No albums found", emptyBody: "Albums appear here after you scan your library.", kind: "album")
            case 2: countList(items: music.artists(), emptyTitle: "No artists found", emptyBody: "Artists appear here after you scan your library.", kind: "artist")
            case 3: countList(items: music.genres(), emptyTitle: "No genres found", emptyBody: "Genres appear here after you scan your library.", kind: "genre")
            case 4: foldersTab
            default: playlistsTab
            }
        }
        .background(colors.background.ignoresSafeArea())
        .alert("New playlist", isPresented: $showCreate) {
            TextField("Playlist name", text: $newPlaylistName)
            Button("Create") { createPlaylist() }
            Button("Cancel", role: .cancel) {}
        }
        .onAppear { sort = settings.defaultSort }
    }

    private var sortMenu: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Sort by").font(AtlasFont.eyebrow()).foregroundStyle(colors.textMuted).padding(12)
            ForEach(SortOption.allCases) { option in
                Button {
                    sort = option
                    settings.defaultSort = option
                    showSort = false
                } label: {
                    HStack {
                        Text(option.label).font(AtlasFont.body())
                        Spacer()
                        if option == sort {
                            Image(systemName: "checkmark").foregroundStyle(colors.accent)
                        }
                    }
                    .foregroundStyle(colors.text)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 9)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 6)
        .background(colors.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
    }

    private var songsTab: some View {
        VStack(alignment: .leading, spacing: 0) {
            if !tracks.isEmpty {
                HStack {
                    Text("\(tracks.count) \(tracks.count == 1 ? "song" : "songs") · \(sort.label)")
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                    Spacer()
                    Button {
                        let shuffled = tracks.shuffled()
                        guard let first = shuffled.first else { return }
                        onPlayAll(shuffled, shuffled.firstIndex { $0.uri == first.uri } ?? 0)
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "shuffle")
                            Text("Shuffle all")
                        }
                        .font(AtlasFont.caption())
                    }
                    .buttonStyle(.bordered)
                    .tint(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 6)
            }
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
    }

    private func countList(items: [NameCount], emptyTitle: String, emptyBody: String, kind: String) -> some View {
        Group {
            if items.isEmpty {
                EmptyState(title: emptyTitle, body: emptyBody)
            } else {
                ScrollView {
                    LazyVStack {
                        ForEach(items) { item in
                            collectionRow(title: item.name, subtitle: "\(item.count) \(item.count == 1 ? "track" : "tracks")", systemImage: "music.note") {
                                onOpenCollection(kind, item.name)
                            }
                        }
                    }
                    .padding(.bottom, 110)
                }
            }
        }
    }

    private var foldersTab: some View {
        let folders = music.folderCounts()
        return Group {
            if folders.isEmpty {
                EmptyState(title: "No folders indexed", body: "Folders appear when your audio files carry a folder path.")
            } else {
                ScrollView {
                    LazyVStack {
                        ForEach(folders) { folder in
                            collectionRow(title: folder.name, subtitle: "\(folder.count) \(folder.count == 1 ? "track" : "tracks")", systemImage: "folder") {
                                onOpenCollection("folder", folder.name)
                            }
                        }
                    }
                    .padding(.bottom, 110)
                }
            }
        }
    }

    private var playlistsTab: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 0) {
                HStack {
                    Text("Auto mixes").font(AtlasFont.eyebrow()).foregroundStyle(colors.textMuted)
                    Spacer()
                    Button {
                        newPlaylistName = ""
                        showCreate = true
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                            Text("New playlist")
                        }
                        .font(AtlasFont.caption())
                    }
                    .buttonStyle(.bordered)
                    .tint(colors.accent)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)

                ForEach(SmartPlaylist.allCases) { smart in
                    collectionRow(title: smart.label, subtitle: "Updates automatically", systemImage: "music.note.list") {
                        onOpenCollection("smart", smart.rawValue)
                    }
                }

                Text("Your playlists")
                    .font(AtlasFont.eyebrow())
                    .foregroundStyle(colors.textMuted)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)

                ForEach(playlists.playlists()) { playlist in
                    collectionRow(title: playlist.name, subtitle: playlist.desc.isEmpty ? "Playlist" : playlist.desc, systemImage: "music.note.list") {
                        onOpenPlaylist(playlist)
                    }
                }

                collectionRow(title: "Duplicates", subtitle: "Find the same song saved twice", systemImage: "doc.on.doc") {
                    onOpenDuplicates()
                }
            }
            .padding(.bottom, 110)
        }
    }

    private func collectionRow(title: String, subtitle: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 14) {
                Image(systemName: systemImage)
                    .font(.title3)
                    .foregroundStyle(colors.accent)
                    .frame(width: 34)
                VStack(alignment: .leading) {
                    Text(title)
                        .font(AtlasFont.body())
                        .foregroundStyle(colors.text)
                        .lineLimit(1)
                    Text(subtitle)
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                }
                Spacer()
                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(colors.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
        }
        .buttonStyle(.plain)
    }

    private func createPlaylist() {
        let name = newPlaylistName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        if let created = try? playlists.create(name: name) {
            onOpenPlaylist(created)
        }
    }
}
