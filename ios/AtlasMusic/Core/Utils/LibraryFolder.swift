// Atlas iOS — Folder-path normalization, display labels and exclusion checks.
import Foundation

/// Faithful port of Android `com.atlas.music.util.LibraryFolder`.
enum LibraryFolder {
    /// "Music/FLAC/" -> "music/flac" (lower-cased, trailing slash trimmed).
    static func normalize(_ relativePath: String?) -> String {
        guard let relativePath, !relativePath.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return "unknown"
        }
        // Kotlin `trim().trimEnd('/')`: only trailing slashes are removed.
        var normalized = relativePath.trimmingCharacters(in: .whitespacesAndNewlines)
        while normalized.hasSuffix("/") { normalized.removeLast() }
        normalized = normalized.lowercased()
        return normalized.isEmpty ? "unknown" : normalized
    }

    /// Display label with original casing preserved when available.
    static func label(_ folder: String) -> String {
        folder.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "unknown" : folder
    }

    /// True when `folder` is inside any excluded prefix (e.g. "podcasts").
    static func isExcluded(_ folder: String, _ excluded: Set<String>) -> Bool {
        let f = folder.lowercased()
        return excluded.contains { raw in
            var e = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            while e.hasSuffix("/") { e.removeLast() }
            return !e.isEmpty && (f == e || f.hasPrefix(e + "/"))
        }
    }
}
