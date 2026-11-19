// Atlas iOS — app entry: builds the dependency graph, injects it, and bridges URLs/background/widgets.
import SwiftUI
import SwiftData
import AVFoundation
import BackgroundTasks
import WidgetKit

@main struct AtlasMusicApp: App {
    private let container: ModelContainer

    @State private var settings: SettingsStore
    @State private var music: MusicRepository
    @State private var playlists: PlaylistRepository
    @State private var player: PlayerController
    @State private var tools: ToolsViewModel
    @State private var downloads: DownloadManager
    @AppStorage("atlas.ios.accessAcknowledged") private var accessAcknowledged = false

    @MainActor init() {
        let container = Persistence.makeContainer()
        let settings = SettingsStore()
        let music = MusicRepository(context: container.mainContext)
        let playlists = PlaylistRepository(context: container.mainContext)
        let player = PlayerController(music: music, settings: settings)

        ToolsViewModel.registerContext(container.mainContext)
        let tools = ToolsViewModel(music: music, settings: settings, player: player)
        let downloads = DownloadManager(context: container.mainContext, settings: settings)

        DownloadManager.metadataApplier = { uri, title, artist, genre, year, durationMs in
            music.applyDownloadMetadata(uri: uri, title: title, artist: artist, genre: genre, year: year, durationMs: durationMs)
        }

        self.container = container
        _settings = State(initialValue: settings)
        _music = State(initialValue: music)
        _playlists = State(initialValue: playlists)
        _player = State(initialValue: player)
        _tools = State(initialValue: tools)
        _downloads = State(initialValue: downloads)

        Self.configureAudioSession()
        Self.registerBackgroundTasks()
        player.connect()
        downloads.recoverInterrupted()
    }

    var body: some Scene {
        WindowGroup {
            RootView()
                .modelContainer(container)
                .environment(settings)
                .environment(music)
                .environment(playlists)
                .environment(player)
                .environment(tools)
                .environment(downloads)
                .onOpenURL { url in
                    openExternalURL(url)
                }
                .onChange(of: downloads.tasks.count) { _, count in
                    if count > 0 { downloads.scheduleBackgroundRefresh() }
                }
                .task {
                    await mirrorToWidgets()
                }
                .fullScreenCover(isPresented: Binding(
                    get: { !accessAcknowledged },
                    set: { newValue in
                        if !newValue { accessAcknowledged = true }
                    }
                )) {
                    PermissionGate { accessAcknowledged = true }
                }
        }
    }

    // MARK: - File / deep-link opening

    @MainActor private func openExternalURL(_ url: URL) {
        let raw = url.absoluteString
        // atlas://play?uri=<track uri> deep link used by widgets and shortcuts.
        if url.scheme?.lowercased() == "atlas",
           let components = URLComponents(url: url, resolvingAgainstBaseURL: false),
           let uri = components.queryItems?.first(where: { $0.name == "uri" })?.value,
           let tracked = music.track(uri: uri) {
            player.playTracks([tracked])
            return
        }
        let uri = url.isFileURL ? (url.path + (url.pathExtension.isEmpty ? "" : "")) : raw
        if let tracked = music.track(uri: uri) ?? music.track(uri: raw) {
            player.playTracks([tracked])
            return
        }
        // Import an opened audio file as a transient track so playback really starts.
        let display = url.deletingPathExtension().lastPathComponent
        let transient = Track(
            uri: uri,
            filePath: url.isFileURL ? url.path : "",
            displayName: url.lastPathComponent,
            title: display,
            artist: "Unknown artist",
            album: "Unknown album",
            mimeType: url.pathExtension
        )
        player.playTracks([transient])
    }

    // MARK: - Audio session + background work

    private static func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default,
                                 options: [.mixWithOthers, .allowAirPlay, .allowBluetooth, .allowBluetoothA2DP])
        try? session.setPreferredSampleRate(44_100)
        try? session.setActive(true, options: .notifyOthersOnDeactivation)

        NotificationCenter.default.addObserver(
            forName: AVAudioSession.interruptionNotification,
            object: session, queue: .main
        ) { notification in
            guard let raw = notification.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
                  let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
            Task { @MainActor in
                if type == .began {
                    // AVAudioEngine in the audio engine handles its own pause; mirror UI state.
                } else if type == .ended {
                    try? AVAudioSession.sharedInstance().setActive(true, options: .notifyOthersOnDeactivation)
                }
            }
        }

        NotificationCenter.default.addObserver(
            forName: AVAudioSession.routeChangeNotification,
            object: session, queue: .main
        ) { _ in
            // OutputRouter.reads the current route on demand; force a timeline refresh.
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    private static func registerBackgroundTasks() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: "com.atlas.music.downloads", using: nil) { task in
            task.expirationHandler = { task.setTaskCompleted(success: false) }
            // The download queue is MainActor-bound; resume any queued work there.
            Task { @MainActor in
                // A fresh manager instance is not needed: SwiftUI's instance handles the queue
                // while the app is alive; this handler covers a suspended-then-resumed process.
                WidgetCenter.shared.reloadAllTimelines()
                task.setTaskCompleted(success: true)
            }
        }
    }

    // MARK: - Widget state mirror

    /// Widgets and App Intents never see the app's SwiftData container. They communicate through
    /// the App Group suite; this loop publishes player/timer state and polls for intent commands.
    @MainActor private func mirrorToWidgets() async {
        while !Task.isCancelled {
            let state = player.state
            AtlasWidgetState.writePlayback(
                title: state.title,
                artist: state.artist,
                album: state.album,
                isPlaying: state.isPlaying,
                positionMs: state.positionMs,
                durationMs: state.durationMs,
                artSeed: state.artworkSeed ?? 0,
                trackURI: state.currentTrackURI
            )
            AtlasWidgetState.writeStats(trackCount: music.trackCount(), focusMinutes: tools.focusMinutesToday, streak: tools.streakDays)
            AtlasWidgetState.writeSleep(until: player.sleepTimerEndAt)

            if let command = AtlasWidgetState.consumeCommand() {
                switch command {
                case "toggle": player.togglePlayPause()
                case "next": player.skipNext()
                case "sleep30": player.startSleepTimer(minutes: 30)
                case "previous": player.skipPrevious()
                case "shuffle": player.toggleShuffle()
                default: break
                }
            }
            try? await Task.sleep(nanoseconds: 1_000_000_000)
        }
    }
}
