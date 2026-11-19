import XCTest
@testable import AtlasMusic

// Atlas iOS — XCTest port of the Android JUnit utility tests (downloads, duplicates, folders, links, versions, EQ, formats).

// MARK: - DownloadStateMachineTest

final class DownloadStateMachineTests: XCTestCase {
    func testHappyPathTransitionsAreLegal() {
        let can = DownloadStateMachine.canTransition
        XCTAssertTrue(can(.queued, .fetchingInfo))
        XCTAssertTrue(can(.fetchingInfo, .downloading))
        XCTAssertTrue(can(.downloading, .completed))
    }

    func testIllegalTransitionsRejected() {
        let can = DownloadStateMachine.canTransition
        XCTAssertFalse(can(.completed, .downloading))
        XCTAssertFalse(can(.queued, .completed))
        XCTAssertFalse(can(.failed, .completed))
    }

    func testFailureAndCancelAreRetryable() {
        let retry = DownloadStateMachine.retry
        XCTAssertEqual(retry(.failed), .queued)
        XCTAssertEqual(retry(.cancelled), .queued)
        XCTAssertNil(retry(.completed))
        XCTAssertNil(retry(.downloading))
    }

    func testTerminalStatesDetected() {
        let isTerminal = DownloadStateMachine.isTerminal
        XCTAssertTrue(isTerminal(.completed))
        XCTAssertTrue(isTerminal(.failed))
        XCTAssertTrue(isTerminal(.cancelled))
        XCTAssertFalse(isTerminal(.downloading))
    }

    func testActiveStatesDetected() {
        let isActive = DownloadStateMachine.isActive
        XCTAssertTrue(isActive(.queued))
        XCTAssertTrue(isActive(.fetchingInfo))
        XCTAssertTrue(isActive(.downloading))
        XCTAssertFalse(isActive(.completed))
    }
}

// MARK: - DuplicateDetectorTest

final class DuplicateDetectorTests: XCTestCase {
    /// Mirrors the Android `track(...)` helper. Fingerprints are produced by the already-written
    /// `Track.makeFingerprint`, which uses `DuplicateNormalizer.normalize` and the identical
    /// rounded-2s-bucket formula as Android's `DuplicateDetector.fingerprint`.
    private func track(
        _ title: String,
        _ artist: String,
        _ durationMs: Int64,
        id: Int64 = 0,
        size: Int64 = 1_000_000
    ) -> Track {
        Track(
            uri: "content://x/\(id)",
            filePath: "/music/\(title).mp3",
            displayName: "\(title).mp3",
            title: title,
            artist: artist,
            album: "Album",
            albumArtist: artist,
            genre: "Rock",
            year: 2001,
            trackNumber: 1,
            discNumber: 1,
            durationMs: durationMs,
            sizeBytes: size,
            dateAddedSec: id,
            dateModifiedSec: 0,
            folder: "music",
            albumId: 1,
            fingerprint: Track.makeFingerprint(title: title, artist: artist, durationMs: durationMs)
        )
    }

    func testSameSongDifferentCaseIsDuplicate() {
        let a = track("Mr. Brightside", "The Killers", 222_000)
        let b = track("MR. BRIGHTSIDE", "the killers", 221_500)
        // within 2s bucket — treated as the same recording
        XCTAssertEqual(a.fingerprint, b.fingerprint)
    }

    func testFeatClauseIsNormalized() {
        let a = track("Blinding Lights", "The Weeknd", 200_000)
        let b = track("Blinding Lights (feat. Someone)", "The Weeknd", 200_000)
        XCTAssertEqual(a.fingerprint, b.fingerprint)
    }

    func testDifferentDurationsAreNotDuplicates() {
        let a = track("Song", "Artist", 100_000)
        let b = track("Song", "Artist", 200_000)
        XCTAssertNotEqual(a.fingerprint, b.fingerprint)
    }

    func testFindDuplicatesGroupsCorrectly() {
        let tracks = [
            track("Song A", "Artist", 100_000, id: 1),
            track("Song A", "Artist", 100_500, id: 2, size: 2_000_000),
            track("Song A (Official Video)", "Artist", 100_000, id: 3),
            track("Song B", "Artist", 180_000, id: 4),
        ]
        let findDuplicates = DuplicateDetector.findDuplicates
        let groups = findDuplicates(tracks)
        XCTAssertEqual(groups.count, 1)
        XCTAssertEqual(groups[0].tracks.count, 3)
        XCTAssertEqual(groups[0].totalSizeBytes, 2_000_000 + 1_000_000 + 1_000_000)
    }

