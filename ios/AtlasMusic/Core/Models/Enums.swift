import Foundation

// MARK: - Library / settings enums (mirrors Android util + di enums)

enum SortOption: String, CaseIterable, Identifiable, Codable {
    case title, artist, album, duration, dateAdded, year, trackNumber

    var id: String { rawValue }
    var label: String {
        switch self {
        case .title: return "Title"
        case .artist: return "Artist"
        case .album: return "Album"
        case .duration: return "Duration"
        case .dateAdded: return "Date added"
        case .year: return "Year"
        case .trackNumber: return "Track number"
        }
    }
    static func fromKey(_ key: String) -> SortOption { SortOption(rawValue: key) ?? .title }
}

enum ArtworkStyle: String, CaseIterable, Identifiable, Codable {
    case large, compact
    var id: String { rawValue }
    var label: String { self == .large ? "Large artwork" : "Compact rows" }
}

enum ThemeMode: String, CaseIterable, Identifiable, Codable {
    case system, dark, light, mocha, latte, gruvbox, tokyoNight, nord, dracula, kanagawa, rosePine, everforest, oxocarbon

    var id: String { rawValue }
    var label: String {
        switch self {
        case .system: return "System"
        case .dark: return "Dark"
        case .light: return "Light"
        case .mocha: return "Mocha"
        case .latte: return "Latte"
        case .gruvbox: return "Gruvbox"
        case .tokyoNight: return "Tokyo Night"
        case .nord: return "Nord"
        case .dracula: return "Dracula"
        case .kanagawa: return "Kanagawa"
        case .rosePine: return "Rose Pine"
        case .everforest: return "Everforest"
        case .oxocarbon: return "Oxocarbon"
        }
    }
    static func fromKey(_ key: String) -> ThemeMode { ThemeMode(rawValue: key) ?? .dark }
}

enum DownloadStatus: String, Codable, CaseIterable {
    case queued, fetchingInfo, downloading, completed, failed, cancelled
}

enum SmartPlaylist: String, CaseIterable, Identifiable {
    case mostPlayed, recentlyPlayed, recentlyAdded, favorites
    var id: String { rawValue }
    var label: String {
        switch self {
        case .mostPlayed: return "Most played"
        case .recentlyPlayed: return "Recently played"
        case .recentlyAdded: return "Recently added"
        case .favorites: return "Favorites"
        }
    }
}

/// Numeric values match Media3 `Player.REPEAT_MODE_*` so persisted values stay compatible.
enum RepeatMode: Int, CaseIterable, Identifiable {
    case off = 0, all = 1, one = 2
    var id: Int { rawValue }
    var next: RepeatMode {
        switch self { case .off: return .all; case .all: return .one; case .one: return .off }
    }
}

enum CoverVariant: String, CaseIterable {
    case geo, rings, grid, diag, dunes, aurora, meridian, arcs, noise, spark, horizon, orbit
}

enum OutputKind: String, CaseIterable {
    case speaker, wired, bluetooth, usbDAC, hdmi, earpiece, other

    var label: String {
        switch self {
        case .speaker: return "Speaker"
        case .wired: return "Wired"
        case .bluetooth: return "Bluetooth"
        case .usbDAC: return "USB DAC"
        case .hdmi: return "HDMI"
        case .earpiece: return "Earpiece"
        case .other: return "Other"
        }
    }
}

enum BackgroundMode: String, CaseIterable, Identifiable {
    case off, conway, boids, wave, maze, sort, starfield, matrix, flow, lorenz, pendulum, plasma, langton, dunes
    var id: String { rawValue }
    var label: String { rawValue.capitalized }
    static func fromId(_ id: String) -> BackgroundMode { BackgroundMode(rawValue: id) ?? .wave }
}

enum ClickWheelZone {
    case outside, centerSelect, ringTopMenu, ringRightNext, ringBottomPlay, ringLeftPrev
}

enum ToolPage: String, CaseIterable, Identifiable {
    case hub, audio, focus, sleep, library
    var id: String { rawValue }
}

enum IpodShellFinish: String, CaseIterable, Identifiable {
    case silver, black, gold, crimson
    var id: String { rawValue }
    var label: String {
        switch self {
        case .silver: return "Classic Silver"
        case .black: return "Slate Black"
        case .gold: return "Observatory Gold"
        case .crimson: return "U2 Crimson"
        }
    }
}

enum LibraryDestination: String, Codable {
    case automatic, filesApp
}

enum TagUpdateResult {
    struct Outcome { let dbOk: Bool; let fileOk: Bool; let fileError: String? }
}
