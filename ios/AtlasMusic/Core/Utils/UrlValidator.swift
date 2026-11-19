// Atlas iOS — Strict URL validation and canonicalization for YouTube / SoundCloud downloads.
import Foundation

/// Mirrors Android `YoutubeLink`. `stableKey` is the download/identity key.
struct YoutubeLink: Equatable {
    let videoID: String?
    let playlistID: String?
    let isPlaylist: Bool
    let provider: String
    let token: String?

    init(_ videoID: String?, _ playlistID: String?, isPlaylist: Bool,
         provider: String = "youtube", token: String? = nil) {
        self.videoID = videoID
        self.playlistID = playlistID
        self.isPlaylist = isPlaylist
        self.provider = provider
        self.token = token
    }

    init(videoID: String?, playlistID: String?, isPlaylist: Bool,
         provider: String = "youtube", token: String? = nil) {
        self.init(videoID, playlistID, isPlaylist: isPlaylist, provider: provider, token: token)
    }

    var stableKey: String { videoID ?? playlistID ?? "" }

    /// Android spelling aliases for callers ported verbatim from Kotlin.
    var videoId: String? { videoID }
    var playlistId: String? { playlistID }
}

/// Faithful port of Android `com.atlas.music.util.UrlValidator`.
/// Never hands raw user input to a downloader — only canonical https URLs are produced.
enum UrlValidator {
    private static let videoID = try! NSRegularExpression(pattern: "^[A-Za-z0-9_-]{10,12}$")
    private static let playlistID = try! NSRegularExpression(pattern: "^[A-Za-z0-9_-]{10,64}$")
    private static let scTrack = try! NSRegularExpression(pattern: "^/([\\w-]{1,100})/([\\w-]{1,100})/?$")
    private static let scSet = try! NSRegularExpression(pattern: "^/([\\w-]{1,100})/sets/([\\w-]{1,100})(?:/(s-[\\w-]{3,64}))?/?$")

    private static let videoHosts: Set<String> = [
        "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
        "youtube-nocookie.com", "www.youtube-nocookie.com",
    ]
    private static let shortHosts: Set<String> = ["youtu.be", "www.youtu.be"]
    private static let scHosts: Set<String> = ["soundcloud.com", "www.soundcloud.com", "m.soundcloud.com"]
    private static let scReserved: Set<String> = [
        "sets", "tracks", "albums", "likes", "reposts", "spotlight", "comments", "stations", "search",
    ]

    static func parse(_ raw: String) -> YoutubeLink? {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count > 2048 { return nil }
        if trimmed.unicodeScalars.contains(where: { CharacterSet.whitespacesAndNewlines.contains($0) }) { return nil }
        guard let components = URLComponents(string: trimmed),
              let scheme = components.scheme?.lowercased(), scheme == "https",
              components.userInfo == nil,
              let host = components.host?.lowercased() else { return nil }

        let path = components.path
        let queryParams = parseQuery(components.query)

        if shortHosts.contains(host) {
            let id = String(String(path.drop(while: { $0 == "/" })).components(separatedBy: "/").first ?? "")
            if matches(videoID, id) { return YoutubeLink(id, nil, isPlaylist: false, provider: "youtube") }
            return nil
        }

        if scHosts.contains(host) {
            if let setMatch = firstMatch(scSet, path) {
                let uploader = group(setMatch, 1, in: path)
                let slug = group(setMatch, 2, in: path)
                let tokenValue = group(setMatch, 3, in: path)
                var token: String? = nil
                if let candidate = tokenValue,
                   !candidate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    token = candidate
                }
                return YoutubeLink(nil, "scset:\(uploader)/\(slug)", isPlaylist: true,
                                   provider: "soundcloud", token: token)
            }
            if let trackMatch = firstMatch(scTrack, path) {
                let uploader = group(trackMatch, 1, in: path)
                let title = group(trackMatch, 2, in: path)
                if scReserved.contains(uploader.lowercased()) { return nil }
                return YoutubeLink("sc:\(uploader)/\(title)", nil, isPlaylist: false, provider: "soundcloud")
            }
            return nil
        }

