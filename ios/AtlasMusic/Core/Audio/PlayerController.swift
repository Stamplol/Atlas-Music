import Foundation

// Atlas iOS — Main-actor playback controller: owns the queue, repeat/shuffle logic, sleep timer,
// DSP settings application, queue persistence and the published PlayerUiState. All audio work is
// delegated to AudioEngine; this type only coordinates state and publishes it on the main actor.

@MainActor @Observable final class PlayerController {

    // MARK: - Published state

    var state = PlayerUiState()
    var sleepTimerEndAt: Date?
    /// Bumped whenever the queue contents change so SwiftUI views can refresh decisively.
    var queueRevision = 0

    // MARK: - Dependencies

    private let music: MusicRepository
    private let settings: SettingsStore
    private let engine = AudioEngine()

    // MARK: - Internal state

    private var queue: [Track] = []
    private var currentIndex = 0
    private var shuffleOrder: [Int] = []
    private var focusVolume: Float = 1
    private var tickTimer: Timer?

    /// Play requests issued before `connect()` are flushed once the engine is ready.
    private var pendingPlay: (tracks: [Track], index: Int)?
    /// Restored queue position, applied on connect without autoplay.
    private var pendingRestore: (track: Track, positionMs: Int64)?

    // MARK: - Init

    init(music: MusicRepository, settings: SettingsStore) {
        self.music = music
        self.settings = settings
        wireEngine()
        restoreSavedQueueState()
        restoreSleepTimer()
    }

    // MARK: - Lifecycle

    func connect() {
        guard !state.connected else { return }
        engine.prepare()
        applyAudioSettings()
        engine.setMasterVolume(focusVolume)
        state.connected = true
        startTicker()

        if let pending = pendingPlay {
            pendingPlay = nil
            playTracksInternal(pending.tracks, pending.index)
        } else if let restore = pendingRestore {
            pendingRestore = nil
            if let url = restore.track.resolvedURL {
                state.isBuffering = true
                engine.load(url: url, uri: restore.track.uri,
                            durationMs: restore.track.durationMs,
                            startMs: restore.positionMs, autoPlay: false)
            }
        }
    }

    func disconnect() {
        persistQueue()
        stopTicker()
        engine.teardown()
        state.connected = false
        state.isPlaying = false
    }

    /// Pulls the latest clock/session-derived values from the engine and re-checks the sleep timer.
    func tick() {
        let position = engine.currentPositionMs()
        let duration = engine.trackDurationMs()
        state.positionMs = position
        if duration > 0 { state.durationMs = duration }
        state.isPlaying = engine.isPlaying()
        state.isBuffering = false
        if state.isPlaying { state.lastError = nil }
        maybeFireSleepTimer()
    }

    // MARK: - Transport

    func play() {
        guard state.connected else { connect(); return }
        guard !queue.isEmpty else { return }
        engine.play()
        state.isPlaying = true
    }

    func pause() {
        guard state.connected else { return }
        engine.pause()
        state.isPlaying = false
        persistQueue()
    }

    func togglePlayPause() {
        guard state.connected else { connect(); return }
        if state.isPlaying { pause() } else { play() }
    }

    func skipNext() {
        guard !queue.isEmpty else { return }
        guard let next = nextIndex(after: currentIndex, wrap: state.repeatMode == .all) else {
            // End of queue with repeat off: stop cleanly instead of dead-stopping mid-file.
            engine.stop()
            state.isPlaying = false
            persistQueue()
            return
        }
        currentIndex = next
        state.lastError = nil
        loadCurrent(autoPlay: true)
        persistQueue()
    }

    func skipPrevious() {
        guard !queue.isEmpty else { return }
        if state.positionMs > 3000 {
            seek(to: 0)   // >3 s into a track: previous means "restart this one".
            return
        }
        guard let previous = previousIndex(before: currentIndex) else {
            seek(to: 0)
            return
        }
        currentIndex = previous
        loadCurrent(autoPlay: true)
        persistQueue()
    }

    func seek(to ms: Int64) {
        let ceiling = state.durationMs > 0 ? state.durationMs - 1 : ms
        let clamped = min(max(0, ms), max(0, ceiling))
        state.positionMs = clamped
        engine.seek(to: clamped)
    }

    func toggleShuffle() {
        state.shuffleEnabled.toggle()
        rebuildShuffleOrder()
        queueRevision += 1
    }

