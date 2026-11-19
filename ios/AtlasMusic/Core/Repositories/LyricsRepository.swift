import Foundation
import AVFoundation

// Atlas iOS — Offline lyrics resolver: sidecar .lrc first, then embedded metadata (no network).

struct LyricsResult {
    let lines: [LyricLine]
    let source: String
    let raw: Bool
}

final class LyricsRepository {

    init() {}

    func lyrics(for track: Track) -> LyricsResult? {
        // 1) Sidecar .lrc next to the audio file, same stem (both lowercase and uppercase extension).
        for candidate in candidateSidecars(for: track) {
            guard let text = try? String(contentsOf: candidate, encoding: .utf8),
                  !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { continue }
            let parsed = Lyrics.parse(text)
            if !parsed.isEmpty {
                return LyricsResult(lines: parsed, source: "lrc", raw: parsed.allSatisfy { $0.timeMs == 0 })
            }
        }

        // 2) Embedded lyrics from the file's metadata. Android used a Python/mutagen bridge;
        //    AVFoundation exposes the same tags directly on iOS.
        guard let url = track.resolvedURL else { return nil }
        let asset = AVAsset(url: url)
        for item in asset.commonMetadata {
            let id = item.identifier.rawValue.lowercased()
            guard id.contains("lyric"), let value = try? item.stringValue, !value.isEmpty else { continue }
            let parsed = Lyrics.parse(value)
            if !parsed.isEmpty {
                return LyricsResult(lines: parsed, source: "embedded", raw: parsed.allSatisfy { $0.timeMs == 0 })
            }
        }
        return nil
    }

    private func candidateSidecars(for track: Track) -> [URL] {
        guard !track.filePath.isEmpty else { return [] }
        let fileURL = URL(fileURLWithPath: track.filePath)
        let base = fileURL.deletingPathExtension()
        return [
            base.appendingPathExtension("lrc"),
            base.appendingPathExtension("LRC"),
        ]
    }
}
