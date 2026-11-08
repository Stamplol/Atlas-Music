package com.atlas.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.atlas.music.R
import kotlinx.coroutines.launch

class ToolsWidgetProvider : AppWidgetProvider() {
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
                    val ids = manager.getAppWidgetIds(ComponentName(app, ToolsWidgetProvider::class.java))
                    ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
                } catch (e: Exception) {
                    android.util.Log.w("ToolsWidget", "updateAll failed", e)
                }
            }
        }

        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_tools)
            fun broadcast(action: String, code: Int) = android.app.PendingIntent.getBroadcast(
                context, code,
                Intent(context, WidgetActionReceiver::class.java).setAction(action),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.tools_play, broadcast(WidgetActionReceiver.ACTION_TOGGLE, 1))
            views.setOnClickPendingIntent(R.id.tools_next, broadcast(WidgetActionReceiver.ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.tools_sleep, broadcast(WidgetActionReceiver.ACTION_SLEEP30, 3))
            val open = android.app.PendingIntent.getActivity(
                context, 4,
                Intent(context, com.atlas.music.MainActivity::class.java),
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.tools_open, open)
            views.setOnClickPendingIntent(R.id.tools_root, open)
            return views
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context.applicationContext))
        }
    }
}