    func cycleRepeatMode() {
        state.repeatMode = state.repeatMode.next
    }

    func setPlaybackSpeed(_ speed: Float) {
        let clamped = min(2.0, max(0.5, speed))
        state.playbackSpeed = clamped
        settings.playbackSpeed = clamped
        engine.setPlaybackSpeed(clamped)
    }

    /// Pomodoro focus duck. Clamped to 0...1 like Android's `focus_volume`.
    func setFocusVolume(_ volume: Float) {
        focusVolume = min(1, max(0, volume))
        engine.setMasterVolume(focusVolume)
    }

    // MARK: - Queue

    func playTracks(_ tracks: [Track], startAt index: Int = 0) {
        guard !tracks.isEmpty else { return }
        if !state.connected {
            pendingPlay = (tracks, max(0, index))
            connect()
            return
        }
        playTracksInternal(tracks, index)
    }

    func addToQueue(_ tracks: [Track]) {
        guard !tracks.isEmpty else { return }
        let wasEmpty = queue.isEmpty
        queue.append(contentsOf: tracks)
        if wasEmpty {
            playTracksInternal(tracks, 0)
            return
        }
        rebuildShuffleOrder()
        queueRevision += 1
        persistQueue()
    }

    func playNext(_ tracks: [Track]) {
        guard !tracks.isEmpty else { return }
        if queue.isEmpty {
            playTracks(tracks, startAt: 0)
            return
        }
        let insertion = min(currentIndex + 1, queue.count)
        queue.insert(contentsOf: tracks, at: insertion)
        rebuildShuffleOrder()
        queueRevision += 1
        persistQueue()
    }

    func removeQueueItem(at index: Int) {
        guard queue.indices.contains(index) else { return }
        let wasCurrent = index == currentIndex
        let wasPlaying = state.isPlaying
        queue.remove(at: index)
        if queue.isEmpty {
            clearQueue()
            return
        }
        if index < currentIndex {
            currentIndex -= 1
        } else if wasCurrent {
            currentIndex = min(currentIndex, queue.count - 1)
            loadCurrent(autoPlay: wasPlaying)
        }
        rebuildShuffleOrder()
        updateNowPlayingMetadata()
        persistQueue()
        queueRevision += 1
    }

    func moveQueueItem(from: Int, to: Int) {
        guard queue.indices.contains(from), queue.indices.contains(to), from != to else { return }
        let track = queue.remove(at: from)
        queue.insert(track, at: to)
        if from == currentIndex {
            currentIndex = to
        } else if from < currentIndex && to >= currentIndex {
            currentIndex -= 1
        } else if from > currentIndex && to <= currentIndex {
            currentIndex += 1
        }
        rebuildShuffleOrder()
        persistQueue()
        queueRevision += 1
    }

    func clearQueue() {
        queue = []
        currentIndex = 0
        shuffleOrder = []
        engine.stop()
        state.hasQueue = false
        state.currentTrackURI = nil
        state.title = ""
        state.artist = ""
        state.album = ""
        state.artworkSeed = nil
        state.durationMs = 0
        state.positionMs = 0
        state.bufferedMs = 0
        state.isPlaying = false
        settings.clearSavedQueue()
        queueRevision += 1
    }

    func queueTracks() -> [Track] { queue }

    // MARK: - Favorites

    func toggleFavoriteCurrent() {
        guard queue.indices.contains(currentIndex) else { return }
        let track = queue[currentIndex]
        music.setFavorite(track, !track.isFavorite)
        music.save()
        queueRevision += 1
    }

    func isFavoriteCurrent() -> Bool {
        guard queue.indices.contains(currentIndex) else { return false }
        return queue[currentIndex].isFavorite
    }

    // MARK: - Sleep timer

    func startSleepTimer(minutes: Int) {
        let end = Date().addingTimeInterval(Double(max(0, minutes)) * 60)
        sleepTimerEndAt = end
        settings.sleepTimerEndAt = end
    }

    func clearSleepTimer() {
        sleepTimerEndAt = nil
        settings.sleepTimerEndAt = nil
    }

    private func maybeFireSleepTimer() {
        guard let end = sleepTimerEndAt, Date() >= end else { return }
        pause()
        sleepTimerEndAt = nil
        settings.sleepTimerEndAt = nil
    }

