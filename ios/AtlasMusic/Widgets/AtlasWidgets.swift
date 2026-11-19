// Atlas iOS — WidgetKit timeline provider and shared App Group state for small/medium/large widgets.
import WidgetKit
import SwiftUI
import AppIntents

/// All widget/app-intent communication goes through this App Group suite. The app mirrors player
/// state into it and polls `atlas.widget.command` so intents can control real playback.
enum AtlasWidgetState {
    static let suiteName = "group.com.atlas.music"
    static let defaults = UserDefaults(suiteName: suiteName)

    static func writePlayback(title: String, artist: String, album: String, isPlaying: Bool,
                              positionMs: Int64, durationMs: Int64, artSeed: Int, trackURI: String?) {
        guard let defaults else { return }
        defaults.set(title, forKey: "atlas.widget.title")
        defaults.set(artist, forKey: "atlas.widget.artist")
        defaults.set(album, forKey: "atlas.widget.album")
        defaults.set(isPlaying, forKey: "atlas.widget.isPlaying")
        defaults.set(positionMs, forKey: "atlas.widget.positionMs")
        defaults.set(durationMs, forKey: "atlas.widget.durationMs")
        defaults.set(artSeed, forKey: "atlas.widget.artSeed")
        if let trackURI { defaults.set(trackURI, forKey: "atlas.widget.trackURI") }
    }

    static func writeStats(trackCount: Int, focusMinutes: Int, streak: Int) {
        guard let defaults else { return }
        defaults.set(trackCount, forKey: "atlas.widget.trackCount")
        defaults.set(focusMinutes, forKey: "atlas.widget.focusMinutes")
        defaults.set(streak, forKey: "atlas.widget.streak")
    }

    static func writeSleep(until date: Date?) {
        guard let defaults else { return }
        if let date { defaults.set(date, forKey: "atlas.widget.sleepEndAt") }
        else { defaults.removeObject(forKey: "atlas.widget.sleepEndAt") }
    }

    static func enqueueCommand(_ command: String) {
        defaults?.set(command, forKey: "atlas.widget.command")
    }

    static func consumeCommand() -> String? {
        guard let defaults, let command = defaults.string(forKey: "atlas.widget.command"), !command.isEmpty else { return nil }
        defaults.removeObject(forKey: "atlas.widget.command")
        return command
    }

    static var title: String { defaults?.string(forKey: "atlas.widget.title") ?? "" }
    static var artist: String { defaults?.string(forKey: "atlas.widget.artist") ?? "" }
    static var album: String { defaults?.string(forKey: "atlas.widget.album") ?? "" }
    static var isPlaying: Bool { defaults?.bool(forKey: "atlas.widget.isPlaying") ?? false }
    static var positionMs: Int64 { Int64(defaults?.integer(forKey: "atlas.widget.positionMs") ?? 0) }
    static var durationMs: Int64 { Int64(defaults?.integer(forKey: "atlas.widget.durationMs") ?? 0) }
    static var artSeed: Int { defaults?.integer(forKey: "atlas.widget.artSeed") ?? 0 }
    static var trackURI: String? { defaults?.string(forKey: "atlas.widget.trackURI") }
    static var sleepEndAt: Date? { defaults?.object(forKey: "atlas.widget.sleepEndAt") as? Date }
    static var pomodoroEndAt: Date? { defaults?.object(forKey: "atlas.widget.pomodoroEndAt") as? Date }
    static var pomodoroMode: String { defaults?.string(forKey: "atlas.widget.pomodoroMode") ?? "FOCUS" }
    static var trackCount: Int { defaults?.integer(forKey: "atlas.widget.trackCount") ?? 0 }
    static var focusMinutes: Int { defaults?.integer(forKey: "atlas.widget.focusMinutes") ?? 0 }
    static var streak: Int { defaults?.integer(forKey: "atlas.widget.streak") ?? 0 }

    static func appURL() -> URL {
        var components = URLComponents()
        components.scheme = "atlas"
        components.host = "open"
        if let uri = trackURI {
            components.path = "/play"
            components.queryItems = [URLQueryItem(name: "uri", value: uri)]
        }
        return components.url ?? URL(string: "atlas://open")!
    }

    static func countdownText(_ date: Date?, now: Date = Date()) -> String {
        guard let date else { return "No timer" }
        let seconds = max(0, Int(date.timeIntervalSince(now)))
        return String(format: "%d:%02d left", seconds / 60, seconds % 60)
    }
}

struct AtlasWidgetEntry: TimelineEntry {
    let date: Date
    let family: WidgetFamily
    let title: String
    let artist: String
    let album: String
    let isPlaying: Bool
    let positionMs: Int64
    let durationMs: Int64
    let artSeed: Int
    let sleepEndAt: Date?
    let pomodoroEndAt: Date?
    let pomodoroMode: String
    let trackCount: Int
    let focusMinutes: Int
    let streak: Int

