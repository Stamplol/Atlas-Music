import Foundation

// Atlas iOS — Immutable snapshot of player state published to the UI by PlayerController.

/// Mirrors Android `PlayerUiState`. Published on the main actor via `PlayerController.state`.
/// Track identity is `currentTrackURI`; artwork is rendered procedurally from `artworkSeed`.
struct PlayerUiState {
    var connected = false
    var isPlaying = false
    var isBuffering = false

    var currentTrackURI: String?
    var title = ""
    var artist = ""
    var album = ""
    var artworkSeed: Int?

    var durationMs: Int64 = 0
    var positionMs: Int64 = 0
    var bufferedMs: Int64 = 0

    var repeatMode: RepeatMode = .off
    var shuffleEnabled = false
    var playbackSpeed: Float = 1

    var hasQueue = false
    var lastError: String?
}
