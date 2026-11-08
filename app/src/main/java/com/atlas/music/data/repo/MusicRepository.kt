package com.atlas.music.data.repo

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import java.util.Locale
import android.provider.MediaStore
import com.atlas.music.data.db.AtlasDatabase
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.util.DuplicateDetector
import com.atlas.music.util.LibraryFolder
import com.atlas.music.util.SortOption
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ScanResult(
    val added: Int,
    val updated: Int,
    val removed: Int,
    val skippedExcluded: Int,
    val durationMs: Long,
)

class MusicRepository(private val context: Context, private val db: AtlasDatabase) {

    private val trackDao = db.trackDao()
    private val scanMutex = Mutex()

    companion object { private const val TAG = "AtlasScan" }

    fun observeAll(sort: SortOption): Flow<List<TrackEntity>> = trackDao.observeAll().map { list ->
        when (sort) {
            SortOption.TITLE -> list.sortedBy { it.title.lowercase() }
            SortOption.ARTIST -> list.sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
            SortOption.ALBUM -> list.sortedWith(compareBy({ it.album.lowercase() }, { it.discNumber }, { it.trackNumber }))
            SortOption.DURATION -> list.sortedBy { it.durationMs }
            SortOption.DATE_ADDED -> list.sortedByDescending { it.dateAddedSec }
            SortOption.YEAR -> list.sortedByDescending { it.year }
            SortOption.TRACK_NUMBER -> list.sortedBy { it.trackNumber }
        }
    }

    fun observeFavorites() = trackDao.observeFavorites()
    fun observeRecentlyPlayed() = trackDao.observeRecentlyPlayed()
    fun observeRecentlyAdded() = trackDao.observeRecentlyAdded()
    fun observeMostPlayed() = trackDao.observeMostPlayed()
    fun observeFolders() = trackDao.observeFolders()
    fun observeFolderCounts() = trackDao.observeFolderCounts()
    fun observeFolderTracks(folder: String) = trackDao.observeFolder(folder)
    fun observeAlbums() = trackDao.observeAlbums()
    fun observeArtists() = trackDao.observeArtists()
    fun observeGenres() = trackDao.observeGenres()
    fun observeAlbumCounts() = trackDao.observeAlbumCounts()
    fun observeArtistCounts() = trackDao.observeArtistCounts()
    fun observeGenreCounts() = trackDao.observeGenreCounts()
    fun observeAlbumTracks(album: String) = trackDao.observeAlbumTracks(album)
    fun observeArtistTracks(artist: String) = trackDao.observeArtistTracks(artist)
    fun observeGenreTracks(genre: String) = trackDao.observeGenreTracks(genre)
    fun observeTrackCount() = trackDao.observeTrackCount()
    fun observeTotalDurationMs() = trackDao.observeTotalDurationMs()
    fun observeTotalSizeBytes() = trackDao.observeTotalSizeBytes()

    suspend fun findDuplicates(): List<DuplicateDetector.DuplicateGroup> =
        DuplicateDetector.findDuplicates(trackDao.getAll())

    /** Full or incremental library scan. Never call on main thread.
     *  When [customFolderUri] is set and [customFolderOnly] is true, only that SAF folder is scanned
     *  (MediaStore skipped). When [customFolderOnly] is false, SAF folder is added on top of MediaStore. */
    suspend fun scan(
        excludedFolders: Set<String>,
        includeUnsupported: Boolean,
        customFolderUri: String? = null,
        customFolderOnly: Boolean = true,
    ): ScanResult =
        withContext(Dispatchers.IO) {
            scanMutex.withLock { scanLocked(excludedFolders, includeUnsupported, customFolderUri, customFolderOnly) }
        }

    private suspend fun scanLocked(
        excludedFolders: Set<String>,
        includeUnsupported: Boolean,
        customFolderUri: String? = null,
        customFolderOnly: Boolean = true,
    ): ScanResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            android.util.Log.i(TAG, "scan started (excluded=$excludedFolders, includeUnsupported=$includeUnsupported, customFolder=$customFolderUri, only=$customFolderOnly)")
            val resolver = context.contentResolver
            val existing = trackDao.getAll().associateBy { it.uri }
            val seenUris = mutableSetOf<String>()
            var added = 0
            var updated = 0
            var skipped = 0

