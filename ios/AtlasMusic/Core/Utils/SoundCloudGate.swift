// Atlas iOS — SoundCloud Creative-Commons download permission gate.
import Foundation

/// Faithful port of Android `SoundCloudGate`. The `license` field is the only honest grant.
enum SoundCloudGate {
    /// True when the artist licensed this track for redistribution.
    static func licenseAllowsDownload(_ license: String?) -> Bool {
        guard let license else { return false }
        let normalized = license.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacingOccurrences(of: "_", with: "-")
            .replacingOccurrences(of: "creativecommons", with: "cc")
            .replacingOccurrences(of: " ", with: "-")
        if normalized == "cc0" { return true }
        if normalized == "sampling+" { return true }
        if normalized.hasPrefix("cc-") { return true }
        return false
    }

    static func licenseLabel(_ license: String?) -> String {
        guard let license else { return "All rights reserved" }
        if licenseAllowsDownload(license) {
            return license.uppercased()
                .replacingOccurrences(of: "_", with: "-")
                .replacingOccurrences(of: "CC-", with: "CC ")
        }
        return "All rights reserved"
    }

    static let BLOCKED_REASON = "This track isn't licensed for download — Atlas passes on all-rights-reserved streams."
}
