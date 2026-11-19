// Atlas iOS — Tools hub grid plus shared tool-screen building blocks (headers, rings, sparkbars).
import SwiftUI
import Foundation

// MARK: - Shared helpers reused by every tools / now-playing screen in this module.

/// m:ss counter used by focus, sleep and the landscape tools panel.
func atlasFormatMs(_ ms: Int64) -> String {
    let total = max(0, ms) / 1000
    return String(format: "%d:%02d", total / 60, total % 60)
}

/// Uppercase mono eyebrow with an optional trailing readout. Mirrors Android `StudioHeader`.
struct StudioHeader: View {
    private let label: String
    private let trailing: String?

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    init(_ label: String, trailing: String? = nil) {
        self.label = label
        self.trailing = trailing
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(label.uppercased())
                .font(AtlasFont.eyebrow())
                .kerning(1.6)
                .foregroundStyle(colors.accent)
                .lineLimit(1)
            Spacer(minLength: 8)
            if let trailing {
                Text(trailing)
                    .font(AtlasFont.label())
                    .foregroundStyle(colors.textMuted)
                    .lineLimit(1)
            }
        }
    }
}

/// Rounded grouped card used by every tools subpage.
struct ToolCard<Content: View>: View {
    private let minHeight: CGFloat?
    private let content: Content

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    init(minHeight: CGFloat? = nil, @ViewBuilder content: () -> Content) {
        self.minHeight = minHeight
        self.content = content()
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        content
            .frame(maxWidth: .infinity, minHeight: minHeight ?? 0, alignment: .topLeading)
            .padding(16)
            .background(colors.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(colors.border.opacity(0.45), lineWidth: 0.5)
            )
    }
}

/// Vertical meter bars for a 9/10-band curve. Flat curves draw a single quiet rule.
struct EqSparkbars: View {
    let gains: [Float]
    var maxAbs: Float = 9
    let accent: Color

    var body: some View {
        Canvas { context, size in
            let n = max(gains.count, 1)
            let slot = size.width / CGFloat(n)
            let barWidth = slot * 0.6
            let mid = size.height / 2

            if gains.allSatisfy({ abs($0) < 0.05 }) {
                var rule = Path()
                rule.move(to: CGPoint(x: 0, y: mid))
                rule.addLine(to: CGPoint(x: size.width, y: mid))
                context.stroke(rule, with: .color(accent.opacity(0.6)), style: StrokeStyle(lineWidth: 2))
            } else {
                for (index, db) in gains.enumerated() {
                    let height = min(CGFloat(abs(db) / max(maxAbs, 0.01)), 1) * (mid - 1)
                    let x = CGFloat(index) * slot + (slot - barWidth) / 2
                    let y = db >= 0 ? mid - height : mid
                    let rect = CGRect(x: x, y: y, width: barWidth, height: max(height, 2))
                    context.fill(Path(roundedRect: rect, cornerRadius: barWidth / 2), with: .color(accent))
                }
            }
        }
    }
}

/// Miniature curve preview for a named preset tile.
struct PresetCurveBars: View {
    let name: String
    let selected: Bool

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        let gains = EqCurves.gainsFor(name).map { Float($0) / 100.0 }
        EqSparkbars(gains: gains, maxAbs: 6, accent: selected ? colors.accent : colors.textMuted.opacity(0.55))
    }
}

/// Pomodoro ring: dashed-style track plus a gold progress arc and centred readout.
struct FocusRing: View {
    var size: CGFloat = 150
    let fraction: Double
    let mode: String
    let displayMs: Int64
    let stateWord: String

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        let c = colors
        ZStack {
            Canvas { context, canvas in
                let stroke: CGFloat = size >= 140 ? 11 : 8
                let inset = stroke / 2
                let arcRect = CGRect(x: inset, y: inset,
                                     width: canvas.width - stroke,
                                     height: canvas.height - stroke)
                let track = Path(ellipseIn: arcRect)
                context.stroke(track, with: .color(c.textMuted.opacity(0.14)),
                               style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                if fraction > 0 {
                    let progress = Path { path in
                        path.addArc(center: CGPoint(x: arcRect.midX, y: arcRect.midY),
                                    radius: arcRect.width / 2,
                                    startAngle: .degrees(-90),
                                    endAngle: .degrees(-90 + 360 * min(max(fraction, 0), 1)),
                                    clockwise: false)
                    }
                    context.stroke(progress, with: .color(c.accent),
                                   style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                }
            }
            VStack(spacing: 2) {
                Text(mode)
                    .font(AtlasFont.eyebrow())
                    .kerning(1.2)
                    .foregroundStyle(c.accent)
                Text(atlasFormatMs(displayMs))
                    .font(size >= 140 ? AtlasFont.headline() : AtlasFont.title())
                    .fontWeight(.bold)
                    .foregroundStyle(c.text)
                    .monospacedDigit()
                Text(stateWord)
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)
            }
        }
        .frame(width: size, height: size)
    }
}

