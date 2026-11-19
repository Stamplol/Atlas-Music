// Atlas iOS — LRC timestamped-lyrics parser used by LyricsRepository and the lyrics screen.
import Foundation

/// One synced lyric line. Raw-only lyrics are returned as a single line at timeMs = 0.
struct LyricLine: Equatable, Hashable {
    let timeMs: Int64
    let text: String
}

/// Faithful port of Android `com.atlas.music.util.Lyrics`.
enum Lyrics {
    /// `[mm:ss.xx]` / `[mm:ss:xxx]` timestamps, one line emitted per timestamp, sorted.
    static func parse(_ raw: String) -> [LyricLine] {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return [] }

        guard firstMatch(TIME_REGEX, trimmed) != nil else {
            return [LyricLine(timeMs: 0, text: trimmed.components(separatedBy: .newlines).joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines))]
        }

        var lines: [LyricLine] = []
        for rawLine in trimmed.components(separatedBy: .newlines) {
            let range = NSRange(rawLine.startIndex..., in: rawLine)
            let matches = TIME_REGEX.matches(in: rawLine, range: range)

            var timestamps: [Int64] = []
            for match in matches {
                let min = Int(groupValue(match, 1, in: rawLine) ?? "0") ?? 0
                let sec = Int(groupValue(match, 2, in: rawLine) ?? "0") ?? 0
                let rawFrac = groupValue(match, 3, in: rawLine) ?? ""
                let padded = (rawFrac + "000").prefix(3)
                let frac = Int64(padded) ?? 0
                timestamps.append(Int64(min) * 60_000 + Int64(sec) * 1000 + frac)
            }
            if timestamps.isEmpty { continue }

            let lyric = TIME_REGEX.stringByReplacingMatches(in: rawLine, range: range, withTemplate: "")
                .trimmingCharacters(in: .whitespacesAndNewlines)
            if lyric.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { continue }
            for t in timestamps {
                if !lyric.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    lines.append(LyricLine(timeMs: t, text: lyric))
                }
            }
        }
        return stableSorted(lines) { $0.timeMs < $1.timeMs }
    }

    private static let TIME_REGEX = try! NSRegularExpression(
        pattern: "\\[(\\d{1,2}):(\\d{1,2})(?:[.:](\\d{1,3}))?]"
    )

    private static func firstMatch(_ regex: NSRegularExpression, _ text: String) -> NSTextCheckingResult? {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text))
    }

    private static func groupValue(_ match: NSTextCheckingResult, _ index: Int, in text: String) -> String? {
        let r = match.range(at: index)
        guard r.location != NSNotFound, let range = Range(r, in: text) else { return nil }
        return String(text[range])
    }

    private static func stableSorted(_ items: [LyricLine], by isBefore: (LyricLine, LyricLine) -> Bool) -> [LyricLine] {
        let indexed = items.enumerated().map { (index: $0.offset, value: $0.element) }
        return indexed.sorted { a, b in
            if isBefore(a.value, b.value) { return true }
            if isBefore(b.value, a.value) { return false }
            return a.index < b.index
        }.map { $0.value }
    }
}
