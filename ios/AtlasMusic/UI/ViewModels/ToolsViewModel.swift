// Atlas iOS — tools state: pomodoro timer, focus stats, sleep timer, EQ presets and library repair.
import Foundation
import SwiftData
import WidgetKit

@MainActor @Observable final class ToolsViewModel {
    // MARK: - Contract surface
    var pomodoroMode: String = "FOCUS"          // FOCUS | BREAK | IDLE
    var running = false
    var remainingMs: Int64 = 0
    var totalMs: Int64 = 0
    var focusMinutesToday = 0
    var sessionsToday = 0
    var streakDays = 0
    var sessions: [PomodoroSession] = []
    var lastEvent = ""

    // MARK: - Dependencies
    private let music: MusicRepository
    private let settings: SettingsStore
    private let player: PlayerController
    private let context: ModelContext

    /// AtlasMusicApp registers the container's main context before constructing this VM.
    /// This keeps the contract's exact 3-argument initializer usable from anywhere else.
    nonisolated(unsafe) private static var registeredContext: ModelContext?

    static func registerContext(_ context: ModelContext) {
        registeredContext = context
    }

    private var ticker: Task<Void, Never>?
    private var endAt: Date?
    private var pausedRemainingMs: Int64 = 0
    private var lastStatsRefresh = Date.distantPast

    init(music: MusicRepository, settings: SettingsStore, player: PlayerController, context: ModelContext? = nil) {
        self.music = music
        self.settings = settings
        self.player = player
        if let context {
            self.context = context
            Self.registeredContext = context
        } else if let shared = Self.registeredContext {
            self.context = shared
        } else {
            // Last-resort in-memory store. The app always registers the real container first,
            // so this path is only reached by standalone previews/tests.
            let config = ModelConfiguration(isStoredInMemoryOnly: true)
            let container = try! ModelContainer(for: PomodoroSession.self, configurations: config)
            self.context = ModelContext(container)
        }
        reloadStats()
        startTicker()
    }

    deinit { ticker?.cancel() }

    // MARK: - Ticking (countdown + sleep countdown + cheap stat refresh)

