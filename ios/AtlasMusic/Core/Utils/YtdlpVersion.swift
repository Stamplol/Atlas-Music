// Atlas iOS — Semantic yt-dlp release-tag parsing and comparison.
import Foundation

/// Mirrors Android `YtdlpVersion` (`YYYY.MM.DD[.rev]` / optional leading `v`).
struct YtdlpVersion: Comparable, CustomStringConvertible {
    let year: Int
    let month: Int
    let day: Int
    let rev: Int

    init(year: Int, month: Int, day: Int, rev: Int = 0) {
        self.year = year
        self.month = month
        self.day = day
        self.rev = rev
    }

    static func < (lhs: YtdlpVersion, rhs: YtdlpVersion) -> Bool {
        (lhs.year, lhs.month, lhs.day, lhs.rev) < (rhs.year, rhs.month, rhs.day, rhs.rev)
    }

    var stringValue: String {
        rev > 0
            ? String(format: "%04d.%02d.%02d.%d", locale: YtdlpVersion.locale, year, month, day, rev)
            : String(format: "%04d.%02d.%02d", locale: YtdlpVersion.locale, year, month, day)
    }

    var description: String { stringValue }

    func toString() -> String { stringValue }

    static func parse(_ raw: String) -> YtdlpVersion? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        let range = NSRange(trimmed.startIndex..., in: trimmed)
        guard let match = tag.firstMatch(in: trimmed, range: range) else { return nil }
        func value(_ index: Int) -> String {
            let r = match.range(at: index)
            guard r.location != NSNotFound, let swiftRange = Range(r, in: trimmed) else { return "" }
            return String(trimmed[swiftRange])
        }
        let month = Int(value(2)) ?? 0
        let day = Int(value(3)) ?? 0
        guard (1...12).contains(month), (1...31).contains(day) else { return nil }
        let revText = value(4)
        return YtdlpVersion(
            year: Int(value(1)) ?? 0,
            month: month,
            day: day,
            rev: revText.isEmpty ? 0 : (Int(revText) ?? 0)
        )
    }

    private static let locale = Locale(identifier: "en_US_POSIX")
    private static let tag = try! NSRegularExpression(pattern: "^v?(\\d{4})\\.(\\d{1,2})\\.(\\d{1,2})(?:\\.(\\d{1,3}))?$")
}
