import Foundation
import SwiftData
import AVFoundation
import CoreMedia

// Atlas iOS — File-system library scan that replaces Android's MediaStore / SAF traversal.

@MainActor
final class LibraryScanner {
    struct ScanResult {
        var added: Int = 0
        var updated: Int = 0
        var removed: Int = 0
        var skippedExcluded: Int = 0
        var durationMs: Int64 = 0
    }

    private let music: MusicRepository
    private let settings: SettingsStore

    init(music: MusicRepository, settings: SettingsStore) {
        self.music = music
        self.settings = settings
    }

    /// Platform difference: Android queries MediaStore (and an optional SAF tree). iOS has no
    /// MediaStore, so this scans the app Documents directory plus an optional security-scoped
    /// folder bookmark picked through the Files app. Metadata comes from AVFoundation instead of
    /// MediaMetadataRetriever. User-edited tags are always preserved, exactly like Android.
    func scan() async -> ScanResult {
        let started = Date()
        var result = ScanResult()

        let custom = resolveCustomFolder()
        var accessedSecurityScope = false
        if let custom, let url = custom.url, url.startAccessingSecurityScopedResource() {
            accessedSecurityScope = true
        }

        var roots: [(url: URL, isCustom: Bool)] = []
        if let custom {
            roots.append((custom.url, true))
            if !settings.musicFolderOnly, let documents = documentsURL() {
                roots.append((documents, false))
            }
        } else if let documents = documentsURL() {
            roots.append((documents, false))
        }

        let context = music.context
        let allTracks = (try? context.fetch(FetchDescriptor<Track>())) ?? []
        var existingByURI = Dictionary(
            allTracks.map { ($0.uri, $0) },
            uniquingKeysWith: { first, _ in first }
        )
        var seenURIs = Set<String>()
        let audioExtensions: Set<String> = ["mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "aiff", "aif"]
        let keys: Set<URLResourceKey> = [.isRegularFileKey, .fileSizeKey, .creationDateKey, .modificationDateKey]

        for root in roots {
            guard let enumerator = FileManager.default.enumerator(
                at: root.url,
                includingPropertiesForKeys: Array(keys),
                options: [.skipsHiddenFiles],
                errorHandler: { _, _ in true }
            ) else { continue }

            for case let fileURL as URL in enumerator {
                guard let values = try? fileURL.resourceValues(forKeys: keys),
                      values.isRegularFile == true else { continue }

                let ext = fileURL.pathExtension.lowercased()
                guard audioExtensions.contains(ext) || settings.libraryIncludesUnsupported else { continue }

                let relativeFolder = folder(for: fileURL, under: root.url)
                let folder = LibraryFolder.normalize(relativeFolder)
                guard !LibraryFolder.isExcluded(folder, settings.excludedFolders),
                      !BuiltInExclusions.isBuiltInExcluded(folder) else {
                    result.skippedExcluded += 1
                    continue
                }

                let uri = fileURL.absoluteString
                let sizeBytes = Int64(values.fileSize ?? 0)
                let dateModified = Int64(values.modificationDate?.timeIntervalSince1970 ?? 0)
                let dateAdded = Int64(values.creationDate?.timeIntervalSince1970 ?? Double(dateModified))

                let previous = existingByURI[uri]
                if let previous,
                   previous.dateModifiedSec == dateModified,
                   previous.sizeBytes == sizeBytes {
                    seenURIs.insert(uri)
                    continue
                }

                let meta = metadata(for: fileURL, fallbackTitle: fileURL.deletingPathExtension().lastPathComponent)
                let keepEdited = previous?.userEdited == true
                let fingerprint = Track.makeFingerprint(title: meta.title, artist: meta.artist, durationMs: meta.durationMs)

                if let previous {
                    previous.durationMs = meta.durationMs
                    previous.sizeBytes = sizeBytes
                    previous.dateAddedSec = dateAdded
                    previous.dateModifiedSec = dateModified
                    previous.folder = folder
                    previous.mimeType = mimeType(for: ext)
                    if !keepEdited {
                        previous.title = meta.title
                        previous.artist = meta.artist
                        previous.album = meta.album
                        previous.albumArtist = meta.artist
                        previous.genre = meta.genre
                        previous.year = meta.year
                        previous.trackNumber = min(max(meta.trackNumber, 0), 999)
                        previous.discNumber = 1
                        previous.fingerprint = fingerprint
                    }
                    if root.isCustom { previous.securityBookmark = custom?.bookmark }
                    result.updated += 1
                } else {
                    let track = Track(
                        uri: uri,
                        filePath: fileURL.path,
                        displayName: fileURL.lastPathComponent,
                        title: meta.title,
                        artist: meta.artist,
                        album: meta.album,
                        albumArtist: meta.artist,
                        genre: meta.genre,
                        year: meta.year,
                        trackNumber: min(max(meta.trackNumber, 0), 999),
                        discNumber: 1,
                        durationMs: meta.durationMs,
                        sizeBytes: sizeBytes,
                        dateAddedSec: dateAdded,
                        dateModifiedSec: dateModified,
                        folder: folder,
                        albumId: 0,
                        fingerprint: fingerprint,
                        mimeType: mimeType(for: ext),
                        isFavorite: false,
                        userEdited: false,
                        securityBookmark: root.isCustom ? custom?.bookmark : nil
                    )
                    context.insert(track)
                    existingByURI[uri] = track
                    result.added += 1
                }
                seenURIs.insert(uri)
            }
        }

        // Android skips removals when the scan was scoped to a custom folder (a lost permission
        // must never wipe the library). Keep that guard and only prune files under scanned roots.
        if !(custom != nil && settings.musicFolderOnly) {
            let scannedPrefixes = roots.map { $0.url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/" }
            for track in allTracks where !seenURIs.contains(track.uri) {
                let path = track.filePath.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
                guard scannedPrefixes.contains(where: { path.hasPrefix($0) }) else { continue }
                music.delete(track)
                result.removed += 1
            }
        }

        if let custom, accessedSecurityScope { custom.url.stopAccessingSecurityScopedResource() }

        let elapsed = Int64(Date().timeIntervalSince(started) * 1000)
        result.durationMs = elapsed
        settings.lastScanMs = Int64(Date().timeIntervalSince1970 * 1000)
        music.save()
        return result
    }

    // MARK: - Helpers

    private func documentsURL() -> URL? {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
    }

    private func resolveCustomFolder() -> (url: URL, bookmark: Data?)? {
        let raw = settings.musicFolderURI.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !raw.isEmpty else { return nil }

        if let bookmark = Data(base64Encoded: raw) {
            var stale = false
            if let url = try? URL(
                resolvingBookmarkData: bookmark,
                options: [.withSecurityScope],
                relativeTo: nil,
                bookmarkDataIsStale: &stale
            ) {
                return (url, bookmark)
            }
        }

        let url = URL(fileURLWithPath: raw)
        var isDirectory: ObjCBool = false
        if FileManager.default.fileExists(atPath: url.path, isDirectory: &isDirectory), isDirectory.boolValue {
            return (url, nil)
        }
        return nil
    }

    private func folder(for fileURL: URL, under root: URL) -> String {
        let directory = fileURL.deletingLastPathComponent().path
        let rootPath = root.path
        var relative = directory
        if directory == rootPath {
            return root.lastPathComponent
        }
        if directory.hasPrefix(rootPath + "/") {
            relative = String(directory.dropFirst(rootPath.count + 1))
        }
        return relative.isEmpty ? root.lastPathComponent : relative
    }

    private struct FileMetadata {
        var title: String
        var artist: String = "Unknown artist"
        var album: String = "Unknown album"
        var genre: String = "Unknown genre"
        var year: Int = 0
        var trackNumber: Int = 0
        var durationMs: Int64 = 0
    }

    private func metadata(for url: URL, fallbackTitle: String) -> FileMetadata {
        let asset = AVAsset(url: url)
        let seconds = CMTimeGetSeconds(asset.duration)
        var result = FileMetadata(
            title: fallbackTitle.isEmpty ? "Unknown title" : fallbackTitle,
            durationMs: seconds.isFinite && seconds > 0 ? Int64((seconds * 1000).rounded()) : 0
        )
        for item in asset.commonMetadata {
            guard let value = try? item.stringValue, !value.isEmpty else { continue }
            let id = item.identifier.rawValue.lowercased()
            if id.contains("title") {
                result.title = value
            } else if id.contains("artist") {
                result.artist = value
            } else if id.contains("album") {
                result.album = value
            } else if id.contains("genre") {
                result.genre = value
            } else if id.contains("year") || id.contains("date") {
                result.year = Int(value.prefix(4)) ?? 0
            } else if id.contains("track") {
                result.trackNumber = Int(value.split(separator: "/").first ?? "") ?? 0
            }
        }
        return result
    }

    private func mimeType(for ext: String) -> String {
        switch ext.lowercased() {
        case "mp3": return "audio/mpeg"
        case "m4a", "mp4": return "audio/mp4"
        case "flac": return "audio/flac"
        case "wav": return "audio/wav"
        case "aac": return "audio/aac"
        case "ogg", "oga", "opus": return "audio/ogg"
        case "aiff", "aif": return "audio/aiff"
        default: return "audio/\(ext)"
        }
    }
}
