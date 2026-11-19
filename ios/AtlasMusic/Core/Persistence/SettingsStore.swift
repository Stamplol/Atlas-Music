import Foundation

// Atlas iOS — Observable UserDefaults-backed settings store mirroring Android AtlasSettings / SettingsRepository.

/// Last playback queue snapshot. Track identity on iOS is the URI string (Android used numeric ids).
struct SavedQueue: Codable {
    var ids: [String]
    var index: Int
    var positionMs: Int64
    var hint: String?
}

@MainActor
@Observable
final class SettingsStore {

    private enum K {
        static let theme = "atlas.theme_mode"
        static let dynamic = "atlas.dynamic_color"
        static let artwork = "atlas.artwork_style"
        static let sort = "atlas.default_sort"
        static let gapless = "atlas.gapless_enabled"
        static let crossfade = "atlas.crossfade_enabled"
        static let crossfadeSeconds = "atlas.crossfade_seconds"
        static let eq = "atlas.equalizer_enabled"
        static let bass = "atlas.bass_boost_enabled"
        static let bassStrength = "atlas.bass_boost_strength"
        static let normalize = "atlas.normalization_enabled"
        static let loudnessGainMb = "atlas.loudness_gain_mb"
        static let headphoneProfile = "atlas.headphone_profile_id"
        static let preampDb = "atlas.preamp_db"
        static let autoHeadroom = "atlas.auto_headroom_enabled"
        static let virtualizerStrength = "atlas.virtualizer_strength"
        static let channelBalance = "atlas.channel_balance"
        static let limiter = "atlas.limiter_enabled"
        static let scWarmth = "atlas.sound_check_warmth"
        static let scPunch = "atlas.sound_check_punch"
        static let speed = "atlas.playback_speed"
        static let ipodEnabled = "atlas.ipod_mode_enabled"
        static let ipodUnlocked = "atlas.ipod_mode_unlocked"
        static let ipodFinish = "atlas.ipod_shell_finish"
        static let ipodClick = "atlas.ipod_click_sound"
        static let sleepMin = "atlas.sleep_timer_default_minutes"
        static let excluded = "atlas.excluded_folders"
        static let musicFolderURI = "atlas.music_folder_uri"
        static let musicFolderName = "atlas.music_folder_name"
        static let musicFolderOnly = "atlas.music_folder_only"
        static let dlFormat = "atlas.download_format"
        static let dlDest = "atlas.download_destination_uri"
        static let autoImport = "atlas.auto_import_downloads"
        static let autoYtdlp = "atlas.auto_check_ytdlp"
        static let diagnostics = "atlas.diagnostics_enabled"
        static let includeUnsupported = "atlas.library_include_unsupported"
        static let bgMode = "atlas.background_mode"
        static let bgDensity = "atlas.background_density"
        static let bgSpeed = "atlas.background_speed"
        static let bgTrail = "atlas.background_trail"
        static let bgReact = "atlas.background_react"
        static let sortAlgo = "atlas.sort_algo"
        static let pomodoroFocus = "atlas.pomodoro_focus_minutes"
        static let pomodoroBreak = "atlas.pomodoro_break_minutes"
        static let pomodoroSync = "atlas.pomodoro_sync_playback"
        static let sleepEndOfTrack = "atlas.sleep_timer_end_of_track"
        static let eqBands = "atlas.equalizer_band_gains"
        static let sleepEndAt = "atlas.sleep_timer_end_at"
        static let lastScanMs = "atlas.library_last_scan_ms"
        static let ytdlpNotice = "atlas.ytdlp_update_notice_version"
        static let savedQueue = "atlas.playback_queue_snapshot"
    }

    /// App group suite keeps widgets and the app on the same settings.
    private let defaults: UserDefaults
    /// Suppresses writes while `init()` seeds properties from storage.
    private var isLoading = false

    // Theme / library
    var themeMode: ThemeMode = .dark { didSet { persist() } }
    var dynamicColor: Bool = false { didSet { persist() } }
    var artworkStyle: ArtworkStyle = .large { didSet { persist() } }
    var defaultSort: SortOption = .title { didSet { persist() } }

    // Playback transitions
    var gaplessEnabled: Bool = true { didSet { persist() } }
    var crossfadeEnabled: Bool = false { didSet { persist() } }
    var crossfadeSeconds: Int = 6 { didSet { persist() } }

