package com.atlas.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.atlas.music.R
import com.atlas.music.di.ServiceLocator
import com.atlas.music.playback.AtlasBitmapLoader
import kotlinx.coroutines.launch

class AtlasLargeWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // Build bitmap + RemoteViews on IO, not the main thread.
        updateAll(context)
    }
    companion object {
        private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        private val cache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, android.graphics.Bitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, android.graphics.Bitmap>): Boolean = size > 12
        })
        fun updateAll(context: Context) {
            val app = context.applicationContext
            scope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, AtlasLargeWidgetProvider::class.java))
                    ids.forEach { id -> manager.updateAppWidget(id, buildViews(app)) }
                } catch (e: Exception) {
                    android.util.Log.w("LargeWidget", "updateAll failed", e)
                }
            }
        }
        private fun buildViews(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_atlas_large)
            val state = runCatching { ServiceLocator.playbackConnection.state.value }.getOrNull()
            views.setTextViewText(R.id.large_title, state?.title?.takeIf { it.isNotBlank() } ?: "No track playing")
            views.setTextViewText(R.id.large_artist, (state?.artist?.takeIf { it.isNotBlank() } ?: "Atlas Music") + (state?.album?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""))
            views.setImageViewResource(R.id.large_play, if (state?.isPlaying == true) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            // Progress (throttled by caller)
            val dur = state?.durationMs ?: 0L
            val pos = state?.positionMs ?: 0L
            views.setProgressBar(R.id.large_progress, 100, if (dur > 0) ((pos * 100) / dur).toInt() else 0, false)
            // Cover
            var bmp: android.graphics.Bitmap? = null
            if (state != null && state.title.isNotBlank()) {
                val key = (state.title + "|" + state.artist + "|" + state.album + "|")
                bmp = cache[key] ?: runCatching { AtlasBitmapLoader(context).coverForTrack(state.title, state.artist, state.album) }.getOrNull()?.also { cache[key] = it }
            }
            if (bmp != null) views.setImageViewBitmap(R.id.large_cover, bmp) else views.setImageViewResource(R.id.large_cover, R.drawable.ic_launcher_foreground)
            fun broadcast(action: String, code: Int) = android.app.PendingIntent.getBroadcast(context, code, Intent(context, WidgetActionReceiver::class.java).setAction(action), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.large_shuffle, broadcast(WidgetActionReceiver.ACTION_SHUFFLE, 1))
            views.setOnClickPendingIntent(R.id.large_prev, broadcast(WidgetActionReceiver.ACTION_PREV, 2))
            views.setOnClickPendingIntent(R.id.large_play, broadcast(WidgetActionReceiver.ACTION_TOGGLE, 3))
            views.setOnClickPendingIntent(R.id.large_next, broadcast(WidgetActionReceiver.ACTION_NEXT, 4))
            views.setOnClickPendingIntent(R.id.large_repeat, broadcast(WidgetActionReceiver.ACTION_REPEAT, 5))
            views.setOnClickPendingIntent(R.id.large_like, broadcast(WidgetActionReceiver.ACTION_LIKE, 6))
            val open = android.app.PendingIntent.getActivity(context, 7, Intent(context, com.atlas.music.MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.large_root, open)
            views.setOnClickPendingIntent(R.id.large_title, open)
            views.setOnClickPendingIntent(R.id.large_artist, open)
            views.setOnClickPendingIntent(R.id.large_cover, open)
            return views
        }
        private fun updateWidget(context: Context, manager: AppWidgetManager, id: Int) {
            manager.updateAppWidget(id, buildViews(context.applicationContext))
        }
    }
}
