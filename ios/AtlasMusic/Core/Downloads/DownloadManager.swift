// Atlas iOS — download queue with direct-audio URLSession transfer and a pluggable resolver bridge.
import Foundation
import SwiftData
import AVFoundation
import BackgroundTasks

// MARK: - Public value types (contract surface)

struct DownloadPreview: Codable {
    var ok = false
    var title = ""
    var uploader = ""
    var durationSec = 0
    var thumbnailURL = ""
    var isPlaylist = false
    var playlistTitle = ""
    var entries: [String] = []
    var license: String?
    var error: String?
}

struct DownloadSearchResult: Codable {
    var ok = false
    var provider = ""
    var query = ""
    var entries: [DownloadSearchItem] = []
    var error: String?

    enum CodingKeys: String, CodingKey {
        case ok, provider, query, entries, error
    }
}

struct DownloadSearchItem: Identifiable, Codable {
    var id: String
    var title: String
    var uploader: String
    var durationSec: Int
    var thumbnailURL: String
    var url: String

    enum CodingKeys: String, CodingKey {
        case id, title, uploader
        case durationSec = "duration"
        case thumbnailURL = "thumbnail"
        case url
    }
}

/// One playlist row returned by a resolver. The contract flattens these to titles in `DownloadPreview`.
struct DownloadPlaylistEntry: Codable {
    var url: String
    var title: String
    var durationSec: Int = 0
    var thumbnailURL: String = ""

    enum CodingKeys: String, CodingKey {
        case url, title
        case durationSec = "duration"
        case thumbnailURL = "thumbnail"
    }
}

/// Plain values handed to a resolver so no SwiftData model crosses the isolation boundary.
struct DownloadRequest {
    let url: String
    let title: String
    let uploader: String
    let thumbnailURL: String
    let format: String
}

struct ResolvedAudio {
    let url: URL
    let title: String
    let uploader: String
    let durationSec: Int
    let genre: String
    let year: Int
    let thumbnailURL: String
    let license: String?
}

// MARK: - Resolver abstraction

/// iOS has no embedded Python/yt-dlp. Direct audio URLs are handled locally; video-site links
/// are delegated to a server-side bridge that implements this small JSON protocol. Configure the
/// bridge URL in the Downloads screen (persisted in standard UserDefaults) and everything else —
/// state machine, progress, cancellation, license gate, import — stays identical.
protocol DownloadResolver {
    var name: String { get }
    var isConfigured: Bool { get }
    func preview(_ raw: String) async -> DownloadPreview
    func search(_ query: String, provider: String) async -> DownloadSearchResult
    func playlistEntries(_ raw: String) async -> [DownloadPlaylistEntry]
    func resolveAudio(for request: DownloadRequest) async throws -> ResolvedAudio
}

enum DownloadResolverError: LocalizedError {
    case bridgeRequired
    case badRequest(String)

    var errorDescription: String? {
        switch self {
        case .bridgeRequired:
            return "This link needs a server-side resolver. Add a resolver URL in the Downloads screen, or paste a direct audio (https … .mp3/.m4a/.ogg/.opus/.wav/.flac) link."
        case .badRequest(let message):
            return message
        }
    }
}

/// Reads files that are already direct audio. No external service required.
struct DirectAudioResolver: DownloadResolver {
    let name = "Direct audio"
    let isConfigured = true

    private static let audioExtensions: Set<String> = ["mp3", "m4a", "mp4", "aac", "ogg", "oga", "opus", "wav", "flac", "aiff", "aif"]

    static func isDirectAudio(_ raw: String) -> Bool {
        guard let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              url.host != nil else { return false }
        return audioExtensions.contains(url.pathExtension.lowercased())
    }

    func preview(_ raw: String) async -> DownloadPreview {
        var preview = DownloadPreview()
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let url = URL(string: trimmed), Self.isDirectAudio(trimmed) else {
            preview.error = DownloadResolverError.bridgeRequired.localizedDescription
            return preview
        }
        preview.ok = true
        preview.title = url.deletingPathExtension().lastPathComponent
        preview.thumbnailURL = ""
        let asset = AVAsset(url: url)
        if let duration = try? await asset.load(.duration), duration.isValid {
            preview.durationSec = Int(duration.seconds.rounded())
        }
        return preview
    }

    func search(_ query: String, provider: String) async -> DownloadSearchResult {
        DownloadSearchResult(ok: false, provider: provider, query: query,
                             error: "Search needs a server-side resolver. Add a resolver URL in the Downloads screen, or paste a direct audio link.")
    }

