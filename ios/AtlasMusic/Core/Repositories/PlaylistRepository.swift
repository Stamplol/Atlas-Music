import Foundation
import SwiftData

// Atlas iOS — Playlist CRUD and ordered membership (mirrors Android PlaylistRepository).

enum PlaylistError: Error {
    case emptyName
    case nameTooLong
}

@MainActor
final class PlaylistRepository {
    private let context: ModelContext

    init(context: ModelContext) {
        self.context = context
    }

    func playlists() -> [Playlist] {
        let descriptor = FetchDescriptor<Playlist>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)])
        return (try? context.fetch(descriptor)) ?? []
    }

    @discardableResult
    func create(name: String, description: String = "") throws -> Playlist {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { throw PlaylistError.emptyName }
        guard trimmed.count <= 80 else { throw PlaylistError.nameTooLong }
        let playlist = Playlist(name: trimmed, description: description.trimmingCharacters(in: .whitespacesAndNewlines))
        context.insert(playlist)
        save()
        return playlist
    }

    func rename(_ p: Playlist, name: String, description: String?) {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        p.name = trimmed
        if let description { p.desc = description.trimmingCharacters(in: .whitespacesAndNewlines) }
        save()
    }

    func delete(_ p: Playlist) {
        context.delete(p)
        save()
    }

    func add(_ track: Track, to p: Playlist) {
        guard !isTrack(track, in: p) else { return }
        let position = p.entries.map(\.position).max().map { $0 + 1 } ?? 0
        let entry = PlaylistEntry(position: position, addedAt: Date(), track: track, playlist: p)
        context.insert(entry)
        p.entries.append(entry)
        save()
    }

    func addTracks(_ tracks: [Track], to p: Playlist) {
        for track in tracks { add(track, to: p) }
    }

    func remove(_ track: Track, from p: Playlist) {
        for entry in p.entries where entry.track?.uri == track.uri {
            if let index = p.entries.firstIndex(where: { $0 === entry }) { p.entries.remove(at: index) }
            context.delete(entry)
        }
        normalizePositions(in: p)
        save()
    }

    func move(_ track: Track, in p: Playlist, to index: Int) {
        let entries = p.entries.sorted { $0.position < $1.position }
        guard let from = entries.firstIndex(where: { $0.track?.uri == track.uri }),
              index >= 0, index < entries.count else { return }
        var reordered = entries
        let moved = reordered.remove(at: from)
        reordered.insert(moved, at: index)
        for (position, entry) in reordered.enumerated() { entry.position = position }
        p.entries = reordered
        save()
    }

    func tracks(in p: Playlist) -> [Track] {
        p.entries.sorted { $0.position < $1.position }.compactMap { $0.track }
    }

    func isTrack(_ track: Track, in p: Playlist) -> Bool {
        p.entries.contains { $0.track?.uri == track.uri }
    }

    @discardableResult
    func saveQueueAsPlaylist(name: String, tracks: [Track]) -> Playlist? {
        let trimmed = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let playlist = try? create(name: trimmed.isEmpty ? "Saved queue" : trimmed)
        guard let playlist else { return nil }
        addTracks(tracks, to: playlist)
        save()
        return playlist
    }

    func save() {
        try? context.save()
    }

    private func normalizePositions(in p: Playlist) {
        for (index, entry) in p.entries.sorted(by: { $0.position < $1.position }).enumerated() {
            entry.position = index
        }
    }
}
