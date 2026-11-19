// Atlas iOS — Sound Studio: live DSP curve, AutoEq headphones, Sound Check pad, EQ bands & feel.
import SwiftUI
import Foundation

/// Compact selectable chip used across the tools screens.
struct AtlasFilterChip: View {
    private let label: String
    private let selected: Bool
    private let action: () -> Void

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    init(_ label: String, selected: Bool = false, action: @escaping () -> Void) {
        self.label = label
        self.selected = selected
        self.action = action
    }

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        let c = colors
        Button(action: action) {
            Text(label)
                .font(AtlasFont.label())
                .foregroundStyle(selected ? c.onAccent : c.textMuted)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(selected ? c.accent : c.surfaceElevated, in: Capsule())
                .overlay(Capsule().stroke(selected ? Color.clear : c.border, lineWidth: 0.5))
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel(label)
    }
}

/// The full sound-tuning workspace. Every control writes to SettingsStore and re-applies audio.
struct AudioStudioView: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(PlayerController.self) private var player
    @Environment(ToolsViewModel.self) private var toolsVM
    @Environment(\.atlasColors) private var injected

    @State private var showHeadphoneSheet = false
    @State private var showManualBands = false
    @State private var showOutputPicker = false
    @State private var query = ""
    @State private var preferredOutputID: String?

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: settings.themeMode, dynamic: settings.dynamicColor)
    }

    private var selectedHeadphone: HeadphoneProfile? {
        HeadphoneProfiles.byID(settings.headphoneProfileId)
    }

    private var curve: EffectiveAudioCurve {
        AudioDsp.computeEffectiveCurve(
            profileId: settings.headphoneProfileId,
            userBandsMb: settings.eqBands,
            preampDb: settings.preampDb,
            soundCheckWarmth: settings.soundCheckWarmth,
            soundCheckPunch: settings.soundCheckPunch,
            autoHeadroom: settings.autoHeadroomEnabled
        )
    }

    private var activePreset: String { EqCurves.matchingPresetName(settings.eqBands) }

    private var bands9: [Int] {
        (0..<9).map { i in i < settings.eqBands.count ? settings.eqBands[i] : 0 }
    }

    private var outputs: [AudioOutputDevice] { OutputRouter.listOutputs() }

    private var activeOutput: AudioOutputDevice? {
        if let preferredOutputID, let match = outputs.first(where: { $0.id == preferredOutputID }) { return match }
        return outputs.first { $0.kind != .speaker && $0.kind != .earpiece } ?? outputs.first
    }

    private var outputLabel: String { activeOutput?.name ?? OutputRouter.currentLabel() }

    var body: some View {
        let c = colors
        ScrollView(showsIndicators: false) {
            VStack(spacing: 12) {
                engineCard(c)
                responseCard(c)
                feelCard(c)
                spaceCard(c)
                presetsCard(c)
                playbackCard(c)
                resetButton(c)
            }
            .padding(16)
        }
        .onAppear {
            preferredOutputID = UserDefaults.standard.string(forKey: "atlas.preferred.output")
        }
        .sheet(isPresented: $showHeadphoneSheet) { headphonePicker(c) }
        .sheet(isPresented: $showOutputPicker) {
            OutputPickerSheet(outputs: outputs, preferredID: $preferredOutputID) { showOutputPicker = false }
        }
    }

    // MARK: 1 — Engine

    private func engineCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("SOUND ENGINE",
                         trailing: !settings.equalizerEnabled ? "bypassed" : "10-band DSP + limiter")
            toggleRow(
                symbol: "graphicEQ",
                title: settings.equalizerEnabled ? "Sound enhancement on" : "Direct bypass (flat)",
                subtitle: settings.equalizerEnabled
                    ? "Tap the switch to A/B compare with raw audio"
                    : "All headphones, EQ, bass & stage bypassed",
                isOn: Binding(get: { settings.equalizerEnabled }, set: {
                    settings.equalizerEnabled = $0
                    player.applyAudioSettings()
                }),
                c: c
            )
            Divider().background(c.border.opacity(0.5)).padding(.vertical, 6)
            HStack(spacing: 10) {
                Image(systemName: "headphones").foregroundStyle(c.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text("Playing through").font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                    Text(outputLabel).font(AtlasFont.small()).foregroundStyle(c.text).lineLimit(1)
                }
                Spacer(minLength: 4)
                Button("Switch output") { showOutputPicker = true }
                    .font(AtlasFont.label())
                    .foregroundStyle(c.accent)
            }
        }
    }

    private func toggleRow(symbol: String, title: String, subtitle: String,
                           isOn: Binding<Bool>, c: AtlasColors) -> some View {
        HStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 20))
                .foregroundStyle(isOn.wrappedValue ? c.accent : c.textMuted)
            VStack(alignment: .leading, spacing: 3) {
                Text(title).font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                Text(subtitle).font(AtlasFont.caption()).foregroundStyle(c.textMuted)
            }
            Spacer(minLength: 4)
            Toggle("", isOn: isOn).labelsHidden().tint(c.accent)
        }
        .padding(.top, 8)
    }

    // MARK: 2 — Frequency response + AutoEq

    private func responseCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("FREQUENCY RESPONSE",
                         trailing: settings.equalizerEnabled
                            ? String(format: "headroom %+.1f dB", curve.effectivePreampDb)
                            : "bypassed")
            FrequencyGraph(curve: curve, enabled: settings.equalizerEnabled,
                           hasProfile: selectedHeadphone != nil, colors: c)
                .frame(height: 168)
                .padding(.top, 6)

            HStack(spacing: 8) {
                Button { showHeadphoneSheet = true } label: {
                    HStack(spacing: 12) {
                        Image(systemName: "headphones")
                            .font(.system(size: 24))
                            .foregroundStyle(selectedHeadphone != nil ? c.accent : c.textMuted)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(selectedHeadphone?.displayName ?? "Choose your headphones (AutoEq)")
                                .font(AtlasFont.rowTitle())
                                .foregroundStyle(c.text)
                                .lineLimit(1)
                            Text(selectedHeadphone?.summary
                                 ?? "Match Sony, Apple, Sennheiser, Moondrop & 30+ models to the Harman target")
                                .font(AtlasFont.caption())
                                .foregroundStyle(c.textMuted)
                                .lineLimit(2)
                        }
                        Spacer(minLength: 4)
                    }
                }
                .buttonStyle(ToolTilePressStyle())
                .accessibilityLabel("Headphone calibration")
                if selectedHeadphone != nil {
                    Button {
                        settings.headphoneProfileId = ""
                        player.applyAudioSettings()
                    } label: {
                        Image(systemName: "xmark.circle.fill").foregroundStyle(c.textMuted)
                    }
                    .buttonStyle(ToolTilePressStyle())
                    .accessibilityLabel("Clear headphone model")
                } else {
                    Image(systemName: "chevron.right").foregroundStyle(c.textMuted)
                }
            }
            .padding(12)
            .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        }
    }

    // MARK: 3 — Sound Check XY pad

    private func feelCard(_ c: AtlasColors) -> some View {
        ToolCard {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    StudioHeader("FIND YOUR SOUND",
                                 trailing: abs(settings.soundCheckWarmth) > 0.05 || abs(settings.soundCheckPunch) > 0.05
                                    ? "custom feel" : "neutral")
                    Text("Drag anywhere — no technical dB math needed.")
                        .font(AtlasFont.caption())
                        .foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                if abs(settings.soundCheckWarmth) > 0.05 || abs(settings.soundCheckPunch) > 0.05 {
                    Button("Center") {
                        settings.soundCheckWarmth = 0
                        settings.soundCheckPunch = 0
                        player.applyAudioSettings()
                    }
                    .font(AtlasFont.label())
                    .foregroundStyle(c.accent)
                }
            }
            SoundPad(
                warmth: Binding(get: { settings.soundCheckWarmth }, set: {
                    settings.soundCheckWarmth = $0
                    player.applyAudioSettings()
                }),
                punch: Binding(get: { settings.soundCheckPunch }, set: {
                    settings.soundCheckPunch = $0
                    player.applyAudioSettings()
                }),
                colors: c
            )
            .padding(.top, 8)
        }
    }

    // MARK: 4 — Space & dynamics

    private func spaceCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("SPACE & DYNAMICS", trailing: "headphones")

            labelledSlider(
                symbol: "surround",
                title: "Soundstage width",
                value: settings.virtualizerStrength > 0
                    ? "\(settings.virtualizerStrength)% wider stereo image"
                    : "Off — natural stereo width",
                slider: Slider(value: Binding(
                    get: { Float(settings.virtualizerStrength) },
                    set: { settings.virtualizerStrength = Int($0.rounded()); player.applyAudioSettings() }
                ), in: 0...100).tint(c.accent),
                c: c
            )

            toggleRow(
                symbol: "speaker.wave.3.fill",
                title: "Sub-bass boost",
                subtitle: settings.bassBoostEnabled
                    ? "+\(settings.bassBoostStrength)% deep low-end punch" : "Off",
                isOn: Binding(get: { settings.bassBoostEnabled }, set: {
                    settings.bassBoostEnabled = $0
                    player.applyAudioSettings()
                }),
                c: c
            )
            if settings.bassBoostEnabled {
                Slider(value: Binding(
                    get: { Float(settings.bassBoostStrength) },
                    set: { settings.bassBoostStrength = Int($0.rounded()); player.applyAudioSettings() }
                ), in: 0...100).tint(c.accent)
            }

            HStack(spacing: 12) {
                Image(systemName: "balance.horizontal")
                    .font(.system(size: 20))
                    .foregroundStyle(abs(settings.channelBalance) > 0.03 ? c.accent : c.textMuted)
                VStack(alignment: .leading, spacing: 3) {
                    Text("Left / Right balance").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                    Text(balanceLabel).font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                if abs(settings.channelBalance) > 0.03 {
                    Button("Center") { settings.channelBalance = 0; player.applyAudioSettings() }
                        .font(AtlasFont.label()).foregroundStyle(c.accent)
                }
            }
            .padding(.top, 6)
            CenterZeroSlider(
                value: Binding(get: { settings.channelBalance }, set: {
                    settings.channelBalance = abs($0) < 0.06 ? 0 : $0
                    player.applyAudioSettings()
                }),
                range: -1...1, accent: c.accent, track: c.surfaceElevated
            )

            Divider().background(c.border.opacity(0.5)).padding(.vertical, 6)

            toggleRow(
                symbol: "shield.fill",
                title: "Peak limiter & headroom",
                subtitle: settings.limiterEnabled
                    ? String(format: "Protects loud boosts from crackling (%.1f dB trim)",
                             curve.autoHeadroomCutDb)
                    : "Limiter off — raw digital peaks",
                isOn: Binding(get: { settings.limiterEnabled }, set: {
                    settings.limiterEnabled = $0
                    player.applyAudioSettings()
                }),
                c: c
            )

            toggleRow(
                symbol: "waveform.path.ecg",
                title: "Quiet-track loudness lift",
                subtitle: settings.normalizationEnabled
                    ? String(format: "+%.1f dB gentle gain lift", Double(settings.loudnessGainMb) / 100.0)
                    : "Off",
                isOn: Binding(get: { settings.normalizationEnabled }, set: {
                    settings.normalizationEnabled = $0
                    player.applyAudioSettings()
                }),
                c: c
            )
            if settings.normalizationEnabled {
                Slider(value: Binding(
                    get: { Float(settings.loudnessGainMb) },
                    set: { settings.loudnessGainMb = Int($0.rounded()); player.applyAudioSettings() }
                ), in: 0...1000).tint(c.accent)
            }
        }
    }

    private var balanceLabel: String {
        let percent = Int(abs(settings.channelBalance) * 100)
        if settings.channelBalance < -0.04 { return "\(percent)% toward Left ear" }
        if settings.channelBalance > 0.04 { return "\(percent)% toward Right ear" }
        return "Centered"
    }

    private func labelledSlider<SliderContent: View>(symbol: String, title: String, value: String,
                                slider: SliderContent, c: AtlasColors) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack(spacing: 10) {
                Image(systemName: symbol).font(.system(size: 20)).foregroundStyle(c.accent)
                VStack(alignment: .leading, spacing: 2) {
                    Text(title).font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                    Text(value).font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                }
            }
            slider
        }
        .padding(.bottom, 6)
    }

    // MARK: 5 — Presets + manual bands

    private func presetsCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("SOUND PRESETS", trailing: activePreset)
            Text("Instant tonal curves — stacks cleanly with your headphone profile.")
                .font(AtlasFont.caption())
                .foregroundStyle(c.textMuted)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(EqCurves.PRESET_ORDER, id: \.self) { preset in
                        VStack(spacing: 4) {
                            PresetCurveBars(name: preset, selected: activePreset == preset)
                                .frame(width: 76, height: 34)
                            AtlasFilterChip(preset, selected: activePreset == preset) {
                                toolsVM.applyEqPreset(preset)
                            }
                        }
                    }
                }
                .padding(.vertical, 4)
            }

            Divider().background(c.border.opacity(0.5)).padding(.vertical, 6)

            Button { withAnimation(AtlasMotion.quick) { showManualBands.toggle() } } label: {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Fine-tune manual bands").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                        Text(showManualBands
                             ? "Tap to collapse 9 graphic bands & preamp"
                             : "Expand for per-band sliders (60 Hz – 16 kHz)")
                            .font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                    }
                    Spacer(minLength: 4)
                    Image(systemName: showManualBands ? "chevron.up" : "chevron.down")
                        .foregroundStyle(c.accent)
                }
            }
            .buttonStyle(ToolTilePressStyle())
            .accessibilityLabel("Manual EQ bands")

            if showManualBands {
                manualBands(c)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }

    private func manualBands(_ c: AtlasColors) -> some View {
        let bands = bands9
        return VStack(spacing: 10) {
            HStack(spacing: 10) {
                Text("Preamp").font(AtlasFont.caption()).foregroundStyle(c.text).frame(width: 58, alignment: .leading)
                CenterZeroSlider(
                    value: Binding(get: { settings.preampDb }, set: {
                        settings.preampDb = $0
                        player.applyAudioSettings()
                    }),
                    range: AudioDsp.MIN_PREAMP_DB...AudioDsp.MAX_PREAMP_DB,
                    accent: c.accent, track: c.surfaceElevated
                )
                Text(String(format: "%+.1f dB", settings.preampDb))
                    .font(AtlasFont.label())
                    .foregroundStyle(abs(settings.preampDb) > 0.1 ? c.accent : c.textMuted)
                    .frame(width: 58, alignment: .trailing)
            }
            ForEach(EqCurves.MANUAL_BANDS.indices, id: \.self) { index in
                let label = EqCurves.MANUAL_BANDS[index]
                let gain = bands[min(index, bands.count - 1)]
                HStack(spacing: 10) {
                    Text(label).font(AtlasFont.caption()).foregroundStyle(c.text).frame(width: 58, alignment: .leading)
                    CenterZeroSlider(
                        value: Binding(get: { Float(gain) }, set: { newValue in
                            var next = bands
                            next[index] = Int(newValue.rounded())
                                .clamped(to: -EqCurves.BAND_MAX_MB...EqCurves.BAND_MAX_MB)
                            toolsVM.setCustomBands(next)
                        }),
                        range: Float(-EqCurves.BAND_MAX_MB)...Float(EqCurves.BAND_MAX_MB),
                        accent: c.accent, track: c.surfaceElevated
                    )
                    Text(String(format: "%+.1f dB", Double(gain) / 100.0))
                        .font(AtlasFont.label())
                        .foregroundStyle(gain == 0 ? c.textMuted : c.accent)
                        .frame(width: 58, alignment: .trailing)
                }
            }
        }
        .padding(.top, 8)
    }

    // MARK: 6 — Playback feel

    private func playbackCard(_ c: AtlasColors) -> some View {
        ToolCard {
            StudioHeader("PLAYBACK FEEL", trailing: "transitions & speed")
            HStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Crossfade between tracks").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                    Text(settings.crossfadeEnabled
                         ? "Smoothly blends across \(settings.crossfadeSeconds)s"
                         : "Gapless — zero pause between songs")
                        .font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                Toggle("", isOn: Binding(get: { settings.crossfadeEnabled }, set: {
                    settings.crossfadeEnabled = $0
                    player.applyAudioSettings()
                })).labelsHidden().tint(c.accent)
            }
            if settings.crossfadeEnabled {
                HStack(spacing: 8) {
                    ForEach([2, 5, 8, 12], id: \.self) { seconds in
                        AtlasFilterChip("\(seconds)s", selected: settings.crossfadeSeconds == seconds) {
                            settings.crossfadeSeconds = seconds
                            player.applyAudioSettings()
                        }
                    }
                }
                .padding(.top, 4)
            }
            Divider().background(c.border.opacity(0.5)).padding(.vertical, 8)
            HStack {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Playback speed").font(AtlasFont.rowTitle()).foregroundStyle(c.text)
                    Text("Tempo without changing pitch").font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                }
                Spacer(minLength: 4)
                Text(String(format: "%.1f×", player.state.playbackSpeed))
                    .font(AtlasFont.label()).foregroundStyle(c.accent)
            }
            Slider(value: Binding(get: { player.state.playbackSpeed }, set: {
                player.setPlaybackSpeed($0)
            }), in: 0.5...2).tint(c.accent)
        }
    }

    // MARK: 7 — Reset

    private func resetButton(_ c: AtlasColors) -> some View {
        Button {
            toolsVM.resetAudio()
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "arrow.counterclockwise")
                Text("Reset all sound settings to flat")
            }
            .font(AtlasFont.body())
            .foregroundStyle(c.error)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 14)
            .background(c.error.opacity(0.12), in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel("Reset all sound settings to flat")
    }

    // MARK: - Sheets

    private func headphonePicker(_ c: AtlasColors) -> some View {
        let filtered = HeadphoneProfiles.search(query)
        return NavigationStack {
            VStack(spacing: 10) {
                Text("Choose your headphones")
                    .font(AtlasFont.title()).foregroundStyle(c.text)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("Harman-target calibration profiles (\(HeadphoneProfiles.PRESETS.count) models)")
                    .font(AtlasFont.caption()).foregroundStyle(c.textMuted)
                    .frame(maxWidth: .infinity, alignment: .leading)
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(c.textMuted)
                    TextField("Search brand or model (e.g. Sony, HD 600)", text: $query)
                        .textFieldStyle(.plain)
                        .foregroundStyle(c.text)
                    if !query.isEmpty {
                        Button { query = "" } label: {
                            Image(systemName: "xmark.circle.fill").foregroundStyle(c.textMuted)
                        }
                    }
                }
                .padding(10)
                .background(c.surfaceElevated, in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))

                ScrollView {
                    LazyVStack(spacing: 6) {
                        if query.trimmingCharacters(in: .whitespaces).isEmpty {
                            profileRow(title: "None (Use default headphone tuning)",
                                       subtitle: "Leaves frequency response calibrated only by your EQ and Sound Check",
                                       badge: "Default", selected: settings.headphoneProfileId.isEmpty) {
                                settings.headphoneProfileId = ""
                                player.applyAudioSettings()
                                showHeadphoneSheet = false
                            }
                        }
                        ForEach(filtered) { profile in
                            profileRow(title: profile.displayName, subtitle: profile.summary,
                                       badge: profile.formFactor,
                                       selected: settings.headphoneProfileId == profile.id) {
                                settings.headphoneProfileId = profile.id
                                settings.equalizerEnabled = true
                                player.applyAudioSettings()
                                showHeadphoneSheet = false
                            }
                        }
                    }
                    .padding(.bottom, 20)
                }
            }
            .padding(16)
            .background(c.background)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { showHeadphoneSheet = false }.foregroundStyle(c.accent)
                }
            }
        }
        .presentationDetents([.large])
    }

    private func profileRow(title: String, subtitle: String, badge: String,
                            selected: Bool, onSelect: @escaping () -> Void) -> some View {
        let c = colors
        return Button(action: onSelect) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(title).font(AtlasFont.rowTitle()).foregroundStyle(c.text).lineLimit(1)
                        Text(badge)
                            .font(AtlasFont.caption())
                            .foregroundStyle(c.textMuted)
                            .padding(.horizontal, 7).padding(.vertical, 2)
                            .background(c.surfaceHighest, in: Capsule())
                    }
                    Text(subtitle).font(AtlasFont.caption()).foregroundStyle(c.textMuted).lineLimit(2)
                }
                Spacer(minLength: 4)
                if selected { Image(systemName: "checkmark").foregroundStyle(c.accent) }
            }
            .padding(12)
            .background(selected ? c.accent.opacity(0.12) : c.surfaceElevated,
                        in: RoundedRectangle(cornerRadius: Radius.sm, style: .continuous))
        }
        .buttonStyle(ToolTilePressStyle())
        .accessibilityLabel(title)
    }
}

