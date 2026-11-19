// Atlas iOS — app shell: five tabs, mini player, now-playing sheet, dialogs, iPod short-circuit and error banner.
import SwiftUI

// MARK: - Shared helpers used by every main screen.

/// First index of a track (by stable uri) inside a list, defaulting to 0.
@MainActor
func atlasTrackIndex(_ track: Track, in list: [Track]) -> Int {
    list.firstIndex { $0.uri == track.uri } ?? 0
}

/// Toggle a track's favorite flag and persist it, mirroring Android TrackActions.toggleFavorite.
@MainActor
func atlasToggleFavorite(_ track: Track, music: MusicRepository) {
    music.setFavorite(track, !track.isFavorite)
    music.save()
}

// MARK: - App-level dialog / toast coordination.

/// Injected by RootView so any screen can request the app-level add-to-playlist and
/// track-editor dialogs or raise a snackbar-style toast without owning that UI itself.
@MainActor
@Observable
final class TrackDialogCoordinator {
    var addToPlaylistTrack: Track?
    var editorTrack: Track?
    var banner: String?
    var onInfo: ((Track) -> Void)?
    private var bannerTask: Task<Void, Never>?

    func addToPlaylist(_ track: Track) { addToPlaylistTrack = track }
    func editTags(_ track: Track) { editorTrack = track }
    func info(_ track: Track) { onInfo?(track) }

    func toast(_ message: String) {
        bannerTask?.cancel()
        banner = message
        bannerTask = Task { @MainActor [weak self] in
            try? await Task.sleep(for: .seconds(3))
            if !Task.isCancelled { self?.banner = nil }
        }
    }
}

/// Shared tab state so Home can jump straight to the Library playlists tab or
/// the Sound Studio page of Tools (Android's `library?tab=5` / `tools?page=audio`).
@MainActor
@Observable
final class TabNavigationState {
    var libraryTab = 0
    var toolsPage: ToolPage = .hub
}

// MARK: - Navigation routes.

enum AppRoute: Hashable {
    case search
    case queue
    case downloads
    case duplicates
    case tools(ToolPage)
    case collection(kind: String, value: String, title: String, subtitle: String)
    case playlist(Playlist)
    case trackInfo(String)

    static func == (lhs: AppRoute, rhs: AppRoute) -> Bool {
        switch (lhs, rhs) {
        case (.search, .search), (.queue, .queue), (.downloads, .downloads), (.duplicates, .duplicates): return true
        case let (.tools(a), .tools(b)): return a == b
        case let (.collection(ak, av, at, asub), .collection(bk, bv, bt, bsub)):
            return ak == bk && av == bv && at == bt && asub == bsub
        case let (.playlist(a), .playlist(b)): return a === b
        case let (.trackInfo(a), .trackInfo(b)): return a == b
        default: return false
        }
    }

    func hash(into hasher: inout Hasher) {
        switch self {
        case .search: hasher.combine(0)
        case .queue: hasher.combine(1)
        case .downloads: hasher.combine(2)
        case .duplicates: hasher.combine(3)
        case .tools(let page): hasher.combine(4); hasher.combine(page)
        case let .collection(kind, value, title, subtitle):
            hasher.combine(5); hasher.combine(kind); hasher.combine(value); hasher.combine(title); hasher.combine(subtitle)
        case .playlist(let playlist): hasher.combine(6); hasher.combine(ObjectIdentifier(playlist))
        case .trackInfo(let uri): hasher.combine(7); hasher.combine(uri)
        }
    }
}

// MARK: - Root.

struct RootView: View {
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(MusicRepository.self) private var music
    @Environment(PlaylistRepository.self) private var playlists
    @Environment(ToolsViewModel.self) private var toolsVM

    @State private var selectedTab: AppTab = .home
    @State private var path: [AppRoute] = []
    @State private var showNowPlaying = false
    @State private var coordinator = TrackDialogCoordinator()
    @State private var tabNav = TabNavigationState()

    @MainActor init() {}

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    var body: some View {
        Group {
            if settings.ipodModeEnabled {
                IpodModeView(onExit: { settings.ipodModeEnabled = false })
            } else {
                mainContent
            }
        }
        .environment(coordinator)
        .environment(tabNav)
        .onChange(of: player.state.lastError) { _, error in
            if let error { coordinator.toast(error) }
        }
        .onAppear {
            coordinator.onInfo = { track in path.append(.trackInfo(track.uri)) }
            if let error = player.state.lastError { coordinator.toast(error) }
        }
    }