    private func startTicker() {
        ticker?.cancel()
        ticker = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 1_000_000_000)
                guard !Task.isCancelled else { break }
                self.tick()
            }
        }
    }

    private func tick() {
        if running, let endAt {
            let remaining = max(0, Int64(endAt.timeIntervalSinceNow * 1000))
            remainingMs = remaining
            if remaining <= 0 { finishSegment() }
        }
        if Date().timeIntervalSince(lastStatsRefresh) >= 15 {
            lastStatsRefresh = Date()
            reloadStats()
        }
    }

    // MARK: - Pomodoro controls

    func startPomodoro() {
        let totalMinutes = pomodoroMode == "BREAK" ? settings.pomodoroBreakMinutes : settings.pomodoroFocusMinutes
        let total = Int64(max(1, totalMinutes)) * 60_000
        totalMs = total
        remainingMs = total
        endAt = Date().addingTimeInterval(TimeInterval(total) / 1000.0)
        pausedRemainingMs = total
        running = true
        lastEvent = ""
        persistWidgetTimer()
        if settings.pomodoroSyncPlayback && pomodoroMode == "FOCUS" {
            player.setFocusVolume(0.35)
        }
    }

    func pausePomodoro() {
        guard running else { return }
        pausedRemainingMs = max(0, Int64(endAt?.timeIntervalSinceNow ?? 0) * 1000)
        remainingMs = pausedRemainingMs
        running = false
        endAt = nil
        persistWidgetTimer()
    }

    func resumePomodoro() {
        guard !running else { return }
        endAt = Date().addingTimeInterval(TimeInterval(pausedRemainingMs) / 1000.0)
        running = true
        persistWidgetTimer()
    }

    func resetPomodoro() {
        running = false
        endAt = nil
        remainingMs = totalMs
        pausedRemainingMs = totalMs
        lastEvent = ""
        persistWidgetTimer()
        if settings.pomodoroSyncPlayback { player.setFocusVolume(1.0) }
    }

    /// No-argument switch used by the Focus screen: FOCUS <-> BREAK.
    func switchMode() {
        switchMode(pomodoroMode == "FOCUS" ? "BREAK" : "FOCUS")
    }

    func switchMode(_ mode: String) {
        pomodoroMode = mode
        running = false
        endAt = nil
        remainingMs = 0
        totalMs = 0
        pausedRemainingMs = 0
        persistWidgetTimer()
    }

    func setFocusMinutes(_ minutes: Int) {
        settings.pomodoroFocusMinutes = max(1, minutes)
    }

    func setBreakMinutes(_ minutes: Int) {
        settings.pomodoroBreakMinutes = max(1, minutes)
    }

    func setSyncPlayback(_ enabled: Bool) {
        settings.pomodoroSyncPlayback = enabled
    }

    private func finishSegment() {
        let mode = pomodoroMode
        let totalMinutes = max(1, Int(totalMs / 60_000))
        let now = Date()
        let started = endAt.map { $0.addingTimeInterval(-TimeInterval(totalMs) / 1000.0) } ?? now.addingTimeInterval(-TimeInterval(totalMs) / 1000.0)
        let session = PomodoroSession(
            startedAt: started,
            endedAt: now,
            durationMinutes: totalMinutes,
            kind: mode,
            completed: true
        )
        context.insert(session)
        try? context.save()

        let nextMode = mode == "FOCUS" ? "BREAK" : "FOCUS"
        pomodoroMode = nextMode
        running = false
        endAt = nil
        remainingMs = 0
        totalMs = 0
        pausedRemainingMs = 0
        lastEvent = mode == "FOCUS" ? "Focus complete — take a break" : "Break over — back to focus"
        persistWidgetTimer()
        if mode == "FOCUS" && settings.pomodoroSyncPlayback { player.setFocusVolume(1.0) }
        reloadStats()
        WidgetReloader.reload()
    }

    // MARK: - Focus statistics (from PomodoroSession rows)

    private func reloadStats() {
        let descriptor = FetchDescriptor<PomodoroSession>(sortBy: [SortDescriptor(\.startedAt, order: .reverse)])
        let all = (try? context.fetch(descriptor)) ?? []
        sessions = all
        let today = all.filter { $0.kind == "FOCUS" && $0.completed && $0.startedAt >= Self.startOfToday() }
        focusMinutesToday = today.reduce(0) { $0 + $1.durationMinutes }
        sessionsToday = today.count
        streakDays = Self.computeStreak(all)
        lastStatsRefresh = Date()
    }

    private static func startOfToday() -> Date {
        let calendar = Calendar.current
        return calendar.startOfDay(for: Date())
    }

    private static func computeStreak(_ sessions: [PomodoroSession]) -> Int {
        let focus = sessions.filter { $0.kind == "FOCUS" && $0.completed }
        guard !focus.isEmpty else { return 0 }
        let calendar = Calendar.current
        let days = Set(focus.map { session -> Int in
            let comps = calendar.dateComponents([.year, .day], from: session.startedAt)
            return (comps.year ?? 0) * 1000 + (comps.day ?? 0)
        })
        func key(_ date: Date) -> Int {
            let comps = calendar.dateComponents([.year, .day], from: date)
            return (comps.year ?? 0) * 1000 + (comps.day ?? 0)
        }
        var cursor = calendar.startOfDay(for: Date())
        if !days.contains(key(cursor)) {
            guard let yesterday = calendar.date(byAdding: .day, value: -1, to: cursor) else { return 0 }
            cursor = yesterday
            if !days.contains(key(cursor)) { return 0 }
        }
        var streak = 0
        while days.contains(key(cursor)) {
            streak += 1
            guard let previous = calendar.date(byAdding: .day, value: -1, to: cursor) else { break }
            cursor = previous
        }
        return streak
    }

    // MARK: - Widget timer mirror

    private func persistWidgetTimer() {
        let defaults = UserDefaults(suiteName: "group.com.atlas.music")
        defaults?.set(endAt, forKey: "atlas.widget.pomodoroEndAt")
        defaults?.set(pomodoroMode, forKey: "atlas.widget.pomodoroMode")
        defaults?.set(totalMs, forKey: "atlas.widget.pomodoroTotalMs")
        WidgetReloader.reload()
    }

    // MARK: - Sleep timer

    func startSleepTimer(minutes: Int) {
        player.startSleepTimer(minutes: minutes)
        lastEvent = "Sleep timer: \(minutes) min"
    }

    func clearSleepTimer() {
        player.clearSleepTimer()
        lastEvent = "Sleep timer cleared"
    }

    // MARK: - EQ presets / manual bands / reset

    func applyEqPreset(_ name: String) {
        let bands = EqCurves.gainsFor(name)
        if bands.isEmpty {
            settings.equalizerEnabled = false
            settings.eqBands = []
        } else {
            settings.equalizerEnabled = true
            settings.eqBands = bands
        }
        player.applyAudioSettings()
        lastEvent = "EQ: \(name)"
        WidgetReloader.reload()
    }

    func setCustomBands(_ bands: [Int]) {
        if bands.isEmpty {
            settings.equalizerEnabled = false
            settings.eqBands = []
            lastEvent = "EQ: Flat"
        } else {
            settings.equalizerEnabled = true
            settings.eqBands = bands
            lastEvent = "EQ: Custom"
        }
        player.applyAudioSettings()
    }

    func resetAudio() {
        settings.resetAudio()
        player.applyAudioSettings()
        lastEvent = "EQ: Flat"
    }

    // MARK: - Unknown metadata repair

    func fixUnknowns(artist: String, album: String) {
        let cleanArtist = artist.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Unknown artist" : artist.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanAlbum = album.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Unknown album" : album.trimmingCharacters(in: .whitespacesAndNewlines)
        let all = music.allTracks(sort: .title)
        var changed = 0
        for track in all where track.artist == "Unknown artist" || track.album == "Unknown album" {
            _ = music.updateTags(
                track,
                title: track.title,
                artist: track.artist == "Unknown artist" ? cleanArtist : track.artist,
                album: track.album == "Unknown album" ? cleanAlbum : track.album,
                genre: track.genre,
                year: track.year,
                trackNumber: track.trackNumber,
                discNumber: track.discNumber
            )
            changed += 1
        }
        lastEvent = "Fixed \(changed) unknowns → \(cleanArtist)"
    }
}

/// Small indirection so every timer transition refreshes home-screen timelines.
enum WidgetReloader {
    static func reload() {
        WidgetCenter.shared.reloadAllTimelines()
    }
}
