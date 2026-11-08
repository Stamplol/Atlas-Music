package com.atlas.music.widget

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.atlas.music.di.ServiceLocator

/**
 * Quick Settings tile — one tap play/pause without opening the app.
 */
class AtlasTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) { unlockAndRun { toggleAndRefresh() } } else { toggleAndRefresh() }
    }

    private fun toggleAndRefresh() {
        runCatching {
            val conn = ServiceLocator.playbackConnection
            conn.connect()
            conn.togglePlayPause()
        }
        // Ask the system to re-listen so refreshTile reads the fresh state.
        try { requestListeningState(this, android.content.ComponentName(this, AtlasTileService::class.java)) } catch (e: Exception) { refreshTile() }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val state = runCatching { ServiceLocator.playbackConnection.state.value }.getOrNull()
        val playing = state?.isPlaying == true
        val title = state?.title?.takeIf { it.isNotBlank() } ?: "Atlas Music"
        tile.label = if (playing) "Pause \u00b7 $title".take(42) else if (state?.hasQueue == true) "Play \u00b7 $title".take(42) else "Atlas Music"
        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }
}