    private var mainContent: some View {
        NavigationStack(path: $path) {
            TabView(selection: $selectedTab) {
                HomeView(
                    onOpenLibrary: { selectedTab = .library },
                    onOpenTools: { tabNav.toolsPage = .audio; selectedTab = .tools },
                    onOpenDownloads: { path.append(.downloads) },
                    onOpenPlaylists: { tabNav.libraryTab = 5; selectedTab = .library },
                    onPlayAll: playTracks,
                    onSecretUnlock: { settings.ipodModeEnabled = true }
                )
                .tabItem { Label(AppTab.home.label, systemImage: AppTab.home.systemName) }
                .tag(AppTab.home)

                LibraryView(
                    onOpenPlaylist: { path.append(.playlist($0)) },
                    onOpenCollection: { kind, value in path.append(collectionRoute(kind, value)) },
                    onOpenDuplicates: { path.append(.duplicates) },
                    onPlayAll: playTracks
                )
                .tabItem { Label(AppTab.library.label, systemImage: AppTab.library.systemName) }
                .tag(AppTab.library)

                SearchView(
                    onOpenCollection: { kind, value in path.append(collectionRoute(kind, value)) },
                    onPlayAll: playTracks
                )
                .tabItem { Label(AppTab.search.label, systemImage: AppTab.search.systemName) }
                .tag(AppTab.search)

                ToolsView(
                    initialPage: tabNav.toolsPage,
                    onOpenNowPlaying: { showNowPlaying = true },
                    onOpenDownloads: { path.append(.downloads) },
                    onOpenDuplicates: { path.append(.duplicates) }
                )
                .id(tabNav.toolsPage)
                .tabItem { Label(AppTab.tools.label, systemImage: AppTab.tools.systemName) }
                .tag(AppTab.tools)

                AtlasSettingsTabView(
                    onOpenDuplicates: { path.append(.duplicates) },
                    onOpenDownloads: { path.append(.downloads) }
                )
                .tabItem { Label(AppTab.settings.label, systemImage: AppTab.settings.systemName) }
                .tag(AppTab.settings)
            }
            // Mini player sits directly above the tab bar, exactly like the Android bottom bar.
            .safeAreaInset(edge: .bottom, spacing: 0) {
                MiniPlayerBar(onOpenNowPlaying: { showNowPlaying = true })
            }
            .navigationDestination(for: AppRoute.self) { route in
                destination(route)
            }
        }
        .sheet(isPresented: $showNowPlaying) {
            NowPlayingView(
                onDismiss: { showNowPlaying = false },
                toolsVM: toolsVM,
                onOpenQueue: { showNowPlaying = false; path.append(.queue) }
            )
        }
        .sheet(isPresented: Binding(
            get: { coordinator.addToPlaylistTrack != nil },
            set: { if !$0 { coordinator.addToPlaylistTrack = nil } }
        )) {
            if let track = coordinator.addToPlaylistTrack {
                AddToPlaylistDialog(
                    track: track,
                    playlists: playlists.playlists(),
                    onAdd: { playlist in
                        playlists.add(track, to: playlist)
                        playlists.save()
                        coordinator.addToPlaylistTrack = nil
                        coordinator.toast("Added to \"\(playlist.name)\"")
                    },
                    onDismiss: { coordinator.addToPlaylistTrack = nil }
                )
            }
        }
        .sheet(isPresented: Binding(
            get: { coordinator.editorTrack != nil },
            set: { if !$0 { coordinator.editorTrack = nil } }
        )) {
            if let track = coordinator.editorTrack {
                TrackEditorDialog(
                    track: track,
                    onSave: { title, artist, album, genre, year, trackNumber, discNumber in
                        coordinator.editorTrack = nil
                        let outcome = music.updateTags(
                            track, title: title, artist: artist, album: album, genre: genre,
                            year: year, trackNumber: trackNumber, discNumber: discNumber
                        )
                        if outcome.fileOk || outcome.fileError == nil {
                            coordinator.toast("Tags saved")
                        } else {
                            coordinator.toast("Tags saved to library\n\(outcome.fileError ?? "")")
                        }
                    },
                    onReset: {
                        coordinator.editorTrack = nil
                        music.resetTags(track)
                        coordinator.toast("Tags reset to file values")
                    },
                    onDismiss: { coordinator.editorTrack = nil }
                )
            }
        }
        .overlay(alignment: .top) { banner }
        .animation(AtlasMotion.settle, value: coordinator.banner)
    }

