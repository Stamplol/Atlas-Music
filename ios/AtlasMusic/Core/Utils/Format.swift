// Atlas iOS — Duration, ETA, transfer-speed, byte-size and metadata string formatting.
import Foundation

/// Faithful port of Android `com.atlas.music.util.Format`.
/// All output is locale-fixed (POSIX) so tests and UI never see comma decimal separators.
enum Format {
    static func duration(_ ms: Int64) -> String {
        if ms <= 0 { return "0:00" }
        let totalSec = ms / 1000
        let minutes = totalSec / 60
        let seconds = totalSec % 60
        let hours = minutes / 60
        if hours > 0 {
            return fmt("%d:%02d:%02d", Int(hours), Int(minutes % 60), Int(seconds))
        }
        return fmt("%d:%02d", Int(minutes), Int(seconds))
    }

    static func eta(_ seconds: Int64) -> String {
        if seconds <= 0 { return "calculating…" }
        if seconds < 60 { return "\(seconds)s left" }
        if seconds < 3600 { return "\(seconds / 60)m \(seconds % 60)s left" }
        return "\(seconds / 3600)h \((seconds % 3600) / 60)m left"
    }

    static func speed(_ bytesPerSec: Int64) -> String {
        if bytesPerSec <= 0 { return "—" }
        if bytesPerSec < 1024 { return "\(bytesPerSec) B/s" }
        if bytesPerSec < 1024 * 1024 { return fmt("%.1f KB/s", Double(bytesPerSec) / 1024.0) }
        return fmt("%.1f MB/s", Double(bytesPerSec) / (1024.0 * 1024.0))
    }

    static func bytes(_ bytes: Int64) -> String {
        if bytes <= 0 { return "0 B" }
        if bytes < 1024 { return "\(bytes) B" }
        if bytes < 1024 * 1024 { return fmt("%.1f KB", Double(bytes) / 1024.0) }
        if bytes < 1024 * 1024 * 1024 { return fmt("%.1f MB", Double(bytes) / (1024.0 * 1024.0)) }
        return fmt("%.2f GB", Double(bytes) / (1024.0 * 1024.0 * 1024.0))
    }

    static func totalDuration(_ ms: Int64) -> String {
        let hours = ms / 3_600_000
        let minutes = (ms % 3_600_000) / 60_000
        return hours > 0 ? "\(hours) hr \(minutes) min" : "\(minutes) min"
    }

    static func artistAlbumLine(artist: String, album: String) -> String {
        [artist, album]
            .filter { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
            .joined(separator: "  ·  ")
    }

    private static let posix = Locale(identifier: "en_US_POSIX")

    private static func fmt(_ format: String, _ args: CVarArg...) -> String {
        String(format: format, locale: posix, arguments: args)
    }
}
