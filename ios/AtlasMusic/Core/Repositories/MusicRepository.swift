import Foundation
import SwiftData
import AVFoundation
import Observation

// Atlas iOS — SwiftData-backed library queries, play history, tag edits and search.

struct NameCount: Identifiable {
    let name: String
    let count: Int
    var id: String { name }
}

struct SearchResults {
    var tracks: [Track] = []
    var albums: [String] = []
    var artists: [String] = []
    var genres: [String] = []
}

@MainActor
@Observable
final class MusicRepository {
    /// Shared with `LibraryScanner` so both operate on one SwiftData context.
    let context: ModelContext

    init(context: ModelContext) {
        self.context = context
    }

    // MARK: - Basic fetches

    private func fetchAll() -> [Track] {
        (try? context.fetch(FetchDescriptor<Track>())) ?? []
    }

    private func sortedByTitle(_ tracks: [Track]) -> [Track] {
        tracks.sorted { $0.title.lowercased() < $1.title.lowercased() }
    }

    func allTracks(sort: SortOption) -> [Track] {
        let tracks = fetchAll()
        switch sort {
        case .title:
            return sortedByTitle(tracks)
        case .artist:
            return tracks.sorted { a, b in
                let la = a.artist.lowercased(), lb = b.artist.lowercased()
                if la != lb { return la < lb }
                let aa = a.album.lowercased(), ab = b.album.lowercased()
                if aa != ab { return aa < ab }
                return a.trackNumber < b.trackNumber
            }
        case .album:
            return tracks.sorted { a, b in
                let aa = a.album.lowercased(), ab = b.album.lowercased()
                if aa != ab { return aa < ab }
                if a.discNumber != b.discNumber { return a.discNumber < b.discNumber }
                return a.trackNumber < b.trackNumber
            }
        case .duration:
            return tracks.sorted { $0.durationMs < $1.durationMs }
        case .dateAdded:
            return tracks.sorted { $0.dateAddedSec > $1.dateAddedSec }
        case .year:
            return tracks.sorted { $0.year > $1.year }
        case .trackNumber:
            return tracks.sorted { $0.trackNumber < $1.trackNumber }
        }
    }

    func favorites() -> [Track] {
        let predicate = #Predicate<Track> { $0.isFavorite }
        let matches = (try? context.fetch(FetchDescriptor<Track>(predicate: predicate))) ?? []
        return sortedByTitle(matches)
    }

    private func allPlayEvents() -> [PlayEvent] {
        (try? context.fetch(FetchDescriptor<PlayEvent>())) ?? []
    }

    func recentlyPlayed(limit: Int) -> [Track] {
        guard limit > 0 else { return [] }
        let byURI = Dictionary(fetchAll().map { ($0.uri, $0) }, uniquingKeysWith: { first, _ in first })
        var seen = Set<String>()
        var result: [Track] = []
        for event in allPlayEvents().sorted(by: { $0.playedAt > $1.playedAt }) {
            guard !seen.contains(event.trackURI), let track = byURI[event.trackURI] else { continue }
            seen.insert(event.trackURI)
            result.append(track)
            if result.count >= limit { break }
        }
        return result
    }

    func recentlyAdded(limit: Int) -> [Track] {
        guard limit > 0 else { return [] }
        return Array(fetchAll().sorted { a, b in
            if a.dateAddedSec != b.dateAddedSec { return a.dateAddedSec > b.dateAddedSec }
            return a.uri > b.uri
        }.prefix(limit))
    }

    func mostPlayed(limit: Int) -> [Track] {
        guard limit > 0 else { return [] }
        var counts: [String: Int] = [:]
        for event in allPlayEvents() { counts[event.trackURI, default: 0] += 1 }
        let byURI = Dictionary(fetchAll().map { ($0.uri, $0) }, uniquingKeysWith: { first, _ in first })
        return counts
            .sorted { a, b in a.value == b.value ? a.key < b.key : a.value > b.value }
            .compactMap { byURI[$0.key] }
            .prefix(limit)
            .map { $0 }
    }

    // MARK: - Collections

    private func counts(of field: (Track) -> String, excluding: Set<String> = [""]) -> [NameCount] {
        var counts: [String: Int] = [:]
        for track in fetchAll() {
            let value = field(track)
            guard !excluding.contains(value) else { continue }
            counts[value, default: 0] += 1
        }
        return counts
            .map { NameCount(name: $0.key, count: $0.value) }
            .sorted { $0.name.lowercased() < $1.name.lowercased() }
    }

