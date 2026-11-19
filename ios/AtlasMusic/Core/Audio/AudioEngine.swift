import AVFoundation
import Foundation

// Atlas iOS — Real AVAudioEngine playback graph: A/B player nodes for boundary crossfade,
// 10-band AVAudioUnitEQ, AVAudioUnitVarispeed, a final AVAudioMixerNode, plus audio-session,
// interruption and route-change handling. All engine work runs on a private serial queue;
// every callback to PlayerController is dispatched to the main actor.
//
// Platform note: Android's BassBoost / Virtualizer / LoudnessEnhancer / DynamicsProcessing
// have no public iOS equivalents. They are approximated upstream by AudioDsp.computeEffectiveCurve
// (AutoEq + Sound Check warmth/punch + anti-clipping headroom) and by the mixer/EQ gain here.
// The limiter flag is approximated by capping the global (preamp) gain so boosts cannot clip.

final class AudioEngine {

    /// A single upcoming playable track handed to the engine by PlayerController (lookahead).
    struct NextDescriptor: Sendable {
        let url: URL
        let uri: String
        let durationMs: Int64
    }

    // MARK: - Callbacks (always invoked on the main actor by this engine)

    /// Natural end of the current track. PlayerController credits the play and advances its queue.
    var onTrackCompleted: (@MainActor (String) -> Void)?
    /// Pure lookahead: what should play after the current track (respects repeat/shuffle).
    var onRequestNext: (@MainActor () -> NextDescriptor?)?
    /// The whole queue ended with no next track (repeat off, last item).
    var onPlaybackEnded: (@MainActor () -> Void)?
    /// Mirror of the engine's playing state into UI state.
    var onPlayingChanged: (@MainActor (Bool) -> Void)?
    /// A load/render failure surfaced to the UI.
    var onError: (@MainActor (String) -> Void)?
    /// Sleep-timer "end of track" fired; controller should persist the flag reset.
    var onSleepEndOfTrackFired: (@MainActor () -> Void)?

    // MARK: - Graph

    private let engine = AVAudioEngine()
    private let playerA = AVAudioPlayerNode()
    private let playerB = AVAudioPlayerNode()
    private let varispeed = AVAudioUnitVarispeed()
    private let eq = AVAudioUnitEQ(numberOfBands: 10)
    private let mixer = AVAudioMixerNode()

    private let isoFrequencies: [Float] = [44, 88, 177, 355, 710, 1420, 2840, 5680, 11360, 22000]

    // MARK: - Threading

    /// Every mutable field below is only touched on this serial queue.
    private let queue = DispatchQueue(label: "com.atlas.music.audio", qos: .userInitiated)
    private var watcher: DispatchSourceTimer?
    private var observerTokens: [NSObjectProtocol] = []

    // MARK: - State (queue-confined)

    private var graphReady = false
    private var graphBuilt = false
    private var sessionActive = false

    private var active = 0                       // 0 -> playerA, 1 -> playerB
    private var currentURI: String?
    private var currentFile: AVAudioFile?
    private var currentDurationMs: Int64 = 0
    private var seekOffsetSeconds: Double = 0    // where in the file the current segment starts
    private var pausedPositionMs: Int64 = 0
    private var expectedEndDate: Date?
    private var playing = false
    private var ended = false
    private var resumeAfterInterruption = false

    // Pre-roll / crossfade
    private var crossfadeEnabled = false
    private var crossfadeSeconds = 2
    private var preRollFile: AVAudioFile?
    private var preRollURI: String?
    private var preRollDurationMs: Int64 = 0
    private var preRollRequested = false
    private var preRollToken: String?
    private var fadeActive = false
    private var spareStarted = false

    // DSP / volume
    private var masterVolume: Float = 1
    private var speed: Float = 1
    private var sleepEndOfTrack = false
    private var pendingEQ: (bands: [Float], preamp: Float, balance: Float, limiter: Bool)?

    // MARK: - Init / teardown

    init() {
        // Observers are delivered on our serial queue, so handlers need no extra hop.
        observerTokens.append(
            NotificationCenter.default.addObserver(
                forName: AVAudioSession.interruptionNotification, object: nil, queue: queue
            ) { [weak self] note in self?.handleInterruption(note) }
        )
        observerTokens.append(
            NotificationCenter.default.addObserver(
                forName: AVAudioSession.routeChangeNotification, object: nil, queue: queue
            ) { [weak self] note in self?.handleRouteChange(note) }
        )
        observerTokens.append(
            NotificationCenter.default.addObserver(
                forName: AVAudioSession.mediaServicesWereResetNotification, object: nil, queue: queue
            ) { [weak self] _ in self?.handleMediaServicesReset() }
        )
    }

