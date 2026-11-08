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

class PomodoroWidgetProvider : AppWidgetProvider() {
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
                    val ids = manager.getAppWidgetIds(ComponentName(app, PomodoroWidgetProvider::class.java))
                    ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
                } catch (e: Exception) {
                    android.util.Log.w("PomodoroWidget", "updateAll failed", e)
                }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_pomodoro)
            // Read remaining from DataStore-backed timer file (written by ToolsViewModel).
            var remainingText = "25:00"
            var modeText = "FOCUS"
            try {
                val prefs = context.getSharedPreferences("atlas_pomodoro", Context.MODE_PRIVATE)
                val endAt = prefs.getLong("end_at", 0L)
                val mode = prefs.getString("mode", "FOCUS") ?: "FOCUS"
                modeText = mode
                if (endAt > 0) {
                    val rem = (endAt - System.currentTimeMillis()) / 1000
                    if (rem < 0) remainingText = "0:00" else remainingText = "%d:%02d".format(rem / 60, rem % 60)
                }
            } catch (e: Exception) {}
            views.setTextViewText(R.id.pomo_time, remainingText)
            views.setTextViewText(R.id.pomo_mode, modeText)
            val toggle = android.app.PendingIntent.getBroadcast(
                context, 0,
                Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_POMODORO_TOGGLE),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.pomo_toggle, toggle)
            val open = android.app.PendingIntent.getActivity(
                context, 1,
                Intent(context, com.atlas.music.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.pomo_root, open)
            views.setOnClickPendingIntent(R.id.pomo_time, open)
            return views
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context.applicationContext))
        }
    }
}