// MARK: - Frequency graph

private struct FrequencyGraph: View {
    let curve: EffectiveAudioCurve
    let enabled: Bool
    let hasProfile: Bool
    let colors: AtlasColors

    private static let hzLabels = ["31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k"]

    var body: some View {
        VStack(spacing: 4) {
            Canvas { context, size in
                let w = size.width
                let h = size.height
                let midY = h / 2
                let maxDb: CGFloat = 12
                func y(for db: Float) -> CGFloat {
                    min(max(midY - CGFloat(db) / maxDb * (midY * 0.85), 4), h - 4)
                }

                for db in [-6.0, 6.0] {
                    let yPos = y(for: Float(db))
                    var line = Path()
                    line.move(to: CGPoint(x: 0, y: yPos))
                    line.addLine(to: CGPoint(x: w, y: yPos))
                    context.stroke(line, with: .color(colors.textMuted.opacity(0.22)),
                                   style: StrokeStyle(lineWidth: 1))
                }
                var zero = Path()
                zero.move(to: CGPoint(x: 0, y: midY))
                zero.addLine(to: CGPoint(x: w, y: midY))
                context.stroke(zero, with: .color(colors.textMuted.opacity(0.4)),
                               style: StrokeStyle(lineWidth: 1.2))

                let count = curve.totalBandsDb10.count
                for i in 0..<max(count, 1) {
                    let x = count <= 1 ? 0 : w * CGFloat(i) / CGFloat(count - 1)
                    var column = Path()
                    column.move(to: CGPoint(x: x, y: 0))
                    column.addLine(to: CGPoint(x: x, y: h))
                    context.stroke(column, with: .color(colors.textMuted.opacity(0.13)),
                                   style: StrokeStyle(lineWidth: 1))
                }

                if hasProfile {
                    let reference = smoothPath(curve.headphoneCurveDb10, width: w, y: y)
                    context.stroke(reference,
                                   with: .color(colors.textMuted.opacity(0.55)),
                                   style: StrokeStyle(lineWidth: 1.6, dash: [8, 6]))
                }

                let points = enabled ? curve.totalBandsDb10 : [Float](repeating: 0, count: 10)
                let stroke = smoothPath(points, width: w, y: y)
                var fill = stroke
                fill.addLine(to: CGPoint(x: w, y: midY))
                fill.addLine(to: CGPoint(x: 0, y: midY))
                fill.closeSubpath()
                context.fill(fill, with: .linearGradient(
                    Gradient(colors: [colors.accent.opacity(0.28), colors.accent.opacity(0.03)]),
                    startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: h)
                ))
                context.stroke(stroke, with: .color(enabled ? colors.accent : colors.textMuted),
                               style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))

                for (index, db) in points.enumerated() {
                    let x = points.count <= 1 ? 0 : w * CGFloat(index) / CGFloat(points.count - 1)
                    let dot = CGRect(x: x - 3, y: y(for: db) - 3, width: 6, height: 6)
                    context.fill(Path(ellipseIn: dot), with: .color(enabled ? colors.accent : colors.textMuted))
                }
            }
            HStack(spacing: 0) {
                ForEach(Self.hzLabels, id: \.self) { label in
                    Text(label)
                        .font(AtlasFont.mono(size: 9))
                        .foregroundStyle(colors.textMuted)
                        .frame(maxWidth: .infinity)
                }
            }
        }
    }

    private func smoothPath(_ values: [Float], width: CGFloat,
                            y: (Float) -> CGFloat) -> Path {
        var path = Path()
        guard !values.isEmpty else { return path }
        let points = values.enumerated().map { index, db -> CGPoint in
            CGPoint(x: values.count == 1 ? 0 : width * CGFloat(index) / CGFloat(values.count - 1),
                    y: y(db))
        }
        path.move(to: points[0])
        if points.count > 1 {
            for i in 0..<(points.count - 1) {
                let a = points[i], b = points[i + 1]
                let midX = (a.x + b.x) / 2
                path.addCurve(to: b, control1: CGPoint(x: midX, y: a.y),
                              control2: CGPoint(x: midX, y: b.y))
            }
        }
        return path
    }
}

