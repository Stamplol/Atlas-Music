package com.atlas.music.downloads

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyException
import com.chaquo.python.PyObject
import org.json.JSONObject
import java.io.File

/** Thin, failure-safe wrapper around the bundled yt-dlp Python module. */
class PythonBridge(private val appContext: Context) {

    @Volatile private var module: PyObject? = null

    /** Directory the app may prepend to sys.path for a runtime-updated yt-dlp. */
    val updatesRoot: File get() = File(appContext.filesDir, "ytdlp-updates")

    /** The active (newest, verified) update directory, or null to use the bundled yt-dlp. */
    fun activeUpdateDir(): File? {
        val pointer = File(updatesRoot, "ACTIVE")
        if (!pointer.isFile) return null
        val name = pointer.readText().trim()
        if (name.isEmpty()) return null
        val dir = File(updatesRoot, name)
        return if (dir.isDirectory) dir else null
    }

    @Synchronized
    private fun ytdlpModule(): PyObject? {
        if (!Python.isStarted()) return null
        if (module == null) {
            runCatching {
                val mod = Python.getInstance().getModule("atlas_ytdlp")
                activeUpdateDir()?.let { dir ->
                    mod.callAttr("set_update_dir", dir.absolutePath)
                }
                module = mod
            }
        }
        return module
    }

    fun isAvailable(): Boolean = ytdlpModule() != null

    private fun call(function: String, vararg args: Any?): JSONObject {
        val mod = ytdlpModule() ?: return JSONObject().put("ok", false).put("error", "Python runtime unavailable on this device.")
        return try {
            val result = mod.callAttr(function, *args)
            JSONObject(result.toString())
        } catch (e: PyException) {
            JSONObject().put("ok", false).put("error", e.message?.take(400) ?: "Downloader error")
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", (e.message ?: "Unexpected downloader error").take(400))
        }
    }

    fun ytdlpVersion(): JSONObject {
        val result = call("ytdlp_version")
        android.util.Log.i("AtlasYt", "ytdlp_version -> $result")
        return result
    }

    fun fetchInfo(url: String): JSONObject = call("fetch_info", url, "best")

    fun download(
        url: String,
        outputDir: String,
        format: String,
        progress: (state: String, downloaded: Long, total: Long, speed: Long, etaSeconds: Long) -> Unit,
        shouldCancel: () -> Boolean,
    ): JSONObject {
        val mod = ytdlpModule() ?: return JSONObject().put("ok", false).put("error", "Python runtime unavailable on this device.")
        return callDownload(mod, url, outputDir, format, progress, shouldCancel)
    }

    private fun callDownload(
        mod: PyObject,
        url: String,
        outputDir: String,
        format: String,
        progress: (String, Long, Long, Long, Long) -> Unit,
        shouldCancel: () -> Boolean,
    ): JSONObject {
        return try {
            val callbacks = AtlasProgressCallbacks(progress, shouldCancel)
            val result = mod.callAttr("download", url, outputDir, format, callbacks.progressListener, callbacks.cancelCheck)
            JSONObject(result.toString())
        } catch (e: PyException) {
            JSONObject().put("ok", false).put("error", e.message?.take(400) ?: "Download failed")
        } catch (e: Exception) {
            JSONObject().put("ok", false).put("error", (e.message ?: "Download failed").take(400))
        }
    }

    /** Tell Python to drop any runtime-override directory and cached modules. */
    fun clearUpdateOverride(): JSONObject = call("set_update_dir", "")

    fun sha256Of(path: String): JSONObject = call("sha256_file", path)

    fun writeTags(path: String, title: String, artist: String, album: String, genre: String, year: Int, track: Int, disc: Int): JSONObject =
        call("write_tags", path, title, artist, album, genre, year, track, disc)
}

/** Bridge object passed to Python; Chaquopy exposes public methods to the interpreter. */
class AtlasProgressCallbacks(
    private val onProgress: (String, Long, Long, Long, Long) -> Unit,
    private val onShouldCancel: () -> Boolean,
) {
    val progressListener: ProgressListener = object : ProgressListener {
        override fun onProgress(state: String, downloaded: Long, total: Long, speed: Long, eta: Long) {
            onProgress(state, downloaded, total, speed, eta)
        }
    }
    val cancelCheck: CancelCheck = object : CancelCheck {
        override fun shouldCancel(): Boolean = onShouldCancel()
    }

    interface ProgressListener {
        fun onProgress(state: String, downloaded: Long, total: Long, speed: Long, eta: Long)
    }
    interface CancelCheck {
        fun shouldCancel(): Boolean
    }
}
