// Atlas iOS — Audio extension normalization, MIME types, magic-byte detection and UI labels.
import Foundation

/// Faithful port of Android `MediaFormatHelper`. Magic bytes take precedence over a
/// misleading extension, exactly as in the original.
enum MediaFormatHelper {
    static func normalizeDownloadExtension(_ sourceExtension: String, headerBytes: Data? = nil) -> String {
        if let detected = detectExtensionFromMagic(headerBytes) { return detected }
        let ext = sourceExtension
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .drop(while: { $0 == "." })
            .lowercased()
        if ext.isEmpty { return "mka" }
        if ext == "webm" { return "mka" }
        return String(ext)
    }

    /// MediaStore-compatible MIME type. (iOS sees the same extension convention.)
    static func mimeForExtension(_ ext: String) -> String {
        switch ext.trimmingCharacters(in: .whitespacesAndNewlines).drop(while: { $0 == "." }).lowercased() {
        case "opus", "ogg", "oga": return "audio/ogg"
        case "mka", "webm": return "audio/x-matroska"
        case "m4a", "mp4": return "audio/mp4"
        case "mp3": return "audio/mpeg"
        case "flac": return "audio/flac"
        case "wav": return "audio/wav"
        case "aac": return "audio/aac"
        default: return "audio/*"
        }
    }

    /// Android name alias.
    static func mediaStoreMimeForExtension(_ ext: String) -> String { mimeForExtension(ext) }

    static func displayAudioFormatLabel(_ mimeType: String?, displayName: String? = nil) -> String {
        var ext: String?
        if let name = displayName, let dot = name.lastIndex(of: ".") {
            let candidate = String(name[name.index(after: dot)...])
                .trimmingCharacters(in: .whitespacesAndNewlines)
                .lowercased()
            if !candidate.isEmpty { ext = candidate }
        }

        switch (mimeType ?? "").trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "audio/mpeg", "audio/mp3": return "MP3"
        case "audio/mp4", "audio/m4a", "audio/x-m4a": return "M4A"
        case "audio/x-wav", "audio/wav", "audio/vnd.wave": return "WAV"
        case "audio/flac", "audio/x-flac": return "FLAC"
        case "audio/x-matroska": return "MKA"
        case "audio/webm": return "WEBM"
        case "audio/opus": return "OPUS"
        case "audio/ogg": return ext == "opus" ? "OPUS" : "OGG"
        case "audio/aac", "audio/aacp": return "AAC"
        default:
            var fromMime = (mimeType ?? "").components(separatedBy: "/").last ?? ""
            if fromMime.hasPrefix("x-") { fromMime = String(fromMime.dropFirst(2)) }
            let upper = fromMime.uppercased()
            return upper.isEmpty ? (ext?.uppercased() ?? "AUDIO") : upper
        }
    }

    static func detectExtensionFromMagic(_ header: Data?) -> String? {
        guard let header, header.count >= 4 else { return nil }
        let b = [UInt8](header.prefix(8))

        // EBML / Matroska / WebM: 1A 45 DF A3
        if b[0] == 0x1A && b[1] == 0x45 && b[2] == 0xDF && b[3] == 0xA3 { return "mka" }
        // Ogg ("OggS")
        if b[0] == 0x4F && b[1] == 0x67 && b[2] == 0x67 && b[3] == 0x53 { return "ogg" }
        // FLAC ("fLaC")
        if b[0] == 0x66 && b[1] == 0x4C && b[2] == 0x61 && b[3] == 0x43 { return "flac" }
        // RIFF / WAV ("RIFF")
        if b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46 { return "wav" }
        // MP4 / M4A ("ftyp" at 4..7)
        if b.count >= 8 && b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70 { return "m4a" }
        // ID3v2 ("ID3")
        if b[0] == 0x49 && b[1] == 0x44 && b[2] == 0x33 { return "mp3" }
        return nil
    }
}
