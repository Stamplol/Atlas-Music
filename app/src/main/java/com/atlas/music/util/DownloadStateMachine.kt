package com.atlas.music.util

import com.atlas.music.data.db.DownloadStatus

/** Legal transitions for a download task. Keeps the queue consistent across restarts. */
object DownloadStateMachine {
    private val LEGAL: Map<DownloadStatus, Set<DownloadStatus>> = mapOf(
        DownloadStatus.QUEUED to setOf(DownloadStatus.FETCHING_INFO, DownloadStatus.CANCELLED, DownloadStatus.FAILED),
        DownloadStatus.FETCHING_INFO to setOf(DownloadStatus.DOWNLOADING, DownloadStatus.FAILED, DownloadStatus.CANCELLED),
        DownloadStatus.DOWNLOADING to setOf(DownloadStatus.COMPLETED, DownloadStatus.FAILED, DownloadStatus.CANCELLED),
        DownloadStatus.COMPLETED to emptySet(),
        DownloadStatus.FAILED to setOf(DownloadStatus.QUEUED),   // retry
        DownloadStatus.CANCELLED to setOf(DownloadStatus.QUEUED) // retry
    )

    fun canTransition(from: DownloadStatus, to: DownloadStatus): Boolean =
        LEGAL[from]?.contains(to) == true

    fun retry(status: DownloadStatus): DownloadStatus? =
        if (status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED) DownloadStatus.QUEUED else null

    fun isTerminal(status: DownloadStatus): Boolean =
        status == DownloadStatus.COMPLETED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELLED

    fun isActive(status: DownloadStatus): Boolean =
        status == DownloadStatus.QUEUED || status == DownloadStatus.FETCHING_INFO || status == DownloadStatus.DOWNLOADING
}
