package com.atlas.music.data.repo

import com.atlas.music.data.db.AtlasDatabase
import com.atlas.music.data.db.PlaylistEntity
import com.atlas.music.data.db.PlaylistTrackEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class PlaylistRepository(private val db: AtlasDatabase) {
    private val dao = db.playlistDao()

    fun observePlaylists(): Flow<List<PlaylistEntity>> = dao.observePlaylists()
    fun observePlaylistTrackIds(id: Long): Flow<List<Long>> = dao.observePlaylistTrackIds(id)
    fun observePlaylistSize(id: Long): Flow<Int> = dao.observePlaylistSize(id)

    suspend fun getTracksForPlaylist(id: Long) =
        dao.observePlaylistTrackIds(id).first().let { db.trackDao().byIds(it).sortedBy { t -> it.indexOf(t.id) } }

    suspend fun create(name: String, description: String = ""): Long {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Playlist name cannot be empty" }
        require(trimmed.length <= 80) { "Playlist name is too long" }
        return dao.insert(PlaylistEntity(name = trimmed, description = description.trim()))
    }

    suspend fun rename(id: Long, name: String, description: String? = null) {
        val existing = dao.byId(id) ?: return
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        dao.update(existing.copy(name = trimmed, description = description ?: existing.description))
    }

    suspend fun delete(id: Long) {
        dao.clearPlaylist(id)
        dao.delete(id)
    }

    suspend fun addTrack(playlistId: Long, trackId: Long) {
        val next = dao.nextPosition(playlistId)
        dao.addTrack(PlaylistTrackEntity(playlistId = playlistId, trackId = trackId, position = next))
    }

    suspend fun removeTrack(playlistId: Long, trackId: Long) = dao.removeTrack(playlistId, trackId)

    suspend fun moveTrack(playlistId: Long, trackId: Long, toIndex: Int) {
        val ids = dao.observePlaylistTrackIds(playlistId).first().toMutableList()
        val from = ids.indexOf(trackId)
        if (from == -1 || toIndex !in ids.indices) return
        ids.removeAt(from)
        ids.add(toIndex, trackId)
        ids.forEachIndexed { index, id -> dao.setPosition(playlistId, id, index) }
    }

    suspend fun isTrackIn(playlistId: Long, trackId: Long) =
        dao.observePlaylistTrackIds(playlistId).first().contains(trackId)
}
