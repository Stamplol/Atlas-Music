import Foundation
import SwiftData

/// One row per audio file in the library. Mirrors Android `TrackEntity`.
/// `uri` is the stable identity (a `photos://` / `file://` / imported-bookmark identifier on iOS).
@Model
final class Track {
    @Attribute(.unique) var uri: String
    var filePath: String
    var displayName: String

    var title: String
    var artist: String
    var album: String
    var albumArtist: String
    var genre: String
    var year: Int
    var trackNumber: Int
    var discNumber: Int

    var durationMs: Int64
    var sizeBytes: Int64
    var dateAddedSec: Int64
    var dateModifiedSec: Int64
    var folder: String
    var albumId: Int64
    var fingerprint: String
    var mimeType: String

    var isFavorite: Bool
    var userEdited: Bool

    /// Binary bookmark for files the user granted access to through the Files app.
    var securityBookmark: Data?

    init(
        uri: String,
        filePath: String = "",
        displayName: String = "",
        title: String = "Unknown title",
        artist: String = "Unknown artist",
        album: String = "Unknown album",
        albumArtist: String = "",
        genre: String = "Unknown genre",
        year: Int = 0,
        trackNumber: Int = 0,
        discNumber: Int = 1,
        durationMs: Int64 = 0,
        sizeBytes: Int64 = 0,
        dateAddedSec: Int64 = 0,
        dateModifiedSec: Int64 = 0,
        folder: String = "unknown",
        albumId: Int64 = 0,
        fingerprint: String = "",
        mimeType: String = "",
        isFavorite: Bool = false,
        userEdited: Bool = false,
        securityBookmark: Data? = nil
    ) {
        self.uri = uri
        self.filePath = filePath
        self.displayName = displayName
        self.title = title
        self.artist = artist
        self.album = album
        self.albumArtist = albumArtist
        self.genre = genre
        self.year = year
        self.trackNumber = trackNumber
        self.discNumber = discNumber
        self.durationMs = durationMs
        self.sizeBytes = sizeBytes
        self.dateAddedSec = dateAddedSec
        self.dateModifiedSec = dateModifiedSec
        self.folder = folder
        self.albumId = albumId
        self.fingerprint = fingerprint
        self.mimeType = mimeType
        self.isFavorite = isFavorite
        self.userEdited = userEdited
        self.securityBookmark = securityBookmark
    }

    var resolvedURL: URL? {
        if let bookmark = securityBookmark {
            var stale = false
            if let url = try? URL(resolvingBookmarkData: bookmark, options: [], relativeTo: nil, bookmarkDataIsStale: &stale) {
                if url.startAccessingSecurityScopedResource() { return url }
                return url
            }
        }
        if filePath.isEmpty { return nil }
        return URL(fileURLWithPath: filePath)
    }

    var displayTitle: String { title.isEmpty ? "Unknown title" : title }
    var displayArtist: String { artist.isEmpty ? "Unknown artist" : artist }
    var displayAlbum: String { album.isEmpty ? "Unknown album" : album }
}

extension Track {
    static func makeFingerprint(title: String, artist: String, durationMs: Int64) -> String {
        "\(DuplicateNormalizer.normalize(title))|\(DuplicateNormalizer.normalize(artist))|\(Int((Double(durationMs) / 2000.0).rounded()))"
    }
}
