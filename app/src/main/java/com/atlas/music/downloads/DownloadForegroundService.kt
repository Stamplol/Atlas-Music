package com.atlas.music.downloads

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.atlas.music.AtlasApplication
import com.atlas.music.MainActivity
import com.atlas.music.R
import com.atlas.music.data.db.DownloadStatus
import com.atlas.music.data.db.DownloadTaskEntity
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Sequential download queue. Lives as a foreground service so an in-progress
 * download survives the UI being closed. Never used for anything but downloads.
 */
class DownloadForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var running = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1)
                if (taskId != -1L) scope.launch { ServiceLocator.downloadManager.cancel(taskId) }
                return START_NOT_STICKY
            }
            else -> startProcessing()
        }
        return START_NOT_STICKY
    }

    private fun startProcessing() {
        if (running) return
        running = true
        startForeground(NOTIFICATION_ID, buildNotification(null))
        scope.launch {
            try {
                ServiceLocator.downloadManager.processQueue { task ->
                    updateNotification(task)
                }
            } finally {
                running = false
                // Queue drained — stop cleanly, leaving the last result visible in Downloads.
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun updateNotification(task: DownloadTaskEntity) {
        val notification = buildNotification(task)
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(task: DownloadTaskEntity?): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title = when {
            task == null -> "Downloads"
            task.status == DownloadStatus.FETCHING_INFO -> "Reading link…"
            task.status == DownloadStatus.DOWNLOADING -> "Downloading “${task.title.take(60)}”"
            task.status == DownloadStatus.COMPLETED -> "Saved “${task.title.take(60)}”"
            task.status == DownloadStatus.FAILED -> "Download failed"
            task.status == DownloadStatus.CANCELLED -> "Download cancelled"
            else -> "Queued “${task.title.take(60)}”"
        }
        val progress = if (task != null && task.status == DownloadStatus.DOWNLOADING) (task.progress * 100).toInt() else 0
        val indeterminate = task?.status == DownloadStatus.FETCHING_INFO
        return NotificationCompat.Builder(this, AtlasApplication.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setOngoing(task != null && task.status == DownloadStatus.DOWNLOADING)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_CANCEL = "com.atlas.music.downloads.CANCEL"
        const val EXTRA_TASK_ID = "task_id"
        private const val NOTIFICATION_ID = 7001

        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context, taskId: Long) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_TASK_ID, taskId)
            }
            context.startService(intent)
        }
    }
}
