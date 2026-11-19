// Atlas iOS — classic iPod simulation: shell finishes, LCD menu tree, click wheel, cover flow, now playing.
import SwiftUI
import UIKit
import AVFoundation

// MARK: - Shell palettes (exact values from Android IpodModeScreen.kt)

private struct IpodPalette {
    let bodyTop: Color
    let bodyBottom: Color
    let bezel: Color
    let wheelSurface: Color
    let wheelRim: Color
    let wheelText: Color
    let centerButton: Color
    let centerBorder: Color
    let highlight: Color
}

private func ipodPaletteFor(_ finish: String) -> IpodPalette {
    switch finish {
    case "black":
        return IpodPalette(bodyTop: Color(0xFF2B2E36), bodyBottom: Color(0xFF15171C), bezel: Color(0xFF0A0B0E),
                           wheelSurface: Color(0xFF1D2027), wheelRim: Color(0xFF393E4A), wheelText: Color(0xFFD6DAE3),
                           centerButton: Color(0xFF2E323C), centerBorder: Color(0xFF4B5160), highlight: Color(0xFF3A7AFE))
    case "gold":
        return IpodPalette(bodyTop: Color(0xFFDCC89E), bodyBottom: Color(0xFFB59A64), bezel: Color(0xFF1B1914),
                           wheelSurface: Color(0xFFF6EFE0), wheelRim: Color(0xFFCBB88B), wheelText: Color(0xFF5D4C27),
                           centerButton: Color(0xFFE7D7B2), centerBorder: Color(0xFFB8A06E), highlight: Color(0xFF9C6D18))
    case "crimson":
        return IpodPalette(bodyTop: Color(0xFF1F2024), bodyBottom: Color(0xFF101114), bezel: Color(0xFF07080A),
                           wheelSurface: Color(0xFFB6212D), wheelRim: Color(0xFFDF404D), wheelText: Color(0xFFFFECEE),
                           centerButton: Color(0xFF17181C), centerBorder: Color(0xFF42454E), highlight: Color(0xFFD22332))
    default:
        return IpodPalette(bodyTop: Color(0xFFEAECEF), bodyBottom: Color(0xFFC8CDD4), bezel: Color(0xFF14161A),
                           wheelSurface: Color(0xFFF8F9FA), wheelRim: Color(0xFFC4C9D2), wheelText: Color(0xFF5E6673),
                           centerButton: Color(0xFFE2E5EA), centerBorder: Color(0xFFB6BDC7), highlight: Color(0xFF2E7DF6))
    }
}

// MARK: - Page tree

private enum IpodPage: Hashable {
    case root, music, allSongs, favorites, recentlyAdded, artists, albums, playlists
    case artistSongs(String), albumSongs(String), playlistSongs(String)
    case nowPlaying, coverFlow, settings, shellFinish

    var title: String {
        switch self {
        case .root: return "Atlas iPod"
        case .music: return "Music"
        case .allSongs: return "Songs"
        case .favorites: return "Favorites"
        case .recentlyAdded: return "Recent"
        case .artists: return "Artists"
        case .artistSongs(let value): return value
        case .albums: return "Albums"
        case .albumSongs(let value): return value
        case .playlists: return "Playlists"
        case .playlistSongs(let value): return value
        case .nowPlaying: return "Now Playing"
        case .coverFlow: return "Cover Flow"
        case .settings: return "Settings"
        case .shellFinish: return "Shell Finish"
        }
    }
}

private struct IpodRowItem {
    let label: String
    let trailing: String?
    let hasChevron: Bool
    let onSelect: () -> Void

    init(_ label: String, trailing: String? = nil, hasChevron: Bool = false, onSelect: @escaping () -> Void) {
        self.label = label
        self.trailing = trailing
        self.hasChevron = hasChevron
        self.onSelect = onSelect
    }
}

// MARK: - Main screen

struct IpodModeView: View {
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(MusicRepository.self) private var music
    @Environment(PlaylistRepository.self) private var playlists
    @Environment(\.dismiss) private var dismiss