        guard videoHosts.contains(host) else { return nil }

        if path.hasPrefix("/shorts/") {
            let rest = String(path.dropFirst("/shorts/".count))
                .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
            let id = rest.components(separatedBy: "/").first ?? ""
            if matches(videoID, id) { return YoutubeLink(id, nil, isPlaylist: false) }
            return nil
        }

        if path == "/watch" || path.hasPrefix("/watch") {
            guard let id = queryParams["v"], matches(videoID, id) else { return nil }
            let list = queryParams["list"]
            let playlist = (list != nil && matches(playlistID, list!)) ? list : nil
            return YoutubeLink(id, playlist, isPlaylist: false)
        }

        if path == "/playlist" {
            guard let list = queryParams["list"], matches(playlistID, list) else { return nil }
            return YoutubeLink(nil, list, isPlaylist: true)
        }

        return nil
    }

    static func isValidYoutubeURL(_ raw: String) -> Bool { parse(raw) != nil }

    /// Android spelling alias.
    static func isValidYoutubeUrl(_ raw: String) -> Bool { isValidYoutubeURL(raw) }

    /// Canonical https URL handed to the downloader — never the raw user string.
    static func canonicalURL(_ raw: String) -> String? {
        guard let link = parse(raw) else { return nil }
        if link.provider == "soundcloud" && link.isPlaylist {
            guard let value = link.playlistID else { return nil }
            let body = value.hasPrefix("scset:") ? String(value.dropFirst("scset:".count)) : value
            let parts = body.split(separator: "/", maxSplits: 1, omittingEmptySubsequences: false).map(String.init)
            if parts.count != 2 { return nil }
            let token = link.token.map { "/\($0)" } ?? ""
            return "https://soundcloud.com/\(parts[0])/sets/\(parts[1])\(token)"
        }
        if link.provider == "soundcloud" {
            guard let id = link.videoID else { return nil }
            let body = id.hasPrefix("sc:") ? String(id.dropFirst("sc:".count)) : id
            return "https://soundcloud.com/\(body)"
        }
        if let id = link.videoID { return "https://www.youtube.com/watch?v=\(id)" }
        if let list = link.playlistID { return "https://www.youtube.com/playlist?list=\(list)" }
        return nil
    }

    /// Android spelling alias.
    static func canonicalUrl(_ raw: String) -> String? { canonicalURL(raw) }

    /// Canonical playlist URL for batch listing (works for watch+list URLs too).
    static func canonicalPlaylistURL(_ raw: String) -> String? {
        guard let link = parse(raw) else { return nil }
        if link.provider == "soundcloud" && link.isPlaylist { return canonicalURL(raw) }
        guard let list = link.playlistID else { return nil }
        return "https://www.youtube.com/playlist?list=\(list)"
    }

    /// "youtube" | "soundcloud" — nil for unrecognized links.
    static func providerOf(_ raw: String) -> String? { parse(raw)?.provider }

    // MARK: - Internals

    private static func parseQuery(_ query: String?) -> [String: String] {
        var result: [String: String] = [:]
        guard let query else { return result }
        for rawParam in query.split(separator: "&", omittingEmptySubsequences: false) {
            let param = String(rawParam)
            guard let idx = param.firstIndex(of: "="), idx > param.startIndex else { continue }
            let key = String(param[..<idx])
            let value = String(param[param.index(after: idx)...])
            result[key] = value
        }
        return result
    }

    private static func matches(_ regex: NSRegularExpression, _ text: String) -> Bool {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text)) != nil
    }

    private static func firstMatch(_ regex: NSRegularExpression, _ text: String) -> NSTextCheckingResult? {
        regex.firstMatch(in: text, range: NSRange(text.startIndex..., in: text))
    }

    private static func group(_ match: NSTextCheckingResult, _ index: Int, in text: String) -> String {
        let r = match.range(at: index)
        guard r.location != NSNotFound, let range = Range(r, in: text) else { return "" }
        return String(text[range])
    }
}
