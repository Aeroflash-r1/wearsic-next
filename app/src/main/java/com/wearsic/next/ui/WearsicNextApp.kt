package com.wearsic.next.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.browse.BrowseKind
import com.wearsic.next.ui.browse.BrowseScreen
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSettingsActionPill
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.data.RecentStore
import com.wearsic.next.ui.playback.NowPlayingScreen
import com.wearsic.next.ui.search.SearchScreen
import com.wearsic.next.ui.settings.SettingsScreen
import com.wearsic.next.ui.screens.FavoritesScreen
import com.wearsic.next.ui.screens.QueueScreen
import com.wearsic.next.ui.screens.RecommendationsScreen
import com.wearsic.next.ui.screens.DownloadsScreen
import com.wearsic.next.ui.screens.PlaylistDetailScreen
import com.wearsic.next.ui.screens.PlaylistsScreen
import com.wearsic.next.ui.screens.VolumeScreen
import com.wearsic.next.ui.lyrics.LyricsScreen
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicAppBackground
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicGlassBorder
import com.wearsic.next.ui.theme.WearsicPrefs
import com.wearsic.next.ui.theme.WearsicSurfaceBorderSubtle
import com.wearsic.next.ui.theme.WearsicSurfaceRaised
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.WearsicTextPrimaryDark
import com.wearsic.next.ui.theme.WearsicVibrantLavender
import com.wearsic.next.ui.theme.watchScaled
import com.wearsic.next.ui.theme.wearsicAccentContainer
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.ArtworkColors
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * Screens of the app; a simple stack keeps cold-start light and navigation
 * instant. Browse carries its target so album → artist → album nesting keeps
 * a real back trail.
 */
internal sealed interface Destination {
    data object Home : Destination
    data object Search : Destination
    data object Player : Destination
    data object Queue : Destination
    data object Sound : Destination
    data object Favorites : Destination
    data object MadeForYou : Destination
    data object Downloads : Destination
    data object Playlists : Destination
    data object PickPlaylist : Destination
    data class PlaylistDetail(val name: String) : Destination
    data object Lyrics : Destination
    data object Settings : Destination
    data class Browse(
        val browseId: String,
        val kind: BrowseKind,
        val title: String,
    ) : Destination
}

private fun <T> SnapshotStateList<T>.push(item: T) {
    if (last() != item) add(item)
}

private fun <T> SnapshotStateList<T>.pop() {
    if (size > 1) removeAt(lastIndex)
}

/**
 * App root: Home → (Search / Now Playing / Queue / Sound / Settings).
 *
 * · A plain stack backs hardware "back", so leaving a screen never destroys
 *   search text or the player.
 * · The accent colour resolves once here: a fixed choice from Settings or —
 *   with ACCENT_AUTO — the artwork hue hoisted from the player (the dynamic
 *   accent, like the original).
 * · No server configuration anywhere.
 */
