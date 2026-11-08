package com.atlas.music.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import com.atlas.music.R
import com.atlas.music.di.ServiceLocator
import com.atlas.music.playback.AtlasBitmapLoader
import kotlinx.coroutines.launch

/**
 * Home-screen widget — cover + title/artist + prev/play/next.
 * Tapping the cover/title opens the app (Now Playing).
 */
class AtlasWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        // System callback is on the main thread — delegate to async updateAll (IO bitmap gen).
        updateAll(context)
    }

    companion object {
        const val ACTION_WIDGET_UPDATE = "com.atlas.music.WIDGET_UPDATE"
        private val bitmapCache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, Bitmap>(8, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean = size > 12
        })

        private val widgetScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)
        fun updateAll(context: Context) {
            val app = context.applicationContext
            widgetScope.launch {
                try {
                    val manager = AppWidgetManager.getInstance(app)
                    val ids = manager.getAppWidgetIds(ComponentName(app, AtlasWidgetProvider::class.java))
                    ids.forEach { id ->
                        val views = buildViews(app)
                        manager.updateAppWidget(id, views)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AtlasWidget", "updateAll failed", e)
                }
            }
        }
        private fun buildViews(context: Context): RemoteViews {
            // Build on IO thread (bitmap gen), return RemoteViews ready to post.
            val views = RemoteViews(context.packageName, R.layout.widget_atlas)
            val state = runCatching { ServiceLocator.playbackConnection.state.value }.getOrNull()
            val title = state?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.widget_no_track)
            val artist = state?.artist?.takeIf { it.isNotBlank() } ?: "Atlas Music"
            views.setTextViewText(R.id.widget_title, title)
            views.setTextViewText(R.id.widget_artist, artist)
            views.setImageViewResource(R.id.widget_play, if (state?.isPlaying == true) R.drawable.ic_widget_pause else R.drawable.ic_widget_play)
            var bitmap: Bitmap? = null
            if (state != null && state.title.isNotBlank()) {
                val key = (state.title + "|" + state.artist + "|" + state.album + "|")
                bitmap = bitmapCache[key] ?: runCatching { AtlasBitmapLoader(context).coverForTrack(state.title, state.artist, state.album) }.getOrNull()?.also { bitmapCache[key] = it }
            }
            if (bitmap != null) views.setImageViewBitmap(R.id.widget_cover, bitmap) else views.setImageViewResource(R.id.widget_cover, R.drawable.ic_launcher_foreground)
            fun broadcast(action: String, code: Int) = android.app.PendingIntent.getBroadcast(context, code, Intent(context, WidgetActionReceiver::class.java).setAction(action), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_prev, broadcast(WidgetActionReceiver.ACTION_PREV, 1))
            views.setOnClickPendingIntent(R.id.widget_play, broadcast(WidgetActionReceiver.ACTION_TOGGLE, 2))
            views.setOnClickPendingIntent(R.id.widget_next, broadcast(WidgetActionReceiver.ACTION_NEXT, 3))
            val openIntent = android.app.PendingIntent.getActivity(context, 4, Intent(context, com.atlas.music.MainActivity::class.java), android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, openIntent)
            views.setOnClickPendingIntent(R.id.widget_cover, openIntent)
            views.setOnClickPendingIntent(R.id.widget_title, openIntent)
            views.setOnClickPendingIntent(R.id.widget_artist, openIntent)
            return views
        }
        fun updateWidget(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            manager.updateAppWidget(appWidgetId, buildViews(context.applicationContext))
        }
    }
}