    deinit {
        observerTokens.forEach { NotificationCenter.default.removeObserver($0) }
        watcher?.cancel()
        watcher = nil
        if engine.isRunning { engine.stop() }
    }

    // MARK: - Public API

    /// Configures the `.playback` audio session, builds the graph and starts the 200 ms watcher.
    func prepare() {
        queue.async {
            guard !self.graphReady else { return }
            let session = AVAudioSession.sharedInstance()
            do {
                try session.setCategory(.playback, mode: .default, options: [])
                try session.setActive(true, options: [])
                self.sessionActive = true
            } catch {
                self.emitOnMain { self.onError?(error.localizedDescription) }
            }
            self.buildGraph()
            self.graphReady = true
            self.applyEQInternal()
            self.startWatcher()
        }
    }

    func teardown() {
        queue.async {
            self.stopInternal()
            self.watcher?.cancel()
            self.watcher = nil
            if self.engine.isRunning { self.engine.stop() }
            self.graphReady = false   // reconnect must restart the engine (nodes stay attached)
            if self.sessionActive {
                try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
                self.sessionActive = false
            }
        }
    }

    /// Loads a URL into the active player. Sample-accurate `startMs` via `scheduleSegment`.
    func load(url: URL, uri: String, durationMs: Int64, startMs: Int64 = 0, autoPlay: Bool = true) {
        queue.async {
            guard self.graphReady else { return }
            self.loadInternal(url: url, uri: uri, durationMs: durationMs, startMs: startMs, autoPlay: autoPlay)
        }
    }

    func play() { queue.async { self.playInternal() } }
    func pause() { queue.async { self.pauseInternal() } }
    func stop() { queue.async { self.stopInternal() } }

    func seek(to ms: Int64) { queue.async { self.seekInternal(ms) } }

    /// Current position derived from `lastRenderTime` + `playerTime(forNodeTime:)`, in ms.
    func currentPositionMs() -> Int64 { queue.sync { self.positionMsLocked() } }

    func trackDurationMs() -> Int64 { queue.sync { self.currentDurationMs } }

    func isPlaying() -> Bool { queue.sync { self.playing } }

    func setPlaybackSpeed(_ value: Float) {
        queue.async {
            let v = min(2.0, max(0.5, value))
            self.speed = v
            self.varispeed.rate = v
            self.recomputeExpectedEnd()
        }
    }

    /// Master output gain (0...1). Used for the Pomodoro focus duck.
    func setMasterVolume(_ value: Float) {
        queue.async {
            self.masterVolume = min(1, max(0, value))
            self.mixer.volume = self.masterVolume
        }
    }

    /// Applies the final 10-band curve, preamp, balance and limiter approximation.
    func applyEQ(totalBandsDb10: [Float], preampDb: Float, balance: Float, limiter: Bool) {
        queue.async {
            self.pendingEQ = (totalBandsDb10, preampDb, balance, limiter)
            guard self.graphReady else { return }
            self.applyEQInternal()
        }
    }

    func setCrossfade(enabled: Bool, seconds: Int) {
        queue.async {
            self.crossfadeEnabled = enabled
            self.crossfadeSeconds = min(12, max(1, seconds))
            if !enabled { self.cancelFade() }
        }
    }

    func setSleepTimerEndOfTrack(_ enabled: Bool) {
        queue.async { self.sleepEndOfTrack = enabled }
    }

    // MARK: - Graph construction

    private func buildGraph() {
        if !graphBuilt {
            engine.attach(playerA)
            engine.attach(playerB)
            engine.attach(varispeed)
            engine.attach(eq)
            engine.attach(mixer)

            engine.connect(playerA, to: varispeed, format: nil)
            engine.connect(playerB, to: varispeed, format: nil)
            engine.connect(varispeed, to: eq, format: nil)
            engine.connect(eq, to: mixer, format: nil)
            engine.connect(mixer, to: engine.outputNode, format: nil)

            let sampleRate = Double(engine.outputNode.inputFormat(forBus: 0).sampleRate)
            let nyquist = sampleRate > 0 ? sampleRate / 2 : 22050
            for (i, band) in eq.bands.enumerated() {
                band.filterType = .parametric
                band.frequency = min(isoFrequencies[i], Float(max(100, nyquist - 100)))
                band.bandwidth = 0.8
                band.gain = 0
                band.bypass = false
            }
            graphBuilt = true
        }
        mixer.volume = masterVolume
        mixer.pan = 0
        varispeed.rate = speed

        if !engine.isRunning {
            do {
                try engine.start()
            } catch {
                emitOnMain { self.onError?(error.localizedDescription) }
            }
        }
    }

