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
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PlayCircle
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.playback.NowPlayingScreen
import com.wearsic.next.ui.search.SearchScreen
import com.wearsic.next.ui.settings.SettingsScreen
import com.wearsic.next.ui.screens.QueueScreen
import com.wearsic.next.ui.screens.VolumeScreen
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicAppBackground
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicPrefs
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicVibrantLavender
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
 * HOME — the launch surface. Faithful to the stable Wearsic home's visual
 * weight: a big bold title + subtitle explains where you are, ONE
 * accent-filled flagship pill (Search Music), then taller stadium-shaped
 * glass rows. Everything tappable is a 48dp+ target.
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
    val accent = LocalWearsicAccent.current
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Music",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = "Your music, on your wrist",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 11.sp
                    )
                }
            }

            item {
                // Flagship CTA — the accent-filled stadium pill with an icon
                // chip, straight from the stable home.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(accent)
                        .wearsicClickable(pressedScale = 0.97f, onClick = onSearch)
                        .padding(start = 10.dp, end = 16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF17161A),
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "Search Music",
                        color = Color(0xFF17161A),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.2).sp
                    )
                }
            }

            if (hasSong) {
                item {
                    HomePillRow(
                        icon = Icons.Rounded.PlayCircle,
                        label = "Now playing",
                        sublabel = state.item?.title,
                        tint = accent,
                        onClick = onNowPlaying,
                    )
                }
            }
            item {
                HomePillRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Queue",
                    sublabel = "What's lined up",
                    onClick = onQueue,
                )
            }
            item {
                HomePillRow(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Sound",
                    sublabel = "Volume on this watch",
                    onClick = onSound,
                )
            }
            item {
                HomePillRow(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    sublabel = "Colour · disc · haptics",
                    onClick = onSettings,
                )
            }
        }
    }
}

/**
 * Secondary home action: stadium glass row, semibold 15sp label + quiet
 * sublabel — the stable home's secondary-row weight.
 */
@Composable
private fun HomePillRow(
    icon: ImageVector,
    label: String,
    sublabel: String?,
    onClick: () -> Unit,
    tint: Color = Color.White.copy(alpha = 0.90f),
    enabled: Boolean = true,
) {
    val accent = LocalWearsicAccent.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(26.dp))
            .wearsicClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accent else WearsicTextMuted,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            sublabel?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

