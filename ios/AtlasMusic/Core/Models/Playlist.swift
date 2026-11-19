import Foundation
import SwiftData

// Atlas iOS — User playlists and their ordered track membership (mirrors Android PlaylistEntity / PlaylistTrackEntity).

/// A user-created playlist. Entries are cascade-deleted with the playlist.
@Model
final class Playlist {
    var name: String
    var desc: String
    var createdAt: Date

    @Relationship(deleteRule: .cascade, inverse: \PlaylistEntry.playlist)
    var entries: [PlaylistEntry]

    init(name: String, description: String = "") {
        self.name = name
        self.desc = description
        self.createdAt = Date()
        self.entries = []
    }
}

/// One ordered membership row inside a playlist.
@Model
final class PlaylistEntry {
    var position: Int
    var addedAt: Date
    var track: Track?
    var playlist: Playlist?

    init(position: Int = 0, addedAt: Date = Date(), track: Track? = nil, playlist: Playlist? = nil) {
        self.position = position
        self.addedAt = addedAt
        self.track = track
        self.playlist = playlist
    }
}