    private func applyEQInternal() {
        guard let e = pendingEQ else { return }
        for (i, band) in eq.bands.enumerated() {
            band.gain = e.bands.indices.contains(i) ? e.bands[i] : 0
            band.bypass = false
        }
        // Limiter approximation: never let the preamp boost above unity when limiting.
        let pre = e.limiter ? min(e.preamp, 0) : e.preamp
        eq.globalGain = min(6, max(-18, pre))
        mixer.pan = min(1, max(-1, e.balance))
    }

    // MARK: - Core transport (queue-confined)

    private func activeNode() -> AVAudioPlayerNode { active == 0 ? playerA : playerB }
    private func spareNode() -> AVAudioPlayerNode { active == 0 ? playerB : playerA }

    private func loadInternal(url: URL, uri: String, durationMs: Int64, startMs: Int64, autoPlay: Bool) {
        playerA.stop(); playerB.stop()
        playerA.volume = 1; playerB.volume = 0
        resetPreRoll()
        ended = false

        do {
            let file = try AVAudioFile(forReading: url)
            let fileDurMs = Int64((Double(file.length) / max(1, file.fileFormat.sampleRate) * 1000).rounded())
            currentFile = file
            currentURI = uri
            currentDurationMs = durationMs > 0 ? durationMs : fileDurMs
            active = 0
            let start = min(max(0, startMs), max(0, currentDurationMs - 1))
            seekOffsetSeconds = Double(start) / 1000.0
            pausedPositionMs = start
            schedule(file: file, startMs: start, node: playerA)
            playerA.volume = 1
            playerB.volume = 0

            if autoPlay {
                playerA.play()
                playing = true
                recomputeExpectedEnd()
                notifyPlaying(true)
            } else {
                playing = false
                expectedEndDate = nil
                notifyPlaying(false)
            }
        } catch {
            currentFile = nil
            currentURI = nil
            playing = false
            notifyPlaying(false)
            let message = error.localizedDescription
            emitOnMain { self.onError?(message) }
        }
    }

    /// scheduleSegment with a sample offset is the sample-accurate seek mechanism.
    private func schedule(file: AVAudioFile, startMs: Int64, node: AVAudioPlayerNode) {
        let sampleRate = max(1, file.fileFormat.sampleRate)
        let startFrame = AVAudioFramePosition((Double(max(0, startMs)) / 1000.0 * sampleRate).rounded())
        let frames = max(0, file.length - startFrame)
        if frames > 0 {
            node.scheduleSegment(file, startingFrame: startFrame, frameCount: AVAudioFrameCount(frames), at: nil)
        } else {
            // Seek at/past EOF: schedule the whole file as a safe fallback.
            node.scheduleFile(file, at: nil)
        }
    }

    private func playInternal() {
        guard graphReady, currentFile != nil else { return }
        if ended { seekInternal(0); ended = false }
        activeNode().play()
        // If a crossfade was in progress when playback was paused, resume the rising node too.
        if fadeActive, preRollFile != nil { spareNode().play() }
        playing = true
        recomputeExpectedEnd()
        notifyPlaying(true)
    }

    private func pauseInternal() {
        guard playing else { return }
        pausedPositionMs = positionMsLocked()
        playerA.pause()
        playerB.pause()
        playing = false
        expectedEndDate = nil
        notifyPlaying(false)
    }

    private func stopInternal() {
        playerA.stop(); playerB.stop()
        playerA.volume = 1; playerB.volume = 0
        resetPreRoll()
        playing = false
        ended = true
        expectedEndDate = nil
        pausedPositionMs = 0
        seekOffsetSeconds = 0
        notifyPlaying(false)
    }

    private func seekInternal(_ ms: Int64) {
        guard let file = currentFile else { return }
        let wasPlaying = playing && !ended
        cancelFade()
        let node = activeNode()
        node.stop()
        let maxPos = currentDurationMs > 0 ? currentDurationMs - 1 : ms
        let clamped = min(max(0, ms), max(0, maxPos))
        seekOffsetSeconds = Double(clamped) / 1000.0
        pausedPositionMs = clamped
        schedule(file: file, startMs: clamped, node: node)
        node.volume = 1
        ended = false
        if wasPlaying {
            node.play()
            playing = true
            recomputeExpectedEnd()
            notifyPlaying(true)
        }
    }

