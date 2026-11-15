@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.atlas.music.ui.screens

import com.atlas.music.ui.components.AtlasIconButton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.ui.components.SectionHeader
import com.atlas.music.ui.components.TrackRow
import com.atlas.music.ui.viewmodel.SearchUiState
import com.atlas.music.util.Format

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    state: SearchUiState,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onPlay: (TrackEntity, List<TrackEntity>, Int) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onShowInfo: (TrackEntity) -> Unit,
    onEditTags: (TrackEntity) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        TopAppBar(title = { Text("Search", fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) }, modifier = Modifier.statusBarsPadding(), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background))
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            placeholder = { Text("Songs, artists, albums, genres…") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    AtlasIconButton(text = "Clear search", onClick = onClear) { Icon(Icons.Filled.Clear, contentDescription = "Clear search") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
        )

        if (state.query.isBlank()) {
            com.atlas.music.ui.components.EmptyState(
                title = "Find anything in your library",
                body = "Search matches titles, artists, albums, genres and folders. Everything stays on your device.",
            )
            return
        }

        val results = state.results
        val hasAny = results.tracks.isNotEmpty() || results.albums.isNotEmpty() ||
            results.artists.isNotEmpty() || results.genres.isNotEmpty()
        if (!hasAny && !state.searching) {
            com.atlas.music.ui.components.EmptyState(
                title = "No matches for “${state.query}”",
                body = "Try a different spelling, or check the folder the file lives in.",
            )
            return
        }

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 110.dp)) {
            if (results.tracks.isNotEmpty()) {
                item { SectionHeader("Songs") }
                items(results.tracks, key = { "t-${it.id}" }) { track ->
                    TrackRow(
                        track = track,
                        onPlay = { onPlay(track, results.tracks, results.tracks.indexOf(track)) },
                        onToggleFavorite = onToggleFavorite,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onShowInfo = onShowInfo,
                    onEditTags = onEditTags,
                    )
                }
            }
            if (results.albums.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(results.albums, key = { "al-$it" }) { album ->
                    SearchResultRow(title = album, subtitle = "Album", onClick = { onOpenAlbum(album) })
                }
            }
            if (results.artists.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(results.artists, key = { "ar-$it" }) { artist ->
                    SearchResultRow(title = artist, subtitle = "Artist", onClick = { onOpenArtist(artist) })
                }
            }
            if (results.genres.isNotEmpty()) {
                item { SectionHeader("Genres") }
                items(results.genres, key = { "g-$it" }) { genre ->
                    SearchResultRow(title = genre, subtitle = "Genre", onClick = { onOpenGenre(genre) })
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