    func albums() -> [NameCount] { counts(of: { $0.album }) }
    func artists() -> [NameCount] { counts(of: { $0.artist }) }
    func genres() -> [NameCount] { counts(of: { $0.genre }) }

    func folderCounts() -> [NameCount] {
        let all = counts(of: { $0.folder })
        return all.sorted { a, b in a.count == b.count ? a.name < b.name : a.count > b.count }
    }

    func tracks(album: String) -> [Track] {
        fetchAll()
            .filter { $0.album == album }
            .sorted { a, b in
                if a.discNumber != b.discNumber { return a.discNumber < b.discNumber }
                if a.trackNumber != b.trackNumber { return a.trackNumber < b.trackNumber }
                return a.title.lowercased() < b.title.lowercased()
            }
    }

    func tracks(artist: String) -> [Track] {
        fetchAll()
            .filter { $0.artist == artist }
            .sorted { a, b in
                let aa = a.album.lowercased(), ab = b.album.lowercased()
                if aa != ab { return aa < ab }
                if a.discNumber != b.discNumber { return a.discNumber < b.discNumber }
                return a.trackNumber < b.trackNumber
            }
    }

    func tracks(genre: String) -> [Track] {
        fetchAll().filter { $0.genre == genre }.sorted { $0.title.lowercased() < $1.title.lowercased() }
    }

    func tracks(folder: String) -> [Track] {
        fetchAll().filter { $0.folder == folder }.sorted { $0.title.lowercased() < $1.title.lowercased() }
    }

    func track(uri: String) -> Track? {
        let predicate = #Predicate<Track> { $0.uri == uri }
        return (try? context.fetch(FetchDescriptor<Track>(predicate: predicate)))?.first
    }

    // MARK: - Search

    func search(_ query: String, limit: Int = 200) -> SearchResults {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, limit > 0 else { return SearchResults() }
        let q = trimmed.lowercased()

        func contains(_ value: String) -> Bool { value.lowercased().contains(q) }
        let matches = fetchAll().filter { track in
            contains(track.title) || contains(track.artist) || contains(track.album)
                || contains(track.genre) || contains(track.folder)
        }

        func rank(_ track: Track) -> Int {
            var score = 0
            let title = track.title.lowercased(), artist = track.artist.lowercased()
            let album = track.album.lowercased()
            if title.hasPrefix(q) { score += 100 }
            if title.contains(q) { score += 50 }
            if artist.hasPrefix(q) { score += 40 }
            if artist.contains(q) { score += 20 }
            if album.contains(q) { score += 10 }
            return score
        }

        let ranked = matches.sorted { a, b in
            let ra = rank(a), rb = rank(b)
            return ra == rb ? a.title.lowercased() < b.title.lowercased() : ra > rb
        }
        let albums = matches.map(\.album).filter { !$0.isEmpty && $0 != "Unknown album" }.distinct().prefix(20)
        let artists = matches.map(\.artist).filter { !$0.isEmpty && $0 != "Unknown artist" }.distinct().prefix(20)
        let genres = matches.map(\.genre).filter { !$0.isEmpty && $0 != "Unknown genre" }.distinct().prefix(10)

        return SearchResults(
            tracks: Array(ranked.prefix(limit)),
            albums: Array(albums),
            artists: Array(artists),
            genres: Array(genres)
        )
    }

    // MARK: - Mutations

    func recordPlay(_ trackURI: String) {
        context.insert(PlayEvent(trackURI: trackURI))
        save()
    }

    func setFavorite(_ track: Track, _ favorite: Bool) {
        track.isFavorite = favorite
        save()
    }

    /// Edits the library metadata and recomputes the duplicate fingerprint. iOS cannot rewrite
    /// embedded file tags with public AVFoundation APIs, so the file itself is left untouched.
    func updateTags(
        _ track: Track,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        discNumber: Int
    ) -> TagUpdateResult.Outcome {
        track.title = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            ? track.title
            : title.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanArtist = artist.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanAlbum = album.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanGenre = genre.trimmingCharacters(in: .whitespacesAndNewlines)
        track.artist = cleanArtist.isEmpty ? "Unknown artist" : cleanArtist
        track.album = cleanAlbum.isEmpty ? "Unknown album" : cleanAlbum
        track.albumArtist = cleanArtist.isEmpty ? "Unknown artist" : cleanArtist
        track.genre = cleanGenre.isEmpty ? "Unknown genre" : cleanGenre
        track.year = min(max(year, 0), 2100)
        track.trackNumber = min(max(trackNumber, 0), 999)
        track.discNumber = min(max(discNumber, 1), 9)
        track.userEdited = true
        track.fingerprint = Track.makeFingerprint(title: track.title, artist: track.artist, durationMs: track.durationMs)
        save()
        return TagUpdateResult.Outcome(
            dbOk: true,
            fileOk: false,
            fileError: "File tags are read-only on iOS — saved to the Atlas library only"
        )
    }

