package com.atlas.music.downloads

import android.content.Context
import com.atlas.music.data.db.AtlasDatabase
import com.atlas.music.data.db.DownloadStatus
import com.atlas.music.data.db.DownloadTaskEntity
import com.atlas.music.data.repo.MusicRepository
import com.atlas.music.di.SettingsRepository
import com.atlas.music.util.DownloadStateMachine
import com.atlas.music.util.UrlValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

data class YoutubePreview(
    val ok: Boolean,
    val title: String = "",
    val uploader: String = "",
    val durationSec: Int = 0,
    val thumbnailUrl: String = "",
    val playlistCount: Int? = null,
    val formats: List<FormatOption> = emptyList(),
    val error: String? = null,
)

data class FormatOption(
    val formatId: String,
    val ext: String,
    val codec: String,
    val abr: Double?,
    val note: String?,
)

class DownloadManager(
    private val context: Context,
    private val db: AtlasDatabase,
    private val musicRepository: MusicRepository,
    private val settingsRepository: SettingsRepository,
) {
    private val dao = db.downloadDao()
    private val pythonBridge = PythonBridge(context)
    private val processing = Mutex()
    private val cancelRequested = ConcurrentHashMap.newKeySet<Long>()

    val tasks: Flow<List<DownloadTaskEntity>> = dao.observeAll()
    val completedCount: Flow<Int> = dao.observeCompletedCount()

    fun isDownloaderAvailable(): Boolean = pythonBridge.isAvailable()

    fun installedYtdlpVersion(): String {
        val json = pythonBridge.ytdlpVersion()
        return if (json.optBoolean("ok")) json.optString("version", "unknown") else "unavailable"
    }

    /** Validate + preview before any bytes move. Never downloads media. */
    fun preview(url: String): YoutubePreview {
        val canonical = UrlValidator.canonicalUrl(url)
            ?: return YoutubePreview(false, error = "Enter a valid https://www.youtube.com or youtu.be link.")
        val json = pythonBridge.fetchInfo(canonical)
        if (!json.optBoolean("ok")) {
            return YoutubePreview(false, error = json.optString("error", "Couldn't read this link."))
        }
        val formats = json.optJSONArray("formats")
        val options = buildList {
            if (formats != null) {
                for (i in 0 until formats.length()) {
                    val f = formats.getJSONObject(i)
                    add(
                        FormatOption(
                            formatId = f.optString("format_id"),
                            ext = f.optString("ext"),
                            codec = f.optString("acodec"),
                            abr = f.opt("abr")?.toString()?.toDoubleOrNull(),
                            note = f.optString("note").ifBlank { null },
                        )
                    )
                }
            }
        }
        return YoutubePreview(
            ok = true,
            title = json.optString("title").ifBlank { "Untitled video" },
            uploader = json.optString("uploader").ifBlank { "Unknown uploader" },
            durationSec = json.optInt("duration", 0),
            thumbnailUrl = json.optString("thumbnail"),
            playlistCount = json.opt("playlist_count")?.toString()?.toIntOrNull(),
            formats = options,
        )
    }

    /** Enqueue a validated link. Returns the task id, or throws with a user-readable message. */
    suspend fun enqueue(url: String, title: String, uploader: String, thumbnailUrl: String, format: String): Long {
        val canonical = UrlValidator.canonicalUrl(url)
            ?: throw IllegalArgumentException("Enter a valid YouTube link.")
        val link = UrlValidator.parse(canonical) ?: throw IllegalArgumentException("Enter a valid YouTube link.")
        val formatChoice = when (format) {
            "opus", "m4a", "best" -> format
            else -> "opus"
        }
        // Duplicate detection: same video already queued/completed?
        val key = link.stableKey
        check(!dao.hasActiveOrDone(key)) { "This link is already in your downloads." }
        val id = dao.insert(
            DownloadTaskEntity(
                url = canonical,
                videoId = key,
                title = title.ifBlank { "Untitled video" },
                uploader = uploader.ifBlank { "Unknown uploader" },
                thumbnailUrl = thumbnailUrl,
                format = formatChoice,
                status = DownloadStatus.QUEUED,
            )
        )
        DownloadForegroundService.start(context)
        return id
    }

    suspend fun retry(taskId: Long) {
        val task = dao.byId(taskId) ?: return
        val next = DownloadStateMachine.retry(task.status) ?: return
        dao.update(
            task.copy(
                status = next,
                progress = 0f,
                bytesDownloaded = 0,
                bytesTotal = 0,
                error = null,
                completedAtMs = null,
            )
        )
        DownloadForegroundService.start(context)
    }

    suspend fun cancel(taskId: Long) {
        cancelRequested.add(taskId)
        // Hard-stop active row immediately so UI responds; the worker also checks this set.
        dao.byId(taskId)?.let { task ->
            if (DownloadStateMachine.isActive(task.status)) {
                dao.setStatus(taskId, DownloadStatus.CANCELLED, "Cancelled by you.", System.currentTimeMillis())
            }
        }
    }

    suspend fun deleteTask(taskId: Long) {
        cancelRequested.add(taskId) // stop if running
        dao.delete(taskId)
    }

    suspend fun clearCompleted() = dao.clearCompleted()
    suspend fun clearHistory() = dao.clearHistory()

    fun isCancellationRequested(taskId: Long) = taskId in cancelRequested
    fun consumeCancellation(taskId: Long) = cancelRequested.remove(taskId)

    /** Process the queue sequentially. Called only from DownloadForegroundService. */
    suspend fun processQueue(onProgress: (DownloadTaskEntity) -> Unit) {
        processing.withLock {
            while (true) {
                val next = dao.nextActive() ?: break
                if (next.status == DownloadStatus.QUEUED) {
                    runTask(next, onProgress)
                } else {
                    // FETCHING_INFO/DOWNLOADING left over from a crash — mark failed, retryable.
                    dao.setStatus(next.id, DownloadStatus.FAILED, "Interrupted. Tap retry to try again.", System.currentTimeMillis())
                }
            }
        }
    }

    private suspend fun runTask(initial: DownloadTaskEntity, onProgress: (DownloadTaskEntity) -> Unit) {
        val taskId = initial.id
        consumeCancellation(taskId)
        val info = updateStatus(taskId, DownloadStatus.FETCHING_INFO, null)
        onProgress(info)

        // 1) Fetch metadata (metadata only, no media yet).
        val previewResult = pythonBridge.fetchInfo(initial.url)
        if (!previewResult.optBoolean("ok")) {
            failTask(taskId, previewResult.optString("error", "Couldn't read this link."), onProgress)
            return
        }

        // 2) Download to a temp file first; interrupted downloads never appear as tracks.
        val cacheDir = java.io.File(context.cacheDir, "downloads").apply { mkdirs() }
        val taskDir = java.io.File(cacheDir, "task-$taskId").apply { deleteRecursively(); mkdirs() }

        updateStatus(taskId, DownloadStatus.DOWNLOADING, null)
        val startedAt = System.currentTimeMillis()
        val downloadResult = pythonBridge.download(
            url = initial.url,
            outputDir = taskDir.absolutePath,
            format = initial.format,
            progress = { _, downloaded, total, speed, eta ->
                kotlinx.coroutines.runBlocking {
                    val fraction = if (total > 0) downloaded.toFloat() / total.toFloat() else 0f
                    dao.setProgress(taskId, fraction.coerceIn(0f, 1f), downloaded, total, speed, eta)
                    dao.byId(taskId)?.let(onProgress)
                }
            },
            shouldCancel = { isCancellationRequested(taskId) },
        )

        if (!downloadResult.optBoolean("ok")) {
            val cancelled = downloadResult.optBoolean("cancelled")
            val message = downloadResult.optString("error", "Download failed.")
            if (cancelled) {
                dao.setStatus(taskId, DownloadStatus.CANCELLED, "Cancelled by you.", System.currentTimeMillis())
                taskDir.deleteRecursively()
                dao.byId(taskId)?.let(onProgress)
            } else {
                failTask(taskId, message, onProgress)
                taskDir.deleteRecursively()
            }
            return
        }

        // 3) Move the finished file into the library destination.
        val tempPath = downloadResult.optString("path")
        val metaTitle = downloadResult.optJSONObject("meta")?.optString("title")?.takeIf { it.isNotBlank() } ?: initial.title
        val finalPath = moveIntoLibrary(taskDir, tempPath, initial, metaTitle)
        if (finalPath == null) {
            failTask(taskId, "Couldn't save the downloaded file into your music folder.", onProgress)
            taskDir.deleteRecursively()
            return
        }
        taskDir.deleteRecursively()

        dao.setStatus(taskId, DownloadStatus.COMPLETED, null, System.currentTimeMillis())
        dao.byId(taskId)?.let {
            dao.update(it.copy(progress = 1f, outputPath = finalPath, completedAtMs = System.currentTimeMillis()))
            onProgress(it)
        }

        // 4) Import into the library if the user wants it there automatically.
        val autoImport = settingsRepository.settings.first().autoImportDownloads
        if (autoImport) {
            val st = settingsRepository.settings.first()
            runCatching { musicRepository.scan(st.excludedFolders, includeUnsupported = true, customFolderUri = st.musicFolderUri.takeIf { it.isNotBlank() }, customFolderOnly = st.musicFolderOnly) }
        }
    }

    private suspend fun updateStatus(id: Long, status: DownloadStatus, error: String?): DownloadTaskEntity {
        dao.setStatus(id, status, error, if (DownloadStateMachine.isTerminal(status)) System.currentTimeMillis() else null)
        return dao.byId(id) ?: throw IllegalStateException("Download $id not found")
    }

    private suspend fun failTask(id: Long, message: String, onProgress: (DownloadTaskEntity) -> Unit) {
        dao.setStatus(id, DownloadStatus.FAILED, message, System.currentTimeMillis())
        dao.byId(id)?.let(onProgress)
    }

    /** Copy the finished temp file to Music/Atlas Music (MediaStore) or the user's SAF folder. */
    private suspend fun moveIntoLibrary(
        taskDir: java.io.File,
        tempPath: String,
        task: DownloadTaskEntity,
        title: String,
    ): String? {
        val source = java.io.File(tempPath).takeIf { it.isFile }
            ?: taskDir.listFiles()?.maxByOrNull { it.lastModified() }?.takeIf { it.isFile }
            ?: return null
        val destUri = settingsRepository.settings.first().downloadDestinationUri
        return if (destUri.isBlank()) {
            moveToMediaStore(source, title)
        } else {
            moveToSafTree(source, destUri, title)
        }
    }

    private fun moveToMediaStore(source: java.io.File, title: String): String? {
        val ext = source.extension.ifBlank { "opus" }
        val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
        val displayName = "$safeTitle [atlas].$ext"
        val resolver = context.contentResolver
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(android.provider.MediaStore.Audio.Media.MIME_TYPE, mimeForExtension(ext))
            put(android.provider.MediaStore.Audio.Media.RELATIVE_PATH, "Music/Atlas Music")
            put(android.provider.MediaStore.Audio.Media.IS_PENDING, 1)
            put(android.provider.MediaStore.Audio.Media.TITLE, title)
        }
        val uri = resolver.insert(android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("Unable to open destination stream")
            val done = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Audio.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            uri.toString()
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    }

    private fun moveToSafTree(source: java.io.File, treeUri: String, title: String): String? {
        val tree = android.net.Uri.parse(treeUri)
        val resolver = context.contentResolver
        val root = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, tree) ?: return null
        val ext = source.extension.ifBlank { "opus" }
        val safeTitle = title.replace(Regex("[/\\\\:*?\"<>|]"), "_").take(80)
        val doc = root.createFile(mimeForExtension(ext), "$safeTitle [atlas].$ext") ?: return null
        return try {
            resolver.openOutputStream(doc.uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
                ?: return null
            doc.uri.toString()
        } catch (e: Exception) {
            runCatching { doc.delete() }
            null
        }
    }

    private fun mimeForExtension(ext: String): String = when (ext.lowercase()) {
        "opus", "ogg" -> "audio/ogg"
        "webm" -> "audio/webm"
        "m4a", "mp4" -> "audio/mp4"
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "aac" -> "audio/aac"
        else -> "audio/*"
    }
}