    @State private var navStack: [IpodPage] = [.root]
    @State private var selStack: [Int] = [0]
    @State private var allSongs: [Track] = []
    @State private var favorites: [Track] = []
    @State private var recentSongs: [Track] = []
    @State private var artistCounts: [NameCount] = []
    @State private var albumCounts: [NameCount] = []
    @State private var playlistModels: [Playlist] = []
    @State private var scopedPlaylistTracks: [Track] = []
    @State private var currentTrack: Track?
    @State private var nowPlayingWheelScrub = false
    @State private var showVolumeHud = false
    @State private var volumeRatio: Float = 0.6
    @State private var coverFlowIndex = 0
    @State private var batteryPct = 100
    @State private var volumeHudTask: Task<Void, Never>?

    private let onExit: () -> Void
    private let useFallbackExit: Bool

    init(onExit: @escaping () -> Void) {
        self.onExit = onExit
        self.useFallbackExit = false
    }

    init(onExitIpod: @escaping () -> Void) {
        self.onExit = onExitIpod
        self.useFallbackExit = false
    }

    init() {
        self.onExit = {}
        self.useFallbackExit = true
    }

    private var currentPage: IpodPage { navStack.last ?? .root }
    private var currentIndex: Int { max(0, selStack.last ?? 0) }
    private var palette: IpodPalette { ipodPaletteFor(settings.ipodShellFinish) }

    var body: some View {
        GeometryReader { geo in
            ZStack {
                LinearGradient(colors: [palette.bodyTop, palette.bodyBottom], startPoint: .top, endPoint: .bottom)
                    .ignoresSafeArea()
                if geo.size.width > geo.size.height {
                    HStack(spacing: 24) {
                        lcdHousing
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                        clickWheel
                            .frame(width: min(geo.size.height * 0.72, geo.size.width * 0.42))
                            .aspectRatio(1, contentMode: .fit)
                    }
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                } else {
                    VStack(spacing: 16) {
                        lcdHousing
                            .frame(maxWidth: .infinity)
                            .frame(height: max(220, geo.size.height * 0.46))
                        clickWheel
                            .frame(width: min(geo.size.width * 0.78, geo.size.height * 0.44))
                            .aspectRatio(1, contentMode: .fit)
                    }
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .padding(.horizontal, 20)
                    .padding(.vertical, 14)
                }
            }
        }
        .onAppear(perform: refreshLibrary)
        .onChange(of: player.state.currentTrackURI) { _, _ in
            currentTrack = player.state.currentTrackURI.flatMap { music.track(uri: $0) }
        }
        .onChange(of: currentPage) { _, page in
            if case .playlistSongs(let name) = page,
               let playlist = playlistModels.first(where: { $0.name == name }) {
                scopedPlaylistTracks = playlists.tracks(in: playlist)
            }
            let safe = safeIndex(for: menuRows)
            if selStack.count == navStack.count, selStack[selStack.count - 1] != safe {
                selStack[selStack.count - 1] = safe
            }
        }
        .statusBar(hidden: true)
    }

    private func refreshLibrary() {
        allSongs = music.allTracks(sort: .title)
        favorites = music.favorites()
        recentSongs = music.recentlyAdded(limit: 40)
        artistCounts = music.artists()
        albumCounts = music.albums()
        playlistModels = playlists.playlists()
        currentTrack = player.state.currentTrackURI.flatMap { music.track(uri: $0) }
        volumeRatio = AVAudioSession.sharedInstance().outputVolume
        UIDevice.current.isBatteryMonitoringEnabled = true
        let level = UIDevice.current.batteryLevel
        batteryPct = level >= 0 ? max(1, Int(level * 100)) : 100
    }

    // MARK: - Navigation

    private func pushPage(_ page: IpodPage) {
        navStack.append(page)
        selStack.append(0)
    }

    private func popPage() {
        if navStack.count > 1 {
            navStack.removeLast()
            selStack.removeLast()
        } else {
            exitIpod()
        }
    }

