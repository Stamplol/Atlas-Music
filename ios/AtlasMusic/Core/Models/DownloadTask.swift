import Foundation
import SwiftData

// Atlas iOS — Persisted download attempt with progress/state (mirrors Android DownloadTaskEntity).

@Model
final class DownloadTask {
    var url: String
    var videoID: String
    var title: String
    var uploader: String
    var thumbnailURL: String
    var format: String
    var status: DownloadStatus
    var progress: Double
    var bytesDownloaded: Int64
    var bytesTotal: Int64
    var speedBytesPerSec: Int64
    var etaSeconds: Int64
    var outputPath: String?
    var error: String?
    var createdAt: Date
    var completedAt: Date?

    init(
        url: String = "",
        videoID: String = "",
        title: String = "",
        uploader: String = "",
        thumbnailURL: String = "",
        format: String = "opus",
        status: DownloadStatus = .queued,
        progress: Double = 0,
        bytesDownloaded: Int64 = 0,
        bytesTotal: Int64 = 0,
        speedBytesPerSec: Int64 = 0,
        etaSeconds: Int64 = 0,
        outputPath: String? = nil,
        error: String? = nil,
        createdAt: Date = Date(),
        completedAt: Date? = nil
    ) {
        self.url = url
        self.videoID = videoID
        self.title = title
        self.uploader = uploader
        self.thumbnailURL = thumbnailURL
        self.format = format
        self.status = status
        self.progress = progress
        self.bytesDownloaded = bytesDownloaded
        self.bytesTotal = bytesTotal
        self.speedBytesPerSec = speedBytesPerSec
        self.etaSeconds = etaSeconds
        self.outputPath = outputPath
        self.error = error
        self.createdAt = createdAt
        self.completedAt = completedAt
    }
}