    @ViewBuilder
    private func destination(_ route: AppRoute) -> some View {
        switch route {
        case .queue:
            QueueView(onDismiss: { path.removeLast() })
        case .downloads:
            DownloadsView(onDismiss: { path.removeLast() })
        case .duplicates:
            DuplicatesView()
        case .tools(let page):
            ToolsView(
                initialPage: page,
                onOpenNowPlaying: { showNowPlaying = true },
                onOpenDownloads: { path.append(.downloads) },
                onOpenDuplicates: { path.append(.duplicates) }
            )
        case let .collection(kind, value, title, subtitle):
            SmartListView(
                title: title,
                subtitle: subtitle,
                tracks: tracksFor(kind: kind, value: value),
                onPlayAll: playTracks
            )
        case .playlist(let playlist):
            PlaylistDetailView(playlist: playlist, onPlayAll: playTracks)
        case .trackInfo(let uri):
            if let track = music.track(uri: uri) {
                TrackInfoView(track: track)
            }
        case .search:
            SearchView(
                onOpenCollection: { kind, value in path.append(collectionRoute(kind, value)) },
                onPlayAll: playTracks
            )
        }
    }

    @ViewBuilder
    private var banner: some View {
        if let message = coordinator.banner {
            HStack(spacing: 10) {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundStyle(colors.accent)
                Text(message)
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.text)
                    .lineLimit(3)
                Spacer(minLength: 4)
                Button {
                    coordinator.banner = nil
                } label: {
                    Image(systemName: "xmark").foregroundStyle(colors.textMuted)
                }
                .accessibilityLabel("Dismiss")
                .buttonStyle(.borderless)
            }
            .padding(12)
            .background(colors.surfaceHighest)
            .clipShape(RoundedRectangle(cornerRadius: Radius.sm))
            .shadow(color: colors.scrim, radius: 8, y: 4)
            .padding(.horizontal, 16)
            .padding(.top, 8)
            .transition(.move(edge: .top).combined(with: .opacity))
        }
    }

    private func playTracks(_ tracks: [Track], _ index: Int) {
        player.playTracks(tracks, startAt: index)
    }

    private func collectionRoute(_ kind: String, _ value: String) -> AppRoute {
        switch kind {
        case "album": return .collection(kind: kind, value: value, title: value, subtitle: "Album")
        case "artist": return .collection(kind: kind, value: value, title: value, subtitle: "Artist")
        case "genre": return .collection(kind: kind, value: value, title: value, subtitle: "Genre")
        case "folder": return .collection(kind: kind, value: value, title: value, subtitle: "Folder")
        case "smart":
            let smart = SmartPlaylist(rawValue: value)
            return .collection(kind: kind, value: value,
                               title: smart?.label ?? "Smart playlist",
                               subtitle: "Generated from your listening")
        default: return .collection(kind: kind, value: value, title: value, subtitle: "")
        }
    }

    private func tracksFor(kind: String, value: String) -> [Track] {
        switch kind {
        case "album": return music.tracks(album: value)
        case "artist": return music.tracks(artist: value)
        case "genre": return music.tracks(genre: value)
        case "folder": return music.tracks(folder: value)
        case "smart":
            switch SmartPlaylist(rawValue: value) {
            case .mostPlayed: return music.mostPlayed(limit: 50)
            case .recentlyPlayed: return music.recentlyPlayed(limit: 50)
            case .recentlyAdded: return music.recentlyAdded(limit: 50)
            case .favorites: return music.favorites()
            case .none: return music.recentlyAdded(limit: 50)
            }
        default: return []
        }
    }
}

// MARK: - Settings tab (functional local panel; no standalone SettingsView is owned by another agent).
// Every control here reads and writes the real SettingsStore from the environment.

private struct SettingsMenuOption<T>: Identifiable {
    let label: String
    let value: T
    var id: String { label }
}

private struct AtlasSettingsTabView: View {
    @Environment(SettingsStore.self) private var settings

    let onOpenDuplicates: () -> Void
    let onOpenDownloads: () -> Void

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text("Settings")
                    .font(AtlasFont.display())
                    .foregroundStyle(colors.text)
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    .padding(.bottom, 12)

                section("Appearance") {
                    settingMenu("Theme", value: settings.themeMode.label,
                                options: ThemeMode.allCases.map { SettingsMenuOption(label: $0.label, value: $0) }) {
                        settings.themeMode = $0
                    }
                    toggleRow("Dynamic color", isOn: Binding(get: { settings.dynamicColor }, set: { settings.dynamicColor = $0 }))
                    settingMenu("Artwork", value: settings.artworkStyle.label,
                                options: ArtworkStyle.allCases.map { SettingsMenuOption(label: $0.label, value: $0) }) {
                        settings.artworkStyle = $0
                    }
                }

