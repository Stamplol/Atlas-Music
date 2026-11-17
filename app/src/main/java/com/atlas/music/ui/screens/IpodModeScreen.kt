package com.atlas.music.ui.screens

import android.content.Context
import android.media.AudioManager
import android.os.BatteryManager
import android.view.HapticFeedbackConstants
import android.view.SoundEffectConstants
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.atlas.music.data.db.TrackEntity
import com.atlas.music.di.AtlasSettings
import com.atlas.music.di.ServiceLocator
import com.atlas.music.playback.PlayerUiState
import com.atlas.music.ui.components.TrackCoverArt
import com.atlas.music.util.ClickWheelMath
import com.atlas.music.util.ClickWheelZone
import com.atlas.music.util.Format
import com.atlas.music.util.SortOption
import com.atlas.music.util.isLandscape
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class IpodPalette(
    val bodyTop: Color,
    val bodyBottom: Color,
    val bezel: Color,
    val wheelSurface: Color,
    val wheelRim: Color,
    val wheelText: Color,
    val centerButton: Color,
    val centerBorder: Color,
    val highlight: Color,
)

private fun ipodPaletteFor(finish: String): IpodPalette = when (finish) {
    "black" -> IpodPalette(
        bodyTop = Color(0xFF2B2E36),
        bodyBottom = Color(0xFF15171C),
        bezel = Color(0xFF0A0B0E),
        wheelSurface = Color(0xFF1D2027),
        wheelRim = Color(0xFF393E4A),
        wheelText = Color(0xFFD6DAE3),
        centerButton = Color(0xFF2E323C),
        centerBorder = Color(0xFF4B5160),
        highlight = Color(0xFF3A7AFE),
    )
    "gold" -> IpodPalette(
        bodyTop = Color(0xFFDCC89E),
        bodyBottom = Color(0xFFB59A64),
        bezel = Color(0xFF1B1914),
        wheelSurface = Color(0xFFF6EFE0),
        wheelRim = Color(0xFFCBB88B),
        wheelText = Color(0xFF5D4C27),
        centerButton = Color(0xFFE7D7B2),
        centerBorder = Color(0xFFB8A06E),
        highlight = Color(0xFF9C6D18),
    )
    "crimson" -> IpodPalette(
        bodyTop = Color(0xFF1F2024),
        bodyBottom = Color(0xFF101114),
        bezel = Color(0xFF07080A),
        wheelSurface = Color(0xFFB6212D),
        wheelRim = Color(0xFFDF404D),
        wheelText = Color(0xFFFFECEE),
        centerButton = Color(0xFF17181C),
        centerBorder = Color(0xFF42454E),
        highlight = Color(0xFFD22332),
    )
    else -> IpodPalette(
        bodyTop = Color(0xFFEAECEF),
        bodyBottom = Color(0xFFC8CDD4),
        bezel = Color(0xFF14161A),
        wheelSurface = Color(0xFFF8F9FA),
        wheelRim = Color(0xFFC4C9D2),
        wheelText = Color(0xFF5E6673),
        centerButton = Color(0xFFE2E5EA),
        centerBorder = Color(0xFFB6BDC7),
        highlight = Color(0xFF2E7DF6),
    )
}

private sealed interface IpodPage {
    val title: String

    data object Root : IpodPage { override val title = "Atlas iPod" }
    data object Music : IpodPage { override val title = "Music" }
    data object AllSongs : IpodPage { override val title = "Songs" }
    data object Favorites : IpodPage { override val title = "Favorites" }
    data object RecentlyAdded : IpodPage { override val title = "Recent" }
    data object Artists : IpodPage { override val title = "Artists" }
    data class ArtistSongs(val artist: String) : IpodPage { override val title = artist }
    data object Albums : IpodPage { override val title = "Albums" }
    data class AlbumSongs(val album: String) : IpodPage { override val title = album }
    data object Playlists : IpodPage { override val title = "Playlists" }
    data class PlaylistSongs(val playlistId: Long, val name: String) : IpodPage { override val title = name }
    data object NowPlaying : IpodPage { override val title = "Now Playing" }
    data object CoverFlow : IpodPage { override val title = "Cover Flow" }
    data object Settings : IpodPage { override val title = "Settings" }
    data object ShellFinish : IpodPage { override val title = "Shell Finish" }
}

