package com.atlas.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.atlas.music.R
import com.atlas.music.di.ServiceLocator
import kotlinx.coroutines.launch

class SleepWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { updateWidget(context, manager, it) }
    }
    companion object {
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        fun updateAll(context: Context) {
            val app = context.applicationContext
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, SleepWidgetProvider::class.java))
                    ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
                } catch (e: Exception) {
                    android.util.Log.w("SleepWidget", "updateAll failed", e)
                }
            }
        }
        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_sleep)
            val end = runCatching { ServiceLocator.playbackConnection.sleepTimerEndAt.value }.getOrNull()
            val text = if (end != null && end > System.currentTimeMillis()) {
                val rem = (end - System.currentTimeMillis()) / 1000
                "%d:%02d left".format(rem / 60, rem % 60)
            } else "No timer"
            views.setTextViewText(R.id.sleep_time, text)
            val sleep30 = android.app.PendingIntent.getBroadcast(context, 0, Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_SLEEP30), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.sleep_button, sleep30)
            val open = android.app.PendingIntent.getActivity(context, 1, Intent(context, com.atlas.music.MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.sleep_root, open)
            return views
        }
        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context.applicationContext))
        }
    }
}