    /// Position from the last rendered node time, converted into the player's timeline.
    private func positionMsLocked() -> Int64 {
        guard currentFile != nil else { return 0 }
        if !playing { return pausedPositionMs }
        let node = activeNode()
        guard let last = node.lastRenderTime, let pt = node.playerTime(forNodeTime: last) else {
            return pausedPositionMs
        }
        let elapsed = Double(pt.sampleTime) / max(1, pt.sampleRate)
        return max(0, Int64(((elapsed + seekOffsetSeconds) * 1000).rounded()))
    }

    private func recomputeExpectedEnd() {
        guard playing, currentDurationMs > 0 else {
            expectedEndDate = nil
            return
        }
        let pos = positionMsLocked()
        let remainingSeconds = max(0, Double(currentDurationMs - pos) / 1000.0)
        expectedEndDate = Date().addingTimeInterval(remainingSeconds / Double(max(0.25, speed)))
    }

    // MARK: - Crossfade / watcher (200 ms polling, mirroring the Android service)

    private func startWatcher() {
        watcher?.cancel()
        let timer = DispatchSource.makeTimerSource(queue: queue)
        timer.schedule(deadline: .now() + 0.2, repeating: 0.2)
        timer.setEventHandler { [weak self] in self?.tickWatcher() }
        timer.resume()
        watcher = timer
    }

    private func tickWatcher() {
        guard graphReady, playing, !ended, currentDurationMs > 0 else { return }
        let pos = positionMsLocked()
        let duration = currentDurationMs
        let remaining = duration - pos

        // Sleep timer "end of track": fires regardless of crossfade or queue position.
        if sleepEndOfTrack && remaining > 0 && remaining <= 600 {
            pauseInternal()
            seekInternal(0)
            sleepEndOfTrack = false
            emitOnMain { self.onSleepEndOfTrackFired?() }
            return
        }

        // Natural completion. Wall clock is primary because lastRenderTime can go nil at EOF;
        // the position check is a fallback for files with slightly inaccurate metadata.
        let byClock = expectedEndDate.map { Date() >= $0 } ?? false
        if byClock || remaining <= 0 || pos >= duration - 50 {
            handleNaturalCompletion()
            return
        }

        // Lookahead pre-roll of the next track (gapless always, crossfade when enabled).
        if !preRollRequested, let uri = currentURI {
            let windowMs = crossfadeEnabled ? max(1, crossfadeSeconds) * 1000 : 1500
            if remaining > 0 && remaining <= windowMs {
                preRollRequested = true
                preRollToken = uri
                let token = uri
                Task { @MainActor [weak self] in
                    let descriptor = self?.onRequestNext?()
                    self?.queue.async {
                        guard let self, self.preRollToken == token, self.currentURI == token else { return }
                        self.applyPreRoll(descriptor)
                    }
                }
            }
        }

        // Crossfade volume ramp (active fades out, spare rises from silence).
        if crossfadeEnabled, preRollFile != nil, remaining > 0 {
            let fadeMs = max(1, crossfadeSeconds) * 1000
            if remaining <= fadeMs {
                if !fadeActive {
                    fadeActive = true
                    spareNode().play()
                    spareStarted = true
                }
                let fraction = min(1, max(0, Float(remaining) / Float(fadeMs)))
                activeNode().volume = fraction
                spareNode().volume = 1 - fraction
            } else if fadeActive {
                cancelFade()
            }
        }
    }

    private func applyPreRoll(_ descriptor: NextDescriptor?) {
        guard let descriptor else { return }
        do {
            let file = try AVAudioFile(forReading: descriptor.url)
            let fileDurMs = Int64((Double(file.length) / max(1, file.fileFormat.sampleRate) * 1000).rounded())
            preRollFile = file
            preRollURI = descriptor.uri
            preRollDurationMs = descriptor.durationMs > 0 ? descriptor.durationMs : fileDurMs
            let spare = spareNode()
            spare.stop()
            spare.volume = 0
            schedule(file: file, startMs: 0, node: spare)
            // Not started here: the crossfade ramp starts it, otherwise the completion swap does.
        } catch {
            // A bad next file simply degrades to no crossfade for this boundary.
            preRollRequested = false
            preRollToken = nil
        }
    }