    // DSP
    var equalizerEnabled: Bool = true { didSet { persist() } }
    var bassBoostEnabled: Bool = false { didSet { persist() } }
    var bassBoostStrength: Int = 60 { didSet { persist() } }
    var normalizationEnabled: Bool = false { didSet { persist() } }
    var loudnessGainMb: Int = 400 { didSet { persist() } }
    var headphoneProfileId: String = "" { didSet { persist() } }
    var preampDb: Float = 0 { didSet { persist() } }
    var autoHeadroomEnabled: Bool = true { didSet { persist() } }
    var virtualizerStrength: Int = 0 { didSet { persist() } }
    var channelBalance: Float = 0 { didSet { persist() } }
    var limiterEnabled: Bool = true { didSet { persist() } }
    var soundCheckWarmth: Float = 0 { didSet { persist() } }
    var soundCheckPunch: Float = 0 { didSet { persist() } }
    var playbackSpeed: Float = 1 { didSet { persist() } }
    var eqBands: [Int] = [Int](repeating: 0, count: 9) { didSet { persist() } }

    // iPod simulation
    var ipodModeEnabled: Bool = false { didSet { persist() } }
    var ipodModeUnlocked: Bool = false { didSet { persist() } }
    var ipodShellFinish: String = "silver" { didSet { persist() } }
    var ipodClickSound: Bool = true { didSet { persist() } }

    // Folders / downloads
    var sleepTimerDefaultMinutes: Int = 30 { didSet { persist() } }
    var excludedFolders: Set<String> = [] { didSet { persist() } }
    var musicFolderURI: String = "" { didSet { persist() } }
    var musicFolderName: String = "" { didSet { persist() } }
    var musicFolderOnly: Bool = true { didSet { persist() } }
    var downloadFormat: String = "opus" { didSet { persist() } }
    var downloadDestinationURI: String = "" { didSet { persist() } }
    var autoImportDownloads: Bool = true { didSet { persist() } }
    var autoCheckYtdlpUpdates: Bool = true { didSet { persist() } }
    var diagnosticsEnabled: Bool = false { didSet { persist() } }
    var libraryIncludesUnsupported: Bool = false { didSet { persist() } }

    // Backgrounds
    var backgroundMode: String = "wave" { didSet { persist() } }
    var backgroundDensity: Int = 45 { didSet { persist() } }
    var backgroundSpeed: Int = 55 { didSet { persist() } }
    var backgroundTrail: Int = 72 { didSet { persist() } }
    var backgroundReact: Bool = true { didSet { persist() } }

    // Tools
    var sortAlgo: String = "random" { didSet { persist() } }
    var pomodoroFocusMinutes: Int = 25 { didSet { persist() } }
    var pomodoroBreakMinutes: Int = 5 { didSet { persist() } }
    var pomodoroSyncPlayback: Bool = true { didSet { persist() } }
    var sleepTimerEndOfTrack: Bool = false { didSet { persist() } }

    // Transient / persisted state
    var sleepTimerEndAt: Date? { didSet { persist() } }
    var lastScanMs: Int64 = 0 { didSet { persist() } }
    var ytdlpUpdateNotice: String? { didSet { persist() } }
    var savedQueue: SavedQueue? { didSet { persist() } }

    init() {
        defaults = UserDefaults(suiteName: "group.com.atlas.music") ?? .standard
        isLoading = true
        load()
        isLoading = false
    }

    // MARK: - Reading

    private func number(_ key: String) -> NSNumber? { defaults.object(forKey: key) as? NSNumber }
    private func bool(_ key: String, _ fallback: Bool) -> Bool { (defaults.object(forKey: key) as? Bool) ?? fallback }
    private func int(_ key: String, _ fallback: Int) -> Int { number(key)?.intValue ?? fallback }
    private func int64(_ key: String, _ fallback: Int64) -> Int64 { number(key)?.int64Value ?? fallback }
    private func float(_ key: String, _ fallback: Float) -> Float { number(key)?.floatValue ?? fallback }
    private func string(_ key: String, _ fallback: String) -> String { (defaults.object(forKey: key) as? String) ?? fallback }
    private func optionalString(_ key: String) -> String? { defaults.object(forKey: key) as? String }
    private func date(_ key: String) -> Date? {
        guard let interval = number(key)?.doubleValue, interval > 0 else { return nil }
        return Date(timeIntervalSince1970: interval)
    }