    /// Re-read tags from the source file via AVFoundation and clears the user-edited flag.
    func resetTags(_ track: Track) {
        guard let url = track.resolvedURL else { return }
        let asset = AVAsset(url: url)

        var title = track.title
        var artist = track.artist
        var album = track.album
        var genre = track.genre
        var year = 0
        var trackNumber = 0

        for item in asset.commonMetadata {
            guard let value = try? item.stringValue, !value.isEmpty else { continue }
            let id = item.identifier?.rawValue.lowercased() ?? ""
            if id.contains("title") {
                title = value
            } else if id.contains("artist") {
                artist = value
            } else if id.contains("album") {
                album = value
            } else if id.contains("genre") {
                genre = value
            } else if id.contains("year") || id.contains("date") {
                year = Int(value.prefix(4)) ?? 0
            } else if id.contains("track") {
                trackNumber = Int(value.split(separator: "/").first ?? "") ?? 0
            }
        }

        track.title = title
        track.artist = artist
        track.album = album
        track.albumArtist = artist
        track.genre = genre
        track.year = min(max(year, 0), 2100)
        track.trackNumber = min(max(trackNumber, 0), 999)
        track.discNumber = 1
        track.userEdited = false
        track.fingerprint = Track.makeFingerprint(title: title, artist: artist, durationMs: track.durationMs)
        save()
    }

    /// Backfill metadata on a freshly downloaded file when the scanner found no tags.
    func applyDownloadMetadata(
        uri: String,
        title: String,
        artist: String,
        genre: String,
        year: Int,
        durationMs: Int64
    ) {
        guard let current = track(uri: uri) else { return }
        let resolvedTitle: String
        if !current.title.isEmpty && current.title != "Unknown title" {
            resolvedTitle = current.title
        } else {
            resolvedTitle = title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                ? current.title
                : title.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        let cleanArtist = artist.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedArtist = (current.artist == "Unknown artist" && !cleanArtist.isEmpty && cleanArtist != "Unknown uploader")
            ? cleanArtist
            : current.artist
        let cleanGenre = genre.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedGenre = (current.genre == "Unknown genre" && !cleanGenre.isEmpty) ? cleanGenre : current.genre
        let resolvedYear = (current.year == 0 && (1900...2100).contains(year)) ? year : current.year
        let resolvedDuration = (current.durationMs <= 0 && durationMs > 0) ? durationMs : current.durationMs

        current.title = resolvedTitle
        current.artist = resolvedArtist
        current.albumArtist = resolvedArtist
        current.genre = resolvedGenre
        current.year = resolvedYear
        current.durationMs = resolvedDuration
        current.fingerprint = Track.makeFingerprint(title: resolvedTitle, artist: resolvedArtist, durationMs: resolvedDuration)
        save()
    }

    /// Removes the track and every playlist membership / play event that references it.
    func delete(_ track: Track) {
        let uri = track.uri
        let entries = (try? context.fetch(FetchDescriptor<PlaylistEntry>())) ?? []
        for entry in entries where entry.track?.uri == uri {
            if let playlist = entry.playlist,
               let index = playlist.entries.firstIndex(where: { $0 === entry }) {
                playlist.entries.remove(at: index)
            }
            context.delete(entry)
        }
        let events = (try? context.fetch(FetchDescriptor<PlayEvent>())) ?? []
        for event in events where event.trackURI == uri { context.delete(event) }
        context.delete(track)
        save()
    }

    // MARK: - Duplicates / stats

    func findDuplicates() -> [DuplicateGroup] {
        DuplicateDetector.findDuplicates(fetchAll())
    }

    func unknownCount() -> Int {
        fetchAll().filter { $0.artist == "Unknown artist" || $0.album == "Unknown album" }.count
    }

    func trackCount() -> Int {
        (try? context.fetchCount(FetchDescriptor<Track>())) ?? 0
    }

    func totalDurationMs() -> Int64 {
        fetchAll().reduce(Int64(0)) { $0 + $1.durationMs }
    }

    func totalSizeBytes() -> Int64 {
        fetchAll().reduce(Int64(0)) { $0 + $1.sizeBytes }
    }

    func save() {
        try? context.save()
    }
}

private extension Array where Element: Hashable {
    func distinct() -> [Element] {
        var seen = Set<Element>()
        return filter { seen.insert($0).inserted }
    }
}