    func playlistEntries(_ raw: String) async -> [DownloadPlaylistEntry] { [] }

    func resolveAudio(for request: DownloadRequest) async throws -> ResolvedAudio {
        guard let url = URL(string: request.url), Self.isDirectAudio(request.url) else {
            throw DownloadResolverError.bridgeRequired
        }
        var durationSec = 0
        let asset = AVAsset(url: url)
        if let duration = try? await asset.load(.duration), duration.isValid {
            durationSec = Int(duration.seconds.rounded())
        }
        return ResolvedAudio(url: url,
                             title: request.title.isEmpty ? url.deletingPathExtension().lastPathComponent : request.title,
                             uploader: request.uploader,
                             durationSec: durationSec,
                             genre: "", year: 0,
                             thumbnailURL: request.thumbnailURL,
                             license: nil)
    }
}

/// Optional server-side yt-dlp bridge. Expected JSON endpoints (all https):
///   GET /preview?url=…                    -> DownloadPreview fields
///   GET /search?q=…&provider=…            -> DownloadSearchResult fields
///   GET /playlist?url=…                   -> { ok, entries: [{url,title,duration,thumbnail}] }
///   GET /resolve?url=…&format=…           -> ResolvedAudio fields
struct HTTPDownloadResolver: DownloadResolver {
    let name = "Server resolver"
    var isConfigured: Bool { baseURL != nil }

    private var baseURL: URL? {
        guard let raw = UserDefaults.standard.string(forKey: Self.configurationKey),
              !raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty,
              let url = URL(string: raw.trimmingCharacters(in: .whitespacesAndNewlines)),
              let scheme = url.scheme?.lowercased(), scheme == "https" || scheme == "http",
              url.host != nil else { return nil }
        return url
    }

    static let configurationKey = "atlas.resolver.baseURL"

    private func endpoint(_ path: String, query: [String: String]) -> URL? {
        guard var url = baseURL?.appendingPathComponent(path) else { return nil }
        var components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        components?.queryItems = query.map { URLQueryItem(name: $0.key, value: $0.value) }
        url = components?.url ?? url
        return url
    }

    private func fetchJSON(_ url: URL) async throws -> Data {
        let (data, response) = try await URLSession.shared.data(from: url)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw DownloadResolverError.badRequest("Resolver returned an invalid response.")
        }
        return data
    }

    private struct PreviewResponse: Decodable {
        let ok: Bool
        let title: String?
        let uploader: String?
        let duration: Int?
        let thumbnail: String?
        let isPlaylist: Bool?
        let playlistTitle: String?
        let entries: [String]?
        let license: String?
        let error: String?
        enum CodingKeys: String, CodingKey {
            case ok, title, uploader, duration, thumbnail, entries, license, error
            case isPlaylist = "is_playlist"
            case playlistTitle = "playlist_title"
        }
    }

    func preview(_ raw: String) async -> DownloadPreview {
        guard let url = endpoint("preview", query: ["url": raw]) else {
            var p = DownloadPreview(); p.error = DownloadResolverError.bridgeRequired.localizedDescription; return p
        }
        do {
            let data = try await fetchJSON(url)
            let decoded = try JSONDecoder().decode(PreviewResponse.self, from: data)
            var p = DownloadPreview()
            p.ok = decoded.ok
            p.title = decoded.title ?? ""
            p.uploader = decoded.uploader ?? ""
            p.durationSec = decoded.duration ?? 0
            p.thumbnailURL = decoded.thumbnail ?? ""
            p.isPlaylist = decoded.isPlaylist ?? false
            p.playlistTitle = decoded.playlistTitle ?? ""
            p.entries = decoded.entries ?? []
            p.license = decoded.license
            p.error = decoded.error
            return p
        } catch {
            var p = DownloadPreview(); p.error = error.localizedDescription; return p
        }
    }

    func search(_ query: String, provider: String) async -> DownloadSearchResult {
        guard let url = endpoint("search", query: ["q": query, "provider": provider]) else {
            return DownloadSearchResult(ok: false, provider: provider, query: query,
                                        error: DownloadResolverError.bridgeRequired.localizedDescription)
        }
        do {
            let data = try await fetchJSON(url)
            var result = try JSONDecoder().decode(DownloadSearchResult.self, from: data)
            if result.provider.isEmpty { result.provider = provider }
            if result.query.isEmpty { result.query = query }
            return result
        } catch {
            return DownloadSearchResult(ok: false, provider: provider, query: query, error: error.localizedDescription)
        }
    }

    func playlistEntries(_ raw: String) async -> [DownloadPlaylistEntry] {
        guard let url = endpoint("playlist", query: ["url": raw]) else { return [] }
        struct Response: Decodable { let ok: Bool?; let entries: [DownloadPlaylistEntry]? }
        do {
            let data = try await fetchJSON(url)
            let decoded = try JSONDecoder().decode(Response.self, from: data)
            return decoded.entries ?? []
        } catch {
            return []
        }
    }

    func resolveAudio(for request: DownloadRequest) async throws -> ResolvedAudio {
        guard let url = endpoint("resolve", query: ["url": request.url, "format": request.format]) else {
            throw DownloadResolverError.bridgeRequired
        }
        let data = try await fetchJSON(url)
        struct Response: Decodable {
            let ok: Bool?
            let url: String?
            let title: String?
            let uploader: String?
            let duration: Int?
            let genre: String?
            let year: Int?
            let thumbnail: String?
            let license: String?
            let error: String?
        }
        let decoded = try JSONDecoder().decode(Response.self, from: data)
        if decoded.ok == false || decoded.url == nil {
            throw DownloadResolverError.badRequest(decoded.error ?? "The resolver could not read this link.")
        }
        guard let audio = decoded.url.flatMap({ URL(string: $0) }) else {
            throw DownloadResolverError.badRequest("The resolver returned no audio URL.")
        }
        return ResolvedAudio(url: audio,
                             title: decoded.title ?? request.title,
                             uploader: decoded.uploader ?? request.uploader,
                             durationSec: decoded.duration ?? 0,
                             genre: decoded.genre ?? "",
                             year: decoded.year ?? 0,
                             thumbnailURL: decoded.thumbnail ?? request.thumbnailURL,
                             license: decoded.license)
    }
}