    private func load() {
        themeMode = ThemeMode(rawValue: string(K.theme, ThemeMode.dark.rawValue)) ?? .dark
        dynamicColor = bool(K.dynamic, false)
        artworkStyle = ArtworkStyle(rawValue: string(K.artwork, ArtworkStyle.large.rawValue)) ?? .large
        defaultSort = SortOption.fromKey(string(K.sort, SortOption.title.rawValue))

        gaplessEnabled = bool(K.gapless, true)
        crossfadeEnabled = bool(K.crossfade, false)
        crossfadeSeconds = int(K.crossfadeSeconds, 6).clamped(to: 2...12)

        equalizerEnabled = bool(K.eq, true)
        bassBoostEnabled = bool(K.bass, false)
        bassBoostStrength = int(K.bassStrength, 60).clamped(to: 0...100)
        normalizationEnabled = bool(K.normalize, false)
        loudnessGainMb = int(K.loudnessGainMb, 400).clamped(to: 0...1000)
        headphoneProfileId = string(K.headphoneProfile, "")
        preampDb = float(K.preampDb, 0).clamped(to: -12...6)
        autoHeadroomEnabled = bool(K.autoHeadroom, true)
        virtualizerStrength = int(K.virtualizerStrength, 0).clamped(to: 0...100)
        channelBalance = float(K.channelBalance, 0).clamped(to: -1...1)
        limiterEnabled = bool(K.limiter, true)
        soundCheckWarmth = float(K.scWarmth, 0).clamped(to: -1...1)
        soundCheckPunch = float(K.scPunch, 0).clamped(to: -1...1)
        playbackSpeed = float(K.speed, 1).clamped(to: 0.5...2)

        let storedBands = string(K.eqBands, "").split(separator: ",").compactMap { Int($0.trimmingCharacters(in: .whitespaces)) }
        eqBands = storedBands.count == 9 ? storedBands : [Int](repeating: 0, count: 9)

        ipodModeEnabled = bool(K.ipodEnabled, false)
        ipodModeUnlocked = bool(K.ipodUnlocked, false)
        ipodShellFinish = string(K.ipodFinish, "silver")
        ipodClickSound = bool(K.ipodClick, true)

        sleepTimerDefaultMinutes = int(K.sleepMin, 30).clamped(to: 5...180)
        excludedFolders = Set((defaults.object(forKey: K.excluded) as? [String]) ?? [])
        musicFolderURI = string(K.musicFolderURI, "")
        musicFolderName = string(K.musicFolderName, "")
        musicFolderOnly = bool(K.musicFolderOnly, true)
        downloadFormat = string(K.dlFormat, "opus")
        downloadDestinationURI = string(K.dlDest, "")
        autoImportDownloads = bool(K.autoImport, true)
        autoCheckYtdlpUpdates = bool(K.autoYtdlp, true)
        diagnosticsEnabled = bool(K.diagnostics, false)
        libraryIncludesUnsupported = bool(K.includeUnsupported, false)

        backgroundMode = string(K.bgMode, "wave")
        backgroundDensity = int(K.bgDensity, 45).clamped(to: 1...100)
        backgroundSpeed = int(K.bgSpeed, 55).clamped(to: 1...100)
        backgroundTrail = int(K.bgTrail, 72).clamped(to: 1...95)
        backgroundReact = bool(K.bgReact, true)

        sortAlgo = string(K.sortAlgo, "random")
        pomodoroFocusMinutes = int(K.pomodoroFocus, 25).clamped(to: 5...180)
        pomodoroBreakMinutes = int(K.pomodoroBreak, 5).clamped(to: 1...60)
        pomodoroSyncPlayback = bool(K.pomodoroSync, true)
        sleepTimerEndOfTrack = bool(K.sleepEndOfTrack, false)

        sleepTimerEndAt = date(K.sleepEndAt)
        lastScanMs = int64(K.lastScanMs, 0)
        ytdlpUpdateNotice = optionalString(K.ytdlpNotice)
        if let data = defaults.data(forKey: K.savedQueue),
           let decoded = try? JSONDecoder().decode(SavedQueue.self, from: data) {
            savedQueue = decoded
        } else {
            savedQueue = nil
        }
    }

    // MARK: - Writing