private data class IpodRowItem(
    val label: String,
    val trailing: String? = null,
    val hasChevron: Boolean = false,
    val onSelect: () -> Unit,
)

@Composable
fun IpodModeScreen(
    playerState: PlayerUiState,
    settings: AtlasSettings,
    onExitIpod: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }

    val allSongs by ServiceLocator.musicRepository.observeAll(SortOption.TITLE).collectAsState(initial = emptyList())
    val favorites by ServiceLocator.musicRepository.observeFavorites().collectAsState(initial = emptyList())
    val recentSongs by ServiceLocator.musicRepository.observeRecentlyAdded().collectAsState(initial = emptyList())
    val artistCounts by ServiceLocator.musicRepository.observeArtistCounts().collectAsState(initial = emptyList())
    val albumCounts by ServiceLocator.musicRepository.observeAlbumCounts().collectAsState(initial = emptyList())
    val playlists by ServiceLocator.playlistRepository.observePlaylists().collectAsState(initial = emptyList())

    val currentTrack by produceState<TrackEntity?>(null, playerState.currentTrackId) {
        value = playerState.currentTrackId?.let { ServiceLocator.musicRepository.getTrack(it) }
    }

    val navStack = remember { mutableStateListOf<IpodPage>(IpodPage.Root) }
    val selStack = remember { mutableStateListOf(0) }
    val currentPage = navStack.last()
    val currentIndex = selStack.last().coerceAtLeast(0)

    var nowPlayingWheelScrub by remember { mutableStateOf(false) }
    var volumeHudAtMs by remember { mutableStateOf(0L) }
    var showVolumeHud by remember { mutableStateOf(false) }
    var currentVolumeRatio by remember {
        val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        mutableFloatStateOf(cur.toFloat() / max.toFloat())
    }
    var coverFlowIndex by remember { mutableIntStateOf(0) }
    var scopedPlaylistTracks by remember { mutableStateOf<List<TrackEntity>>(emptyList()) }

    val palette = remember(settings.ipodShellFinish) { ipodPaletteFor(settings.ipodShellFinish) }

    LaunchedEffect(volumeHudAtMs) {
        if (volumeHudAtMs > 0L) {
            showVolumeHud = true
            delay(1600)
            if (System.currentTimeMillis() - volumeHudAtMs >= 1500) {
                showVolumeHud = false
            }
        }
    }

    LaunchedEffect(currentPage) {
        if (currentPage is IpodPage.PlaylistSongs) {
            scopedPlaylistTracks = ServiceLocator.playlistRepository.getTracksForPlaylist(currentPage.playlistId)
        }
    }

    fun pushPage(page: IpodPage) {
        navStack.add(page)
        selStack.add(0)
    }

    fun popPage() {
        if (navStack.size > 1) {
            navStack.removeAt(navStack.lastIndex)
            selStack.removeAt(selStack.lastIndex)
        } else {
            onExitIpod()
        }
    }

    BackHandler { popPage() }

    val menuRows: List<IpodRowItem> = when (currentPage) {
        IpodPage.Root -> buildList {
            add(IpodRowItem("Music", hasChevron = true) { pushPage(IpodPage.Music) })
            add(IpodRowItem("Cover Flow", trailing = "${allSongs.size}", hasChevron = true) {
                coverFlowIndex = 0
                pushPage(IpodPage.CoverFlow)
            })
            if (playerState.hasQueue) {
                add(IpodRowItem("Now Playing", hasChevron = true) { pushPage(IpodPage.NowPlaying) })
            }
            add(IpodRowItem("Shuffle Songs") {
                if (allSongs.isNotEmpty()) {
                    val shuffled = allSongs.shuffled()
                    ServiceLocator.playbackConnection.playTracks(shuffled, 0)
                    pushPage(IpodPage.NowPlaying)
                }
            })
            add(IpodRowItem("Settings", hasChevron = true) { pushPage(IpodPage.Settings) })
            add(IpodRowItem("Exit Classic Mode", trailing = "Atlas UI") { onExitIpod() })
        }
        IpodPage.Music -> listOf(
            IpodRowItem("Songs", trailing = "${allSongs.size}", hasChevron = true) { pushPage(IpodPage.AllSongs) },
            IpodRowItem("Favorites", trailing = "${favorites.size}", hasChevron = true) { pushPage(IpodPage.Favorites) },
            IpodRowItem("Artists", trailing = "${artistCounts.size}", hasChevron = true) { pushPage(IpodPage.Artists) },
            IpodRowItem("Albums", trailing = "${albumCounts.size}", hasChevron = true) { pushPage(IpodPage.Albums) },
            IpodRowItem("Playlists", trailing = "${playlists.size}", hasChevron = true) { pushPage(IpodPage.Playlists) },
            IpodRowItem("Recently Added", hasChevron = true) { pushPage(IpodPage.RecentlyAdded) },
        )
        IpodPage.AllSongs -> allSongs.mapIndexed { idx, track ->
            IpodRowItem(
                label = track.title,
                trailing = Format.duration(track.durationMs),
            ) {
                ServiceLocator.playbackConnection.playTracks(allSongs, idx)
                pushPage(IpodPage.NowPlaying)
            }
        }
        IpodPage.Favorites -> favorites.mapIndexed { idx, track ->
            IpodRowItem(track.title, Format.duration(track.durationMs)) {
                ServiceLocator.playbackConnection.playTracks(favorites, idx)
                pushPage(IpodPage.NowPlaying)
            }
        }
        IpodPage.RecentlyAdded -> recentSongs.take(40).mapIndexed { idx, track ->
            IpodRowItem(track.title, Format.duration(track.durationMs)) {
                ServiceLocator.playbackConnection.playTracks(recentSongs, idx)
                pushPage(IpodPage.NowPlaying)
            }
        }
        IpodPage.Artists -> artistCounts.map { nc ->
            IpodRowItem(nc.name, "${nc.count}", hasChevron = true) {
                pushPage(IpodPage.ArtistSongs(nc.name))
            }
        }
        is IpodPage.ArtistSongs -> {
            val matching = allSongs.filter { it.artist == currentPage.artist }
            matching.mapIndexed { idx, track ->
                IpodRowItem(track.title, Format.duration(track.durationMs)) {
                    ServiceLocator.playbackConnection.playTracks(matching, idx)
                    pushPage(IpodPage.NowPlaying)
                }
            }
        }
        IpodPage.Albums -> albumCounts.map { nc ->
            IpodRowItem(nc.name, "${nc.count}", hasChevron = true) {
                pushPage(IpodPage.AlbumSongs(nc.name))
            }
        }
        is IpodPage.AlbumSongs -> {
            val matching = allSongs.filter { it.album == currentPage.album }
            matching.mapIndexed { idx, track ->
                IpodRowItem(track.title, Format.duration(track.durationMs)) {
                    ServiceLocator.playbackConnection.playTracks(matching, idx)
                    pushPage(IpodPage.NowPlaying)
                }
            }
        }
        IpodPage.Playlists -> playlists.map { pl ->
            IpodRowItem(pl.name, hasChevron = true) {
                pushPage(IpodPage.PlaylistSongs(pl.id, pl.name))
            }
        }
        is IpodPage.PlaylistSongs -> scopedPlaylistTracks.mapIndexed { idx, track ->
            IpodRowItem(track.title, Format.duration(track.durationMs)) {
                ServiceLocator.playbackConnection.playTracks(scopedPlaylistTracks, idx)
                pushPage(IpodPage.NowPlaying)
            }
        }
        IpodPage.Settings -> listOf(
            IpodRowItem("Shell Finish", trailing = settings.ipodShellFinish.replaceFirstChar { it.uppercase() }, hasChevron = true) {
                pushPage(IpodPage.ShellFinish)
            },
            IpodRowItem("Wheel Clicker", trailing = if (settings.ipodClickSound) "On" else "Off") {
                scope.launch { ServiceLocator.settings.setIpodClickSound(!settings.ipodClickSound) }
            },
            IpodRowItem("Shuffle", trailing = if (playerState.shuffleEnabled) "On" else "Off") {
                onToggleShuffle()
            },
            IpodRowItem(
                "Repeat",
                trailing = when (playerState.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "One"
                    Player.REPEAT_MODE_ALL -> "All"
                    else -> "Off"
                },
            ) { onCycleRepeat() },
            IpodRowItem("Exit Classic Mode") { onExitIpod() },
        )
        IpodPage.ShellFinish -> listOf(
            "silver" to "Classic Silver",
            "black" to "Slate Black",
            "gold" to "Observatory Gold",
            "crimson" to "U2 Crimson",
        ).map { (key, label) ->
            IpodRowItem(label, trailing = if (settings.ipodShellFinish == key) "✓" else null) {
                scope.launch { ServiceLocator.settings.setIpodShellFinish(key) }
            }
        }
        IpodPage.NowPlaying, IpodPage.CoverFlow -> emptyList()
    }

    val safeIndex = if (menuRows.isEmpty()) 0 else currentIndex.coerceIn(0, menuRows.lastIndex)
    LaunchedEffect(menuRows.size, currentPage) {
        if (selStack.isNotEmpty() && selStack.last() != safeIndex) {
            selStack[selStack.lastIndex] = safeIndex
        }
    }

    fun emitDetentFeedback() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        if (settings.ipodClickSound) {
            view.playSoundEffect(SoundEffectConstants.CLICK)
        }
    }

    fun onWheelStep(clicks: Int) {
        if (clicks == 0) return
        emitDetentFeedback()
        when (currentPage) {
            IpodPage.NowPlaying -> {
                if (nowPlayingWheelScrub && playerState.durationMs > 0) {
                    val stepMs = (playerState.durationMs / 40L).coerceIn(1500L, 10_000L)
                    val target = (playerState.positionMs + clicks * stepMs).coerceIn(0L, playerState.durationMs)
                    onSeekTo(target)
                } else {
                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                    val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    val nextVol = (curVol + if (clicks > 0) 1 else -1).coerceIn(0, maxVol)
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, nextVol, 0)
                    currentVolumeRatio = nextVol.toFloat() / maxVol.toFloat()
                    volumeHudAtMs = System.currentTimeMillis()
                }
            }
            IpodPage.CoverFlow -> {
                if (allSongs.isNotEmpty()) {
                    coverFlowIndex = (coverFlowIndex + clicks).coerceIn(0, allSongs.lastIndex)
                }
            }
            else -> {
                if (menuRows.isNotEmpty()) {
                    val next = (selStack.last() + clicks).coerceIn(0, menuRows.lastIndex)
                    selStack[selStack.lastIndex] = next
                }
            }
        }
    }

    fun onCenterClick() {
        emitDetentFeedback()
        when (currentPage) {
            IpodPage.NowPlaying -> nowPlayingWheelScrub = !nowPlayingWheelScrub
            IpodPage.CoverFlow -> {
                if (allSongs.isNotEmpty()) {
                    val targetIdx = coverFlowIndex.coerceIn(0, allSongs.lastIndex)
                    ServiceLocator.playbackConnection.playTracks(allSongs, targetIdx)
                    pushPage(IpodPage.NowPlaying)
                }
            }
            else -> menuRows.getOrNull(safeIndex)?.onSelect?.invoke()
        }
    }

    val wheelRotateAction by rememberUpdatedState<(Int) -> Unit> { clicks -> onWheelStep(clicks) }
    val wheelCenterAction by rememberUpdatedState<() -> Unit> { onCenterClick() }
    val wheelMenuAction by rememberUpdatedState<() -> Unit> {
        emitDetentFeedback()
        popPage()
    }
    val wheelNextAction by rememberUpdatedState<() -> Unit> {
        emitDetentFeedback()
        onSkipNext()
    }
    val wheelPrevAction by rememberUpdatedState<() -> Unit> {
        emitDetentFeedback()
        onSkipPrevious()
    }
    val wheelPlayPauseAction by rememberUpdatedState<() -> Unit> {
        emitDetentFeedback()
        onTogglePlayPause()
    }

    val batteryPct = remember {
        runCatching {
            val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(1, 100)
        }.getOrDefault(100)
    }

    val landscape = isLandscape()
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(palette.bodyTop, palette.bodyBottom)),
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        if (landscape) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(modifier = Modifier.weight(1.1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    IpodLcdHousing(
                        palette = palette,
                        title = currentPage.title,
                        playerState = playerState,
                        batteryPct = batteryPct,
                        modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f),
                    ) {
                        IpodLcdContent(
                            page = currentPage,
                            menuRows = menuRows,
                            selectedIndex = safeIndex,
                            onRowClick = { rowIdx ->
                                selStack[selStack.lastIndex] = rowIdx
                                menuRows.getOrNull(rowIdx)?.onSelect?.invoke()
                            },
                            playerState = playerState,
                            currentTrack = currentTrack,
                            allSongs = allSongs,
                            coverFlowIndex = coverFlowIndex,
                            nowPlayingWheelScrub = nowPlayingWheelScrub,
                            showVolumeHud = showVolumeHud,
                            volumeRatio = currentVolumeRatio,
                            highlightColor = palette.highlight,
                        )
                    }
                }
                Box(modifier = Modifier.weight(0.9f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    ClickWheel(
                        palette = palette,
                        onRotateSteps = { clicks -> wheelRotateAction(clicks) },
                        onMenu = { wheelMenuAction() },
                        onNext = { wheelNextAction() },
                        onPrev = { wheelPrevAction() },
                        onPlayPause = { wheelPlayPauseAction() },
                        onSelect = { wheelCenterAction() },
                        modifier = Modifier.fillMaxHeight(0.86f).aspectRatio(1f),
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                IpodLcdHousing(
                    palette = palette,
                    title = currentPage.title,
                    playerState = playerState,
                    batteryPct = batteryPct,
                    modifier = Modifier.fillMaxWidth().weight(0.46f),
                ) {
                    IpodLcdContent(
                        page = currentPage,
                        menuRows = menuRows,
                        selectedIndex = safeIndex,
                        onRowClick = { rowIdx ->
                            selStack[selStack.lastIndex] = rowIdx
                            menuRows.getOrNull(rowIdx)?.onSelect?.invoke()
                        },
                        playerState = playerState,
                        currentTrack = currentTrack,
                        allSongs = allSongs,
                        coverFlowIndex = coverFlowIndex,
                        nowPlayingWheelScrub = nowPlayingWheelScrub,
                        showVolumeHud = showVolumeHud,
                        volumeRatio = currentVolumeRatio,
                        highlightColor = palette.highlight,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().weight(0.54f),
                    contentAlignment = Alignment.Center,
                ) {
                    ClickWheel(
                        palette = palette,
                        onRotateSteps = { clicks -> wheelRotateAction(clicks) },
                        onMenu = { wheelMenuAction() },
                        onNext = { wheelNextAction() },
                        onPrev = { wheelPrevAction() },
                        onPlayPause = { wheelPlayPauseAction() },
                        onSelect = { wheelCenterAction() },
                        modifier = Modifier.fillMaxWidth(0.78f).aspectRatio(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun IpodLcdHousing(
    palette: IpodPalette,
    title: String,
    playerState: PlayerUiState,
    batteryPct: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .shadow(10.dp, RoundedCornerShape(18.dp))
            .clip(RoundedCornerShape(18.dp))
            .background(palette.bezel)
            .border(2.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(18.dp))
            .padding(10.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFFFDFEFF)),
        ) {
            // Classic iPod silver status header inside LCD
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color(0xFFECEFF4), Color(0xFFD0D6DF))))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (playerState.isPlaying) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = null,
                    tint = palette.highlight,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    title,
                    color = Color(0xFF161A20),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text("$batteryPct%", color = Color(0xFF424A56), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(3.dp))
                Icon(Icons.Filled.BatteryFull, contentDescription = null, tint = Color(0xFF288E41), modifier = Modifier.size(16.dp))
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFB5BDC8)))
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                content()
            }
        }
    }
}

