// Atlas iOS — Track duplicate normalization, fingerprinting and wasted-space reporting.
import Foundation

/// Mirrors Android `DuplicateDetector.DuplicateNormalizer` / `normalize()`.
enum DuplicateNormalizer {
    static func normalize(_ value: String) -> String {
        var text = value.lowercased().trimmingCharacters(in: .whitespacesAndNewlines)
        text = rxFeat.stringByReplacingMatches(in: text, range: full(text), withTemplate: "")
        text = rxBracket.stringByReplacingMatches(in: text, range: full(text), withTemplate: "")
        text = rxParenJunk.stringByReplacingMatches(in: text, range: full(text), withTemplate: "")
        text = rxNonAlphaNum.stringByReplacingMatches(in: text, range: full(text), withTemplate: " ")
        text = rxWhitespace.stringByReplacingMatches(in: text, range: full(text), withTemplate: " ")
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func full(_ s: String) -> NSRange { NSRange(s.startIndex..., in: s) }
    private static let rxFeat = regex("\\((?:feat|ft|with)[^)]*\\)")
    private static let rxBracket = regex("\\[[^\\]]*\\]")
    private static let rxParenJunk = regex("\\([^)]*(?:official|video|audio|lyric|remaster|edit|version)[^)]*\\)")
    private static let rxNonAlphaNum = regex("[^a-z0-9 ]")
    private static let rxWhitespace = regex("\\s+")
    private static func regex(_ pattern: String) -> NSRegularExpression {
        try! NSRegularExpression(pattern: pattern)
    }
}

/// Exact shape required by the shared contract (Agent 1 imports this type).
struct DuplicateGroup: Identifiable {
    let fingerprint: String
    let tracks: [Track]
    let totalSizeBytes: Int64
    var id: String { fingerprint }
}

/// Faithful port of Android `DuplicateDetector`.
enum DuplicateDetector {
    /// Normalized title+artist+duration fingerprint. Tracks within 2 s count as identical.
    /// Kotlin rounds (not floors) the 2 s bucket so neighbours on a boundary still match.
    static func duplicateFingerprint(title: String, artist: String, durationMs: Int64) -> String {
        let bucket = Int((Double(durationMs) / 2000.0).rounded())
        return "\(DuplicateNormalizer.normalize(title))|\(DuplicateNormalizer.normalize(artist))|\(bucket)"
    }

    /// Android name alias.
    static func fingerprint(title: String, artist: String, durationMs: Int64) -> String {
        duplicateFingerprint(title: title, artist: artist, durationMs: durationMs)
    }

    static func findDuplicates(_ tracks: [Track]) -> [DuplicateGroup] {
        var order: [String] = []
        var buckets: [String: [Track]] = [:]
        for track in tracks {
            let key = track.fingerprint
            if buckets[key] == nil { order.append(key) }
            buckets[key, default: []].append(track)
        }

        var result: [DuplicateGroup] = []
        for key in order {
            guard let group = buckets[key], group.count > 1 else { continue }
            let sortedTracks = stableSorted(group) { $0.dateAddedSec < $1.dateAddedSec }
            let total = sortedTracks.reduce(Int64(0)) { $0 + $1.sizeBytes }
            result.append(DuplicateGroup(fingerprint: key, tracks: sortedTracks, totalSizeBytes: total))
        }
        // Largest groups first; ties retain first-appearance order like Kotlin's stable sort.
        return stableSorted(result) { $0.tracks.count > $1.tracks.count }
    }

    /// Bytes freed by keeping one track per duplicate group.
    static func wastedBytes(_ groups: [DuplicateGroup]) -> Int64 {
        groups.reduce(Int64(0)) { groupTotal, group in
            groupTotal + group.tracks.dropFirst().reduce(Int64(0)) { $0 + $1.sizeBytes }
        }
    }
}

/// File-private helper: Swift's `sort` is not guaranteed stable, Kotlin's `sortedBy` is.
private func stableSorted<T>(_ items: [T], by isBefore: (T, T) -> Bool) -> [T] {
    let indexed = items.enumerated().map { (index: $0.offset, value: $0.element) }
    return indexed.sorted { a, b in
        if isBefore(a.value, b.value) { return true }
        if isBefore(b.value, a.value) { return false }
        return a.index < b.index
    }.map { $0.value }
}