    /// Settings mutate rarely; writing the whole snapshot keeps each didSet tiny and consistent.
    private func persist() {
        guard !isLoading else { return }
        defaults.set(themeMode.rawValue, forKey: K.theme)
        defaults.set(dynamicColor, forKey: K.dynamic)
        defaults.set(artworkStyle.rawValue, forKey: K.artwork)
        defaults.set(defaultSort.rawValue, forKey: K.sort)

        defaults.set(gaplessEnabled, forKey: K.gapless)
        defaults.set(crossfadeEnabled, forKey: K.crossfade)
        defaults.set(crossfadeSeconds.clamped(to: 2...12), forKey: K.crossfadeSeconds)

        defaults.set(equalizerEnabled, forKey: K.eq)
        defaults.set(bassBoostEnabled, forKey: K.bass)
        defaults.set(bassBoostStrength.clamped(to: 0...100), forKey: K.bassStrength)
        defaults.set(normalizationEnabled, forKey: K.normalize)
        defaults.set(loudnessGainMb.clamped(to: 0...1000), forKey: K.loudnessGainMb)
        defaults.set(headphoneProfileId, forKey: K.headphoneProfile)
        defaults.set(preampDb.clamped(to: -12...6), forKey: K.preampDb)
        defaults.set(autoHeadroomEnabled, forKey: K.autoHeadroom)
        defaults.set(virtualizerStrength.clamped(to: 0...100), forKey: K.virtualizerStrength)
        defaults.set(channelBalance.clamped(to: -1...1), forKey: K.channelBalance)
        defaults.set(limiterEnabled, forKey: K.limiter)
        defaults.set(soundCheckWarmth.clamped(to: -1...1), forKey: K.scWarmth)
        defaults.set(soundCheckPunch.clamped(to: -1...1), forKey: K.scPunch)
        defaults.set(playbackSpeed.clamped(to: 0.5...2), forKey: K.speed)
        defaults.set(eqBands.map(String.init).joined(separator: ","), forKey: K.eqBands)

        defaults.set(ipodModeEnabled, forKey: K.ipodEnabled)
        defaults.set(ipodModeUnlocked, forKey: K.ipodUnlocked)
        defaults.set(ipodShellFinish, forKey: K.ipodFinish)
        defaults.set(ipodClickSound, forKey: K.ipodClick)

        defaults.set(sleepTimerDefaultMinutes.clamped(to: 5...180), forKey: K.sleepMin)
        defaults.set(Array(excludedFolders), forKey: K.excluded)
        defaults.set(musicFolderURI, forKey: K.musicFolderURI)
        defaults.set(musicFolderName, forKey: K.musicFolderName)
        defaults.set(musicFolderOnly, forKey: K.musicFolderOnly)
        defaults.set(downloadFormat, forKey: K.dlFormat)
        defaults.set(downloadDestinationURI, forKey: K.dlDest)
        defaults.set(autoImportDownloads, forKey: K.autoImport)
        defaults.set(autoCheckYtdlpUpdates, forKey: K.autoYtdlp)
        defaults.set(diagnosticsEnabled, forKey: K.diagnostics)
        defaults.set(libraryIncludesUnsupported, forKey: K.includeUnsupported)

        defaults.set(backgroundMode, forKey: K.bgMode)
        defaults.set(backgroundDensity.clamped(to: 1...100), forKey: K.bgDensity)
        defaults.set(backgroundSpeed.clamped(to: 1...100), forKey: K.bgSpeed)
        defaults.set(backgroundTrail.clamped(to: 1...95), forKey: K.bgTrail)
        defaults.set(backgroundReact, forKey: K.bgReact)

        defaults.set(sortAlgo, forKey: K.sortAlgo)
        defaults.set(pomodoroFocusMinutes.clamped(to: 5...180), forKey: K.pomodoroFocus)
        defaults.set(pomodoroBreakMinutes.clamped(to: 1...60), forKey: K.pomodoroBreak)
        defaults.set(pomodoroSyncPlayback, forKey: K.pomodoroSync)
        defaults.set(sleepTimerEndOfTrack, forKey: K.sleepEndOfTrack)

        if let end = sleepTimerEndAt {
            defaults.set(end.timeIntervalSince1970, forKey: K.sleepEndAt)
        } else {
            defaults.removeObject(forKey: K.sleepEndAt)
        }
        defaults.set(NSNumber(value: lastScanMs), forKey: K.lastScanMs)
        if let notice = ytdlpUpdateNotice {
            defaults.set(notice, forKey: K.ytdlpNotice)
        } else {
            defaults.removeObject(forKey: K.ytdlpNotice)
        }
        if let queue = savedQueue, let data = try? JSONEncoder().encode(queue) {
            defaults.set(data, forKey: K.savedQueue)
        } else {
            defaults.removeObject(forKey: K.savedQueue)
        }
    }

    // MARK: - Actions

    func saveQueue(ids: [String], index: Int, positionMs: Int64, hint: String?) {
        savedQueue = SavedQueue(ids: ids, index: index, positionMs: positionMs, hint: hint)
    }

    func clearSavedQueue() {
        savedQueue = nil
    }

    /// Mirrors Android `SettingsRepository.resetAudio()`: return every audio control to its default.
    func resetAudio() {
        equalizerEnabled = false
        bassBoostEnabled = false
        bassBoostStrength = 60
        normalizationEnabled = false
        loudnessGainMb = 400
        headphoneProfileId = ""
        preampDb = 0
        autoHeadroomEnabled = true
        virtualizerStrength = 0
        channelBalance = 0
        limiterEnabled = true
        soundCheckWarmth = 0
        soundCheckPunch = 0
        crossfadeEnabled = false
        playbackSpeed = 1
        eqBands = [Int](repeating: 0, count: 9)
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        if self < range.lowerBound { return range.lowerBound }
        if self > range.upperBound { return range.upperBound }
        return self
    }
}
