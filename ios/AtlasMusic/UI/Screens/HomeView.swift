// Atlas iOS — discovery home with resume hero, quick chips, rails and library stats; faithful to Android HomeScreen.
import SwiftUI
import UIKit

struct HomeView: View {
    @Environment(MusicRepository.self) private var music
    @Environment(PlayerController.self) private var player
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs

    let onOpenLibrary: () -> Void
    let onOpenTools: () -> Void
    let onOpenDownloads: () -> Void
    let onOpenPlaylists: () -> Void
    let onPlayAll: ([Track], Int) -> Void
    let onSecretUnlock: () -> Void

    @State private var entered = false
    @State private var showFavorites = false
    @State private var scanning = false
    @State private var tapCount = 0
    @State private var lastTap = Date.distantPast

    init(onOpenLibrary: @escaping () -> Void,
         onOpenTools: @escaping () -> Void,
         onOpenDownloads: @escaping () -> Void,
         onOpenPlaylists: @escaping () -> Void,
         onPlayAll: @escaping ([Track], Int) -> Void,
         onSecretUnlock: @escaping () -> Void) {
        self.onOpenLibrary = onOpenLibrary
        self.onOpenTools = onOpenTools
        self.onOpenDownloads = onOpenDownloads
        self.onOpenPlaylists = onOpenPlaylists
        self.onPlayAll = onPlayAll
        self.onSecretUnlock = onSecretUnlock
    }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var recent: [Track] { music.recentlyPlayed(limit: 12) }
    private var added: [Track] { music.recentlyAdded(limit: 5) }
    private var favorites: [Track] { music.favorites() }
    private var count: Int { music.trackCount() }

    var body: some View {
        ZStack(alignment: .top) {
            LinearGradient(
                colors: [colors.accent.opacity(0.14), colors.background.opacity(0)],
                startPoint: .top, endPoint: .bottom
            )
            .frame(height: 280)
            .ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header
                        .padding(.top, 4)

                    if count == 0 {
                        EmptyState(
                            title: "Your library is quiet",
                            body: "Grant access to your music, then scan. Atlas Music plays what you already own — nothing leaves your device.",
                            actionLabel: "Scan library",
                            action: rescan
                        )
                        quickActions(hasTracks: false)
                    } else {
                        if let resume = recent.first {
                            resumeCard(resume)
                                .opacity(entered ? 1 : 0)
                                .offset(y: entered ? 0 : 18)
                        }
                        quickActions(hasTracks: true)

                        if !recent.isEmpty {
                            SectionHeader("Jump back in", actionLabel: "See all") { onOpenLibrary() }
                            horizontalRail(recent)
                                .opacity(entered ? 1 : 0)
                                .offset(y: entered ? 0 : 24)
                        }

                        if !added.isEmpty {
                            SectionHeader("Recently added", actionLabel: "See all") { onOpenLibrary() }
                            ForEach(added, id: \.uri) { track in
                                homeRow(track, list: added)
                            }
                        }

                        if !favorites.isEmpty {
                            SectionHeader("Favorites", actionLabel: "See all") { showFavorites = true }
                            ForEach(favorites.prefix(4), id: \.uri) { track in
                                homeRow(track, list: favorites)
                            }
                        }

                        stats
                    }
                }
                .padding(.bottom, 120)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .background(colors.background.ignoresSafeArea())
        .animation(AtlasMotion.settle, value: entered)
        .onAppear { entered = true }
        .sheet(isPresented: $showFavorites) {
            NavigationStack {
                SmartListView(
                    title: "Favorites",
                    subtitle: "Generated from your listening",
                    tracks: favorites,
                    onPlayAll: onPlayAll
                )
            }
        }
    }