            val useCustomOnly = !customFolderUri.isNullOrBlank() && customFolderOnly
            val batch = mutableListOf<TrackEntity>()
            if (!useCustomOnly) {
            val projection = arrayOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.GENRE,
                MediaStore.Audio.Media.YEAR,
                MediaStore.Audio.Media.TRACK,
                MediaStore.Audio.Media.DISC_NUMBER,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_ADDED,
                MediaStore.Audio.Media.DATE_MODIFIED,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.DISPLAY_NAME,
                MediaStore.Audio.Media.MIME_TYPE,
                MediaStore.Audio.Media.RELATIVE_PATH,
            )
            // Untagged audio often has IS_MUSIC = NULL — include it rather than
            // silently hiding files that have no metadata at all.
            val selection = buildString {
                append("(${MediaStore.Audio.Media.IS_MUSIC} IS NULL OR ${MediaStore.Audio.Media.IS_MUSIC} != 0) ")
                append("AND (${MediaStore.Audio.Media.IS_NOTIFICATION} IS NULL OR ${MediaStore.Audio.Media.IS_NOTIFICATION} = 0) ")
                append("AND (${MediaStore.Audio.Media.IS_RINGTONE} IS NULL OR ${MediaStore.Audio.Media.IS_RINGTONE} = 0) ")
                append("AND (${MediaStore.Audio.Media.IS_ALARM} IS NULL OR ${MediaStore.Audio.Media.IS_ALARM} = 0)")
            }
            val sortOrder = "${MediaStore.Audio.Media.DATE_MODIFIED} ASC"

