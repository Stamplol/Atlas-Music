// Atlas iOS — Legal state transitions and retry rules for download tasks.
import Foundation

/// Faithful port of Android `DownloadStateMachine`.
enum DownloadStateMachine {
    private static let legal: [DownloadStatus: Set<DownloadStatus>] = [
        .queued: [.fetchingInfo, .cancelled, .failed],
        .fetchingInfo: [.downloading, .failed, .cancelled],
        .downloading: [.completed, .failed, .cancelled],
        .completed: [],
        .failed: [.queued],
        .cancelled: [.queued],
    ]

    static func canTransition(from: DownloadStatus, to: DownloadStatus) -> Bool {
        legal[from]?.contains(to) == true
    }

    static func retry(_ status: DownloadStatus) -> DownloadStatus? {
        (status == .failed || status == .cancelled) ? .queued : nil
    }

    static func isTerminal(_ status: DownloadStatus) -> Bool {
        status == .completed || status == .failed || status == .cancelled
    }

    static func isActive(_ status: DownloadStatus) -> Bool {
        status == .queued || status == .fetchingInfo || status == .downloading
    }
}