// MARK: - Download session delegate

private final class FileDownloadDelegate: NSObject, URLSessionDownloadDelegate {
    let destination: URL
    var onProgress: ((Int64, Int64) -> Void)?
    var onCompletion: ((Result<URL, Error>) -> Void)?
    private var finished = false

    init(destination: URL) { self.destination = destination }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didWriteData bytesWritten: Int64, totalBytesWritten: Int64,
                    totalBytesExpectedToWrite: Int64) {
        onProgress?(totalBytesWritten, totalBytesExpectedToWrite)
    }

    func urlSession(_ session: URLSession, downloadTask: URLSessionDownloadTask,
                    didFinishDownloadingTo location: URL) {
        guard !finished else { return }
        finished = true
        do {
            try FileManager.default.createDirectory(at: destination.deletingLastPathComponent(),
                                                    withIntermediateDirectories: true)
            try? FileManager.default.removeItem(at: destination)
            try FileManager.default.copyItem(at: location, to: destination)
            onCompletion?(.success(destination))
        } catch {
            onCompletion?(.failure(error))
        }
    }

    func urlSession(_ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?) {
        guard !finished, let error else { return }
        finished = true
        onCompletion?(.failure(error))
    }
}

// MARK: - Manager

@MainActor @Observable final class DownloadManager {
    var tasks: [DownloadTask] = []
    var isAvailable = true

    private let context: ModelContext
    private let settings: SettingsStore
    private let directResolver = DirectAudioResolver()
    private let httpResolver = HTTPDownloadResolver()

    private var activeDownloads: [String: URLSessionDownloadTask] = [:]
    private var lastProgressBytes: [String: Int64] = [:]
    private var lastProgressTime: [String: Date] = [:]
    private var processing = false

    init(context: ModelContext, settings: SettingsStore) {
        self.context = context
        self.settings = settings
        loadTasks()
    }

    // MARK: - Queries

    func loadTasks() {
        let descriptor = FetchDescriptor<DownloadTask>(sortBy: [SortDescriptor(\.createdAt, order: .reverse)])
        tasks = (try? context.fetch(descriptor)) ?? []
    }

    private var resolver: DownloadResolver {
        httpResolver.isConfigured ? httpResolver : directResolver
    }

    /// True for a complete http(s) link, false for a bare search query.
    func isCompleteURL(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return trimmed.hasPrefix("http://") || trimmed.hasPrefix("https://")
    }

    func resolverDescription() -> String {
        httpResolver.isConfigured ? "Server resolver configured" : "Direct audio only"
    }

    // MARK: - Preview / search