            android.util.Log.i(TAG, "selection = [$selection]")
            val probe = try {
                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    arrayOf("_id"),
                    null,
                    null,
                    null,
                )?.use { it.count } ?: -1
            } catch (e: Exception) {
                android.util.Log.e(TAG, "selectionless probe failed", e)
                -2
            }
            android.util.Log.i(TAG, "selectionless probe count = $probe")
            val cursor: Cursor? = try {
                resolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder,
                )
            } catch (e: Exception) {
                android.util.Log.e(TAG, "MediaStore query failed", e)
                throw e
            }
            android.util.Log.i(TAG, "MediaStore cursor = ${cursor?.count ?: "null"} rows")
            cursor?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val genreIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
                val yearIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val trackIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val discIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISC_NUMBER)
                val durationIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val dateAddedIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val dataIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val nameIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeIdx = c.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val relPathIdx = c.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
                    val relativePath = if (relPathIdx >= 0) c.getString(relPathIdx) else null
                    val folder = LibraryFolder.normalize(relativePath)
                    if (LibraryFolder.isExcluded(folder, excludedFolders)) {
                        skipped++
                        continue
                    }
                    val title = c.getString(titleIdx)?.takeIf { it.isNotBlank() }
                        ?: c.getString(nameIdx)?.substringBeforeLast('.') ?: "Unknown title"
                    val artist = c.getString(artistIdx)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown artist"
                    val album = c.getString(albumIdx)?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: "Unknown album"
                    val durationMs = c.getLong(durationIdx)
                    val fingerprint = DuplicateDetector.fingerprint(title, artist, durationMs)
                    val dateModified = c.getLong(dateModIdx)
                    val sizeBytes = c.getLong(sizeIdx)
                    val existingTrack = existing[uri]
                    if (existingTrack != null &&
                        existingTrack.dateModifiedSec == dateModified &&
                        existingTrack.sizeBytes == sizeBytes
                    ) {
                        seenUris.add(uri)
                        continue
                    }
                    val rawTrack = c.getInt(trackIdx)
                    // MediaStore encodes track as track*1000 + disc on some devices; normalize.
                    val trackNumber = if (rawTrack > 1000) (rawTrack / 1000) % 1000 else rawTrack
                    val discNumberRaw = c.getInt(discIdx)
                    val discNumber = if (discNumberRaw > 0) discNumberRaw else if (rawTrack > 1000) rawTrack / 1000 / 1000 else 1
                    val keepEdited = existingTrack?.userEdited == true
                    val track = TrackEntity(
                        id = existingTrack?.id ?: 0,
                        uri = uri,
                        filePath = c.getString(dataIdx) ?: "",
                        displayName = c.getString(nameIdx) ?: "",
                        title = if (keepEdited) existingTrack!!.title else title,
                        artist = if (keepEdited) existingTrack!!.artist else artist,
                        album = if (keepEdited) existingTrack!!.album else album,
                        albumArtist = if (keepEdited) existingTrack!!.albumArtist else artist,
                        genre = if (keepEdited) existingTrack!!.genre else (c.getString(genreIdx)?.takeIf { it.isNotBlank() } ?: "Unknown genre"),
                        year = if (keepEdited) existingTrack!!.year else c.getInt(yearIdx),
                        trackNumber = (if (keepEdited) existingTrack!!.trackNumber else trackNumber).coerceIn(0, 999),
                        discNumber = (if (keepEdited) existingTrack!!.discNumber else discNumber).coerceIn(1, 9),
                        durationMs = durationMs,
                        sizeBytes = sizeBytes,
                        dateAddedSec = c.getLong(dateAddedIdx),
                        dateModifiedSec = dateModified,
                        folder = folder,
                        albumId = c.getLong(albumIdIdx),
                        fingerprint = if (keepEdited) existingTrack!!.fingerprint else fingerprint,
                        mimeType = (c.getString(mimeIdx) ?: "").lowercase(),
                        isFavorite = existingTrack?.isFavorite ?: false,
                        userEdited = keepEdited,
                    )
                    batch.add(track)
                    seenUris.add(uri)
                    if (existingTrack != null) updated++ else added++
                }
            }
            } // end if (!useCustomOnly)
            // ---- Custom SAF folder scan (if set) ----
            var customScanFailed = false
            if (!customFolderUri.isNullOrBlank()) {
                try {
                    val (cAdded, cUpdated, cSkipped) = scanCustomFolderTree(
                        treeUriString = customFolderUri,
                        excludedFolders = excludedFolders,
                        existing = existing,
                        seenUris = seenUris,
                        batch = batch,
                    )
                    added += cAdded; updated += cUpdated; skipped += cSkipped
                } catch (e: SecurityException) {
                    android.util.Log.w(TAG, "custom folder permission lost", e)
                    customScanFailed = true
                } catch (e: Exception) {
                    android.util.Log.e(TAG, "custom folder scan failed", e)
                    customScanFailed = true
                }
            }
            if (batch.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val inserts = batch.filter { it.id == 0L }
                val updates = batch.filter { it.id != 0L }
                inserts.chunked(100).forEach { chunk -> trackDao.insertAll(chunk) }
                updates.forEach { trackDao.update(it); currentCoroutineContext().ensureActive() }
            }
            // Remove DB rows for files that vanished from storage.
            // Guard: if the custom folder scan failed (permission lost), keep the library intact.
            val dbUris = trackDao.allUris()
            // Delete rows whose URI was not seen in this scan at all (file removed or excluded).
            val removedUris = if (customScanFailed) {
                android.util.Log.w(TAG, "skipping removals: custom folder scan failed")
                emptyList()
            } else {
                dbUris.filter { it !in seenUris }
            }
            if (removedUris.isNotEmpty()) {
                for (uri in removedUris) {
                    trackDao.byUri(uri)?.let { trackDao.delete(it.id) }
                }
            }
            android.util.Log.i(TAG, "scan finished: +$added new, ~$updated updated, -${removedUris.size} removed, $skipped excluded")
            ScanResult(added, updated, removedUris.size, skipped, System.currentTimeMillis() - started)
        }

    /**
     * Walk a SAF tree (user-picked music folder) recursively and create/refresh TrackEntities.
     * Uses MediaMetadataRetriever per file. Returns (added, updated, skippedExcluded).
     */
    private fun scanCustomFolderTree(
        treeUriString: String,
        excludedFolders: Set<String>,
        existing: Map<String, TrackEntity>,
        seenUris: MutableSet<String>,
        batch: MutableList<TrackEntity>,
    ): Triple<Int, Int, Int> {
        var added = 0; var updated = 0; var skipped = 0
        val treeUri = Uri.parse(treeUriString)
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: run {
            android.util.Log.w(TAG, "custom folder: fromTreeUri returned null")
            return Triple(0, 0, 0)
        }
        if (!root.isDirectory) {
            android.util.Log.w(TAG, "custom folder root is not a directory")
            return Triple(0, 0, 0)
        }
        val audioExts = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "oga", "opus", "aiff", "aif")
        fun isAudioFile(name: String?, mime: String?): Boolean {
            val ext = name?.substringAfterLast('.', "")?.lowercase(Locale.ROOT)
            if (ext in audioExts) return true
            if (mime?.startsWith("audio/") == true) return true
            if (mime == "application/ogg" || mime == "application/octet-stream") return name?.let { ext in audioExts } == true
            return false
        }
        // (dir, relativeFolderPathOrEmpty)
        val stack = ArrayDeque<Pair<DocumentFile, String>>()
        stack.add(root to "")
        var filesSeen = 0
        while (stack.isNotEmpty()) {
            val (dir, rel) = stack.removeLast()
            val children = try { dir.listFiles() } catch (e: Exception) { android.util.Log.w(TAG, "listFiles failed for ${dir.uri}", e); continue }
            for (child in children) {
                try {
                    if (child.isDirectory) {
                        val name = child.name?.trim().orEmpty()
                        val childRel = if (rel.isBlank()) name else "$rel/$name"
                        stack.add(child to childRel)
                    } else if (child.isFile) {
                        filesSeen++
                        val name = child.name ?: ""
                        val mime = child.type?.lowercase(Locale.ROOT)
                        if (!isAudioFile(name, mime)) continue
                        val folder = if (rel.isBlank()) LibraryFolder.normalize(root.name) else LibraryFolder.normalize(rel)
                        if (LibraryFolder.isExcluded(folder, excludedFolders)) { skipped++; continue }
                        val uriStr = child.uri.toString()
                        val size = child.length()
                        val lastModSec = if (child.lastModified() > 0) child.lastModified() / 1000 else 0L
                        val prev = existing[uriStr]
                        if (prev != null && prev.dateModifiedSec == lastModSec && prev.sizeBytes == size) {
                            seenUris.add(uriStr)
                            continue
                        }
                        val retriever = MediaMetadataRetriever()
                        var title = name.substringBeforeLast('.').ifBlank { "Unknown title" }
                        var artist = "Unknown artist"; var album = "Unknown album"
                        var genre = "Unknown genre"; var year = 0; var trackNum = 0
                        var durationMs = 0L
                        try {
                            retriever.setDataSource(context, child.uri)
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }?.let { genre = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it.isNotBlank() }?.let { year = it.toIntOrNull() ?: 0 }
                            val cd = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.takeIf { it.isNotBlank() }
                            cd?.substringBefore('/')?.toIntOrNull()?.let { trackNum = it }
                            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.takeIf { it.isNotBlank() }?.let { durationMs = it.toLongOrNull() ?: 0L }
                        } catch (e: Exception) {
                            android.util.Log.w(TAG, "metadata extract failed for $name", e)
                        } finally {
                            try { retriever.release() } catch (e: Exception) {}
                        }
                        val fingerprint = DuplicateDetector.fingerprint(title, artist, durationMs)
                        val keepSaf = prev?.userEdited == true
                        val track = TrackEntity(
                            id = prev?.id ?: 0,
                            uri = uriStr,
                            filePath = "",
                            displayName = name,
                            title = if (keepSaf) prev!!.title else title,
                            artist = if (keepSaf) prev!!.artist else artist,
                            album = if (keepSaf) prev!!.album else album,
                            albumArtist = if (keepSaf) prev!!.albumArtist else artist,
                            genre = if (keepSaf) prev!!.genre else genre,
                            year = if (keepSaf) prev!!.year else year,
                            trackNumber = (if (keepSaf) prev!!.trackNumber else trackNum).coerceIn(0, 999),
                            discNumber = (if (keepSaf) prev!!.discNumber else 1).coerceIn(1, 9),
                            durationMs = durationMs,
                            sizeBytes = size,
                            dateAddedSec = lastModSec,
                            dateModifiedSec = lastModSec,
                            folder = folder,
                            albumId = 0L,
                            fingerprint = if (keepSaf) prev!!.fingerprint else fingerprint,
                            mimeType = (mime ?: "").lowercase(Locale.ROOT),
                            isFavorite = prev?.isFavorite ?: false,
                            userEdited = keepSaf,
                        )
                        batch.add(track)
                        seenUris.add(uriStr)
                        if (prev != null) updated++ else added++
                    }
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "custom file skipped: ${child.name}", e)
                }
            }
        }
        android.util.Log.i(TAG, "custom folder scan: walked $filesSeen files, +$added new, ~$updated updated, $skipped excluded")
        return Triple(added, updated, skipped)
    }

    suspend fun setFavorite(trackId: Long, favorite: Boolean) = trackDao.setFavorite(trackId, favorite)

    /** Update editable tags in the library DB (title/artist/album/genre/year/track/disc). */
    suspend fun updateTags(
        trackId: Long,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        trackNumber: Int,
        discNumber: Int,
    ) {
        val current = trackDao.byId(trackId) ?: return
        val updated = current.copy(
            title = title.trim().ifBlank { current.title },
            artist = artist.trim().ifBlank { "Unknown artist" },
            album = album.trim().ifBlank { "Unknown album" },
            albumArtist = artist.trim().ifBlank { "Unknown artist" },
            genre = genre.trim().ifBlank { "Unknown genre" },
            year = year.coerceIn(0, 2100),
            trackNumber = trackNumber.coerceIn(0, 999),
            discNumber = discNumber.coerceIn(1, 9),
            userEdited = true,
        ).let { it.copy(fingerprint = DuplicateDetector.fingerprint(it.title, it.artist, it.durationMs)) }
        trackDao.update(updated)
    }

    /** Drop the user-edited flag; next rescan will reload file values. */
    suspend fun clearUserEdited(trackId: Long) {
        val current = trackDao.byId(trackId) ?: return
        if (current.userEdited) trackDao.update(current.copy(userEdited = false))
    }

    /** Reset tags to file values immediately (re-read via MediaMetadataRetriever). */
    suspend fun resetTagsToFile(trackId: Long) {
        val current = trackDao.byId(trackId) ?: return
        var title = current.title; var artist = current.artist; var album = current.album
        var genre = current.genre; var year = 0; var trackNum = 0
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(current.uri))
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let { title = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let { artist = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)?.takeIf { it.isNotBlank() }?.let { album = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)?.takeIf { it.isNotBlank() }?.let { genre = it }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.takeIf { it.isNotBlank() }?.let { year = it.toIntOrNull() ?: 0 }
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)?.takeIf { it.isNotBlank() }?.substringBefore('/')?.toIntOrNull()?.let { trackNum = it }
            retriever.release()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "resetTagsToFile extract failed", e)
        }
        trackDao.update(
            current.copy(
                title = title, artist = artist, album = album, albumArtist = artist,
                genre = genre, year = year.coerceIn(0, 2100),
                trackNumber = trackNum.coerceIn(0, 999), discNumber = 1,
                userEdited = false,
                fingerprint = DuplicateDetector.fingerprint(title, artist, current.durationMs),
            )
        )
    }

    suspend fun recordPlay(trackId: Long) = db.playEventDao().insert(com.atlas.music.data.db.PlayEventEntity(trackId = trackId))

    suspend fun getTrack(id: Long): TrackEntity? = trackDao.byId(id)
    suspend fun getTracks(ids: List<Long>): List<TrackEntity> = trackDao.byIds(ids)

    suspend fun deleteTrack(id: Long) {
        db.playlistDao().removeTrackEverywhere(id)
        trackDao.delete(id)
    }

    suspend fun deleteTracks(ids: List<Long>) {
        ids.forEach { deleteTrack(it) }
    }

    /** SQL-backed search (avoids loading the whole DB on every keystroke). */
    suspend fun search(query: String, limit: Int = 200): SearchResults {
        if (query.isBlank()) return SearchResults()
        val q = query.trim().lowercase()
        val sqlMatches = try { trackDao.searchTracks(q, limit * 3) } catch (e: Exception) {
            android.util.Log.w(TAG, "SQL search failed, falling back to memory", e)
            trackDao.getAll().filter { t ->
                t.title.lowercase().contains(q) || t.artist.lowercase().contains(q) ||
                    t.album.lowercase().contains(q) || t.genre.lowercase().contains(q) ||
                    t.folder.lowercase().contains(q)
            }.take(limit * 3)
        }
        val matches = sqlMatches
        fun rank(t: TrackEntity): Int {
            var score = 0
            if (t.title.lowercase().startsWith(q)) score += 100
            if (t.title.lowercase().contains(q)) score += 50
            if (t.artist.lowercase().startsWith(q)) score += 40
            if (t.artist.lowercase().contains(q)) score += 20
            if (t.album.lowercase().contains(q)) score += 10
            return score
        }
        val tracks = matches.sortedByDescending(::rank).take(limit)
        val albums = matches.map { it.album }.filter { it.isNotBlank() && it != "Unknown album" }.distinct().take(20)
        val artists = matches.map { it.artist }.filter { it.isNotBlank() && it != "Unknown artist" }.distinct().take(20)
        val genres = matches.map { it.genre }.filter { it.isNotBlank() && it != "Unknown genre" }.distinct().take(10)
        return SearchResults(tracks = tracks, albums = albums, artists = artists, genres = genres)
    }

    data class SearchResults(
        val tracks: List<TrackEntity> = emptyList(),
        val albums: List<String> = emptyList(),
        val artists: List<String> = emptyList(),
        val genres: List<String> = emptyList(),
    )

    /** Best-effort playable check used to flag unsupported files honestly instead of hiding them. */
    fun looksPlayable(mimeType: String): Boolean {
        if (mimeType.isBlank()) return true
        return mimeType.startsWith("audio/") || mimeType == "application/ogg" ||
            mimeType == "application/octet-stream" // many FLAC files report this; ExoPlayer will try
    }
}