@Composable
fun WearsicNextApp(
    playbackViewModel: PlaybackViewModel = viewModel(),
) {
    val backStack = remember {
        androidx.compose.runtime.mutableStateListOf<Destination>(Destination.Home)
    }
    val current = backStack.last()
    val artworkAccent = remember { mutableStateOf<Color?>(null) }

    // Battery: the position ticker parks whenever no UI is on screen. The
    // player keeps playing (Media3 owns that); only our 2Hz UI poll stops,
    // so a watch with the screen off is not woken every 500ms.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, playbackViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> playbackViewModel.setUiActive(true)
                Lifecycle.Event.ON_STOP -> playbackViewModel.setUiActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            playbackViewModel.setUiActive(true)
        }
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Resolved app accent: fixed choice from prefs, or the artwork hue.
    val hoistedAccent = artworkAccent.value
    val accent = remember(WearsicPrefs.accentName, hoistedAccent) {
        val fixed = WearsicPrefs.accentColorOrNull()
        when {
            fixed != null -> fixed
            hoistedAccent != null -> hoistedAccent
            else -> WearsicVibrantLavender
        }
    }

    // Hardware back: pop the stack; on Home the gesture finishes the app.
    BackHandler(enabled = backStack.size > 1) {
        backStack.pop()
    }

    CompositionLocalProvider(LocalWearsicAccent provides accent) {
        Box(modifier = Modifier.fillMaxSize().background(WearsicAppBackground)) {
            when (current) {
                Destination.Search -> SearchScreen(
                    onResultSelected = { result, visible ->
                        when (result) {
                            is MusicSearchResult.Track -> {
                                // Queue every song in the list you are looking
                                // at, starting from the one you tapped.
                                val tracks = visible.filterIsInstance<MusicSearchResult.Track>()
                                val start = tracks.indexOfFirst {
                                    it.track.videoId == result.track.videoId
                                }.coerceAtLeast(0)
                                playbackViewModel.playSearchResults(tracks, start)
                                backStack.push(Destination.Player)
                            }
                            is MusicSearchResult.Album -> result.album.id?.let { id ->
                                backStack.push(
                                    Destination.Browse(id, BrowseKind.Album, result.album.title)
                                )
                            }
                            is MusicSearchResult.Artist -> result.artist.id?.let { id ->
                                backStack.push(
                                    Destination.Browse(id, BrowseKind.Artist, result.artist.name)
                                )
                            }
                            is MusicSearchResult.Playlist -> result.playlist.id?.let { id ->
                                backStack.push(
                                    Destination.Browse(id, BrowseKind.Playlist, result.playlist.title)
                                )
                            }
                            MusicSearchResult.Unknown -> Unit
                        }
                    },
                    onBack = { backStack.pop() },
                    playbackViewModel = playbackViewModel,
                )
                Destination.Player -> NowPlayingScreen(
                    viewModel = playbackViewModel,
                    onNavigateToQueue = { backStack.push(Destination.Queue) },
                    onNavigateToVolume = { backStack.push(Destination.Sound) },
                    onNavigateToDownloads = { backStack.push(Destination.Downloads) },
                    onDownloadTrack = playbackViewModel::cacheCurrentTrack,
                    onNavigateToPickPlaylist = { backStack.push(Destination.PickPlaylist) },
                    onNavigateToLyrics = { backStack.push(Destination.Lyrics) },
                    onArtworkColor = { colors ->
                        if (colors.fromArtwork) artworkAccent.value = colors.accent
                    },
                )
                is Destination.Browse -> BrowseScreen(
                    browseId = current.browseId,
                    kind = current.kind,
                    fallbackTitle = current.title,
                    playbackViewModel = playbackViewModel,
                    onOpenPlayer = { backStack.push(Destination.Player) },
                    onOpenBrowse = { kind, browseId, title ->
                        backStack.push(Destination.Browse(browseId, kind, title))
                    },
                )
                Destination.Downloads -> DownloadsScreen(
                    viewModel = playbackViewModel,
                    onPlay = { backStack.push(Destination.Player) },
                    onBack = { backStack.pop() },
                )
                Destination.Playlists -> PlaylistsScreen(
                    viewModel = playbackViewModel,
                    onOpenDetail = { name -> backStack.push(Destination.PlaylistDetail(name)) },
                    onBack = { backStack.pop() },
                )
                Destination.PickPlaylist -> PlaylistsScreen(
                    viewModel = playbackViewModel,
                    onOpenDetail = { },
                    onBack = { backStack.pop() },
                    chooser = true,
                )
                Destination.Lyrics -> LyricsScreen(
                    viewModel = playbackViewModel,
                    onBack = { backStack.pop() },
                )
                is Destination.PlaylistDetail -> PlaylistDetailScreen(
                    playlistName = current.name,
                    viewModel = playbackViewModel,
                    onPlayAll = { backStack.push(Destination.Player) },
                    onBack = { backStack.pop() },
                )
                Destination.Queue -> QueueScreen(
                    viewModel = playbackViewModel,
                    onBack = { backStack.pop() },
                )
                Destination.Sound -> VolumeScreen(
                    onPausePlayback = playbackViewModel::pause,
                )
                Destination.Favorites -> FavoritesScreen(
                    viewModel = playbackViewModel,
                    onBack = { backStack.pop() },
                )
                Destination.MadeForYou -> RecommendationsScreen(
                    playbackViewModel = playbackViewModel,
                    onOpenPlayer = { backStack.push(Destination.Player) },
                    onBack = { backStack.pop() },
                )
                Destination.Settings -> SettingsScreen(
                    onBack = { backStack.pop() },
                )
                Destination.Home -> HomeScreen(
                    playbackViewModel = playbackViewModel,
                    onSearch = { backStack.push(Destination.Search) },
                    onNowPlaying = { backStack.push(Destination.Player) },
                    onQueue = { backStack.push(Destination.Queue) },
                    onFavorites = { backStack.push(Destination.Favorites) },
                    onMadeForYou = { backStack.push(Destination.MadeForYou) },
                    onSound = { backStack.push(Destination.Sound) },
                    onPlaylists = { backStack.push(Destination.Playlists) },
                    onDownloads = { backStack.push(Destination.Downloads) },
                    onSettings = { backStack.push(Destination.Settings) },
                )
            }
        }
    }
}