    func testWastedBytesCountsAllButOnePerGroup() {
        let tracks = [
            track("Song A", "Artist", 100_000, id: 1, size: 1_000_000),
            track("Song A", "Artist", 100_000, id: 2, size: 3_000_000),
        ]
        let findDuplicates = DuplicateDetector.findDuplicates
        let wastedBytes = DuplicateDetector.wastedBytes
        let groups = findDuplicates(tracks)
        XCTAssertEqual(wastedBytes(groups), 3_000_000)
    }
}

// MARK: - LibraryFolderTest

final class LibraryFolderTests: XCTestCase {
    func testNormalizesCaseAndSlashes() {
        let normalize = LibraryFolder.normalize
        XCTAssertEqual(normalize("Music/FLAC/"), "music/flac")
        XCTAssertEqual(normalize("Downloads"), "downloads")
    }

    func testBlankBecomesUnknown() {
        let normalize = LibraryFolder.normalize
        XCTAssertEqual(normalize(nil), "unknown")
        XCTAssertEqual(normalize("   "), "unknown")
    }

    func testExcludedPrefixMatchesNestedFolders() {
        let isExcluded = LibraryFolder.isExcluded
        let excluded: Set<String> = ["podcasts"]
        XCTAssertTrue(isExcluded("podcasts", excluded))
        XCTAssertTrue(isExcluded("podcasts/tech", excluded))
        XCTAssertFalse(isExcluded("music/podcasts", excluded))
        XCTAssertFalse(isExcluded("podcastsx", excluded))
    }
}

// MARK: - UrlValidatorTest

final class UrlValidatorTests: XCTestCase {
    func testAcceptsStandardWatchUrl() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertTrue(valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    func testAcceptsShortUrl() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertTrue(valid("https://youtu.be/dQw4w9WgXcQ"))
    }

    func testAcceptsMusicSubdomain() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertTrue(valid("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    func testAcceptsShorts() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertTrue(valid("https://www.youtube.com/shorts/dQw4w9WgXcQ"))
    }

    func testAcceptsPlaylistUrl() {
        let parse = UrlValidator.parse
        guard let link = parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf") else {
            return XCTFail("expected playlist link to parse")
        }
        XCTAssertTrue(link.isPlaylist)
        XCTAssertNil(link.videoID)
    }

    func testRejectsHttpNotHttps() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid("http://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    func testRejectsNonYoutubeHost() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid("https://evil.com/watch?v=dQw4w9WgXcQ"))
    }

    func testRejectsYoutubeLookalike() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid("https://www.youtube.com.attacker.com/watch?v=dQw4w9WgXcQ"))
    }

    func testRejectsShellInjectionStrings() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ; rm -rf /"))
        XCTAssertFalse(valid("https://www.youtube.com/watch?v=`id`"))
        XCTAssertFalse(valid("https://www.youtube.com/watch?v=dQw4w9WgXcQ|cat"))
    }

    func testRejectsEmptyAndGarbage() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid(""))
        XCTAssertFalse(valid("not a url"))
        XCTAssertFalse(valid("youtube.com/watch?v=dQw4w9WgXcQ")) // scheme required
    }

    func testRejectsOverlongInput() {
        let valid = UrlValidator.isValidYoutubeURL
        XCTAssertFalse(valid("https://www.youtube.com/watch?v=" + String(repeating: "a", count: 3000)))
    }

    func testCanonicalizationDropsTrackingParams() {
        let canonical = UrlValidator.canonicalURL
        XCTAssertEqual(
            canonical("https://www.youtube.com/watch?v=dQw4w9WgXcQ&utm_source=share&feature=web"),
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
    }

    func testCanonicalPlaylistKeepsListId() {
        let canonical = UrlValidator.canonicalURL
        XCTAssertEqual(
            canonical("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf&si=abc123"),
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        )
    }
}

// MARK: - PlaylistLinkTest

