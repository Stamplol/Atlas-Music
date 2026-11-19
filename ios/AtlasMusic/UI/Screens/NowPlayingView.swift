// Atlas iOS — signature Now Playing: full-bleed reactive background, 310pt Meridian, spring art, transport.
import SwiftUI
import Foundation
import UIKit

/// The app's signature screen. Portrait stacks the Meridian above the controls; landscape splits
/// artwork / lyrics / active tools from a thumb-reachable control column.
struct NowPlayingView: View {
    private let onDismiss: () -> Void
    private let toolsVM: ToolsViewModel
    private let onOpenQueue: () -> Void

    @Environment(PlayerController.self) private var player
    @Environment(MusicRepository.self) private var music
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected

    @State private var seekPreview: Double?
    @State private var artScale: CGFloat = 0.95
    @State private var showLyrics = false
    @State private var showToolsPanel = false
    @State private var showOutputPicker = false
    @State private var showInfo = false
    @State private var preferredOutputID: String?

    init(onDismiss: @escaping () -> Void, toolsVM: ToolsViewModel,
         onOpenQueue: @escaping () -> Void) {
        self.onDismiss = onDismiss
        self.toolsVM = toolsVM
        self.onOpenQueue = onOpenQueue
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var currentTrack: Track? {
        guard let uri = player.state.currentTrackURI else { return nil }
        return music.track(uri: uri)
    }

    private var effectivePosition: Int64 {
        seekPreview.map { Int64($0) } ?? player.state.positionMs
    }

    private var fraction: Double {
        let duration = player.state.durationMs
        guard duration > 0 else { return 0 }
        return min(max(Double(effectivePosition) / Double(duration), 0), 1)
    }

    private var artistLine: String {
        var parts: [String] = []
        let artist = player.state.artist
        parts.append(artist.isEmpty ? "Unknown artist" : artist)
        if let genre = currentTrack?.genre, !genre.isEmpty, genre != "Unknown genre" { parts.append(genre) }
        if let year = currentTrack?.year, year > 0 { parts.append(String(year)) }
        return parts.joined(separator: "   ·   ")
    }

    private var hasActiveTool: Bool {
        toolsVM.running || player.sleepTimerEndAt != nil
    }

    private var outputs: [AudioOutputDevice] { OutputRouter.listOutputs() }

    private var activeOutput: AudioOutputDevice? {
        if let preferredOutputID, let match = outputs.first(where: { $0.id == preferredOutputID }) { return match }
        return outputs.first { $0.kind != .speaker && $0.kind != .earpiece } ?? outputs.first
    }

    private var outputLabel: String { activeOutput?.name ?? OutputRouter.currentLabel() }

    private var outputSymbol: String {
        switch activeOutput?.kind {
        case .wired: return "headphones"
        case .bluetooth: return "bluetooth"
        case .usbDAC: return "cable.connector"
        case .hdmi: return "tv.hdmi"
        case .earpiece: return "speaker.wave.1"
        default: return "speaker.wave.2"
        }
    }

    var body: some View {
        GeometryReader { geo in
            let landscape = geo.size.width > geo.size.height && geo.size.width > 640
            ZStack {
                backgroundLayer

                if landscape {
                    landscapeLayout(size: geo.size)
                } else if showLyrics {
                    lyricsLayer
                } else {
                    portraitLayout(size: geo.size)
                }
            }
            .clipped()
            .sheet(isPresented: $showOutputPicker) {
                OutputPickerSheet(outputs: outputs, preferredID: $preferredOutputID) {
                    showOutputPicker = false
                }
            }
            .sheet(isPresented: $showInfo) {
                if let track = currentTrack { TrackInfoView(track: track) }
            }
            .onAppear {
                preferredOutputID = UserDefaults.standard.string(forKey: "atlas.preferred.output")
                artScale = player.state.isPlaying ? 1 : 0.95
            }
            .onChange(of: player.state.currentTrackURI) { _, _ in settleArtwork() }
            .onChange(of: player.state.isPlaying) { _, _ in
                withAnimation(AtlasMotion.settle) {
                    artScale = player.state.isPlaying ? 1 : 0.95
                }
            }
        }
    }

    // MARK: - Ambient layers

    private var backgroundLayer: some View {
        let c = colors
        return ZStack {
            AnimatedBackground(
                mode: BackgroundMode.fromId(settings.backgroundMode),
                density: settings.backgroundDensity,
                speed: settings.backgroundSpeed,
                trail: settings.backgroundTrail,
                react: settings.backgroundReact,
                isPlaying: player.state.isPlaying,
                accent: c.accent
            )
            if let track = currentTrack {
                // The artwork is the lighting; the simulation is the texture.
                AlbumArt(track: track, size: 340, corner: 0, showStrip: false)
                    .scaleEffect(1.9)
                    .blur(radius: 72)
                    .opacity(0.32)
                    .allowsHitTesting(false)
            }
            LinearGradient(
                colors: [c.background.opacity(0.28), c.background.opacity(0.72), c.background],
                startPoint: .top, endPoint: .bottom
            )
            .allowsHitTesting(false)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    // MARK: - Portrait

    private func portraitLayout(size: CGSize) -> some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 14) {
                header
                artworkStage
                metadata
                seekSection
                transportRow
                secondaryRow
                outputRow
                if let error = player.state.lastError {
                    errorBanner(error)
                }
                if player.state.currentTrackURI == nil {
                    EmptyState(
                        title: "Nothing playing",
                        body: "Choose a song from your library and the Meridian will come alive here."
                    )
                }
                Spacer(minLength: 24)
            }
            .padding(.horizontal, 20)
            .padding(.top, 6)
            .frame(minHeight: size.height)
        }
    }

