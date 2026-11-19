// Atlas iOS — track metadata inspector, faithful to Android TrackInfoSheet. No private paths shown.
import SwiftUI

struct TrackInfoView: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(\.dismiss) private var dismiss

    let track: Track

    init(track: Track) { self.track = track }

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var addedText: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "d MMM yyyy"
        return formatter.string(from: Date(timeIntervalSince1970: Double(track.dateAddedSec)))
    }

    private var trackNumberText: String {
        var parts: [String] = []
        if track.discNumber > 1 { parts.append("Disc \(track.discNumber)") }
        if track.trackNumber > 0 { parts.append("Track \(track.trackNumber)") }
        return parts.isEmpty ? "—" : parts.joined(separator: " · ")
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Spacer()
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.title2)
                        .foregroundStyle(colors.textMuted)
                }
                .accessibilityLabel("Close")
                .padding(.horizontal, 4)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    Text(track.displayTitle)
                        .font(AtlasFont.title())
                        .foregroundStyle(colors.text)
                    Text(track.displayArtist)
                        .font(AtlasFont.body())
                        .foregroundStyle(colors.textMuted)

                    Divider().background(colors.border).padding(.vertical, 12)

                    infoRow("Album", track.displayAlbum)
                    infoRow("Genre", track.genre)
                    infoRow("Year", track.year > 0 ? "\(track.year)" : "Unknown")
                    infoRow("Track", trackNumberText)
                    infoRow("Duration", Format.duration(track.durationMs))
                    infoRow("Format", Self.formatLabel(mime: track.mimeType, name: track.displayName))
                    infoRow("Size", Format.bytes(track.sizeBytes))
                    infoRow("Folder", track.folder)
                    infoRow("Added", addedText)
                    infoRow("Favorite", track.isFavorite ? "Yes" : "No")

                    Divider().background(colors.border).padding(.vertical, 12)

                    Text("Stored on this device only. Atlas Music never uploads your music or history.")
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.textMuted)
                        .padding(.bottom, 32)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .scrollBounceBehavior(.basedOnSize)
        }
        .padding(.horizontal, 24)
        .padding(.top, 8)
        .background(colors.background.ignoresSafeArea())
    }

    private func infoRow(_ label: String, _ value: String) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(label.uppercased())
                .font(AtlasFont.eyebrow())
                .foregroundStyle(colors.textMuted)
                .frame(width: 96, alignment: .leading)
            Text(value.isEmpty ? "—" : value)
                .font(AtlasFont.body())
                .foregroundStyle(colors.text)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, 6)
    }

    /// Mirrors MediaFormatHelper.displayAudioFormatLabel for the inspector badge.
    private static func formatLabel(mime: String, name: String) -> String {
        let ext = (name as NSString).pathExtension.lowercased()
        switch mime.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
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
            let fromMime = mime.split(separator: "/").last.map(String.init) ?? ""
            let clean = fromMime.hasPrefix("x-") ? String(fromMime.dropFirst(2)) : fromMime
            return clean.isEmpty ? (ext.isEmpty ? "AUDIO" : ext.uppercased()) : clean.uppercased()
        }
    }
}
