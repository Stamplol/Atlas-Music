package com.atlas.music

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.atlas.music.di.ServiceLocator
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AtlasApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        startPython()
        createNotificationChannels()
        appScope.launch { runCatching { ServiceLocator.downloadManager.recoverInterrupted() } }
    }

    private fun startPython() {
        runCatching {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this))
            }
        }.onFailure {
            // Downloader features degrade honestly: the UI checks PythonBridge.isAvailable().
            android.util.Log.e(TAG, "Python runtime failed to start; downloader disabled", it)
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        val playback = NotificationChannel(
            CHANNEL_PLAYBACK,
            "Atlas Music",
            NotificationManager.IMPORTANCE_LOW // no sound/vibration for a media control surface
        ).apply { description = "Now playing controls"; lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC; setLightColor(0xFFE5A83D.toInt()) }
        val downloads = NotificationChannel(
            CHANNEL_DOWNLOADS,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Download progress and results" }
        val updates = NotificationChannel(
            CHANNEL_YT_DLP,
            "yt-dlp updates",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "yt-dlp version availability and update results" }
        manager.createNotificationChannels(listOf(playback, downloads, updates))
    }

    companion object {
        private const val TAG = "AtlasApplication"
        const val CHANNEL_PLAYBACK = "atlas.playback"
        const val CHANNEL_DOWNLOADS = "atlas.downloads"
        const val CHANNEL_YT_DLP = "atlas.ytdlp"
    }
}