    static func snapshot(at date: Date = Date(), family: WidgetFamily = .systemMedium) -> AtlasWidgetEntry {
        AtlasWidgetEntry(
            date: date,
            family: family,
            title: AtlasWidgetState.title.isEmpty ? "Atlas Music" : AtlasWidgetState.title,
            artist: AtlasWidgetState.artist.isEmpty ? "Local-first listening" : AtlasWidgetState.artist,
            album: AtlasWidgetState.album,
            isPlaying: AtlasWidgetState.isPlaying,
            positionMs: AtlasWidgetState.positionMs,
            durationMs: AtlasWidgetState.durationMs,
            artSeed: AtlasWidgetState.artSeed,
            sleepEndAt: AtlasWidgetState.sleepEndAt,
            pomodoroEndAt: AtlasWidgetState.pomodoroEndAt,
            pomodoroMode: AtlasWidgetState.pomodoroMode,
            trackCount: AtlasWidgetState.trackCount,
            focusMinutes: AtlasWidgetState.focusMinutes,
            streak: AtlasWidgetState.streak
        )
    }
}

struct AtlasWidgetProvider: TimelineProvider {
    func placeholder(in context: Context) -> AtlasWidgetEntry { .snapshot(family: context.family) }

    func getSnapshot(in context: Context, completion: @escaping (AtlasWidgetEntry) -> Void) {
        completion(.snapshot(family: context.family))
    }

    /// Minute-granular entries near now keep sleep/pomodoro countdowns honest; the tail is
    /// coarse-grained so the timeline stays cheap.
    func getTimeline(in context: Context, completion: @escaping (Timeline<AtlasWidgetEntry>) -> Void) {
        let now = Date()
        var dates: [Date] = []
        for minute in 0..<30 {
            if let date = Calendar.current.date(byAdding: .minute, value: minute, to: now) { dates.append(date) }
        }
        for hour in 1..<12 {
            if let date = Calendar.current.date(byAdding: .hour, value: hour, to: now) { dates.append(date) }
        }
        let entries = dates.map { AtlasWidgetEntry.snapshot(at: $0, family: context.family) }
        completion(Timeline(entries: entries, policy: .atEnd))
    }
}

// MARK: - Views

private enum WidgetPalette {
    static let gold = Color(red: 0.90, green: 0.66, blue: 0.24)
    static let background = Color(red: 0.04, green: 0.05, blue: 0.07)
    static let surface = Color(red: 0.09, green: 0.11, blue: 0.15)
    static let text = Color(red: 0.96, green: 0.95, blue: 0.91)
    static let muted = Color(red: 0.66, green: 0.66, blue: 0.61)
}

private struct CoverPlaceholder: View {
    let seed: Int
    var body: some View {
        let hue = Double(abs(seed) % 360) / 360.0
        RoundedRectangle(cornerRadius: 8)
            .fill(LinearGradient(colors: [Color(hue: hue, saturation: 0.55, brightness: 0.55),
                                          Color(hue: (hue + 0.12).truncatingRemainder(dividingBy: 1), saturation: 0.6, brightness: 0.28)],
                                 startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay(
                Circle()
                    .stroke(WidgetPalette.gold.opacity(0.55), lineWidth: 2)
                    .padding(10)
            )
    }
}

private struct TransportButtons: View {
    var includeSkip = false
    var body: some View {
        HStack(spacing: 14) {
            if includeSkip {
                Button(intent: WidgetShuffleIntent()) {
                    Image(systemName: "shuffle").font(.system(size: 15, weight: .semibold))
                }
            }
            Button(intent: PreviousTrackIntent()) {
                Image(systemName: "backward.fill").font(.system(size: 18))
            }
            Button(intent: TogglePlaybackIntent()) {
                Image(systemName: "play.fill").font(.system(size: 22, weight: .bold))
                    .foregroundStyle(WidgetPalette.gold)
            }
            Button(intent: NextTrackIntent()) {
                Image(systemName: "forward.fill").font(.system(size: 18))
            }
            if includeSkip {
                Button(intent: StartSleepTimerIntent()) {
                    Image(systemName: "moon.fill").font(.system(size: 15))
                }
            }
        }
        .buttonStyle(.plain)
        .foregroundStyle(WidgetPalette.text)
    }
}

struct AtlasWidgetSmallView: View {
    let entry: AtlasWidgetEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 8) {
                CoverPlaceholder(seed: entry.artSeed).frame(width: 34, height: 34)
                VStack(alignment: .leading, spacing: 1) {
                    Text(entry.title).font(.headline).lineLimit(1).foregroundStyle(WidgetPalette.text)
                    Text(entry.artist).font(.caption2).lineLimit(1).foregroundStyle(WidgetPalette.muted)
                }
            }
            Spacer(minLength: 2)
            HStack {
                Spacer()
                TransportButtons()
                Spacer()
            }
        }
        .padding(12)
        .containerBackground(WidgetPalette.background, for: .widget)
        .widgetURL(AtlasWidgetState.appURL())
    }
}

