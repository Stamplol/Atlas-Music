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
import kotlinx.coroutines.flow.first

class StatsWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // DB reads on IO — main thread stays free for the homescreen.
        updateAll(context)
    }
    companion object {
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        fun updateAll(context: Context) {
            val app = context.applicationContext
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, StatsWidgetProvider::class.java))
                    ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
                } catch (e: Exception) {
                    android.util.Log.w("StatsWidget", "updateAll failed", e)
                }
            }
        }
        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_stats)
            var tracks = 0; var hours = 0L; var minutes = 0
            try {
                val repo = ServiceLocator.musicRepository
                tracks = runBlockingSuspend { repo.observeTrackCount().first() }
                val totalMs = runBlockingSuspend { repo.observeTotalDurationMs().first() }
                hours = totalMs / 3_600_000L
                val todayStart = startOfToday()
                val sessions = runBlockingSuspend { ServiceLocator.database.pomodoroDao().observeAll().first() }
                minutes = sessions.filter { it.startedAtMs >= todayStart && it.kind == "FOCUS" && it.completed }.sumOf { it.durationMinutes }
            } catch (e: Exception) {}
            val streak = runBlockingSuspend {
                val focus = ServiceLocator.database.pomodoroDao().observeAll().first()
                    .filter { it.kind == "FOCUS" && it.completed }
                if (focus.isEmpty()) return@runBlockingSuspend 0
                val days = focus.map { row ->
                    val cal = java.util.Calendar.getInstance(); cal.timeInMillis = row.startedAtMs
                    listOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
                }.toSet()
                val cal = java.util.Calendar.getInstance()
                fun key() = listOf(cal.get(java.util.Calendar.YEAR), cal.get(java.util.Calendar.DAY_OF_YEAR))
                if (key() !in days) { cal.add(java.util.Calendar.DAY_OF_YEAR, -1); if (key() !in days) return@runBlockingSuspend 0 }
                var n = 0
                while (key() in days) { n++; cal.add(java.util.Calendar.DAY_OF_YEAR, -1) }
                n
            }
            views.setTextViewText(R.id.stats_tracks, tracks.toString())
            views.setTextViewText(R.id.stats_hours, "${hours}h")
            views.setTextViewText(R.id.stats_minutes, "${minutes}m")
            views.setTextViewText(R.id.stats_streak, "${streak}d")
            val open = android.app.PendingIntent.getActivity(context, 0, Intent(context, com.atlas.music.MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.stats_root, open)
            return views
        }
        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context.applicationContext))
        }
        private fun startOfToday(): Long {
            val cal = java.util.Calendar.getInstance()
            cal.set(java.util.Calendar.HOUR_OF_DAY, 0); cal.set(java.util.Calendar.MINUTE, 0)
            cal.set(java.util.Calendar.SECOND, 0); cal.set(java.util.Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }
        private fun <T> runBlockingSuspend(block: suspend () -> T): T = kotlinx.coroutines.runBlocking { block() }
    }
}