@Composable
private fun IpodLcdContent(
    page: IpodPage,
    menuRows: List<IpodRowItem>,
    selectedIndex: Int,
    onRowClick: (Int) -> Unit,
    playerState: PlayerUiState,
    currentTrack: TrackEntity?,
    allSongs: List<TrackEntity>,
    coverFlowIndex: Int,
    nowPlayingWheelScrub: Boolean,
    showVolumeHud: Boolean,
    volumeRatio: Float,
    highlightColor: Color,
) {
    when (page) {
        IpodPage.NowPlaying -> {
            IpodNowPlayingLcd(
                playerState = playerState,
                currentTrack = currentTrack,
                wheelScrub = nowPlayingWheelScrub,
                showVolumeHud = showVolumeHud,
                volumeRatio = volumeRatio,
                highlightColor = highlightColor,
            )
        }
        IpodPage.CoverFlow -> {
            IpodCoverFlowLcd(
                tracks = allSongs,
                selectedIndex = coverFlowIndex,
                highlightColor = highlightColor,
            )
        }
        else -> {
            if (menuRows.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing here yet",
                        color = Color(0xFF6B7280),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                val listState = rememberLazyListState()
                LaunchedEffect(selectedIndex, menuRows.size) {
                    if (selectedIndex in menuRows.indices) {
                        val visible = listState.layoutInfo.visibleItemsInfo
                        val isVisible = visible.any { it.index == selectedIndex }
                        if (!isVisible) {
                            listState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
                        }
                    }
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(menuRows) { idx, row ->
                        val selected = idx == selectedIndex
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (selected) Brush.verticalGradient(listOf(highlightColor.copy(alpha = 0.9f), highlightColor))
                                    else Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent)),
                                )
                                .clickable { onRowClick(idx) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(
                                row.label,
                                color = if (selected) Color.White else Color(0xFF191D24),
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            row.trailing?.let { tr ->
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    tr,
                                    color = if (selected) Color.White.copy(alpha = 0.9f) else Color(0xFF6A7280),
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                            if (row.hasChevron) {
                                Spacer(Modifier.width(2.dp))
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = null,
                                    tint = if (selected) Color.White else Color(0xFF7B8390),
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IpodNowPlayingLcd(
    playerState: PlayerUiState,
    currentTrack: TrackEntity?,
    wheelScrub: Boolean,
    showVolumeHud: Boolean,
    volumeRatio: Float,
    highlightColor: Color,
) {
    val fraction = if (playerState.durationMs > 0) {
        (playerState.positionMs.toFloat() / playerState.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFAFBFD))
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(104.dp)
                    .shadow(6.dp, RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF191D24)),
                contentAlignment = Alignment.Center,
            ) {
                if (currentTrack != null) {
                    TrackCoverArt(
                        track = currentTrack,
                        contentDescription = null,
                        size = 104.dp,
                        cornerRadius = 8.dp,
                        showStrip = false,
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    playerState.title.ifBlank { "No Track" },
                    color = Color(0xFF14181F),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    playerState.artist.ifBlank { "Unknown artist" },
                    color = Color(0xFF4F5764),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                playerState.album.takeIf { it.isNotBlank() && it != "Unknown album" }?.let { alb ->
                    Text(
                        alb,
                        color = Color(0xFF6B7380),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (playerState.shuffleEnabled) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, tint = highlightColor, modifier = Modifier.size(14.dp))
                    }
                    if (playerState.repeatMode != Player.REPEAT_MODE_OFF) {
                        Icon(Icons.Filled.Repeat, contentDescription = null, tint = highlightColor, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }

        // Scrubber / Volume HUD
        Column(modifier = Modifier.fillMaxWidth()) {
            val activeRatio = if (showVolumeHud) volumeRatio else fraction
            Canvas(modifier = Modifier.fillMaxWidth().height(16.dp)) {
                val trackY = size.height / 2f
                val barH = 7.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFDDE2E9),
                    topLeft = Offset(0f, trackY - barH / 2f),
                    size = androidx.compose.ui.geometry.Size(size.width, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2f),
                )
                drawRoundRect(
                    color = if (showVolumeHud) Color(0xFF288E41) else highlightColor,
                    topLeft = Offset(0f, trackY - barH / 2f),
                    size = androidx.compose.ui.geometry.Size((size.width * activeRatio).coerceAtLeast(barH), barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barH / 2f),
                )
                if (wheelScrub && !showVolumeHud) {
                    val cx = size.width * activeRatio
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(cx, trackY))
                    drawCircle(highlightColor, radius = 4.5.dp.toPx(), center = Offset(cx, trackY))
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (showVolumeHud) "Volume ${(volumeRatio * 100).toInt()}%" else Format.duration(playerState.positionMs),
                    color = Color(0xFF454D59),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    if (wheelScrub) "Wheel: Seek · ● for Vol" else "Wheel: Vol · ● for Seek",
                    color = highlightColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "-${Format.duration((playerState.durationMs - playerState.positionMs).coerceAtLeast(0L))}",
                    color = Color(0xFF454D59),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun IpodCoverFlowLcd(
    tracks: List<TrackEntity>,
    selectedIndex: Int,
    highlightColor: Color,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF101319), Color(0xFF040507))))
            .padding(10.dp),
    ) {
        if (tracks.isEmpty()) {
            Text(
                "No tracks loaded",
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.Center),
            )
            return@Box
        }
        val safeIdx = selectedIndex.coerceIn(0, tracks.lastIndex)
        val centerTrack = tracks[safeIdx]
        val prevTrack = tracks.getOrNull(safeIdx - 1)
        val nextTrack = tracks.getOrNull(safeIdx + 1)

        Row(
            modifier = Modifier.fillMaxSize().padding(bottom = 34.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(66.dp).height(88.dp).graphicsLayer { rotationY = 26f; alpha = 0.72f }, contentAlignment = Alignment.Center) {
                if (prevTrack != null) {
                    TrackCoverArt(track = prevTrack, contentDescription = null, size = 76.dp, cornerRadius = 8.dp, showStrip = false)
                }
            }
            Spacer(Modifier.width(12.dp))
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .shadow(12.dp, RoundedCornerShape(10.dp))
                    .border(1.5.dp, highlightColor, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                TrackCoverArt(track = centerTrack, contentDescription = null, size = 116.dp, cornerRadius = 10.dp, showStrip = false)
            }
            Spacer(Modifier.width(12.dp))
            Box(modifier = Modifier.width(66.dp).height(88.dp).graphicsLayer { rotationY = -26f; alpha = 0.72f }, contentAlignment = Alignment.Center) {
                if (nextTrack != null) {
                    TrackCoverArt(track = nextTrack, contentDescription = null, size = 76.dp, cornerRadius = 8.dp, showStrip = false)
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                centerTrack.title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${centerTrack.artist}  ·  ${safeIdx + 1} / ${tracks.size}",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ClickWheel(
    palette: IpodPalette,
    onRotateSteps: (Int) -> Unit,
    onMenu: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onPlayPause: () -> Unit,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val latestOnRotate by rememberUpdatedState(onRotateSteps)
    val latestMenu by rememberUpdatedState(onMenu)
    val latestNext by rememberUpdatedState(onNext)
    val latestPrev by rememberUpdatedState(onPrev)
    val latestPlayPause by rememberUpdatedState(onPlayPause)
    val latestSelect by rememberUpdatedState(onSelect)

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val innerRatio = 0.36f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .shadow(10.dp, CircleShape)
                .clip(CircleShape)
                .pointerInput(Unit) {
                    val touchSlopPx = viewConfiguration.touchSlop
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val radius = minOf(cx, cy)
                        val startZone = ClickWheelMath.zoneAt(cx, cy, radius, innerRatio, down.position.x, down.position.y)
                        if (startZone == ClickWheelZone.OUTSIDE) return@awaitEachGesture

                        val allowSpin = startZone != ClickWheelZone.CENTER_SELECT
                        var dragging = false
                        var movedDist = 0f
                        var prevPos = down.position
                        var lastAngle = ClickWheelMath.angleAt(cx, cy, down.position.x, down.position.y)
                        var pendingRad = 0f
                        var upPos = down.position

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                upPos = change.position
                                change.consume()
                                break
                            }
                            val dx = change.position.x - prevPos.x
                            val dy = change.position.y - prevPos.y
                            movedDist += kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()
                            prevPos = change.position
                            if (allowSpin && !dragging && movedDist >= touchSlopPx) {
                                dragging = true
                                lastAngle = ClickWheelMath.angleAt(cx, cy, change.position.x, change.position.y)
                                pendingRad = 0f
                            }
                            if (dragging) {
                                val z = ClickWheelMath.zoneAt(cx, cy, radius, innerRatio, change.position.x, change.position.y)
                                if (z != ClickWheelZone.OUTSIDE && z != ClickWheelZone.CENTER_SELECT) {
                                    val angle = ClickWheelMath.angleAt(cx, cy, change.position.x, change.position.y)
                                    val delta = ClickWheelMath.shortestAngleDelta(lastAngle, angle)
                                    val (clicks, rem) = ClickWheelMath.accumulateClicks(pendingRad, delta)
                                    pendingRad = rem
                                    if (clicks != 0) latestOnRotate(clicks)
                                    lastAngle = angle
                                }
                                change.consume()
                            }
                        }

                        if (!dragging) {
                            when (ClickWheelMath.zoneAt(cx, cy, radius, innerRatio, upPos.x, upPos.y)) {
                                ClickWheelZone.CENTER_SELECT -> latestSelect()
                                ClickWheelZone.RING_TOP_MENU -> latestMenu()
                                ClickWheelZone.RING_RIGHT_NEXT -> latestNext()
                                ClickWheelZone.RING_BOTTOM_PLAY -> latestPlayPause()
                                ClickWheelZone.RING_LEFT_PREV -> latestPrev()
                                ClickWheelZone.OUTSIDE -> Unit
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val outerR = minOf(cx, cy)
                val innerR = outerR * innerRatio

                // Outer tactile ring
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(palette.wheelSurface, palette.wheelSurface.copy(alpha = 0.92f)),
                        center = Offset(cx * 0.85f, cy * 0.85f),
                        radius = outerR * 1.15f,
                    ),
                    radius = outerR,
                )
                drawCircle(
                    color = palette.wheelRim,
                    radius = outerR - 1.dp.toPx(),
                    style = Stroke(width = 2.dp.toPx()),
                )

                // Raised center button
                drawCircle(
                    brush = Brush.verticalGradient(listOf(palette.centerButton.copy(alpha = 0.95f), palette.centerButton)),
                    radius = innerR,
                )
                drawCircle(
                    color = palette.centerBorder,
                    radius = innerR,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }

            // Cardinal wheel labels
            Text(
                "MENU",
                color = palette.wheelText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                letterSpacing = 1.2.sp,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp),
            )
            Icon(
                Icons.Filled.FastRewind,
                contentDescription = "Previous",
                tint = palette.wheelText,
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 20.dp).size(22.dp),
            )
            Icon(
                Icons.Filled.FastForward,
                contentDescription = "Next",
                tint = palette.wheelText,
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 20.dp).size(22.dp),
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp),
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play/Pause", tint = palette.wheelText, modifier = Modifier.size(16.dp))
                Icon(Icons.Filled.Pause, contentDescription = null, tint = palette.wheelText, modifier = Modifier.size(14.dp))
            }
        }
    }
}
