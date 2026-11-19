// Atlas iOS — deterministic procedural cover with the exact Agent 2 render contract.
import SwiftUI

struct AlbumArt: View {
    private let track: Track?
    private let size: CGFloat
    private let corner: CGFloat
    private let showStrip: Bool

    @Environment(\.atlasColors) private var injectedColors
    @Environment(\.colorScheme) private var scheme
    @AppStorage("atlas.theme_mode", store: .standard) private var storedTheme: ThemeMode = .dark

    init(track: Track?, size: CGFloat = 46, corner: CGFloat = 10, showStrip: Bool = true) {
        self.track = track
        self.size = size
        self.corner = corner
        self.showStrip = showStrip
    }

    private var colors: AtlasColors {
        atlasResolvedColors(injected: injectedColors, scheme: scheme, stored: storedTheme)
    }

    var body: some View {
        let seed = track.map { CoverArtKit.seed(for: $0) }
            ?? CoverArtKit.seedForPreview(title: "—", sizeBytes: 0)
        ProceduralCoverView(
            seed: seed,
            title: track?.displayTitle ?? "—",
            ext: "mp3",
            sizeBytes: track?.sizeBytes ?? 0,
            showStrip: showStrip
        )
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: corner, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: corner, style: .continuous)
                .stroke(colors.border.opacity(0.35), lineWidth: 0.5)
        )
        .accessibilityLabel(track.map { "Album artwork for \($0.displayAlbum)" } ?? "Album artwork")
    }
}