    private func restoreSleepTimer() {
        guard let saved = settings.sleepTimerEndAt else { return }
        if saved > Date() {
            sleepTimerEndAt = saved
        } else {
            settings.sleepTimerEndAt = nil
        }
    }

    // MARK: - Audio settings

    /// Reads all relevant settings, computes the effective curve with Agent 2's AudioDsp and
    /// hands the final 10-band result to the engine. BassBoost/LoudnessEnhancer are approximated
    /// here as low-band gain and preamp gain; Virtualizer has no public iOS equivalent.
    func applyAudioSettings() {
        let s = settings
        let curve = AudioDsp.computeEffectiveCurve(
            profileId: s.headphoneProfileId,
            userBandsMb: s.eqBands,
            preampDb: s.preampDb,
            soundCheckWarmth: s.soundCheckWarmth,
            soundCheckPunch: s.soundCheckPunch,
            autoHeadroom: s.autoHeadroomEnabled
        )
        let active = s.equalizerEnabled
        var bands = active ? curve.totalBandsDb10 : [Float](repeating: 0, count: 10)
        if active && s.bassBoostEnabled, bands.count >= 3 {
            let boost = Float(s.bassBoostStrength) / 10.0    // 0...100 -> 0...10 dB
            bands[0] += boost
            bands[1] += boost * 0.7
            bands[2] += boost * 0.4
        }
        // Android keeps preamp/balance active even with the graphic EQ off when the user has
        // moved those controls, so mirror that rather than hard-zeroing them.
        var preamp = curve.effectivePreampDb
        if !active && abs(s.preampDb) <= 0.1 && abs(s.channelBalance) <= 0.01 { preamp = 0 }
        if active && s.normalizationEnabled {
            preamp += Float(s.loudnessGainMb) / 100.0        // mB -> dB
        }

        engine.applyEQ(totalBandsDb10: bands, preampDb: preamp,
                       balance: s.channelBalance, limiter: s.limiterEnabled)
        engine.setCrossfade(enabled: s.crossfadeEnabled, seconds: s.crossfadeSeconds)
        engine.setSleepTimerEndOfTrack(s.sleepTimerEndOfTrack)
        let clampedSpeed = min(2.0, max(0.5, s.playbackSpeed))
        engine.setPlaybackSpeed(clampedSpeed)
        state.playbackSpeed = clampedSpeed
    }

    // MARK: - Engine callbacks

    private func wireEngine() {
        engine.onTrackCompleted = { [weak self] uri in self?.handleTrackCompleted(uri) }
        engine.onRequestNext = { [weak self] in self?.makeNextDescriptor() }
        engine.onPlaybackEnded = { [weak self] in self?.handlePlaybackEnded() }
        engine.onPlayingChanged = { [weak self] playing in
            guard let self else { return }
            self.state.isPlaying = playing
            if playing {
                self.state.lastError = nil
                self.state.isBuffering = false
            }
        }
        engine.onError = { [weak self] message in
            guard let self else { return }
            self.state.lastError = message
            self.state.isBuffering = false
            self.state.isPlaying = false
        }
        engine.onSleepEndOfTrackFired = { [weak self] in
            self?.settings.sleepTimerEndOfTrack = false
        }
    }

    /// Natural completion credit goes to the OUTGOING track, then the queue advances without a
    /// second load (the engine already crossfaded/gaplessly promoted the pre-rolled successor).
    private func handleTrackCompleted(_ uri: String) {
        music.recordPlay(uri)
        if state.repeatMode != .one,
           let next = nextIndex(after: currentIndex, wrap: state.repeatMode == .all) {
            currentIndex = next
        }
        updateNowPlayingMetadata()
        persistQueue()
        queueRevision += 1
    }

    private func handlePlaybackEnded() {
        state.isPlaying = false
        persistQueue()
    }

    private func makeNextDescriptor() -> AudioEngine.NextDescriptor? {
        guard !queue.isEmpty, queue.indices.contains(currentIndex) else { return nil }
        let track: Track
        if state.repeatMode == .one {
            track = queue[currentIndex]
        } else {
            guard let next = nextIndex(after: currentIndex, wrap: state.repeatMode == .all) else {
                return nil
            }
            track = queue[next]
        }
        return descriptor(for: track)
    }

    private func descriptor(for track: Track) -> AudioEngine.NextDescriptor? {
        guard let url = track.resolvedURL else { return nil }
        return AudioEngine.NextDescriptor(url: url, uri: track.uri, durationMs: track.durationMs)
    }