    private func exitIpod() {
        if useFallbackExit {
            settings.ipodModeEnabled = false
            dismiss()
        } else {
            onExit()
        }
    }

    // MARK: - Menu rows

    private var menuRows: [IpodRowItem] {
        switch currentPage {
        case .root:
            var rows: [IpodRowItem] = [
                IpodRowItem("Music", hasChevron: true) { pushPage(.music) },
                IpodRowItem("Cover Flow", trailing: "\(allSongs.count)", hasChevron: true) {
                    coverFlowIndex = 0
                    pushPage(.coverFlow)
                }
            ]
            if player.state.hasQueue {
                rows.append(IpodRowItem("Now Playing", hasChevron: true) { pushPage(.nowPlaying) })
            }
            rows.append(IpodRowItem("Shuffle Songs") {
                if !allSongs.isEmpty {
                    player.playTracks(allSongs.shuffled())
                    pushPage(.nowPlaying)
                }
            })
            rows.append(IpodRowItem("Settings", hasChevron: true) { pushPage(.settings) })
            rows.append(IpodRowItem("Exit Classic Mode", trailing: "Atlas UI") { exitIpod() })
            return rows

        case .music:
            return [
                IpodRowItem("Songs", trailing: "\(allSongs.count)", hasChevron: true) { pushPage(.allSongs) },
                IpodRowItem("Favorites", trailing: "\(favorites.count)", hasChevron: true) { pushPage(.favorites) },
                IpodRowItem("Artists", trailing: "\(artistCounts.count)", hasChevron: true) { pushPage(.artists) },
                IpodRowItem("Albums", trailing: "\(albumCounts.count)", hasChevron: true) { pushPage(.albums) },
                IpodRowItem("Playlists", trailing: "\(playlistModels.count)", hasChevron: true) { pushPage(.playlists) },
                IpodRowItem("Recently Added", hasChevron: true) { pushPage(.recentlyAdded) }
            ]

        case .allSongs:
            return allSongs.enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(allSongs, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .favorites:
            return favorites.enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(favorites, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .recentlyAdded:
            return Array(recentSongs.prefix(40)).enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(recentSongs, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .artists:
            return artistCounts.map { count in
                IpodRowItem(count.name, trailing: "\(count.count)", hasChevron: true) {
                    pushPage(.artistSongs(count.name))
                }
            }

        case .artistSongs(let artist):
            let matching = allSongs.filter { $0.artist == artist }
            return matching.enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(matching, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .albums:
            return albumCounts.map { count in
                IpodRowItem(count.name, trailing: "\(count.count)", hasChevron: true) {
                    pushPage(.albumSongs(count.name))
                }
            }

        case .albumSongs(let album):
            let matching = allSongs.filter { $0.album == album }
            return matching.enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(matching, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .playlists:
            return playlistModels.map { playlist in
                IpodRowItem(playlist.name, hasChevron: true) { pushPage(.playlistSongs(playlist.name)) }
            }

        case .playlistSongs(let name):
            return scopedPlaylistTracks.enumerated().map { index, track in
                IpodRowItem(track.displayTitle, trailing: Format.duration(track.durationMs)) {
                    player.playTracks(scopedPlaylistTracks, startAt: index)
                    pushPage(.nowPlaying)
                }
            }

        case .settings:
            return [
                IpodRowItem("Shell Finish", trailing: IpodShellFinish(rawValue: settings.ipodShellFinish)?.label ?? settings.ipodShellFinish, hasChevron: true) {
                    pushPage(.shellFinish)
                },
                IpodRowItem("Wheel Clicker", trailing: settings.ipodClickSound ? "On" : "Off") {
                    settings.ipodClickSound.toggle()
                },
                IpodRowItem("Shuffle", trailing: player.state.shuffleEnabled ? "On" : "Off") {
                    player.toggleShuffle()
                },
                IpodRowItem("Repeat", trailing: repeatLabel) { player.cycleRepeatMode() },
                IpodRowItem("Exit Classic Mode") { exitIpod() }
            ]

        case .shellFinish:
            return IpodShellFinish.allCases.map { finish in
                IpodRowItem(finish.label, trailing: settings.ipodShellFinish == finish.rawValue ? "✓" : nil) {
                    settings.ipodShellFinish = finish.rawValue
                }
            }

        case .nowPlaying, .coverFlow:
            return []
        }
    }

    private var repeatLabel: String {
        switch player.state.repeatMode {
        case .one: return "One"
        case .all: return "All"
        case .off: return "Off"
        }
    }

    private func safeIndex(for rows: [IpodRowItem]) -> Int {
        rows.isEmpty ? 0 : min(max(0, currentIndex), rows.count - 1)
    }

    // MARK: - Wheel handling

    private func emitDetentFeedback() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if settings.ipodClickSound { UIDevice.current.playInputClick() }
    }

    private func onWheelStep(_ clicks: Int) {
        guard clicks != 0 else { return }
        emitDetentFeedback()
        let state = player.state
        switch currentPage {
        case .nowPlaying:
            if nowPlayingWheelScrub, state.durationMs > 0 {
                let stepMs = max(1_500, min(10_000, state.durationMs / 40))
                let target = min(state.durationMs, max(0, state.positionMs + Int64(clicks) * stepMs))
                player.seek(to: target)
            } else {
                // iOS does not expose settable system volume; the wheel drives the app's audio
                // bus volume instead and shows the same classic volume HUD.
                volumeRatio = min(1, max(0, volumeRatio + Float(clicks) * 0.05))
                player.setFocusVolume(volumeRatio)
                showVolumeHud = true
                volumeHudTask?.cancel()
                volumeHudTask = Task { @MainActor in
                    try? await Task.sleep(nanoseconds: 1_600_000_000)
                    if !Task.isCancelled { showVolumeHud = false }
                }
            }
        case .coverFlow:
            if !allSongs.isEmpty {
                coverFlowIndex = min(allSongs.count - 1, max(0, coverFlowIndex + clicks))
            }
        default:
            let rows = menuRows
            if !rows.isEmpty {
                let next = min(rows.count - 1, max(0, currentIndex + clicks))
                if selStack.count == navStack.count { selStack[selStack.count - 1] = next }
            }
        }
    }

    private func onCenterClick() {
        emitDetentFeedback()
        switch currentPage {
        case .nowPlaying:
            nowPlayingWheelScrub.toggle()
        case .coverFlow:
            if !allSongs.isEmpty {
                player.playTracks(allSongs, startAt: min(allSongs.count - 1, max(0, coverFlowIndex)))
                pushPage(.nowPlaying)
            }
        default:
            let rows = menuRows
            let index = safeIndex(for: rows)
            if rows.indices.contains(index) { rows[index].onSelect() }
        }
    }

    // MARK: - LCD housing

    private var lcdHousing: some View {
        VStack(spacing: 0) {
            HStack(spacing: 6) {
                Image(systemName: player.state.isPlaying ? "play.fill" : "pause.fill")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(palette.highlight)
                Text(currentPage.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(Color(0xFF161A20))
                    .lineLimit(1)
                Spacer()
                Text("\(batteryPct)%")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(Color(0xFF424A56))
                Image(systemName: "battery.100")
                    .foregroundStyle(Color(0xFF288E41))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(LinearGradient(colors: [Color(0xFFECEFF4), Color(0xFFD0D6DF)], startPoint: .top, endPoint: .bottom))
            Rectangle().fill(Color(0xFFB5BDC8)).frame(height: 1)
            lcdContent
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
        .background(Color(0xFFFDFEFF))
        .clipShape(RoundedRectangle(cornerRadius: 10))
        .padding(10)
        .background(palette.bezel, in: RoundedRectangle(cornerRadius: 18))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color.white.opacity(0.14), lineWidth: 2))
        .shadow(radius: 10)
    }

    @ViewBuilder private var lcdContent: some View {
        switch currentPage {
        case .nowPlaying:
            nowPlayingLcd
        case .coverFlow:
            coverFlowLcd
        default:
            menuLcd
        }
    }

    private var menuLcd: some View {
        Group {
            if menuRows.isEmpty {
                Text("Nothing here yet")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color(0xFF6B7280))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollViewReader { proxy in
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(Array(menuRows.indices), id: \.self) { index in
                                let row = menuRows[index]
                                let selected = index == safeIndex(for: menuRows)
                                HStack(spacing: 8) {
                                    Text(row.label)
                                        .font(.system(size: 15, weight: selected ? .bold : .medium))
                                        .foregroundStyle(selected ? .white : Color(0xFF191D24))
                                        .lineLimit(1)
                                    Spacer()
                                    if let trailing = row.trailing {
                                        Text(trailing)
                                            .font(.system(size: 12, design: .monospaced))
                                            .foregroundStyle(selected ? .white.opacity(0.9) : Color(0xFF6A7280))
                                    }
                                    if row.hasChevron {
                                        Image(systemName: "chevron.right")
                                            .font(.system(size: 14, weight: .semibold))
                                            .foregroundStyle(selected ? .white : Color(0xFF7B8390))
                                    }
                                }
                                .padding(.horizontal, 12)
                                .padding(.vertical, 8)
                                .background(selected ? palette.highlight : Color.clear)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    if selStack.count == navStack.count { selStack[selStack.count - 1] = index }
                                    row.onSelect()
                                }
                                .id(index)
                            }
                        }
                    }
                    .onAppear {
                        proxy.scrollTo(safeIndex(for: menuRows), anchor: .center)
                    }
                    .onChange(of: currentIndex) { _, index in
                        withAnimation { proxy.scrollTo(index, anchor: .center) }
                    }
                }
            }
        }
    }

    // MARK: - Now Playing LCD

    private var nowPlayingLcd: some View {
        let state = player.state
        let fraction = state.durationMs > 0 ? min(1, max(0, Double(state.positionMs) / Double(state.durationMs))) : 0
        return VStack(spacing: 8) {
            HStack(spacing: 12) {
                ZStack {
                    RoundedRectangle(cornerRadius: 8).fill(Color(0xFF191D24))
                    if let track = currentTrack {
                        ProceduralCoverView(
                            seed: CoverArtKit.seed(for: track),
                            title: track.displayTitle,
                            ext: track.mimeType,
                            sizeBytes: track.sizeBytes,
                            showStrip: false
                        )
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                    }
                }
                .frame(width: 104, height: 104)
                VStack(alignment: .leading, spacing: 4) {
                    Text(state.title.isEmpty ? "No Track" : state.title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(Color(0xFF14181F))
                        .lineLimit(2)
                    Text(state.artist.isEmpty ? "Unknown artist" : state.artist)
                        .font(.system(size: 13))
                        .foregroundStyle(Color(0xFF4F5764))
                        .lineLimit(1)
                    if !state.album.isEmpty, state.album != "Unknown album" {
                        Text(state.album)
                            .font(.system(size: 12))
                            .foregroundStyle(Color(0xFF6B7380))
                            .lineLimit(1)
                    }
                    HStack(spacing: 8) {
                        if state.shuffleEnabled { Image(systemName: "shuffle").foregroundStyle(palette.highlight).font(.system(size: 13)) }
                        if state.repeatMode != .off { Image(systemName: "repeat").foregroundStyle(palette.highlight).font(.system(size: 13)) }
                    }
                }
                Spacer()
            }
            Spacer(minLength: 0)
            VStack(spacing: 2) {
                GeometryReader { proxy in
                    let width = proxy.size.width
                    let active = showVolumeHud ? CGFloat(volumeRatio) : CGFloat(fraction)
                    ZStack(alignment: .leading) {
                        Capsule().fill(Color(0xFFDDE2E9))
                        Capsule().fill(showVolumeHud ? Color(0xFF288E41) : palette.highlight)
                            .frame(width: max(7, width * active))
                        if nowPlayingWheelScrub && !showVolumeHud {
                            Circle().fill(.white).frame(width: 12, height: 12)
                                .overlay(Circle().fill(palette.highlight).frame(width: 9, height: 9))
                                .offset(x: width * active - 6)
                        }
                    }
                }
                .frame(height: 7)
                HStack {
                    Text(showVolumeHud ? "Volume \(Int(volumeRatio * 100))%" : Format.duration(state.positionMs))
                    Spacer()
                    Text(nowPlayingWheelScrub ? "Wheel: Seek · ● for Vol" : "Wheel: Vol · ● for Seek")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(palette.highlight)
                    Spacer()
                    Text("-\(Format.duration(max(0, state.durationMs - state.positionMs)))")
                }
                .font(.system(size: 11, design: .monospaced))
                .foregroundStyle(Color(0xFF454D59))
            }
        }
        .padding(12)
        .background(Color(0xFFFAFBFD))
    }

    // MARK: - Cover Flow LCD

    private var coverFlowLcd: some View {
        ZStack {
            LinearGradient(colors: [Color(0xFF101319), Color(0xFF040507)], startPoint: .top, endPoint: .bottom)
            if allSongs.isEmpty {
                Text("No tracks loaded").foregroundStyle(.white.opacity(0.7))
            } else {
                let safe = min(allSongs.count - 1, max(0, coverFlowIndex))
                let center = allSongs[safe]
                VStack(spacing: 8) {
                    HStack(alignment: .center, spacing: 12) {
                        coverArt(for: allSongs[max(0, safe - 1)], size: 76)
                            .frame(width: 66, height: 88)
                            .rotation3DEffect(.degrees(26), axis: (x: 0, y: 1, z: 0))
                            .opacity(0.72)
                            .hiddenCondition(safe == 0)
                        coverArt(for: center, size: 116)
                            .frame(width: 116, height: 116)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(palette.highlight, lineWidth: 1.5))
                            .shadow(radius: 12)
                        coverArt(for: allSongs[min(allSongs.count - 1, safe + 1)], size: 76)
                            .frame(width: 66, height: 88)
                            .rotation3DEffect(.degrees(-26), axis: (x: 0, y: 1, z: 0))
                            .opacity(0.72)
                            .hiddenCondition(safe == allSongs.count - 1)
                    }
                    VStack(spacing: 2) {
                        Text(center.displayTitle)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                            .lineLimit(1)
                        Text("\(center.displayArtist)  ·  \(safe + 1) / \(allSongs.count)")
                            .font(.system(size: 11))
                            .foregroundStyle(.white.opacity(0.72))
                            .lineLimit(1)
                    }
                }
                .padding(.bottom, 20)
            }
        }
        .padding(10)
    }

    private func coverArt(for track: Track, size: CGFloat) -> some View {
        ProceduralCoverView(
            seed: CoverArtKit.seed(for: track),
            title: track.displayTitle,
            ext: track.mimeType,
            sizeBytes: track.sizeBytes,
            showStrip: false
        )
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    // MARK: - Click wheel

    private var clickWheel: some View {
        ClickWheelView(
            palette: palette,
            onRotateSteps: { onWheelStep($0) },
            onMenu: { emitDetentFeedback(); popPage() },
            onNext: { emitDetentFeedback(); player.skipNext() },
            onPrev: { emitDetentFeedback(); player.skipPrevious() },
            onPlayPause: { emitDetentFeedback(); player.togglePlayPause() },
            onSelect: { onCenterClick() }
        )
    }
}

// MARK: - Click wheel view (uses ClickWheelMath from Agent 2)

private enum WheelDragMode { case none, spin, button }

private struct ClickWheelView: View {
    let palette: IpodPalette
    let onRotateSteps: (Int) -> Void
    let onMenu: () -> Void
    let onNext: () -> Void
    let onPrev: () -> Void
    let onPlayPause: () -> Void
    let onSelect: () -> Void

    @State private var mode: WheelDragMode = .none
    @State private var lastAngle: Float = 0
    @State private var pendingRad: Float = 0
    @State private var generatedClicks = 0
    @State private var startZone: ClickWheelZone = .outside
    @State private var moved: Float = 0

    private let innerRatio: Float = 0.36

    var body: some View {
        GeometryReader { geo in
            let cx = Float(geo.size.width / 2)
            let cy = Float(geo.size.height / 2)
            let radius = Float(min(cx, cy))
            ZStack {
                Circle()
                    .fill(RadialGradient(colors: [palette.wheelSurface, palette.wheelSurface.opacity(0.92)],
                                         center: .init(x: 0.85, y: 0.85), startRadius: 0, endRadius: CGFloat(radius * 1.15)))
                Circle().stroke(palette.wheelRim, lineWidth: 2)
                Circle()
                    .fill(palette.centerButton)
                    .frame(width: CGFloat(radius * innerRatio * 2), height: CGFloat(radius * innerRatio * 2))
                    .overlay(Circle().stroke(palette.centerBorder, lineWidth: 1.5))

                Text("MENU")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(palette.wheelText)
                    .position(x: CGFloat(cx), y: 20)
                Image(systemName: "backward.fill")
                    .foregroundStyle(palette.wheelText)
                    .position(x: 20, y: CGFloat(cy))
                Image(systemName: "forward.fill")
                    .foregroundStyle(palette.wheelText)
                    .position(x: CGFloat(geo.size.width - 20), y: CGFloat(cy))
                HStack(spacing: 2) {
                    Image(systemName: "play.fill")
                    Image(systemName: "pause.fill")
                }
                .foregroundStyle(palette.wheelText)
                .position(x: CGFloat(cx), y: CGFloat(geo.size.height - 20))
            }
            .clipShape(Circle())
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let x = Float(value.location.x)
                        let y = Float(value.location.y)
                        if mode == .none {
                            startZone = ClickWheelMath.zoneAt(cx: cx, cy: cy, outerRadius: radius,
                                                              innerRatio: innerRatio, x: x, y: y)
                            if startZone == .outside { return }
                            mode = startZone == .centerSelect ? .button : .spin
                            lastAngle = ClickWheelMath.angleAt(cx: cx, cy: cy, x: x, y: y)
                        }
                        if mode == .spin {
                            let dx = Float(value.translation.width)
                            let dy = Float(value.translation.height)
                            moved = sqrt(dx * dx + dy * dy)
                            let angle = ClickWheelMath.angleAt(cx: cx, cy: cy, x: x, y: y)
                            let delta = ClickWheelMath.shortestAngleDelta(prevRad: lastAngle, nextRad: angle)
                            let result = ClickWheelMath.accumulateClicks(pendingRad: pendingRad, deltaRad: delta)
                            pendingRad = result.1
                            if result.0 != 0 {
                                generatedClicks += abs(result.0)
                                onRotateSteps(result.0)
                            }
                            lastAngle = angle
                        }
                    }
                    .onEnded { value in
                        let zone = ClickWheelMath.zoneAt(cx: cx, cy: cy, outerRadius: radius,
                                                         innerRatio: innerRatio,
                                                         x: Float(value.location.x), y: Float(value.location.y))
                        if mode == .button || generatedClicks == 0 {
                            switch startZone == .centerSelect ? startZone : zone {
                            case .centerSelect: onSelect()
                            case .ringTopMenu: onMenu()
                            case .ringRightNext: onNext()
                            case .ringBottomPlay: onPlayPause()
                            case .ringLeftPrev: onPrev()
                            case .outside: break
                            }
                        }
                        mode = .none
                        pendingRad = 0
                        generatedClicks = 0
                        moved = 0
                    }
            )
        }
    }
}

private extension View {
    @ViewBuilder func hiddenCondition(_ condition: Bool) -> some View {
        if condition { self.hidden() } else { self }
    }
}