    private var header: some View {
        HStack(spacing: 12) {
            Button {
                registerSecretTap()
            } label: {
                Image(systemName: "music.note")
                    .font(.title2)
                    .foregroundStyle(colors.accent)
                    .frame(width: 46, height: 46)
                    .background(colors.surfaceElevated)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }
            .accessibilityLabel("Atlas")
            .buttonStyle(.plain)
            VStack(alignment: .leading) {
                Text("Atlas Music")
                    .font(AtlasFont.headline())
                    .foregroundStyle(colors.text)
                Text("AD-LESS MUSIC.")
                    .font(AtlasFont.eyebrow())
                    .foregroundStyle(colors.accent)
            }
            Spacer()
            Button(action: rescan) {
                if scanning {
                    ProgressView()
                        .tint(colors.accent)
                        .frame(width: 22, height: 22)
                } else {
                    Image(systemName: "arrow.clockwise")
                        .foregroundStyle(colors.accent)
                        .frame(width: 32, height: 32)
                }
            }
            .disabled(scanning)
            .accessibilityLabel("Scan for new songs")
            .buttonStyle(.borderless)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    /// Real library rescan using the same scanner the app uses elsewhere, wired to settings.lastScanMs.
    private func rescan() {
        guard !scanning else { return }
        scanning = true
        Task { @MainActor in
            let scanner = LibraryScanner(music: music, settings: settings)
            let result = await scanner.scan()
            settings.lastScanMs = Int64(Date().timeIntervalSince1970 * 1000)
            scanning = false
            dialogs.toast("Scan complete — \(result.added) new, \(result.updated) updated, \(result.removed) removed.")
        }
    }

    private func registerSecretTap() {
        let now = Date()
        tapCount = now.timeIntervalSince(lastTap) <= 2.4 ? tapCount + 1 : 1
        lastTap = now
        if tapCount == 3 || tapCount == 4 {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        } else if tapCount >= 5 {
            tapCount = 0
            UIImpactFeedbackGenerator(style: .heavy).impactOccurred()
            onSecretUnlock()
        }
    }

    private func quickActions(hasTracks: Bool) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                if hasTracks {
                    chip("Shuffle all", systemImage: "shuffle") {
                        let shuffled = music.allTracks(sort: .title).shuffled()
                        if !shuffled.isEmpty { onPlayAll(shuffled, 0) }
                    }
                    chip("Favorites", systemImage: "heart") { showFavorites = true }
                }
                chip("Sound Studio", systemImage: "waveform.path.ecg") { onOpenTools() }
                chip("Downloads", systemImage: "arrow.down.circle") { onOpenDownloads() }
                if hasTracks {
                    chip("Playlists", systemImage: "music.note.list") { onOpenPlaylists() }
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 6)
        }
    }

    private func chip(_ title: String, systemImage: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: systemImage).foregroundStyle(colors.accent)
                Text(title).font(AtlasFont.caption())
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(colors.surface)
            .clipShape(Capsule())
            .overlay(Capsule().stroke(colors.border, lineWidth: 1))
        }
        .buttonStyle(.plain)
        .foregroundStyle(colors.text)
    }

    private func resumeCard(_ track: Track) -> some View {
        Button {
            onPlayAll([track], 0)
        } label: {
            HStack(spacing: 14) {
                AlbumArt(track: track, size: 88, corner: 12, showStrip: false)
                VStack(alignment: .leading) {
                    Text("RESUME")
                        .font(AtlasFont.eyebrow())
                        .foregroundStyle(colors.accent)
                    Text(track.displayTitle)
                        .font(AtlasFont.body().weight(.semibold))
                        .foregroundStyle(colors.text)
                        .lineLimit(1)
                    Text(track.displayArtist)
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                        .lineLimit(1)
                }
                Spacer()
                Image(systemName: "play.fill")
                    .font(.title3)
                    .foregroundStyle(colors.onAccent)
                    .frame(width: 52, height: 52)
                    .background(colors.accent)
                    .clipShape(Circle())
            }
            .padding(12)
            .background(colors.surfaceElevated)
            .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
            .shadow(color: colors.accent.opacity(0.18), radius: 16, y: 6)
        }
        .buttonStyle(.plain)
        .padding(.horizontal, 16)
        .padding(.vertical, 6)
    }

    private func horizontalRail(_ tracks: [Track]) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 14) {
                ForEach(tracks.prefix(12), id: \.uri) { track in
                    Button {
                        onPlayAll(tracks, atlasTrackIndex(track, in: tracks))
                    } label: {
                        VStack(alignment: .leading) {
                            AlbumArt(track: track, size: 132, corner: Radius.lg, showStrip: false)
                            Text(track.displayTitle)
                                .font(AtlasFont.body())
                                .foregroundStyle(colors.text)
                                .lineLimit(1)
                            Text(track.displayArtist)
                                .font(AtlasFont.caption())
                                .foregroundStyle(colors.textMuted)
                                .lineLimit(1)
                        }
                        .frame(width: 132, alignment: .leading)
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 16)
        }
    }

    private func homeRow(_ track: Track, list: [Track]) -> some View {
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

    private var stats: some View {
        HStack(spacing: 10) {
            StatPill(value: "\(count)", label: "tracks")
            StatPill(value: Format.totalDuration(music.totalDurationMs()), label: "of music")
        }
        .padding(16)
    }
}