final class PlaylistLinkTests: XCTestCase {
    func testWatchUrlWithListParamCarriesPlaylistId() {
        let parse = UrlValidator.parse
        guard let link = parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf") else {
            return XCTFail("expected link to parse")
        }
        XCTAssertEqual(link.videoID, "dQw4w9WgXcQ")
        XCTAssertEqual(link.playlistID, "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        // stays a single video for duplicate detection & playback semantics
        XCTAssertFalse(link.isPlaylist)
        XCTAssertEqual(link.stableKey, "dQw4w9WgXcQ")
    }

    func testPlaylistOnlyUrlHasNullVideoId() {
        let parse = UrlValidator.parse
        guard let link = parse("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf") else {
            return XCTFail("expected link to parse")
        }
        XCTAssertTrue(link.isPlaylist)
        XCTAssertNil(link.videoID)
        XCTAssertEqual(link.playlistID, "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
        XCTAssertEqual(link.stableKey, "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf")
    }

    func testCanonicalUrlStaysVideoForWatchWithList() {
        let canonical = UrlValidator.canonicalURL
        XCTAssertEqual(
            canonical("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        )
    }

    func testCanonicalPlaylistUrlResolvesBothForms() {
        let canonicalPlaylist = UrlValidator.canonicalPlaylistURL
        XCTAssertEqual(
            canonicalPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        )
        XCTAssertEqual(
            canonicalPlaylist("https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"),
            "https://www.youtube.com/playlist?list=PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        )
    }

    func testCanonicalPlaylistUrlNullForPlainVideo() {
        let canonicalPlaylist = UrlValidator.canonicalPlaylistURL
        XCTAssertNil(canonicalPlaylist("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    func testRejectsMalformedPlaylistId() {
        let valid = UrlValidator.isValidYoutubeURL
        let parse = UrlValidator.parse
        XCTAssertFalse(valid("https://www.youtube.com/playlist?list=!!!"))
        XCTAssertFalse(valid("https://www.youtube.com/playlist?list=P%27%20OR%201=1--"))
        XCTAssertNil(parse("https://www.youtube.com/playlist?list=<script>alert(1)</script>"))
    }

    func testPlaylistWithWeirdCasingStillAccepted() {
        // Real YouTube playlist IDs are case-sensitive base64-ish; matching must be case-sensitive
        let parse = UrlValidator.parse
        XCTAssertNotNil(parse("https://www.youtube.com/playlist?list=PLRaxlBzk3XxQQSh2CL0OTb1gq"))
    }
}

// MARK: - SoundCloudLinkTest

final class SoundCloudLinkTests: XCTestCase {
    func testAcceptsTrackUrl() {
        let parse = UrlValidator.parse
        let canonical = UrlValidator.canonicalURL
        guard let link = parse("https://soundcloud.com/sakuragirl_official/bubbles") else {
            return XCTFail("expected link to parse")
        }
        XCTAssertEqual(link.provider, "soundcloud")
        XCTAssertEqual(link.stableKey, "sc:sakuragirl_official/bubbles")
        XCTAssertFalse(link.isPlaylist)
        XCTAssertEqual(canonical("https://soundcloud.com/sakuragirl_official/bubbles"), "https://soundcloud.com/sakuragirl_official/bubbles")
    }

    func testAcceptsWwwAndMobileHosts() {
        let parse = UrlValidator.parse
        for host in ["www.soundcloud.com", "m.soundcloud.com"] {
            XCTAssertNotNil(parse("https://\(host)/sakuragirl_official/bubbles"), "host \(host) should parse")
        }
    }

    func testAcceptsSetUrl() {
        let parse = UrlValidator.parse
        guard let link = parse("https://soundcloud.com/trackistador/sets/lofi-chill-playlist") else {
            return XCTFail("expected link to parse")
        }
        XCTAssertTrue(link.isPlaylist)
        XCTAssertEqual(link.playlistID, "scset:trackistador/lofi-chill-playlist")
        XCTAssertEqual(link.stableKey, "scset:trackistador/lofi-chill-playlist")
    }

    func testAcceptsPrivateSetToken() {
        let parse = UrlValidator.parse
        let canonical = UrlValidator.canonicalURL
        guard let link = parse("https://soundcloud.com/user/sets/my-set/s-abc123") else {
            return XCTFail("expected link to parse")
        }
        XCTAssertTrue(link.isPlaylist)
        XCTAssertEqual(link.token, "s-abc123")
        XCTAssertEqual(canonical("https://soundcloud.com/user/sets/my-set/s-abc123"), "https://soundcloud.com/user/sets/my-set/s-abc123")
    }

    func testRejectsEmbedPlayerAndGarbage() {
        let parse = UrlValidator.parse
        XCTAssertNil(parse("https://w.soundcloud.com/player/?url=https://api.soundcloud.com/tracks/1"))
        XCTAssertNil(parse("https://soundcloud.com/"))
        XCTAssertNil(parse("https://soundcloud.com/onlyartist"))
        XCTAssertNil(parse("https://evil.com/sakuragirl_official/bubbles"))
        XCTAssertNil(parse("http://soundcloud.com/sakuragirl_official/bubbles"))
        XCTAssertNil(parse("https://soundcloud.com/sakuragirl_official/bubbles?x=<script>"))
    }

    func testScKeysNeverCollideWithYoutube() {
        let parse = UrlValidator.parse
        guard let yt = parse("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
              let sc = parse("https://soundcloud.com/dQw4w9WgXcQ/dQw4w9WgXcQ") else {
            return XCTFail("expected both links to parse")
        }
        XCTAssertNotEqual(yt.stableKey, sc.stableKey)
    }

    func testProviderOfWorks() {
        let providerOf = UrlValidator.providerOf
        XCTAssertEqual(providerOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"), "youtube")
        XCTAssertEqual(providerOf("https://soundcloud.com/sakuragirl_official/bubbles"), "soundcloud")
        XCTAssertNil(providerOf("not a link"))
    }

    func testGateAllowsCreativeCommonsBlocksReserved() {
        let allowed = SoundCloudGate.licenseAllowsDownload
        for cc in ["cc-by", "cc-by-sa", "cc-by-nc", "cc-by-nd", "cc-by-nc-sa", "cc-by-nc-nd", "cc0", "CC-BY-SA"] {
            XCTAssertTrue(allowed(cc), "expected \(cc) to be allowed")
        }
        let reserved: [String?] = ["all-rights-reserved", "All Rights Reserved", nil, "", "license-free-ish"]
        for item in reserved {
            XCTAssertFalse(allowed(item), "expected \(String(describing: item)) to be blocked")
        }
    }
}

// MARK: - YtdlpVersionTest

final class YtdlpVersionTests: XCTestCase {
    func testParsesPlainReleaseTag() {
        let parse = YtdlpVersion.parse
        guard let v = parse("2025.10.22") else { return XCTFail("expected version to parse") }
        XCTAssertEqual(v.year, 2025)
        XCTAssertEqual(v.month, 10)
        XCTAssertEqual(v.day, 22)
    }

    func testParsesPrefixedTag() {
        let parse = YtdlpVersion.parse
        guard let v = parse("v2025.10.22") else { return XCTFail("expected version to parse") }
        XCTAssertEqual(v.year, 2025)
        XCTAssertEqual(v.month, 10)
        XCTAssertEqual(v.day, 22)
    }

    func testParsesRevisionTag() {
        let parse = YtdlpVersion.parse
        guard let v = parse("2025.10.22.1"), let plain = parse("2025.10.22") else {
            return XCTFail("expected versions to parse")
        }
        XCTAssertEqual(v.year, 2025)
        XCTAssertEqual(v.month, 10)
        XCTAssertEqual(v.day, 22)
        // A non-zero revision must actually participate in ordering.
        XCTAssertTrue(v > plain)
    }

    func testRejectsGarbage() {
        let parse = YtdlpVersion.parse
        XCTAssertNil(parse("latest"))
        XCTAssertNil(parse(""))
        XCTAssertNil(parse("2025.13.40"))
        XCTAssertNil(parse("v1.2"))
    }

    func testOrderingWorksAcrossRevisionsAndMonths() {
        let parse = YtdlpVersion.parse
        guard let a = parse("2025.10.22"), let b = parse("2025.10.21"),
              let c = parse("2026.01.01"), let d = parse("2025.12.31"),
              let e = parse("2025.10.22.1") else {
            return XCTFail("expected versions to parse")
        }
        XCTAssertTrue(a > b)
        XCTAssertTrue(c > d)
        XCTAssertTrue(e > a)
        XCTAssertFalse(a > a)
    }

    func testStringValueRoundTrips() {
        let parse = YtdlpVersion.parse
        XCTAssertEqual(parse("v2025.10.22")?.stringValue, "2025.10.22")
        XCTAssertEqual(parse("2025.10.22.3")?.stringValue, "2025.10.22.3")
    }
}

// MARK: - EqCurvesTest

final class EqCurvesTests: XCTestCase {
    func testEmptyOrAllZeroBandsResolveToFlat() {
        let match = EqCurves.matchingPresetName
        XCTAssertEqual(match([]), "Flat")
        XCTAssertEqual(match([Int](repeating: 0, count: 9)), "Flat")
    }

    func testStoredCurveResolvesBackToExactPresetName() {
        let match = EqCurves.matchingPresetName
        let gains = EqCurves.gainsFor
        // PRESET_ORDER includes "Flat" (empty gains) plus all 16 stored presets.
        for name in EqCurves.PRESET_ORDER {
            XCTAssertEqual(match(gains(name)), name, "preset \(name) did not round-trip")
        }
    }

    func testCustomBandEditsResolveToCustom() {
        let match = EqCurves.matchingPresetName
        XCTAssertEqual(match([250, 0, 0, 0, 0, 0, 0, 0, 0]), "Custom")
    }
}

// MARK: - MediaFormatHelperTest

final class MediaFormatHelperTests: XCTestCase {
    func testWebmExtensionNormalizesToMatroskaAudioForMediaStoreCompatibility() {
        let normalize = MediaFormatHelper.normalizeDownloadExtension
        let mime = MediaFormatHelper.mimeForExtension
        let ext = normalize("webm", nil)
        XCTAssertEqual(ext, "mka")
        XCTAssertEqual(mime(ext), "audio/x-matroska")
        XCTAssertNotEqual(mime(ext), "audio/webm")
    }

    func testWebmMimeMapsToXMatroskaInsteadOfUnsupportedAudioWebm() {
        let mime = MediaFormatHelper.mimeForExtension
        XCTAssertEqual(mime("webm"), "audio/x-matroska")
        XCTAssertEqual(mime("MKA"), "audio/x-matroska")
    }

    func testMagicBytesOverrideMismatchedFileExtension() {
        // The Swift API exposes magic-byte sniffing as `detectExtensionFromMagic`, so the
        // Android "extension + header" assertions are exercised directly against the sniffer.
        // Bare array literals let Swift infer Data vs [UInt8] from the actual API signature.
        let detect = MediaFormatHelper.detectExtensionFromMagic
        XCTAssertEqual(detect(Data([0x1A, 0x45, 0xDF, 0xA3, 0x01])), "mka")          // EBML
        XCTAssertEqual(detect(Data([0x4F, 0x67, 0x67, 0x53, 0x00])), "ogg")          // "OggS\0"
        XCTAssertEqual(detect(Data([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70])), "m4a")  // "ftyp"
        XCTAssertEqual(detect(Data([0x66, 0x4C, 0x61, 0x43, 0x00])), "flac")         // "fLaC"
        XCTAssertEqual(detect(Data([0x52, 0x49, 0x46, 0x46, 0x00])), "wav")          // "RIFF"
        XCTAssertEqual(detect(Data([0x49, 0x44, 0x33, 0x03, 0x00])), "mp3")          // "ID3"
    }

    func testDisplayAudioFormatLabelProducesCleanBadges() {
        let label = MediaFormatHelper.displayAudioFormatLabel
        XCTAssertEqual(label("audio/x-wav", "sample.wav"), "WAV")
        XCTAssertEqual(label("audio/x-matroska", "song.mka"), "MKA")
        XCTAssertEqual(label("audio/mp4", "song.m4a"), "M4A")
        XCTAssertEqual(label("audio/mpeg", "song.mp3"), "MP3")
        XCTAssertEqual(label("audio/ogg", "track.opus"), "OPUS")
        XCTAssertEqual(label("audio/ogg", "track.ogg"), "OGG")
        XCTAssertEqual(label("", "album.flac"), "FLAC")
    }
}