struct AtlasWidgetMediumView: View {
    let entry: AtlasWidgetEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 12) {
                CoverPlaceholder(seed: entry.artSeed).frame(width: 62, height: 62)
                VStack(alignment: .leading, spacing: 3) {
                    Text(entry.title).font(.title3.weight(.bold)).lineLimit(2).foregroundStyle(WidgetPalette.text)
                    Text(entry.artist).font(.caption).lineLimit(1).foregroundStyle(WidgetPalette.muted)
                    if !entry.album.isEmpty {
                        Text(entry.album).font(.caption2).lineLimit(1).foregroundStyle(WidgetPalette.muted)
                    }
                }
                Spacer()
            }
            ProgressBar(positionMs: entry.positionMs, durationMs: entry.durationMs)
            HStack {
                TransportButtons()
                Spacer()
                Text(AtlasWidgetState.countdownText(entry.sleepEndAt, now: entry.date))
                    .font(.caption2).foregroundStyle(WidgetPalette.muted)
            }
        }
        .padding(14)
        .containerBackground(WidgetPalette.background, for: .widget)
        .widgetURL(AtlasWidgetState.appURL())
    }
}

struct AtlasWidgetLargeView: View {
    let entry: AtlasWidgetEntry
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 12) {
                CoverPlaceholder(seed: entry.artSeed).frame(width: 76, height: 76)
                VStack(alignment: .leading, spacing: 2) {
                    Text(entry.title).font(.title2.weight(.bold)).lineLimit(2).foregroundStyle(WidgetPalette.text)
                    Text(entry.artist).font(.subheadline).lineLimit(1).foregroundStyle(WidgetPalette.muted)
                }
                Spacer()
            }
            ProgressBar(positionMs: entry.positionMs, durationMs: entry.durationMs)
            HStack {
                Spacer()
                TransportButtons(includeSkip: true)
                Spacer()
            }
            Divider().overlay(WidgetPalette.surface)
            HStack(alignment: .top, spacing: 10) {
                StatBlock(title: "SLEEP", value: AtlasWidgetState.countdownText(entry.sleepEndAt, now: entry.date))
                StatBlock(title: entry.pomodoroMode, value: AtlasWidgetState.countdownText(entry.pomodoroEndAt, now: entry.date))
                StatBlock(title: "FOCUS", value: "\(entry.focusMinutes)m")
                StatBlock(title: "STREAK", value: "\(entry.streak)d")
            }
            Spacer(minLength: 0)
            Text("\(entry.trackCount) tracks · Atlas Music")
                .font(.caption2).foregroundStyle(WidgetPalette.muted)
        }
        .padding(16)
        .containerBackground(WidgetPalette.background, for: .widget)
        .widgetURL(AtlasWidgetState.appURL())
    }
}

private struct ProgressBar: View {
    let positionMs: Int64
    let durationMs: Int64
    var body: some View {
        let fraction = durationMs > 0 ? min(1, max(0, Double(positionMs) / Double(durationMs))) : 0
        GeometryReader { proxy in
            ZStack(alignment: .leading) {
                Capsule().fill(WidgetPalette.surface)
                Capsule().fill(WidgetPalette.gold)
                    .frame(width: max(6, proxy.size.width * fraction))
            }
        }
        .frame(height: 6)
    }
}

private struct StatBlock: View {
    let title: String
    let value: String
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.caption2.weight(.bold)).foregroundStyle(WidgetPalette.muted)
            Text(value).font(.caption.weight(.semibold)).foregroundStyle(WidgetPalette.text).lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

struct AtlasMusicWidget: Widget {
    let kind = "AtlasMusicWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: AtlasWidgetProvider()) { entry in
            Group {
                switch entry.family {
                case .systemSmall: AtlasWidgetSmallView(entry: entry)
                case .systemMedium: AtlasWidgetMediumView(entry: entry)
                case .systemLarge: AtlasWidgetLargeView(entry: entry)
                default: AtlasWidgetMediumView(entry: entry)
                }
            }
        }
        .configurationDisplayName("Atlas Music")
        .description("Playback, sleep timer and focus at a glance.")
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}

#if ATLAS_WIDGET_EXTENSION
/// Widget Extension entry point. The generator defines `ATLAS_WIDGET_EXTENSION` only for the
/// extension target, so the app keeps its own `@main` in AtlasMusicApp.swift. Keeping the entry
/// point in this file (which already imports WidgetKit) avoids a separate file-level import
/// resolution issue seen with a standalone bundle file under Xcode 26.
@main
struct AtlasWidgetExtensionEntry: WidgetBundle {
    var body: some Widget { AtlasMusicWidget() }
}
#endif

