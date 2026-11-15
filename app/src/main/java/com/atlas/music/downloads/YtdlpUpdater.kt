package com.atlas.music.downloads

import android.content.Context
import com.atlas.music.di.SettingsRepository
import com.atlas.music.util.YtdlpVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

data class UpdateCheck(
    val currentVersion: String,
    val latestVersion: String,
    val updateAvailable: Boolean,
    val releasePageUrl: String,
)

sealed class UpdateResult {
    data class Success(val version: String) : UpdateResult()
    data class Failed(val message: String) : UpdateResult()
}

/**
 * Safe yt-dlp runtime updater.
 *
 * Strategy: the app ships a pinned yt-dlp at build time. This updater can fetch
 * the newest *official* yt-dlp wheel from PyPI, verifies its SHA-256 against the
 * PyPI JSON index, unpacks it under filesDir/ytdlp-updates/, and only then makes
 * it active. The bundled copy is never modified or deleted; a bad update simply
 * never becomes ACTIVE, so the app can't be left without yt-dlp.
 */
class YtdlpUpdater(
    private val appContext: Context,
    private val settingsRepository: SettingsRepository,
) {
    private val client = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(15, TimeUnit.SECONDS)
        .build()

    private val bridge = PythonBridge(appContext)

    /** Installed (bundled or updated) version string, e.g. "2025.10.22". */
    fun installedVersion(): String {
        val json = bridge.ytdlpVersion()
        return if (json.optBoolean("ok")) json.optString("version", "unknown") else "unavailable"
    }

    suspend fun checkForUpdate(): Result<UpdateCheck> = withContext(Dispatchers.IO) {
        runCatching {
            val currentRaw = installedVersion()
            val current = YtdlpVersion.parse(currentRaw)
            val latestTag = fetchLatest() ?: throw IllegalStateException("Neither PyPI nor GitHub is reachable right now.")
            val latest = YtdlpVersion.parse(latestTag.tag) ?: throw IllegalStateException("Unparseable release tag: ${latestTag.tag}")
            UpdateCheck(
                currentVersion = currentRaw,
                latestVersion = latest.toString(),
                updateAvailable = current != null && latest > current,
                releasePageUrl = latestTag.pageUrl,
            )
        }
    }

    /** Download + verify + activate the official wheel. Never throws. */
    suspend fun applyUpdate(): UpdateResult = withContext(Dispatchers.IO) {
        try {
            val latestRaw = fetchLatest()?.tag ?: return@withContext UpdateResult.Failed("Couldn't reach the yt-dlp release source (PyPI and GitHub both offline). Check your connection and try again.")
            val latest = YtdlpVersion.parse(latestRaw) ?: return@withContext UpdateResult.Failed("Couldn't understand the latest yt-dlp version ($latestRaw).")
            val current = YtdlpVersion.parse(installedVersion())
            if (current != null && latest <= current) {
                return@withContext UpdateResult.Success(current.toString()) // already up to date
            }
            val wheel = findWheel(latest.toString()) ?: return@withContext UpdateResult.Failed("No wheel for yt-dlp $latest found on PyPI.")
            val wheelFile = File(appContext.cacheDir, "yt_dlp-$latest.whl")
            downloadTo(wheel.url, wheelFile)
            val actualSha = sha256OfFile(wheelFile)
            if (!actualSha.equals(wheel.sha256, ignoreCase = true)) {
                wheelFile.delete()
                return@withContext UpdateResult.Failed("Downloaded wheel failed its SHA-256 check. The update was discarded and your current yt-dlp is unchanged.")
            }
            val updatesRoot = bridge.updatesRoot.apply { mkdirs() }
            val stageDir = File(updatesRoot, "yt_dlp-$latest.staging")
            stageDir.deleteRecursively()
            stageDir.mkdirs()
            unzipWheel(wheelFile, stageDir)
            if (!File(stageDir, "yt_dlp").isDirectory) {
                stageDir.deleteRecursively()
                wheelFile.delete()
                return@withContext UpdateResult.Failed("Downloaded package didn't contain a yt-dlp module. Update aborted.")
            }
            val finalDir = File(updatesRoot, "yt_dlp-$latest")
            finalDir.deleteRecursively()
            if (!stageDir.renameTo(finalDir)) {
                stageDir.deleteRecursively()
                return@withContext UpdateResult.Failed("Couldn't activate the update. Your current yt-dlp is unchanged.")
            }
            // Activate: point Python at the new directory, then verify it actually imports.
            File(updatesRoot, "ACTIVE").writeText(finalDir.name)
            val verify = PythonBridge(appContext).ytdlpVersion() // fresh bridge picks up new ACTIVE
            wheelFile.delete()
            if (verify.optBoolean("ok") && YtdlpVersion.parse(verify.optString("version")) == latest) {
                UpdateResult.Success(latest.toString())
            } else {
                // Roll back: remove ACTIVE so the bundled copy is used.
                File(updatesRoot, "ACTIVE").delete()
                finalDir.deleteRecursively()
                UpdateResult.Failed("The update didn't pass a post-install check, so Atlas Music rolled back to the bundled yt-dlp ${installedVersion()}.")
            }
        } catch (e: Exception) {
            // Best-effort cleanup: remove leftover staging dirs and cached wheels.
            runCatching {
                bridge.updatesRoot.listFiles()?.filter { it.isDirectory && it.name.endsWith(".staging") }?.forEach { it.deleteRecursively() }
                appContext.cacheDir.listFiles()?.filter { it.name.startsWith("yt_dlp-") && it.name.endsWith(".whl") }?.forEach { it.delete() }
            }
            UpdateResult.Failed(e.message?.take(300) ?: "Update failed for an unknown reason. Your current yt-dlp is unchanged.")
        }
    }

    /** Remove all runtime updates and fall back to the bundled yt-dlp. */
    suspend fun restoreBundled(): Boolean = withContext(Dispatchers.IO) {
        val root = bridge.updatesRoot
        File(root, "ACTIVE").delete()
        root.listFiles()?.filter { it.isDirectory }?.forEach { it.deleteRecursively() }
        // Evict the cached interpreter state in this process too, so the bundled
        // version reports correctly without an app restart.
        PythonBridge(appContext).clearUpdateOverride()
        true
    }

    /** Latest version tag, PyPI first (same source as the wheel download, no auth/rate limit),
     *  GitHub releases/latest as fallback for CDNs that mirror GitHub but not PyPI. */
    private data class LatestTag(val tag: String, val pageUrl: String)

    private fun fetchLatest(): LatestTag? = fetchPypiLatestVersionTag() ?: fetchGithubLatestVersionTag()

    private fun fetchPypiLatestVersionTag(): LatestTag? {
        val request = Request.Builder().url("https://pypi.org/pypi/yt-dlp/json").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val version = json.optJSONObject("info")?.optString("version")?.takeIf { it.isNotBlank() } ?: return null
            LatestTag(version, "https://pypi.org/project/yt-dlp/")
        }
    }

    private fun fetchGithubLatestVersionTag(): LatestTag? {
        val request = Request.Builder()
            .url("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AtlasMusic/1.0 (Android)")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val body = response.body?.string() ?: return null
            val tag = JSONObject(body).optString("tag_name").takeIf { it.isNotBlank() } ?: return null
            LatestTag(tag, JSONObject(body).optString("html_url", "https://github.com/yt-dlp/yt-dlp/releases"))
        }
    }

    private data class Wheel(val url: String, val sha256: String)

    private fun findWheel(version: String): Wheel? {
        val request = Request.Builder().url("https://pypi.org/pypi/yt-dlp/json").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            val json = JSONObject(response.body?.string() ?: return null)
            val releases = json.optJSONObject("releases") ?: return null
            val files = releases.optJSONArray(version) ?: return null
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                val filename = f.optString("filename")
                if (filename.endsWith(".whl") && filename.contains("py3-none-any")) {
                    val sha = f.optJSONObject("digests")?.optString("sha256") ?: return null
                    return Wheel(f.optString("url"), sha)
                }
            }
            return null
        }
    }

    private fun downloadTo(url: String, target: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed with HTTP ${response.code}" }
            response.body?.byteStream()?.use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output, 64 * 1024) }
            } ?: error("Empty response body")
        }
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun unzipWheel(wheel: File, destDir: File) {
        ZipInputStream(wheel.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            val destRoot = destDir.canonicalFile
            while (entry != null) {
                val outFile = File(destDir, entry.name).canonicalFile
                // Zip-slip guard: never write outside the staging directory.
                check(outFile.path.startsWith(destRoot.path + File.separator) || outFile.path == destRoot.path) {
                    "Unsafe path in wheel: ${entry.name}"
                }
                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