    func preview(_ raw: String) async -> DownloadPreview {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard isCompleteURL(trimmed) else {
            var p = DownloadPreview(); p.error = "Paste a direct audio or https video link first."; return p
        }
        if DirectAudioResolver.isDirectAudio(trimmed) {
            return await directResolver.preview(trimmed)
        }
        guard UrlValidator.isValidYoutubeURL(trimmed) else {
            var p = DownloadPreview()
            p.error = "Enter a valid https YouTube, SoundCloud, or direct audio link."
            return p
        }
        return await resolver.preview(trimmed)
    }

    func search(_ query: String, provider: String) async -> DownloadSearchResult {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else {
            return DownloadSearchResult(ok: false, provider: provider, query: clean, error: "Type something to search.")
        }
        guard provider == "youtube" || provider == "soundcloud" else {
            return DownloadSearchResult(ok: false, provider: provider, query: clean, error: "Choose YouTube or SoundCloud first.")
        }
        return await resolver.search(clean, provider: provider)
    }

    // MARK: - Enqueue

    func enqueue(url: String, title: String, uploader: String, thumbnail: String, format: String) async throws {
        let trimmed = url.trimmingCharacters(in: .whitespacesAndNewlines)
        let canonical: String
        let videoKey: String
        if DirectAudioResolver.isDirectAudio(trimmed) {
            canonical = trimmed
            videoKey = trimmed
        } else {
            guard let normalized = UrlValidator.canonicalURL(trimmed),
                  let link = UrlValidator.parse(normalized) else {
                throw DownloadResolverError.badRequest("Enter a valid https YouTube, SoundCloud, or direct audio link.")
            }
            canonical = normalized
            videoKey = link.stableKey
        }
        guard !videoKey.isEmpty else { throw DownloadResolverError.badRequest("Enter a valid link.") }
        guard !tasks.contains(where: { $0.videoID == videoKey }) else {
            throw DownloadResolverError.badRequest("This link is already in your downloads.")
        }

        let task = DownloadTask()
        context.insert(task)
        task.url = canonical
        task.videoID = videoKey
        task.title = title.isEmpty ? "Untitled audio" : title
        task.uploader = uploader.isEmpty ? "Unknown uploader" : uploader
        task.thumbnailURL = thumbnail
        task.format = ["opus", "m4a", "best"].contains(format) ? format : "opus"
        task.status = .queued
        task.progress = 0
        task.bytesDownloaded = 0
        task.bytesTotal = 0
        task.createdAt = Date()
        try? context.save()
        loadTasks()
        scheduleBackgroundRefresh()
        kickQueue()
    }

    func enqueuePlaylist(url: String, format: String) async -> (enqueued: Int, duplicates: Int, failed: Int) {
        let entries = await resolver.playlistEntries(url)
        guard !entries.isEmpty else { return (0, 0, 1) }
        let choice = ["opus", "m4a", "best"].contains(format) ? format : "opus"
        var enqueued = 0, duplicates = 0, failed = 0
        for entry in entries {
            guard !entry.url.isEmpty else { failed += 1; continue }
            let key = DirectAudioResolver.isDirectAudio(entry.url) ? entry.url : (UrlValidator.parse(entry.url)?.stableKey ?? "")
            if key.isEmpty { failed += 1; continue }
            if tasks.contains(where: { $0.videoID == key }) { duplicates += 1; continue }
            let task = DownloadTask()
            context.insert(task)
            task.url = entry.url
            task.videoID = key
            task.title = entry.title.isEmpty ? "Untitled audio" : entry.title
            task.uploader = "Unknown uploader"
            task.thumbnailURL = entry.thumbnailURL
            task.format = choice
            task.status = .queued
            task.createdAt = Date()
            enqueued += 1
        }
        if enqueued > 0 {
            try? context.save()
            loadTasks()
            scheduleBackgroundRefresh()
            kickQueue()
        }
        return (enqueued, duplicates, failed)
    }

    // MARK: - Queue actions

    func retry(_ task: DownloadTask) {
        guard let next = DownloadStateMachine.retry(task.status) else { return }
        task.status = next
        task.progress = 0
        task.bytesDownloaded = 0
        task.bytesTotal = 0
        task.speedBytesPerSec = 0
        task.etaSeconds = 0
        task.error = nil
        task.completedAt = nil
        try? context.save()
        loadTasks()
        scheduleBackgroundRefresh()
        kickQueue()
    }

    func cancel(_ task: DownloadTask) {
        activeDownloads[task.url]?.cancel()
        activeDownloads.removeValue(forKey: task.url)
        if DownloadStateMachine.isActive(task.status) {
            task.status = .cancelled
            task.error = "Cancelled by you."
            task.completedAt = Date()
        }
        try? context.save()
        loadTasks()
    }

