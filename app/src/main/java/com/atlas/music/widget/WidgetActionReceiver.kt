package com.atlas.music.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.launch

/** Handles widget button taps: prev / play-pause / next, then refreshes the widget. */
class WidgetActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext
        runCatching {
            val conn = ServiceLocator.playbackConnection
            conn.connect()
        }
        when (intent.action) {
            ACTION_TOGGLE -> {
                runCatching { ServiceLocator.playbackConnection.togglePlayPause() }
            }
            ACTION_NEXT -> {
                runCatching { ServiceLocator.playbackConnection.skipNext() }
            }
            ACTION_PREV -> {
                runCatching { ServiceLocator.playbackConnection.skipPrevious() }
            }
            ACTION_POMODORO_TOGGLE -> {
                // Pomodoro state lives in ToolsViewModel — open the app to Tools.
                runCatching {
                    val open = Intent(context, com.atlas.music.MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    context.startActivity(open)
                }
            }
            ACTION_SLEEP30 -> {
                runCatching { ServiceLocator.playbackConnection.startSleepTimer(30) }
            }
            ACTION_SHUFFLE -> {
                runCatching { ServiceLocator.playbackConnection.toggleShuffle() }
            }
            ACTION_REPEAT -> {
                runCatching { ServiceLocator.playbackConnection.cycleRepeatMode() }
            }
            ACTION_LIKE -> {
                val conn = ServiceLocator.playbackConnection
                val id = conn.state.value.currentTrackId
                if (id != null) {
                    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
                        val track = ServiceLocator.musicRepository.getTrack(id)
                        if (track != null) ServiceLocator.musicRepository.setFavorite(id, !track.isFavorite)
                    }
                }
            }
            else -> return
        }
        AtlasWidgetProvider.updateAll(appContext)
    }

    companion object {
        const val ACTION_TOGGLE = "com.atlas.music.WIDGET_TOGGLE"
        const val ACTION_NEXT = "com.atlas.music.WIDGET_NEXT"
        const val ACTION_PREV = "com.atlas.music.WIDGET_PREV"
        const val ACTION_POMODORO_TOGGLE = "com.atlas.music.WIDGET_POMODORO"
        const val ACTION_SLEEP30 = "com.atlas.music.WIDGET_SLEEP30"
        const val ACTION_SHUFFLE = "com.atlas.music.WIDGET_SHUFFLE"
        const val ACTION_REPEAT = "com.atlas.music.WIDGET_REPEAT"
        const val ACTION_LIKE = "com.atlas.music.WIDGET_LIKE"
    }
}