    private func handleNaturalCompletion() {
        guard let completedURI = currentURI else { return }

        if let nextFile = preRollFile, let nextURI = preRollURI {
            // Crossfade/gapless: promote the already-scheduled spare, silence the outgoing node.
            let outgoing = activeNode()
            outgoing.stop()
            outgoing.volume = 0

            active = 1 - active
            let incoming = activeNode()
            currentURI = nextURI
            currentFile = nextFile
            currentDurationMs = preRollDurationMs
            seekOffsetSeconds = 0
            pausedPositionMs = 0
            preRollFile = nil
            preRollURI = nil
            preRollDurationMs = 0
            preRollRequested = false
            preRollToken = nil
            fadeActive = false

            incoming.volume = 1
            if !spareStarted { incoming.play() }
            spareStarted = false
            ended = false
            playing = true
            recomputeExpectedEnd()
            notifyPlaying(true)
        } else {
            ended = true
            playing = false
            expectedEndDate = nil
            notifyPlaying(false)
            emitOnMain { self.onPlaybackEnded?() }
        }

        // Play credit is owed to the outgoing track, never to its successor.
        emitOnMain { self.onTrackCompleted?(completedURI) }
    }

    private func resetPreRoll() {
        playerA.stop()
        playerB.stop()
        preRollFile = nil
        preRollURI = nil
        preRollDurationMs = 0
        preRollRequested = false
        preRollToken = nil
        fadeActive = false
        spareStarted = false
    }

    private func cancelFade() {
        fadeActive = false
        spareStarted = false
        spareNode().stop()
        spareNode().volume = 0
        activeNode().volume = 1
        preRollFile = nil
        preRollURI = nil
        preRollDurationMs = 0
        preRollRequested = false
        preRollToken = nil
    }

    // MARK: - Session notifications

    private func handleInterruption(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionInterruptionTypeKey] as? UInt,
              let type = AVAudioSession.InterruptionType(rawValue: raw) else { return }
        switch type {
        case .began:
            resumeAfterInterruption = playing
            if playing { pauseInternal() }
        case .ended:
            let optsRaw = (note.userInfo?[AVAudioSessionInterruptionOptionKey] as? UInt) ?? 0
            let options = AVAudioSession.InterruptionOptions(rawValue: optsRaw)
            guard resumeAfterInterruption || options.contains(.shouldResume) else { return }
            do {
                try AVAudioSession.sharedInstance().setActive(true, options: [])
                sessionActive = true
            } catch {
                emitOnMain { self.onError?(error.localizedDescription) }
                return
            }
            if resumeAfterInterruption {
                resumeAfterInterruption = false
                playInternal()
            }
        @unknown default:
            break
        }
    }

    private func handleRouteChange(_ note: Notification) {
        guard let raw = note.userInfo?[AVAudioSessionRouteChangeReasonKey] as? UInt,
              let reason = AVAudioSession.RouteChangeReason(rawValue: raw) else { return }
        if reason == .oldDeviceUnavailable {
            // Headphones unplugged: pause instead of blasting the speaker.
            if playing { pauseInternal() }
            return
        }
        // New device: restart the engine on the new route and resume the current segment.
        guard graphReady else { return }
        let wasPlaying = playing
        let pos = positionMsLocked()
        engine.stop()
        do {
            try engine.start()
        } catch {
            emitOnMain { self.onError?(error.localizedDescription) }
            return
        }
        if let file = currentFile {
            nodeStopAndReschedule(file: file, startMs: pos, playing: wasPlaying)
        }
    }

    private func nodeStopAndReschedule(file: AVAudioFile, startMs: Int64, playing shouldPlay: Bool) {
        let node = activeNode()
        node.stop()
        seekOffsetSeconds = Double(max(0, startMs)) / 1000.0
        pausedPositionMs = startMs
        schedule(file: file, startMs: startMs, node: node)
        node.volume = 1
        spareNode().stop()
        spareNode().volume = 0
        if shouldPlay {
            node.play()
            playing = true
            recomputeExpectedEnd()
        }
    }

    private func handleMediaServicesReset() {
        // After a media-services reset the audio nodes are invalid. Restart the engine on the
        // rebuilt graph, clear playback and let the UI re-drive a load if needed.
        watcher?.cancel()
        watcher = nil
        if engine.isRunning { engine.stop() }
        graphReady = false
        buildGraph()
        graphReady = true
        applyEQInternal()
        startWatcher()
        stopInternal()
        emitOnMain { self.onError?("Audio services were reset. Select a track to resume playback.") }
    }

    // MARK: - Main-actor dispatch

    private func emitOnMain(_ block: @escaping @MainActor () -> Void) {
        Task { @MainActor in block() }
    }

    private func notifyPlaying(_ value: Bool) {
        emitOnMain { self.onPlayingChanged?(value) }
    }
}