/** Fixture-friendly Home data (used by previews/screenshot tests and tests only). */
internal data class HomeScreenData(
    val hasSong: Boolean,
    val title: String,
    val artist: String,
    val artworkUrl: String?,
    val isPlaying: Boolean,
    val recent: List<com.wearsic.next.playback.PlaybackItem> = emptyList(),
)

/**
 * HOME — faithful port of the stable Wearsic LibraryScreen structure:
 * header → now-playing hero (when a track is loaded) → primary Search Music
 * pill → secondary pill row → uppercase Settings pill. Uses the exact shared
 * components from the original.
 */
@Composable
internal fun HomeScreenContent(
    data: HomeScreenData,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onQueue: () -> Unit,
    onFavorites: () -> Unit,
    onMadeForYou: () -> Unit,
    onSound: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
    playRecent: (index: Int) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val hasSong = data.hasSong

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicEntrance()
                .wearsicRotaryScroll(listState),
            contentPadding = wearsicListContentPadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                WearsicScreenHeader(
                    title = "Music",
                    subtitle = "Your music, everywhere"
                )
            }

            // Now Playing hero — only when a track is actually loaded.
            if (hasSong) {
                item {
                    NowPlayingMiniCard(
                        title = data.title,
                        artist = data.artist,
                        artworkUrl = data.artworkUrl,
                        isPlaying = data.isPlaying,
                        onClick = onNowPlaying
                    )
                }
            }

            // Primary Search Action — the accent-filled flagship pill.
            item {
                WearsicPrimaryPillButton(
                    label = "Search Music",
                    icon = Icons.Rounded.Search,
                    onClick = onSearch
                )
            }

            // Made for you — recommendations from your local taste.
            item {
                WearsicSecondaryPillButton(
                    label = "Made for you",
                    icon = Icons.Rounded.Star,
                    onClick = onMadeForYou,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Secondary actions: Queue + Sound side by side.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WearsicSecondaryPillButton(
                        label = "Queue",
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        onClick = onQueue,
                        modifier = Modifier.weight(1f)
                    )
                    WearsicSecondaryPillButton(
                        label = "Sound",
                        icon = Icons.Rounded.GraphicEq,
                        onClick = onSound,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                WearsicSecondaryPillButton(
                    label = "Favorites",
                    icon = Icons.Rounded.Favorite,
                    onClick = onFavorites,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Playlists + Saved offline side by side.
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WearsicSecondaryPillButton(
                        label = "Playlists",
                        icon = Icons.AutoMirrored.Rounded.QueueMusic,
                        onClick = onPlaylists,
                        modifier = Modifier.weight(1f)
                    )
                    WearsicSecondaryPillButton(
                        label = "Saved offline",
                        icon = Icons.Rounded.CloudDownload,
                        onClick = onDownloads,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Recently Played — one-tap resume, like the original home.
            if (data.recent.isNotEmpty()) {
                item {
                    WearsicSectionTitle(label = "Recently Played", trailing = {
                        Text(
                            text = "${data.recent.size} songs",
                            color = wearsicSecondaryText(),
                            fontSize = 10.sp
                        )
                    })
                }
                data.recent.take(4).forEach { track ->
                    item(key = "recent_${track.videoId}") {
                        WearsicSongRow(
                            title = track.title,
                            artist = track.artistLabel.ifBlank { "—" },
                            artworkUrl = track.artworkUrl,
                            onClick = { playRecent(data.recent.indexOfFirst { it.videoId == track.videoId }) }
                        )
                    }
                }
            }

            // Settings Action Pill — the uppercase pill at the bottom.
            item {
                WearsicSettingsActionPill(
                    label = "Settings",
                    icon = Icons.Rounded.Settings,
                    onClick = onSettings
                )
            }

            // Bottom spacing.
            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun HomeScreen(
    playbackViewModel: PlaybackViewModel,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onQueue: () -> Unit,
    onFavorites: () -> Unit,
    onMadeForYou: () -> Unit,
    onSound: () -> Unit,
    onPlaylists: () -> Unit,
    onDownloads: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by playbackViewModel.state.collectAsState()
    val hasSong = state.item != null && state.phase != PlaybackPhase.Error
    val recent = RecentStore.items
    HomeScreenContent(
        data = HomeScreenData(
            hasSong = hasSong,
            title = state.item?.title.orEmpty(),
            artist = state.item?.artistLabel.orEmpty(),
            artworkUrl = state.item?.artworkUrl,
            isPlaying = state.isPlaying,
            recent = recent,
        ),
        onSearch = onSearch,
        onNowPlaying = onNowPlaying,
        onQueue = onQueue,
        onFavorites = onFavorites,
        onMadeForYou = onMadeForYou,
        onSound = onSound,
        onPlaylists = onPlaylists,
        onDownloads = onDownloads,
        onSettings = onSettings,
        playRecent = { index ->
            playbackViewModel.playTracks(recent, index)
            onNowPlaying()
        }
    )
}

/**
 * Now-playing hero card — verbatim port of the original's NowPlayingMiniCard:
 * artwork backdrop with dark scrim, rounded-square art, uppercase kicker
 * (NOW PLAYING / READY TO PLAY), bold title, play/pause disc.
 */
@Composable
private fun NowPlayingMiniCard(
    title: String,
    artist: String,
    artworkUrl: String?,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val heroShape = RoundedCornerShape(WearsicDimens.CardRadius)
    val context = androidx.compose.ui.platform.LocalContext.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 88.dp)
            .clip(heroShape)
            .background(WearsicSurfaceRaised)
            .border(1.dp, if (isPlaying) LocalWearsicAccent.current.copy(alpha = 0.4f) else WearsicGlassBorder, heroShape)
            .wearsicClickable(pressedScale = 0.97f, onClick = onClick)
    ) {
        if (!artworkUrl.isNullOrBlank()) {
            val backdrop = remember(context, artworkUrl) {
                ImageRequest.Builder(context).data(artworkUrl).size(256).build()
            }
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Black.copy(alpha = 0.55f)))
            )
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val artShape = RoundedCornerShape(WearsicDimens.ArtRadius)
                val artModifier = Modifier
                    .size(watchScaled(42.dp))
                    .clip(artShape)
                    .then(
                        if (isPlaying) Modifier.border(1.dp, Color.White.copy(alpha = 0.35f), artShape)
                        else Modifier.border(1.dp, WearsicSurfaceBorderSubtle, artShape)
                    )
                if (!artworkUrl.isNullOrBlank()) {
                    val artRequest = remember(artworkUrl) {
                        ImageRequest.Builder(context)
                            .data(artworkUrl)
                            .size(96)
                            .build()
                    }
                    AsyncImage(
                        model = artRequest,
                        contentDescription = "Now Playing",
                        contentScale = ContentScale.Crop,
                        modifier = artModifier
                    )
                } else {
                    Box(
                        modifier = artModifier.background(
                            if (isPlaying) Color.Black.copy(alpha = 0.25f) else wearsicAccentContainer()
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MusicNote,
                            contentDescription = null,
                            tint = LocalWearsicAccent.current,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPlaying) "NOW PLAYING" else "READY TO PLAY",
                        color = LocalWearsicAccent.current,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.2.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = title,
                        color = WearsicTextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) Color.White.copy(alpha = 0.9f) else LocalWearsicAccent.current
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (isPlaying) "Playing" else "Play",
                    tint = WearsicTextPrimaryDark,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