/// Center-zero balance / preamp / band slider with a drawn track and real drag handling.
struct CenterZeroSlider: View {
    @Binding var value: Float
    let range: ClosedRange<Float>
    let accent: Color
    let track: Color

    var body: some View {
        GeometryReader { geo in
            let width = max(geo.size.width, 1)
            let span = max(range.upperBound - range.lowerBound, 0.0001)
            let mid = min(max(CGFloat((0 - range.lowerBound) / span), 0), 1)
            let current = min(max(CGFloat((value - range.lowerBound) / span), 0), 1)
            ZStack(alignment: .leading) {
                Capsule().fill(track).frame(height: 6)
                if abs(current - mid) > 0.002 {
                    Capsule().fill(accent)
                        .frame(width: max(2, abs(current - mid) * width), height: 6)
                        .offset(x: min(current, mid) * width)
                }
                Rectangle().fill(accent.opacity(0.8))
                    .frame(width: 2, height: 14)
                    .offset(x: mid * width)
            }
            .frame(height: geo.size.height)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { gesture in
                        let fraction = min(max(0, gesture.location.x / width), 1)
                        let newValue = range.lowerBound + Float(fraction) * Float(span)
                        value = abs(newValue) < 0.0001 ? 0 : newValue
                    }
            )
        }
        .frame(height: 34)
    }
}

// MARK: - Hub grid

/// The tools landing page: live tiles for sound, focus, sleep, library care, downloads & settings.
struct ToolsHub: View {
    private let onOpen: (ToolPage) -> Void
    private let onOpenDownloads: () -> Void
    private let onOpenSettings: () -> Void

    @Environment(PlayerController.self) private var player
    @Environment(MusicRepository.self) private var music
    @Environment(SettingsStore.self) private var settings
    @Environment(\.atlasColors) private var injected
    @Environment(\.horizontalSizeClass) private var sizeClass