                section("Library") {
                    settingMenu("Default sort", value: settings.defaultSort.label,
                                options: SortOption.allCases.map { SettingsMenuOption(label: $0.label, value: $0) }) {
                        settings.defaultSort = $0
                    }
                    navigationRow("Find duplicates", systemImage: "doc.on.doc", action: onOpenDuplicates)
                    navigationRow("Downloads", systemImage: "arrow.down.circle", action: onOpenDownloads)
                }

                section("Playback") {
                    toggleRow("Gapless playback", isOn: Binding(get: { settings.gaplessEnabled }, set: { settings.gaplessEnabled = $0 }))
                    toggleRow("Crossfade", isOn: Binding(get: { settings.crossfadeEnabled }, set: { settings.crossfadeEnabled = $0 }))
                    if settings.crossfadeEnabled {
                        Stepper("Crossfade: \(settings.crossfadeSeconds)s", value: Binding(
                            get: { settings.crossfadeSeconds },
                            set: { settings.crossfadeSeconds = $0 }
                        ), in: 0...12)
                        .font(AtlasFont.body())
                        .foregroundStyle(colors.text)
                    }
                    toggleRow("Normalization", isOn: Binding(get: { settings.normalizationEnabled }, set: { settings.normalizationEnabled = $0 }))
                    sliderRow("Loudness", value: Binding(
                        get: { Double(settings.loudnessGainMb) },
                        set: { settings.loudnessGainMb = Int($0) }
                    ), range: -1000...1000, format: { "\(Int($0)) mB" })
                    sliderRow("Playback speed", value: Binding(
                        get: { Double(settings.playbackSpeed) },
                        set: { settings.playbackSpeed = Float($0) }
                    ), range: 0.5...2.0, format: { String(format: "%.2f×", $0) })
                    toggleRow("Limiter", isOn: Binding(get: { settings.limiterEnabled }, set: { settings.limiterEnabled = $0 }))
                }

                section("Extras") {
                    toggleRow("Classic iPod mode", isOn: Binding(get: { settings.ipodModeEnabled }, set: { settings.ipodModeEnabled = $0 }))
                    toggleRow("Click sound", isOn: Binding(get: { settings.ipodClickSound }, set: { settings.ipodClickSound = $0 }))
                }

                Text("Everything stays on this device. Atlas Music has no account and uploads nothing.")
                    .font(AtlasFont.caption())
                    .foregroundStyle(colors.textMuted)
                    .padding(16)
            }
            .padding(.bottom, 120)
        }
        .background(colors.background.ignoresSafeArea())
    }

    private func section<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading) {
            Text(title.uppercased())
                .font(AtlasFont.eyebrow())
                .foregroundStyle(colors.textMuted)
                .padding(.horizontal, 16)
                .padding(.top, 12)
            content()
        }
    }

    private func toggleRow(_ title: String, isOn: Binding<Bool>) -> some View {
        Toggle(title, isOn: isOn)
            .font(AtlasFont.body())
            .foregroundStyle(colors.text)
            .tint(colors.accent)
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
    }

    private func settingMenu<T>(_ title: String, value: String, options: [SettingsMenuOption<T>], set: @escaping (T) -> Void) -> some View {
        Menu {
            ForEach(options) { option in
                Button(option.label) { set(option.value) }
            }
        } label: {
            HStack {
                Text(title).font(AtlasFont.body()).foregroundStyle(colors.text)
                Spacer()
                Text(value).font(AtlasFont.caption()).foregroundStyle(colors.accent)
                Image(systemName: "chevron.up.chevron.down").font(.caption).foregroundStyle(colors.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }

    private func navigationRow(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: systemImage).foregroundStyle(colors.accent)
                Text(title).font(AtlasFont.body()).foregroundStyle(colors.text)
                Spacer()
                Image(systemName: "chevron.right").font(.caption).foregroundStyle(colors.textMuted)
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
        }
        .buttonStyle(.plain)
    }

    private func sliderRow(_ title: String, value: Binding<Double>, range: ClosedRange<Double>, format: (Double) -> String) -> some View {
        VStack(alignment: .leading) {
            HStack {
                Text(title).font(AtlasFont.body()).foregroundStyle(colors.text)
                Spacer()
                Text(format(value.wrappedValue)).font(AtlasFont.mono(size: 12)).foregroundStyle(colors.accent)
            }
            Slider(value: value, in: range)
                .tint(colors.accent)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }
}