    func delete(_ task: DownloadTask) {
        activeDownloads[task.url]?.cancel()
        activeDownloads.removeValue(forKey: task.url)
        context.delete(task)
        try? context.save()
        loadTasks()
    }

    func clearCompleted() {
        tasks.filter { $0.status == .completed }.forEach { context.delete($0) }
        try? context.save()
        loadTasks()
    }

    func clearHistory() {
        tasks.filter { DownloadStateMachine.isTerminal($0.status) }.forEach { context.delete($0) }
        try? context.save()
        loadTasks()
    }

    /// A cold launch may find rows left `fetchingInfo`/`downloading` by a killed process.
    func recoverInterrupted() {
        var changed = false
        for task in tasks where task.status == .fetchingInfo || task.status == .downloading {
            task.status = .failed
            task.error = "Interrupted. Tap retry to try again."
            task.completedAt = Date()
            changed = true
        }
        if changed { try? context.save() }
        loadTasks()
        kickQueue()
    }

    // MARK: - Sequential processing

    private func kickQueue() {
        Task { [weak self] in
            guard let self else { return }
            await self.drainQueue()
        }
    }

    private func drainQueue() async {
        guard !processing else { return }
        processing = true
        defer { processing = false }
        while true {
            guard let next = tasks.first(where: { DownloadStateMachine.isActive($0.status) }) else { break }
            if next.status == .queued {
                await run(next)
            } else {
                next.status = .failed
                next.error = "Interrupted. Tap retry to try again."
                next.completedAt = Date()
                try? context.save()
                loadTasks()
            }
        }
    }

    private func run(_ task: DownloadTask) async {
        let request = DownloadRequest(url: task.url, title: task.title,
                                      uploader: task.uploader,
                                      thumbnailURL: task.thumbnailURL,
                                      format: task.format)
        task.status = .fetchingInfo
        task.error = nil
        loadTasks()

        let resolved: ResolvedAudio
        do {
            resolved = try await resolver.resolveAudio(for: request)
        } catch {
            fail(task, message: error.localizedDescription)
            return
        }

        if UrlValidator.providerOf(task.url) == "soundcloud" && !SoundCloudGate.licenseAllowsDownload(resolved.license) {
            let license = resolved.license ?? "absent"
            fail(task, message: "\(SoundCloudGate.BLOCKED_REASON) (license: \(license))")
            return
        }

        task.status = .downloading
        task.bytesDownloaded = 0
        task.bytesTotal = 0
        task.progress = 0
        loadTasks()

        let result = await performDownload(task, resolved: resolved)
        switch result {
        case .failure(let error):
            if (error as? URLError)?.code == .cancelled {
                task.status = .cancelled
                task.error = "Cancelled by you."
            } else {
                fail(task, message: error.localizedDescription)
            }
            task.completedAt = Date()
            try? context.save()
            loadTasks()
        case .success(let finalURL):
            task.status = .completed
            task.progress = 1
            task.bytesDownloaded = task.bytesTotal > 0 ? task.bytesTotal : (resolved.durationSec > 0 ? task.bytesDownloaded : 0)
            task.outputPath = finalURL.path
            task.completedAt = Date()
            task.error = nil
            try? context.save()
            loadTasks()
            autoImportIfNeeded(task, resolved: resolved, fileURL: finalURL)
        }
    }

    private func fail(_ task: DownloadTask, message: String) {
        task.status = .failed
        task.error = message
        task.completedAt = Date()
        try? context.save()
        loadTasks()
    }

    private func performDownload(_ task: DownloadTask, resolved: ResolvedAudio) async -> Result<URL, Error> {
        let directory = Self.downloadsDirectory()
        let ext = Self.extension(for: resolved.url, format: task.format)
        let baseName = Self.safeFileName(resolved.title.isEmpty ? task.title : resolved.title)
        let destination = directory.appendingPathComponent("\(baseName) [atlas].\(ext)")

        let delegate = FileDownloadDelegate(destination: destination)
        let session = URLSession(configuration: .default, delegate: delegate, delegateQueue: nil)
        let request = URLRequest(url: resolved.url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 60)

        return await withCheckedContinuation { continuation in
            var delivered = false
            delegate.onProgress = { [weak self] written, total in
                let now = Date()
                Task { @MainActor [weak self] in
                    guard let self else { return }
                    self.updateProgress(task, written: written, total: total, now: now)
                }
            }
            delegate.onCompletion = { result in
                guard !delivered else { return }
                delivered = true
                continuation.resume(returning: result)
            }
            let downloadTask = session.downloadTask(with: request)
            self.activeDownloads[task.url] = downloadTask
            downloadTask.resume()
        }
    }