    init(onOpen: @escaping (ToolPage) -> Void,
         onOpenDownloads: @escaping () -> Void,
         onOpenSettings: @escaping () -> Void) {
        self.onOpen = onOpen
        self.onOpenDownloads = onOpenDownloads
        self.onOpenSettings = onOpenSettings
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var columns: [GridItem] {
        Array(repeating: GridItem(.flexible(), spacing: 12),
              count: sizeClass == .regular ? 3 : 2)
    }

    var body: some View {
        ScrollView(showsIndicators: false) {
            LazyVGrid(columns: columns, spacing: 12) {
                audioTile
                focusTile
                sleepTile
                libraryTile
                downloadsTile
                settingsTile
            }
            .padding(16)
        }
    }

    private var audioTile: some View {
        let c = colors
        let profile = HeadphoneProfiles.byID(settings.headphoneProfileId)
        let preset = EqCurves.matchingPresetName(settings.eqBands)
        let curve = AudioDsp.computeEffectiveCurve(
            profileId: settings.headphoneProfileId,
            userBandsMb: settings.eqBands,
            preampDb: settings.preampDb,
            soundCheckWarmth: settings.soundCheckWarmth,
            soundCheckPunch: settings.soundCheckPunch,
            autoHeadroom: settings.autoHeadroomEnabled
        )
        let headline: String = !settings.equalizerEnabled
            ? "Bypassed"
            : (profile?.model ?? preset)
        var subline: [String] = []
        if let profile { subline.append(profile.brand) }
        subline.append(preset == "Flat" && profile == nil
            ? "AutoEq · Sound Check · Stage"
            : preset)
        if settings.virtualizerStrength > 0 { subline.append("Stage \(settings.virtualizerStrength)%") }

        return Button { onOpen(.audio) } label: {
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 5) {
                    Text("SOUND STUDIO")
                        .font(AtlasFont.eyebrow())
                        .kerning(1.6)
                        .foregroundStyle(c.accent)
                    Text(headline)
                        .font(AtlasFont.headline())
                        .fontWeight(.bold)
                        .foregroundStyle(c.text)
                        .lineLimit(1)
                    Text(subline.joined(separator: " · "))
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.textMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                EqSparkbars(gains: curve.totalBandsDb10, maxAbs: 9, accent: c.accent)
                    .frame(width: 92, height: 48)
            }
            .frame(maxWidth: .infinity, minHeight: 104, alignment: .leading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Sound Studio")
        .gridCellColumns(columns.count)
    }

    private var focusTile: some View {
        let c = colors
        let display = toolsVMDisplayMs
        let fraction = toolsVMFraction
        return Button { onOpen(.focus) } label: {
            VStack(alignment: .leading, spacing: 10) {
                Text("FOCUS")
                    .font(AtlasFont.eyebrow())
                    .kerning(1.6)
                    .foregroundStyle(c.accent)
                HStack {
                    Spacer(minLength: 0)
                    FocusRing(size: 92, fraction: fraction, mode: toolVM.pomodoroMode,
                              displayMs: display, stateWord: focusStateWord)
                    Spacer(minLength: 0)
                }
                Text("Today \(toolVM.focusMinutesToday)m · \(toolVM.streakDays)d streak")
                    .font(AtlasFont.label())
                    .foregroundStyle(c.textMuted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 196, alignment: .topLeading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Focus")
    }

    private var sleepTile: some View {
        let c = colors
        let remaining = sleepRemaining
        let active = remaining > 0
        return Button { onOpen(.sleep) } label: {
            VStack(alignment: .leading, spacing: 12) {
                Text("SLEEP")
                    .font(AtlasFont.eyebrow())
                    .kerning(1.6)
                    .foregroundStyle(c.accent)
                Image(systemName: "bed.double.fill")
                    .font(.system(size: 32))
                    .foregroundStyle(active ? c.accent : c.textMuted.opacity(0.55))
                Text(active ? "Paused in \(atlasFormatMs(remaining))" : "No timer")
                    .font(AtlasFont.title())
                    .foregroundStyle(active ? c.accent : c.text)
                    .lineLimit(1)
                Text(settings.sleepTimerEndOfTrack ? "Stops at end of track" : "Pick a duration")
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 196, alignment: .topLeading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Sleep")
    }

    private var libraryTile: some View {
        let c = colors
        let count = music.trackCount()
        let unknown = music.unknownCount()
        return Button { onOpen(.library) } label: {
            VStack(alignment: .leading, spacing: 12) {
                Text("LIBRARY")
                    .font(AtlasFont.eyebrow())
                    .kerning(1.6)
                    .foregroundStyle(c.accent)
                Image(systemName: "books.vertical")
                    .font(.system(size: 30))
                    .foregroundStyle(c.accent)
                Text("\(count) \(count == 1 ? "track" : "tracks")")
                    .font(AtlasFont.title())
                    .foregroundStyle(c.text)
                    .lineLimit(1)
                Text(unknown > 0 ? "\(unknown) need tags" : "All tracks tagged")
                    .font(AtlasFont.caption())
                    .foregroundStyle(unknown > 0 ? c.accent : c.textMuted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 148, alignment: .topLeading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Library care")
    }

    private var downloadsTile: some View {
        let c = colors
        return Button(action: onOpenDownloads) {
            VStack(alignment: .leading, spacing: 12) {
                Text("DOWNLOADS")
                    .font(AtlasFont.eyebrow())
                    .kerning(1.6)
                    .foregroundStyle(c.accent)
                Image(systemName: "square.and.arrow.down.on.sphere")
                    .font(.system(size: 30))
                    .foregroundStyle(c.accent)
                Text("YT & SoundCloud")
                    .font(AtlasFont.title())
                    .foregroundStyle(c.text)
                    .lineLimit(1)
                Text("Queue an authorized link")
                    .font(AtlasFont.caption())
                    .foregroundStyle(c.textMuted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, minHeight: 148, alignment: .topLeading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Downloads")
    }

    private var settingsTile: some View {
        let c = colors
        return Button(action: onOpenSettings) {
            HStack(spacing: 14) {
                Image(systemName: "gearshape.fill")
                    .font(.system(size: 28))
                    .foregroundStyle(c.accent)
                VStack(alignment: .leading, spacing: 3) {
                    Text("SETTINGS")
                        .font(AtlasFont.eyebrow())
                        .kerning(1.6)
                        .foregroundStyle(c.accent)
                    Text("Appearance · library folders · widgets")
                        .font(AtlasFont.small())
                        .foregroundStyle(c.textMuted)
                        .lineLimit(1)
                }
                Spacer(minLength: 4)
                Image(systemName: "chevron.right")
                    .foregroundStyle(c.textMuted)
            }
            .frame(maxWidth: .infinity, minHeight: 74, alignment: .leading)
            .padding(16)
            .background(c.surface, in: RoundedRectangle(cornerRadius: Radius.xl, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: Radius.xl, style: .continuous)
                    .stroke(c.border.opacity(0.45), lineWidth: 0.5)
            )
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Settings")
        .gridCellColumns(columns.count)
    }

    @Environment(ToolsViewModel.self) private var toolVM

    private var toolsVMDisplayMs: Int64 {
        toolVM.totalMs > 0 ? toolVM.remainingMs : Int64(max(1, settings.pomodoroFocusMinutes)) * 60_000
    }

    private var toolsVMFraction: Double {
        guard toolVM.totalMs > 0 else { return 0 }
        return min(max(Double(toolVM.remainingMs) / Double(toolVM.totalMs), 0), 1)
    }

    private var focusStateWord: String {
        if toolVM.running { return "REMAINING" }
        return toolVM.remainingMs > 0 && toolVM.totalMs > 0 ? "PAUSED" : "READY"
    }

    private var sleepRemaining: Int64 {
        guard let end = player.sleepTimerEndAt else { return 0 }
        return max(0, Int64(end.timeIntervalSinceNow))
    }
}

/// Spring press feedback shared by hub tiles.
struct ToolTilePressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.82 : 1)
            .animation(AtlasMotion.quick, value: configuration.isPressed)
    }
}
