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
import androidx.compose.ui.graphics.Brush
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
 * HOME — the launch surface. A hero disc (now-playing recap when a song is
 * loaded) above glass action rows. Everything tappable is a full 44dp row.
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
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                // Hero: accent-glow logo disc; tappable into Now Playing when
                // a song is loaded.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 2.dp)
                        .wearsicClickable(enabled = hasSong, onClick = onNowPlaying)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    listOf(accent.copy(alpha = 0.30f), Color.Transparent)
                                )
                            )
                            .border(1.dp, accent.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1E1C22)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "WEARSIC NEXT",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    StateLine(state)
                }
            }

            item {
                HomeActionRow(
                    icon = Icons.Rounded.PlayCircle,
                    label = if (hasSong) "Now playing" else "Player",
                    sublabel = if (hasSong) state.item?.title else "Browse what's loaded",
                    enabled = hasSong,
                    onClick = onNowPlaying,
                )
            }
            item {
                HomeActionRow(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    sublabel = "Any song on YouTube Music",
                    onClick = onSearch,
                )
            }
            item {
                HomeActionRow(
                    icon = Icons.AutoMirrored.Rounded.QueueMusic,
                    label = "Queue",
                    sublabel = "What's lined up",
                    onClick = onQueue,
                )
            }
            item {
                HomeActionRow(
                    icon = Icons.Rounded.GraphicEq,
                    label = "Sound",
                    sublabel = "Volume on this watch",
                    onClick = onSound,
                )
            }
            item {
                HomeActionRow(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    sublabel = "Colour · disc · haptics",
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun StateLine(state: com.wearsic.next.playback.PlaybackUiState) {
    val text = when {
        state.phase == PlaybackPhase.Error -> state.errorMessage ?: "Playback error — tap play to retry"
        state.item == null -> "No song loaded"
        else -> {
            val item = requireNotNull(state.item)
            if (state.isPlaying) "Playing · ${item.title}" else "Paused · ${item.title}"
        }
    }
    Text(
        text = text,
        color = WearsicTextMuted,
        fontSize = 10.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 3.dp, start = 28.dp, end = 28.dp)
    )
}

@Composable
private fun HomeActionRow(
    icon: ImageVector,
    label: String,
    sublabel: String?,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val accent = LocalWearsicAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WearsicDimens.SheetRowMinHeight)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(WearsicDimens.CardRadius))
            .wearsicClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) accent else WearsicTextMuted,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = if (enabled) Color.White.copy(alpha = 0.94f) else WearsicTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sublabel.orEmpty(),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
