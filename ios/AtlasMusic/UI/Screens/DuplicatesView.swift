// Atlas iOS — duplicate-group cleanup, faithful to Android DuplicatesScreen.
import SwiftUI

struct DuplicatesView: View {
    @Environment(MusicRepository.self) private var music
    @Environment(SettingsStore.self) private var settings
    @Environment(TrackDialogCoordinator.self) private var dialogs
    @Environment(\.dismiss) private var dismiss

    @State private var groups: [DuplicateGroup] = []
    @State private var loaded = false

    init() {}

    private var colors: AtlasColors {
        AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var wastedBytes: Int64 {
        groups.reduce(Int64(0)) { total, group in
            total + group.tracks.dropFirst().reduce(Int64(0)) { $0 + $1.sizeBytes }
        }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            if loaded && groups.isEmpty {
                EmptyState(
                    title: "No duplicates found",
                    body: "Every track in your library looks distinct — same title, same artist, same duration only once."
                )
            } else {
                Text("\(groups.count) duplicate \(groups.count == 1 ? "group" : "groups")  ·  up to \(Format.bytes(wastedBytes)) reclaimable")
                    .font(AtlasFont.body())
                    .foregroundStyle(colors.accent)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 6)

                ScrollView {
                    LazyVStack(spacing: 10) {
                        ForEach(groups) { group in
                            groupCard(group)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 120)
                }
            }
        }
        .background(colors.background.ignoresSafeArea())
        .task { reload() }
    }

    private var header: some View {
        HStack {
            Button { dismiss() } label: {
                Image(systemName: "chevron.left")
                    .font(.title3.weight(.semibold))
                    .foregroundStyle(colors.accent)
            }
            .accessibilityLabel("Back")
            Text("Duplicates")
                .font(AtlasFont.display())
                .foregroundStyle(colors.text)
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
    }

    private func groupCard(_ group: DuplicateGroup) -> some View {
        let first = group.tracks.first
        return VStack(alignment: .leading, spacing: 8) {
            Text(first?.displayTitle ?? "Unknown title")
                .font(AtlasFont.title())
                .foregroundStyle(colors.text)
                .lineLimit(1)
            Text("\(first?.displayArtist ?? "Unknown artist")  ·  \(group.tracks.count) copies  ·  \(Format.bytes(group.totalSizeBytes)) total")
                .font(AtlasFont.caption())
                .foregroundStyle(colors.textMuted)

            ForEach(group.tracks, id: \.uri) { track in
                HStack(spacing: 10) {
                    AlbumArt(track: track, size: 40, corner: Radius.sm, showStrip: false)
                    VStack(alignment: .leading) {
                        Text(track.folder)
                            .font(AtlasFont.body())
                            .foregroundStyle(colors.text)
                            .lineLimit(1)
                        Text("\(Format.bytes(track.sizeBytes))  ·  added \(Self.monthYear(track.dateAddedSec))")
                            .font(AtlasFont.caption())
                            .foregroundStyle(colors.textMuted)
                    }
                    Spacer()
                    Button("Keep this") { keepOnly(track, in: group) }
                        .font(AtlasFont.caption())
                        .foregroundStyle(colors.accent)
                        .buttonStyle(.borderless)
                    Button {
                        remove(track)
                    } label: {
                        Image(systemName: "trash")
                            .foregroundStyle(colors.error)
                    }
                    .accessibilityLabel("Remove from library")
                    .buttonStyle(.borderless)
                }
                .padding(.vertical, 4)
            }
        }
        .padding(12)
        .background(colors.surfaceElevated)
        .clipShape(RoundedRectangle(cornerRadius: Radius.lg))
    }

    private func reload() {
        groups = music.findDuplicates()
        loaded = true
    }

    private func remove(_ track: Track) {
        music.delete(track)
        music.save()
        dialogs.toast("Removed from library")
        reload()
    }

    private func keepOnly(_ keep: Track, in group: DuplicateGroup) {
        let removed = group.tracks.filter { $0.uri != keep.uri }
        removed.forEach { music.delete($0) }
        music.save()
        let n = removed.count
        dialogs.toast("Kept \"\(keep.displayTitle)\", removed \(n) \(n == 1 ? "copy" : "copies")")
        reload()
    }

    private static func monthYear(_ sec: Int64) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM yyyy"
        return formatter.string(from: Date(timeIntervalSince1970: Double(sec)))
    }
}
