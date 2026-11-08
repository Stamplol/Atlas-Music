package com.atlas.music

import com.atlas.music.data.db.DownloadStatus
import com.atlas.music.util.DownloadStateMachine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadStateMachineTest {
    @Test fun happyPathTransitionsAreLegal() {
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.QUEUED, DownloadStatus.FETCHING_INFO))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.FETCHING_INFO, DownloadStatus.DOWNLOADING))
        assertTrue(DownloadStateMachine.canTransition(DownloadStatus.DOWNLOADING, DownloadStatus.COMPLETED))
    }

    @Test fun illegalTransitionsRejected() {
        assertFalse(DownloadStateMachine.canTransition(DownloadStatus.COMPLETED, DownloadStatus.DOWNLOADING))
        assertFalse(DownloadStateMachine.canTransition(DownloadStatus.QUEUED, DownloadStatus.COMPLETED))
        assertFalse(DownloadStateMachine.canTransition(DownloadStatus.FAILED, DownloadStatus.COMPLETED))
    }

    @Test fun failureAndCancelAreRetryable() {
        assertEquals(DownloadStatus.QUEUED, DownloadStateMachine.retry(DownloadStatus.FAILED))
        assertEquals(DownloadStatus.QUEUED, DownloadStateMachine.retry(DownloadStatus.CANCELLED))
        assertNull(DownloadStateMachine.retry(DownloadStatus.COMPLETED))
        assertNull(DownloadStateMachine.retry(DownloadStatus.DOWNLOADING))
    }

    @Test fun terminalStatesDetected() {
        assertTrue(DownloadStateMachine.isTerminal(DownloadStatus.COMPLETED))
        assertTrue(DownloadStateMachine.isTerminal(DownloadStatus.FAILED))
        assertTrue(DownloadStateMachine.isTerminal(DownloadStatus.CANCELLED))
        assertFalse(DownloadStateMachine.isTerminal(DownloadStatus.DOWNLOADING))
    }

    @Test fun activeStatesDetected() {
        assertTrue(DownloadStateMachine.isActive(DownloadStatus.QUEUED))
        assertTrue(DownloadStateMachine.isActive(DownloadStatus.FETCHING_INFO))
        assertTrue(DownloadStateMachine.isActive(DownloadStatus.DOWNLOADING))
        assertFalse(DownloadStateMachine.isActive(DownloadStatus.COMPLETED))
    }
}