    private func updateProgress(_ task: DownloadTask, written: Int64, total: Int64, now: Date) {
        let key = task.url
        let previousBytes = lastProgressBytes[key] ?? 0
        let previousTime = lastProgressTime[key]
        let elapsed = previousTime.map { now.timeIntervalSince($0) } ?? 0
        if elapsed >= 0.2 && written > previousBytes {
            let speed = Int64(Double(written - previousBytes) / max(elapsed, 0.001))
            task.speedBytesPerSec = speed
            let remaining = total - written
            task.etaSeconds = speed > 0 ? max(0, remaining / speed) : 0
            lastProgressBytes[key] = written
            lastProgressTime[key] = now
        }
        task.bytesDownloaded = written
        task.bytesTotal = total
        task.progress = total > 0 ? min(1, max(0, Double(written) / Double(total))) : 0
    }

    // MARK: - Storage + auto-import

    private static func downloadsDirectory() -> URL {
        let documents = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: NSTemporaryDirectory())
        let folder = documents.appendingPathComponent("Atlas Music", isDirectory: true)
        try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        return folder
    }

    private static func safeFileName(_ raw: String) -> String {
        let invalid = CharacterSet(charactersIn: "/\\:*?\"<>|")
        let cleaned = raw.components(separatedBy: invalid).joined(separator: "_")
        return String(cleaned.prefix(80))
    }

    private static func `extension`(for url: URL, format: String) -> String {
        let ext = url.pathExtension.lowercased()
        if !ext.isEmpty { return ext == "webm" ? "mka" : ext }
        return format == "m4a" ? "m4a" : (format == "best" ? "m4a" : "opus")
    }

    /// Android imports through MediaStore + a full rescan. iOS has no MediaStore; we insert the
    /// finished file directly into the same SwiftData library the scanner uses, which is the
    /// equivalent end state (no duplicate row, metadata applied, saved once).
    private func autoImportIfNeeded(_ task: DownloadTask, resolved: ResolvedAudio, fileURL: URL) {
        guard settings.autoImportDownloads else { return }
        let uri = fileURL.path
        if (try? context.fetch(FetchDescriptor<Track>(predicate: #Predicate { $0.uri == uri })))?.first != nil {
            musicApplyMetadata(uri: uri, resolved: resolved)
            return
        }
        let track = Track(
            uri: uri,
            filePath: fileURL.path,
            displayName: fileURL.lastPathComponent,
            title: resolved.title,
            artist: resolved.uploader.isEmpty ? "Unknown artist" : resolved.uploader,
            album: "Unknown album",
            genre: resolved.genre.isEmpty ? "Unknown genre" : resolved.genre,
            year: resolved.year,
            durationMs: Int64(resolved.durationSec) * 1000,
            sizeBytes: (try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize).map { Int64($0) } ?? 0,
            dateAddedSec: Int64(Date().timeIntervalSince1970),
            folder: "Atlas Music",
            fingerprint: Track.makeFingerprint(title: resolved.title, artist: resolved.uploader, durationMs: Int64(resolved.durationSec) * 1000),
            mimeType: Self.extension(for: fileURL, format: task.format)
        )
        context.insert(track)
        try? context.save()
        musicApplyMetadata(uri: uri, resolved: resolved)
    }

    /// Set by AtlasMusicApp so completed downloads can apply metadata through MusicRepository.
    nonisolated(unsafe) static var metadataApplier: ((String, String, String, String, Int, Int64) -> Void)?

    private func musicApplyMetadata(uri: String, resolved: ResolvedAudio) {
        Self.metadataApplier?(uri, resolved.title, resolved.uploader,
                              resolved.genre.isEmpty ? "Unknown genre" : resolved.genre,
                              resolved.year, Int64(resolved.durationSec) * 1000)
    }

    // MARK: - Background processing

    func scheduleBackgroundRefresh() {
        #if canImport(BackgroundTasks)
        let request = BGProcessingTaskRequest(identifier: "com.atlas.music.downloads")
        request.requiresNetworkConnectivity = true
        request.earliestBeginDate = Date().addingTimeInterval(30)
        try? BGTaskScheduler.shared.submit(request)
        #endif
    }
}
