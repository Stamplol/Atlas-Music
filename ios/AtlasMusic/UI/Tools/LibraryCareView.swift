// Atlas iOS — Library care: format breakdown, bulk tag repair and duplicate discovery.
import SwiftUI
import Foundation

/// Maintenance workspace for library hygiene. All actions mutate the real repository.
struct LibraryCareView: View {
    private let onOpenDuplicates: () -> Void

    @Environment(MusicRepository.self) private var music
    @Environment(ToolsViewModel.self) private var toolsVM
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected

    @State private var showFix = false
    @State private var artist = ""
    @State private var album = ""

    init(onOpenDuplicates: @escaping () -> Void) {
        self.onOpenDuplicates = onOpenDuplicates
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private struct FormatCount: Identifiable {
        let name: String
        let count: Int
        var id: String { name }
    }

    private var formatCounts: [FormatCount] {
        let grouped = Dictionary(grouping: music.allTracks(sort: .title)) { track in
            MediaFormatHelper.displayAudioFormatLabel(track.mimeType, displayName: track.displayName)
        }
        return grouped.map { FormatCount(name: $0.key, count: $0.value.count) }
            .sorted { $0.count == $1.count ? $0.name < $1.name : $0.count > $1.count }
    }

    var body: some View {
        let c = colors
        ScrollView(showsIndicators: false) {
            VStack(spacing: 12) {
                overviewCard(c)
                missingCard(c)
                duplicatesCard(c)
            }
            .padding(16)
        }
        .sheet(isPresented: $showFix) { fixDialog(c) }
    }

    private func overviewCard(_ c: AtlasColors) -> some View {
        let count = music.trackCount()
        let formats = formatCounts
        return ToolCard {
            StudioHeader("LIBRARY CARE", trailing: "everything visible")
            Text("\(count) \(count == 1 ? "track" : "tracks") · \(Format.totalDuration(music.totalDurationMs())) · \(Format.bytes(music.totalSizeBytes()))")
                .font(AtlasFont.headline())
                .fontWeight(.bold)
                .foregroundStyle(c.text)
                .lineLimit(2)
            if !formats.isEmpty {
                Divider().background(c.border.opacity(0.5)).padding(.vertical, 8)
                StudioHeader("FORMATS", trailing: formats.count == 1 ? "1 type" : "\(formats.count) types")
                let maxCount = max(formats.map { $0.count }.max() ?? 1, 1)
                VStack(spacing: 6) {
                    ForEach(formats) { format in
                        HStack(spacing: 8) {
                            Text(format.name).font(AtlasFont.mono(size: 10)).foregroundStyle(c.text).frame(width: 58, alignment: .leading)
                            Text("×\(format.count)").font(AtlasFont.mono(size: 10)).foregroundStyle(c.textMuted).frame(width: 44, alignment: .leading)
                            GeometryReader { geo in
                                ZStack(alignment: .leading) {
                                    Capsule().fill(c.surfaceElevated)
                                    Capsule().fill(c.accent)
                                        .frame(width: geo.size.width * CGFloat(format.count) / CGFloat(maxCount))
                                }
                            }
                            .frame(height: 6)
                        }
                    }
                }
            }
        }
    }

    private func missingCard(_ c: AtlasColors) -> some View {
        let unknown = music.unknownCount()
        return ToolCard {
            StudioHeader("MISSING INFO", trailing: unknown > 0 ? "\(unknown) missing" : "all good")
            if unknown > 0 {
                HStack(spacing: 12) {
                    Text("\(unknown)")
                        .font(AtlasFont.display())
                        .fontWeight(.bold)
                        .foregroundStyle(c.accent)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("songs missing artist or album")
                            .font(AtlasFont.rowTitle())
                            .foregroundStyle(c.text)
                        Text("Fill in missing tags across these songs in one step.")
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted)
                    }
                    Spacer(minLength: 4)
                    Button("Fix") { showFix = true }
                        .font(AtlasFont.label())
                        .foregroundStyle(c.onAccent)
                        .padding(.horizontal, 14).padding(.vertical, 9)
                        .background(c.accent, in: Capsule())
                }
                .padding(.top, 4)
            } else {
                HStack(spacing: 10) {
                    Image(systemName: "checkmark.circle.fill").foregroundStyle(c.accent)
                    Text("Every track carries artist and album tags.")
                        .font(AtlasFont.small())
                        .foregroundStyle(c.text)
                }
                .padding(.top, 4)
            }
        }
    }

    private func duplicatesCard(_ c: AtlasColors) -> some View {
        Button(action: onOpenDuplicates) {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 4) {
                    StudioHeader("DUPLICATES")
                    Text("Find duplicates")
                        .font(AtlasFont.rowTitle())
                        .foregroundStyle(c.text)
                    Text("Same song saved twice — flagged by title, artist and duration")
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right").foregroundStyle(c.textMuted)
            }
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Find duplicates")
    }

    private func fixDialog(_ c: AtlasColors) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text("Fix unknowns")
                    .font(AtlasFont.title())
                    .foregroundStyle(c.text)
                Text("Applies to every track still marked Unknown. Saved as user-edited so the values survive rescans.")
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Artist name").font(AtlasFont.label()).foregroundStyle(c.textMuted)
                    TextField("Shared artist", text: $artist)
                        .textFieldStyle(.roundedBorder)
                }
                VStack(alignment: .leading, spacing: 6) {
                    Text("Album name").font(AtlasFont.label()).foregroundStyle(c.textMuted)
                    TextField("Shared album", text: $album)
                        .textFieldStyle(.roundedBorder)
                }
                Spacer(minLength: 0)
            }
            .padding(20)
            .background(c.background)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showFix = false }.foregroundStyle(c.textMuted)
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Apply") {
                        toolsVM.fixUnknowns(artist: artist, album: album)
                        showFix = false
                    }
                    .foregroundStyle(c.accent)
                    .disabled(artist.trimmingCharacters(in: .whitespaces).isEmpty
                              && album.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
        }
        .presentationDetents([.medium])
    }
}
