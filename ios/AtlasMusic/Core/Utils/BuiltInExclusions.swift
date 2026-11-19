// Atlas iOS — Always-skipped junk folder patterns (messaging, recorders, system sounds, DCIM).
import Foundation

/// Faithful port of Android `BuiltInExclusions`. The original source contains 24 patterns
/// (the shared contract's "27" is a count mismatch); every Android pattern is reproduced here.
enum BuiltInExclusions {
    private static func rx(_ pattern: String) -> NSRegularExpression {
        try! NSRegularExpression(pattern: pattern)
    }

    private static let patterns: [NSRegularExpression] = [
        // Messaging apps — voice notes, audio attachments (opaque path segments)
        rx("whatsapp"), rx("telegram"), rx("signal"), rx("viber"),
        rx("threema"), rx("discord"), rx("messenger"),
        // Voice recorders / dictaphones
        rx("recorder"), rx("recordings"), rx("dictaphone"),
        rx("call\\s*record"), rx("voice[ _-]?memo"), rx("memos"),
        // System sounds — flags are unreliable, path always wins
        rx("ringtones?"), rx("notifications?"), rx("alarms?"),
        rx("notification[ _-]?sounds?"), rx("alarm[ _-]?sounds?"),
        // Camera / archive audio — video soundtrack exports, not a music library
        rx("(^|/)dcim(/|$)"), rx("(^|/)movies(/|$)"), rx("(^|/)pictures(/|$)"),
        rx("screen[ _-]?recordings?"),
        // Atlas's own transient artifacts
        rx("\\.atlas\\b"), rx("atlas[ _-]?temp"),
    ]

    static func isBuiltInExcluded(_ folder: String) -> Bool {
        let haystack = folder.lowercased()
        let range = NSRange(haystack.startIndex..., in: haystack)
        return patterns.contains { $0.firstMatch(in: haystack, range: range) != nil }
    }

    /// Short, legible hint for the Settings copy.
    static let SUMMARY = "Messaging apps (WhatsApp/Telegram/Signal), recorders and memos, ringtones/notifications/alarms, DCIM/Movies/Pictures, Atlas temp"
}
