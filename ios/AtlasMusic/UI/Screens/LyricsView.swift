// Atlas iOS — time-synced lyric reader with active-line highlight, tap-to-seek and empty state.
import SwiftUI
import Foundation

/// Full-height lyrics panel: sidecar .lrc / embedded tags, highlighted current line, tap to seek.
struct LyricsView: View {
    private let track: Track
    private let positionMs: Int64
    private let onSeek: (Int64) -> Void
    private let onClose: () -> Void

    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected

    @State private var result: LyricsResult?
    @State private var loading = true

    init(track: Track, positionMs: Int64,
         onSeek: @escaping (Int64) -> Void, onClose: @escaping () -> Void) {
        self.track = track
        self.positionMs = positionMs
        self.onSeek = onSeek
        self.onClose = onClose
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var lines: [LyricLine] { result?.lines ?? [] }

    private var isRaw: Bool {
        result?.raw ?? lines.allSatisfy { $0.timeMs == 0 }
    }

    private var activeIndex: Int {
        guard !isRaw, lines.count > 1 else { return 0 }
        var index = 0
        for (i, line) in lines.enumerated() where line.timeMs <= positionMs { index = i }
        return index
    }

    var body: some View {
        let c = colors
        VStack(spacing: 0) {
            header(c)
            content(c)
        }
        .background(c.background)
        .task(id: track.uri) {
            loading = true
            result = LyricsRepository().lyrics(for: track)
            loading = false
        }
    }

    private func header(_ c: AtlasColors) -> some View {
        ZStack {
            HStack {
                AtlasIconButton(systemName: "xmark", label: "Close lyrics", action: onClose)
                Spacer()
            }
            Text("LYRICS")
                .font(AtlasFont.eyebrow())
                .kerning(1.6)
                .foregroundStyle(c.accent)
            HStack {
                Spacer()
                if let source = result?.source, !source.isEmpty {
                    Text(source.uppercased())
                        .font(AtlasFont.mono(size: 10))
                        .foregroundStyle(c.textMuted)
                        .padding(.horizontal, 10).padding(.vertical, 5)
                        .background(c.surfaceElevated, in: Capsule())
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func content(_ c: AtlasColors) -> some View {
        if loading {
            VStack(spacing: 12) {
                ProgressView().tint(c.accent)
                Text("Reading lyrics…")
                    .font(AtlasFont.small())
                    .foregroundStyle(c.textMuted)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else if lines.isEmpty {
            EmptyState(
                title: "No lyrics found",
                body: "This track has no embedded lyrics and no .lrc file beside it. Drop a file named like the track (e.g. \(suggestedFileName).lrc) into the same folder, then rescan your library.",
                actionLabel: "Close",
                action: onClose
            )
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            ScrollViewReader { proxy in
                ScrollView(showsIndicators: false) {
                    LazyVStack(spacing: 16) {
                        ForEach(lines.indices, id: \.self) { index in
                            let line = lines[index]
                            let current = !isRaw && index == activeIndex
                            Text(line.text)
                                .font(current ? AtlasFont.title() : AtlasFont.body())
                                .fontWeight(current ? .bold : .regular)
                                .foregroundStyle(current ? c.accent : c.text.opacity(0.78))
                                .multilineTextAlignment(.center)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 4)
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    guard !isRaw else { return }
                                    onSeek(line.timeMs)
                                }
                                .id(index)
                        }
                    }
                    .padding(.horizontal, 24)
                    .padding(.vertical, 16)
                }
                .onChange(of: positionMs) { _, _ in
                    guard !isRaw, lines.count > 1 else { return }
                    withAnimation(AtlasMotion.quick) {
                        proxy.scrollTo(activeIndex, anchor: .center)
                    }
                }
                .onAppear {
                    guard !isRaw, lines.count > 1 else { return }
                    proxy.scrollTo(activeIndex, anchor: .center)
                }
            }
        }
    }

    private var suggestedFileName: String {
        let stem = track.displayName as NSString
        let base = stem.deletingPathExtension
        return base.isEmpty ? "song" : base
    }
}