    // MARK: - Queue mechanics

    private func playTracksInternal(_ tracks: [Track], _ index: Int) {
        queue = tracks
        currentIndex = min(max(0, index), tracks.count - 1)
        rebuildShuffleOrder()
        state.lastError = nil
        loadCurrent(autoPlay: true)
        persistQueue()
        queueRevision += 1
    }

    private func loadCurrent(autoPlay: Bool, startMs: Int64 = 0) {
        guard queue.indices.contains(currentIndex) else { return }
        let track = queue[currentIndex]
        guard let url = track.resolvedURL else {
            state.lastError = "Could not open \(track.displayTitle)"
            state.isPlaying = false
            state.isBuffering = false
            return
        }
        state.isBuffering = true
        state.positionMs = startMs
        engine.load(url: url, uri: track.uri, durationMs: track.durationMs,
                    startMs: startMs, autoPlay: autoPlay)
        updateNowPlayingMetadata()
    }

    private func updateNowPlayingMetadata() {
        guard queue.indices.contains(currentIndex) else {
            state.currentTrackURI = nil
            state.title = ""
            state.artist = ""
            state.album = ""
            state.artworkSeed = nil
            state.durationMs = 0
            state.hasQueue = false
            return
        }
        let track = queue[currentIndex]
        state.currentTrackURI = track.uri
        state.title = track.displayTitle
        state.artist = track.displayArtist
        state.album = track.displayAlbum
        state.artworkSeed = CoverArtKit.seed(for: track)
        state.durationMs = track.durationMs
        state.bufferedMs = track.durationMs
        state.hasQueue = !queue.isEmpty
    }

    private func rebuildShuffleOrder(keepCurrentFirst: Bool = true) {
        var order = Array(queue.indices)
        if state.shuffleEnabled {
            order.shuffle()
            if keepCurrentFirst, let position = order.firstIndex(of: currentIndex), position > 0 {
                order.swapAt(0, position)
            }
        }
        shuffleOrder = order
    }

    /// Pure lookahead that does not mutate state — safe for the engine's crossfade pre-roll.
    private func nextIndex(after index: Int, wrap: Bool) -> Int? {
        guard !queue.isEmpty else { return nil }
        let order = state.shuffleEnabled ? shuffleOrder : Array(queue.indices)
        guard let position = order.firstIndex(of: index) else { return order.first }
        let next = position + 1
        if next < order.count { return order[next] }
        return wrap ? order.first : nil
    }

    private func previousIndex(before index: Int) -> Int? {
        guard !queue.isEmpty else { return nil }
        let order = state.shuffleEnabled ? shuffleOrder : Array(queue.indices)
        guard let position = order.firstIndex(of: index), position > 0 else { return nil }
        return order[position - 1]
    }

    // MARK: - Persistence

    private func persistQueue() {
        guard !queue.isEmpty else { return }
        settings.saveQueue(
            ids: queue.map { $0.uri },
            index: min(max(0, currentIndex), queue.count - 1),
            positionMs: max(0, state.positionMs),
            hint: state.title.isEmpty ? nil : state.title
        )
    }

    /// Restore never autoplays, and clamps the saved position into the restored track so a
    /// stale end-of-track position cannot produce a dead 00:00 restore (mirrors Android).
    private func restoreSavedQueueState() {
        guard let saved = settings.savedQueue, !saved.ids.isEmpty else { return }
        let tracks = saved.ids.compactMap { music.track(uri: $0) }
        guard !tracks.isEmpty else {
            settings.clearSavedQueue()
            return
        }
        queue = tracks
        currentIndex = min(max(0, saved.index), tracks.count - 1)
        rebuildShuffleOrder()
        let duration = tracks[currentIndex].durationMs
        let safePosition: Int64 = duration > 0
            ? min(max(0, saved.positionMs), max(0, duration - 500))
            : 0
        pendingRestore = (tracks[currentIndex], safePosition)
        updateNowPlayingMetadata()
        state.positionMs = safePosition
        state.durationMs = duration
    }

    // MARK: - Ticker

    private func startTicker() {
        stopTicker()
        tickTimer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { [weak self] _ in
            Task { @MainActor in self?.tick() }
        }
    }

    private func stopTicker() {
        tickTimer?.invalidate()
        tickTimer = nil
    }
}
