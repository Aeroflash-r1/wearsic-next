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
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
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
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSettingsActionPill
import com.wearsic.next.ui.playback.NowPlayingScreen
import com.wearsic.next.ui.search.SearchScreen
import com.wearsic.next.ui.settings.SettingsScreen
import com.wearsic.next.ui.screens.QueueScreen
import com.wearsic.next.ui.screens.VolumeScreen
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
import com.wearsic.next.ui.util.ArtworkColors
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

/** Screens of the app; a simple stack keeps cold-start light and navigation instant. */
internal enum class Destination {
    Home, Search, Player, Queue, Sound, Settings,
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
    val backStack = remember { androidx.compose.runtime.mutableStateListOf(Destination.Home) }
    val current = backStack.last()
    val artworkAccent = remember { mutableStateOf<Color?>(null) }

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
                    onResultSelected = { result ->
                        if (result is MusicSearchResult.Track) {
                            playbackViewModel.playSearchResult(result)
                            backStack.push(Destination.Player)
                        }
                    },
                    onBack = { backStack.pop() },
                    playbackViewModel = playbackViewModel,
                )
                Destination.Player -> NowPlayingScreen(
                    viewModel = playbackViewModel,
                    onNavigateToQueue = { backStack.push(Destination.Queue) },
                    onNavigateToVolume = { backStack.push(Destination.Sound) },
                    onArtworkColor = { colors ->
                        if (colors.fromArtwork) artworkAccent.value = colors.accent
                    },
                )
                Destination.Queue -> QueueScreen(
                    viewModel = playbackViewModel,
                    onBack = { backStack.pop() },
                )
                Destination.Sound -> VolumeScreen()
                Destination.Settings -> SettingsScreen(
                    onBack = { backStack.pop() },
                )
                Destination.Home -> HomeScreen(
                    playbackViewModel = playbackViewModel,
                    onSearch = { backStack.push(Destination.Search) },
                    onNowPlaying = { backStack.push(Destination.Player) },
                    onQueue = { backStack.push(Destination.Queue) },
                    onSound = { backStack.push(Destination.Sound) },
                    onSettings = { backStack.push(Destination.Settings) },
                )
            }
        }
    }
}

/**
 * HOME — faithful port of the stable Wearsic LibraryScreen structure:
 * header → now-playing hero (when a track is loaded) → primary Search Music
 * pill → secondary pill row → uppercase Settings pill. Uses the exact shared
 * components from the original.
 */
@Composable
private fun HomeScreen(
    playbackViewModel: PlaybackViewModel,
    onSearch: () -> Unit,
    onNowPlaying: () -> Unit,
    onQueue: () -> Unit,
    onSound: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by playbackViewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()
    val hasSong = state.item != null && state.phase != PlaybackPhase.Error

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
                        title = state.item?.title.orEmpty(),
                        artist = state.item?.artistLabel.orEmpty(),
                        artworkUrl = state.item?.artworkUrl,
                        isPlaying = state.isPlaying,
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

