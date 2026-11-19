import Foundation
import SwiftData

// Atlas iOS — Local-only listening history entry (mirrors Android PlayEventEntity).

@Model
final class PlayEvent {
    var trackURI: String
    var playedAt: Date

    init(trackURI: String, playedAt: Date = Date()) {
        self.trackURI = trackURI
        self.playedAt = playedAt
    }
}