    // MARK: - Landscape split

    private func landscapeLayout(size: CGSize) -> some View {
        ZStack(alignment: .bottomTrailing) {
            HStack(alignment: .center, spacing: 6) {
                Group {
                    if showLyrics { lyricsPane } else if showToolsPanel { toolsPane } else { landscapeArtPane }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)

                ScrollView(showsIndicators: false) {
                    VStack(spacing: 10) {
                        header
                        metadata
                        seekSection
                        transportRow
                        secondaryRow
                        outputRow
                        if let error = player.state.lastError { errorBanner(error) }
                    }
                    .padding(.horizontal, 10)
                    .padding(.bottom, 30)
                }
                .frame(width: min(max(size.width * 0.48, 330), 520))
            }
            toolsToggle
        }
    }

    private var landscapeArtPane: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height) * 0.78
            ZStack {
                MeridianArc(progress: fraction, size: side * 1.16)
                if let track = currentTrack {
                    AlbumArt(track: track, size: side, corner: Radius.xl, showStrip: true)
                        .shadow(color: colors.accent.opacity(0.3), radius: 24, y: 8)
                        .scaleEffect(artScale)
                        .animation(AtlasMotion.settle, value: artScale)
                } else {
                    RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                        .fill(colors.surfaceElevated)
                        .frame(width: side, height: side)
                        .overlay(
                            Image(systemName: "music.note")
                                .font(.system(size: 48))
                                .foregroundStyle(colors.accent.opacity(0.4))
                        )
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
        }
    }

    @ViewBuilder
    private var lyricsPane: some View {
        if let track = currentTrack {
            LyricsView(track: track, positionMs: player.state.positionMs,
                       onSeek: { player.seek(to: $0) },
                       onClose: { withAnimation(AtlasMotion.quick) { showLyrics = false } })
        } else {
            EmptyState(title: "No lyrics", body: "Start a song to read its lyrics.")
        }
    }

    private var toolsPane: some View {
        let c = colors
        return ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 10) {
                HStack {
                    Text("TOOLS")
                        .font(AtlasFont.eyebrow())
                        .kerning(1.6)
                        .foregroundStyle(c.accent)
                    Spacer(minLength: 4)
                    Button("Cover") { withAnimation(AtlasMotion.quick) { showToolsPanel = false } }
                        .font(AtlasFont.label())
                        .foregroundStyle(c.textMuted)
                }

                Text("Focus — \(toolsVM.pomodoroMode)")
                    .font(AtlasFont.rowTitle())
                    .foregroundStyle(c.text)
                Text(atlasFormatMs(toolsVM.totalMs > 0
                                   ? toolsVM.remainingMs
                                   : Int64(max(1, settings.pomodoroFocusMinutes)) * 60_000))
                    .font(AtlasFont.display())
                    .fontWeight(.bold)
                    .foregroundStyle(c.text)
                HStack(spacing: 8) {
                    if toolsVM.running {
                        Button("Pause") { toolsVM.pausePomodoro() }.toolButton(c)
                    } else if toolsVM.remainingMs > 0 && toolsVM.totalMs > 0 {
                        Button("Resume") { toolsVM.resumePomodoro() }.toolButton(c)
                    } else {
                        Button("Start") { toolsVM.startPomodoro() }.toolButton(c)
                    }
                    Button("Reset") { toolsVM.resetPomodoro() }.toolButton(c)
                    Button(toolsVM.pomodoroMode == "FOCUS" ? "Break" : "Focus") {
                        toolsVM.switchMode()
                    }
                    .toolButton(c)
                }
                Text("Today \(toolsVM.focusMinutesToday)m · \(toolsVM.sessionsToday) done")
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)

                Divider().background(c.border.opacity(0.5)).padding(.vertical, 6)

                Text("Sleep").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                let remaining = player.sleepTimerEndAt.map { max(0, Int64($0.timeIntervalSinceNow)) } ?? 0
                Text(remaining > 0 ? "Paused in \(atlasFormatMs(remaining))" : "No timer")
                    .font(AtlasFont.small())
                    .foregroundStyle(c.accent)
                HStack(spacing: 6) {
                    ForEach([10, 30, 60], id: \.self) { minutes in
                        AtlasFilterChip("\(minutes)m") { toolsVM.startSleepTimer(minutes: minutes) }
                    }
                    if remaining > 0 {
                        Button("Cancel") { toolsVM.clearSleepTimer() }
                            .font(AtlasFont.label()).foregroundStyle(c.textMuted)
                    }
                }

                Divider().background(c.border.opacity(0.5)).padding(.vertical, 6)

                Text("Sound").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                let activePreset = EqCurves.matchingPresetName(settings.eqBands)
                HStack(spacing: 6) {
                    ForEach(["Flat", "Bass", "Vocal", "Night"], id: \.self) { preset in
                        AtlasFilterChip(preset, selected: activePreset == preset) {
                            toolsVM.applyEqPreset(preset)
                        }
                    }
                }
                Button("Audio output") { showOutputPicker = true }
                    .font(AtlasFont.label())
                    .foregroundStyle(c.accent)
                if !toolsVM.lastEvent.isEmpty {
                    Text(toolsVM.lastEvent)
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.accent)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(16)
        }
    }

    private var toolsToggle: some View {
        let c = colors
        return Button {
            withAnimation(AtlasMotion.quick) {
                showToolsPanel.toggle()
                if showToolsPanel { showLyrics = false }
            }
        } label: {
            ZStack {
                Circle().fill(showToolsPanel || hasActiveTool ? c.accent : c.surfaceElevated)
                    .frame(width: 44, height: 44)
                Image(systemName: "wrench.and.screwdriver.fill")
                    .font(.system(size: 17))
                    .foregroundStyle(showToolsPanel || hasActiveTool ? c.onAccent : c.text)
                if hasActiveTool && !showToolsPanel {
                    Circle().fill(c.accent)
                        .frame(width: 8, height: 8)
                        .offset(x: 16, y: -16)
                }
            }
        }
        .buttonStyle(ToolTilePressStyle())
        .padding(20)
        .accessibilityLabel("Toggle tools")
    }

    // MARK: - Shared chrome

    private var header: some View {
        let c = colors
        return HStack(alignment: .center) {
            AtlasIconButton(systemName: "xmark", label: "Close now playing", action: onDismiss)
            Spacer(minLength: 4)
            Text("NOW PLAYING")
                .font(AtlasFont.eyebrow())
                .kerning(1.6)
                .foregroundStyle(c.textMuted)
            Spacer(minLength: 4)
            AtlasIconButton(
                systemName: "text.bubble",
                label: "Lyrics",
                action: {
                    withAnimation(AtlasMotion.quick) {
                        showLyrics.toggle()
                        if showLyrics { showToolsPanel = false }
                    }
                },
                tint: showLyrics ? c.accent : nil
            )
            AtlasIconButton(systemName: "info.circle", label: "Track information",
                            action: { showInfo = true })
        }
    }

    private var artworkStage: some View {
        ZStack {
            MeridianArc(progress: fraction, size: 310)
            if let track = currentTrack {
                AlbumArt(track: track, size: 260, corner: 28, showStrip: true)
                    .shadow(color: colors.accent.opacity(0.32), radius: 28, y: 10)
                    .scaleEffect(artScale)
                    .animation(AtlasMotion.settle, value: artScale)
            } else {
                RoundedRectangle(cornerRadius: 28, style: .continuous)
                    .fill(colors.surfaceElevated)
                    .frame(width: 260, height: 260)
                    .overlay(
                        Image(systemName: "music.note")
                            .font(.system(size: 64))
                            .foregroundStyle(colors.accent.opacity(0.4))
                    )
                    .scaleEffect(artScale)
                    .animation(AtlasMotion.settle, value: artScale)
            }
        }
        .frame(height: 310)
    }

    private var metadata: some View {
        let c = colors
        return VStack(alignment: .leading, spacing: 4) {
            if !player.state.album.isEmpty, player.state.album != "Unknown album" {
                Text(player.state.album.uppercased())
                    .font(AtlasFont.eyebrow())
                    .kerning(1.2)
                    .foregroundStyle(c.accent)
                    .lineLimit(1)
            }
            Text(player.state.currentTrackURI == nil ? "Nothing playing" : player.state.title)
                .font(AtlasFont.display())
                .foregroundStyle(c.text)
                .lineLimit(2)
            Text(artistLine)
                .font(AtlasFont.small())
                .foregroundStyle(c.textMuted)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var seekSection: some View {
        let c = colors
        return VStack(spacing: 4) {
            Slider(
                value: Binding(
                    get: { seekPreview ?? Double(player.state.positionMs) },
                    set: { seekPreview = $0 }
                ),
                in: 0...max(1, Double(player.state.durationMs)),
                onEditingChanged: { editing in
                    guard !editing else { return }
                    if let value = seekPreview { player.seek(to: Int64(value)) }
                    seekPreview = nil
                }
            )
            .tint(c.accent)
            .disabled(player.state.durationMs <= 0)
            HStack {
                Text(Format.duration(effectivePosition))
                    .font(AtlasFont.mono(size: 12))
                    .foregroundStyle(c.textMuted)
                Spacer(minLength: 4)
                Text(Format.duration(player.state.durationMs))
                    .font(AtlasFont.mono(size: 12))
                    .foregroundStyle(c.textMuted)
            }
        }
    }

    private var transportRow: some View {
        let c = colors
        return HStack(alignment: .center) {
            Spacer(minLength: 0)
            AtlasIconButton(
                systemName: "shuffle",
                label: "Toggle shuffle",
                action: { player.toggleShuffle() },
                tint: player.state.shuffleEnabled ? c.accent : c.textMuted
            )
            Spacer(minLength: 0)
            Button { player.skipPrevious(); tapFeedback() } label: {
                Image(systemName: "backward.fill")
                    .font(.system(size: 27, weight: .bold))
                    .foregroundStyle(c.text)
                    .frame(width: 46, height: 46)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel("Previous track")
            Spacer(minLength: 0)
            Button {
                player.togglePlayPause()
                tapFeedback()
            } label: {
                ZStack {
                    Circle()
                        .fill(c.accent)
                        .frame(width: 76, height: 76)
                        .shadow(color: c.accent.opacity(0.4), radius: 18, y: 6)
                    Image(systemName: player.state.isPlaying ? "pause.fill" : "play.fill")
                        .font(.system(size: 30, weight: .bold))
                        .foregroundStyle(c.onAccent)
                        .offset(x: player.state.isPlaying ? 0 : 2)
                }
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel(player.state.isPlaying ? "Pause" : "Play")
            Spacer(minLength: 0)
            Button { player.skipNext(); tapFeedback() } label: {
                Image(systemName: "forward.fill")
                    .font(.system(size: 27, weight: .bold))
                    .foregroundStyle(c.text)
                    .frame(width: 46, height: 46)
                    .contentShape(Rectangle())
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel("Next track")
            Spacer(minLength: 0)
            AtlasIconButton(
                systemName: player.state.repeatMode == .one ? "repeat.1" : "repeat",
                label: "Cycle repeat mode",
                action: { player.cycleRepeatMode() },
                tint: player.state.repeatMode == .off ? c.textMuted : c.accent
            )
            Spacer(minLength: 0)
        }
    }

    private var secondaryRow: some View {
        let c = colors
        let favorite = player.isFavoriteCurrent()
        return HStack(alignment: .center) {
            Spacer(minLength: 0)
            AtlasIconButton(
                systemName: favorite ? "heart.fill" : "heart",
                label: favorite ? "Remove from favorites" : "Add to favorites",
                action: { player.toggleFavoriteCurrent() },
                tint: favorite ? c.accent : c.text
            )
            Spacer(minLength: 0)
            sleepChip
            Spacer(minLength: 0)
            AtlasIconButton(systemName: "list.bullet.rectangle", label: "Open queue",
                            action: onOpenQueue, tint: c.text)
            Spacer(minLength: 0)
        }
    }

    @ViewBuilder
    private var sleepChip: some View {
        let c = colors
        if let end = player.sleepTimerEndAt {
            let minutes = max(1, Int(end.timeIntervalSinceNow / 60) + 1)
            Button {
                player.clearSleepTimer()
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "timer").font(.system(size: 14))
                    Text("\(minutes) min · cancel").font(AtlasFont.label()).lineLimit(1)
                }
                .foregroundStyle(c.accent)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(c.accent.opacity(0.16), in: Capsule())
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel("Cancel sleep timer")
        } else {
            Menu {
                ForEach([10, 20, 30, 45, 60, 90], id: \.self) { minutes in
                    Button("\(minutes) minutes") { player.startSleepTimer(minutes: minutes) }
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: "timer").font(.system(size: 14))
                    Text("Sleep").font(AtlasFont.label())
                }
                .foregroundStyle(c.text)
                .padding(.horizontal, 12).padding(.vertical, 8)
                .background(c.surfaceElevated, in: Capsule())
            }
            .accessibilityLabel("Start sleep timer")
        }
    }

    private var outputRow: some View {
        let c = colors
        return HStack(spacing: 10) {
            Image(systemName: outputSymbol)
                .font(.system(size: 16))
                .foregroundStyle(c.accent)
            Button { showOutputPicker = true } label: {
                Text(outputLabel)
                    .font(AtlasFont.small())
                    .foregroundStyle(c.text)
                    .lineLimit(1)
                    .padding(.horizontal, 12).padding(.vertical, 8)
                    .background(c.surfaceElevated, in: Capsule())
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel("Audio output: \(outputLabel)")
            Menu {
                ForEach([0.5, 0.75, 1.0, 1.25, 1.5, 2.0], id: \.self) { speed in
                    Button(String(format: "%.2g×", speed)) {
                        player.setPlaybackSpeed(Float(speed))
                    }
                }
            } label: {
                Text(String(format: "%.1f×", player.state.playbackSpeed))
                    .font(AtlasFont.label())
                    .foregroundStyle(c.accent)
                    .padding(.horizontal, 10).padding(.vertical, 6)
                    .background(c.accent.opacity(0.12), in: Capsule())
            }
            .accessibilityLabel("Playback speed")
            Spacer(minLength: 0)
        }
    }

    private func errorBanner(_ message: String) -> some View {
        let c = colors
        return HStack(spacing: 8) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(c.error)
            Text(message).font(AtlasFont.caption()).foregroundStyle(c.error).lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(12)
        .background(c.error.opacity(0.12), in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
    }

    @ViewBuilder
    private var lyricsLayer: some View {
        if let track = currentTrack {
            LyricsView(track: track, positionMs: player.state.positionMs,
                       onSeek: { player.seek(to: $0) },
                       onClose: { withAnimation(AtlasMotion.quick) { showLyrics = false } })
        } else {
            EmptyState(title: "No lyrics", body: "Start a song to read its lyrics.")
        }
    }

    // MARK: - Motion / feedback

    private func settleArtwork() {
        withAnimation(AtlasMotion.settle) { artScale = 0.92 }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
            withAnimation(AtlasMotion.settle) {
                artScale = self.player.state.isPlaying ? 1 : 0.95
            }
        }
    }

    private func tapFeedback() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

private extension View {
    func toolButton(_ colors: AtlasColors) -> some View {
        self
            .font(AtlasFont.label())
            .foregroundStyle(colors.text)
            .padding(.horizontal, 10).padding(.vertical, 7)
            .background(colors.surfaceElevated, in: Capsule())
    }
}