// MARK: - Sound Check pad

private struct SoundPad: View {
    @Binding var warmth: Float
    @Binding var punch: Float
    let colors: AtlasColors

    var body: some View {
        VStack(spacing: 4) {
            Text("Punchy").font(AtlasFont.label()).foregroundStyle(colors.textMuted)
            HStack(spacing: 6) {
                Text("Bright").font(AtlasFont.caption()).foregroundStyle(colors.textMuted).frame(width: 44)
                GeometryReader { geo in
                    let side = geo.size
                    ZStack {
                        Canvas { context, size in
                            let cx = size.width / 2, cy = size.height / 2
                            var vertical = Path()
                            vertical.move(to: CGPoint(x: cx, y: 0))
                            vertical.addLine(to: CGPoint(x: cx, y: size.height))
                            context.stroke(vertical, with: .color(colors.textMuted.opacity(0.2)),
                                           style: StrokeStyle(lineWidth: 1))
                            var horizontal = Path()
                            horizontal.move(to: CGPoint(x: 0, y: cy))
                            horizontal.addLine(to: CGPoint(x: size.width, y: cy))
                            context.stroke(horizontal, with: .color(colors.textMuted.opacity(0.2)),
                                           style: StrokeStyle(lineWidth: 1))
                            let dot = CGPoint(
                                x: (CGFloat(warmth) + 1) / 2 * size.width,
                                y: (1 - CGFloat(punch)) / 2 * size.height
                            )
                            context.fill(Path(ellipseIn: CGRect(x: dot.x - 20, y: dot.y - 20, width: 40, height: 40)),
                                         with: .color(colors.accent.opacity(0.18)))
                            context.fill(Path(ellipseIn: CGRect(x: dot.x - 8, y: dot.y - 8, width: 16, height: 16)),
                                         with: .color(colors.accent))
                        }
                    }
                    .frame(width: side.width, height: side.height)
                    .background(colors.surfaceElevated,
                                in: RoundedRectangle(cornerRadius: Radius.lg, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: Radius.lg, style: .continuous)
                            .stroke(colors.textMuted.opacity(0.18), lineWidth: 1)
                    )
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0).onChanged { value in
                        let w = Float((value.location.x / max(side.width, 1)) * 2 - 1)
                        let p = Float(1 - (value.location.y / max(side.height, 1)) * 2)
                        warmth = abs(w) < 0.08 ? 0 : min(max(w, -1), 1)
                        punch = abs(p) < 0.08 ? 0 : min(max(p, -1), 1)
                    })
                }
                .frame(height: 170)
                Text("Warm").font(AtlasFont.caption()).foregroundStyle(colors.textMuted).frame(width: 44)
            }
            Text("Relaxed").font(AtlasFont.label()).foregroundStyle(colors.textMuted)
        }
    }
}

// MARK: - Output picker (shared with Now Playing through identical contract API)

struct OutputPickerSheet: View {
    let outputs: [AudioOutputDevice]
    @Binding var preferredID: String?
    let onDismiss: () -> Void

    @Environment(\.atlasColors) private var injected
    @Environment(\.colorScheme) private var scheme

    private var colors: AtlasColors {
        injected ?? AtlasTheme.colors(for: scheme == .dark ? .dark : .light)
    }

    var body: some View {
        let c = colors
        NavigationStack {
            List {
                Section {
                    outputRow(id: nil, name: "System default", kind: .speaker,
                              selected: preferredID == nil) { select(nil) }
                } header: {
                    Text("Atlas keeps your choice until you change it.")
                        .font(AtlasFont.caption())
                }
                Section("Detected routes") {
                    if outputs.isEmpty {
                        Text("No removable outputs detected. Connect a USB DAC or pair Bluetooth and it will appear here.")
                            .font(AtlasFont.small())
                            .foregroundStyle(c.textMuted)
                    }
                    ForEach(outputs) { device in
                        outputRow(id: device.id, name: device.name, kind: device.kind,
                                  selected: preferredID == device.id) { select(device.id) }
                    }
                }
            }
            .scrollContentBackground(.hidden)
            .background(c.background)
            .navigationTitle("Audio output")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done", action: onDismiss).foregroundStyle(c.accent)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    private func select(_ id: String?) {
        preferredID = id
        if let id {
            UserDefaults.standard.set(id, forKey: "atlas.preferred.output")
        } else {
            UserDefaults.standard.removeObject(forKey: "atlas.preferred.output")
        }
        onDismiss()
    }

    private func outputRow(id: String?, name: String, kind: OutputKind,
                           selected: Bool, action: @escaping () -> Void) -> some View {
        let c = colors
        return Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: outputSymbol(kind)).foregroundStyle(c.accent)
                Text(name).font(AtlasFont.small()).foregroundStyle(c.text).lineLimit(1)
                Spacer(minLength: 4)
                if selected { Image(systemName: "checkmark").foregroundStyle(c.accent) }
            }
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel(id == nil ? "System default" : name)
    }

    private func outputSymbol(_ kind: OutputKind) -> String {
        switch kind {
        case .wired: return "headphones"
        case .bluetooth: return "bluetooth"
        case .usbDAC: return "cable.connector"
        case .hdmi: return "tv.hdmi"
        case .earpiece: return "speaker.wave.1"
        default: return "speaker.wave.2"
        }
    }
}

private extension Comparable {
    func clamped(to range: ClosedRange<Self>) -> Self {
        min(max(self, range.lowerBound), range.upperBound)
    }
}
