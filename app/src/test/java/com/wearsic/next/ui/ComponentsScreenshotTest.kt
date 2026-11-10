package com.wearsic.next.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSettingsActionPill
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicBlack
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicTextWhite60
import com.wearsic.next.ui.theme.WearsicVibrantLavender
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.engine.Artist
import com.wearsic.next.engine.Album
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.ui.HomeScreenContent
import com.wearsic.next.ui.HomeScreenData
import com.wearsic.next.ui.playback.ArtworkDisc
import com.wearsic.next.ui.playback.TransportRow
import com.wearsic.next.ui.search.SearchScreenContent
import com.wearsic.next.ui.search.SearchUiState
import com.wearsic.next.ui.search.SearchFilter
import org.junit.Rule
import org.junit.Test

/** Galaxy Watch 7 44mm-ish round fixture: 480x480 px. */
val Watch44Config: DeviceConfig = DeviceConfig(
    screenWidth = 480,
    screenHeight = 480,
    xdpi = 340,
    ydpi = 340,
    density = com.android.resources.Density.XXXHIGH,
    screenRound = com.android.resources.ScreenRound.ROUND,
)

/** Compact 40mm-ish round fixture: 396x396 px. */
val Watch40Config: DeviceConfig = DeviceConfig(
    screenWidth = 396,
    screenHeight = 396,
    xdpi = 320,
    ydpi = 320,
    density = com.android.resources.Density.XXHIGH,
    screenRound = com.android.resources.ScreenRound.ROUND,
)

@PaparazziTest
class ComponentsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = Watch44Config,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun snapshot(name: String, body: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWearsicAccent provides WearsicVibrantLavender) {
                MaterialTheme {
                    ScreenScaffold {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(WearsicBlack)
                                .padding(horizontal = WearsicDimens.ScreenPaddingH),
                        ) {
                            body()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun header() = snapshot("header") {
        WearsicScreenHeader(title = "Music", subtitle = "Your music, everywhere")
    }

    @Test
    fun primary_pill() = snapshot("primary_pill") {
        WearsicPrimaryPillButton(
            label = "Search Music",
            icon = Icons.Rounded.Search,
            onClick = {},
        )
    }

    @Test
    fun secondary_pill() = snapshot("secondary_pill") {
        WearsicSecondaryPillButton(
            label = "Queue",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            onClick = {},
        )
    }

    @Test
    fun settings_pill() = snapshot("settings_pill") {
        WearsicSettingsActionPill(label = "Settings", icon = Icons.Rounded.Settings, onClick = {})
    }

    @Test
    fun song_row() = snapshot("song_row") {
        WearsicSongRow(
            title = "VXLTA GE — Undress (Rehearsal Hard)",
            artist = "Kakashi • 1:46",
            artworkUrl = null,
            onClick = {},
            modifier = Modifier,
        )
    }

    @Test
    fun section_title() = snapshot("section_title") {
        WearsicSectionTitle(
            label = "Recently Played",
            trailing = {
                Text(text = "6", color = WearsicTextWhite60, fontSize = 10.sp)
            },
        )
    }

    @Test
    fun empty_state() = snapshot("empty_state") {
        WearsicEmptyState(
            title = "Queue is empty",
            message = "Play a song from Search and it will show up here.",
            icon = Icons.Rounded.MusicNote,
        )
    }

    @Test
    fun shimmer() = snapshot("shimmer") { WearsicSearchShimmerRow() }

    @Test
    fun home_no_hero() = snapshot("home_no_hero") {
        HomeScreenContent(
            data = HomeScreenData(
                hasSong = false, title = "", artist = "", artworkUrl = null,
                isPlaying = false, recent = fakeRecent(),
            ),
            onSearch = {}, onNowPlaying = {}, onQueue = {}, onFavorites = {},
            onMadeForYou = {}, onSound = {}, onPlaylists = {}, onDownloads = {},
            onSettings = {}, playRecent = {},
        )
    }

    @Test
    fun search_idle_with_suggestions() = snapshot("search_idle_with_suggestions") {
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = com.wearsic.next.ui.search.SearchUiState.Idle,
            query = "cold",
            suggestions = listOf("coldplay", "coldplay yellow", "coldplay paradise"),
            playingVideoId = null,
            onQueryChanged = {},
            onRetry = {},
            onLoadMore = {},
        )
    }

    @Test
    fun search_shows_loading() = snapshot("search_shows_loading") {
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = com.wearsic.next.ui.search.SearchUiState.Loading(
                query = "coldplay",
                filter = com.wearsic.next.ui.search.SearchFilter.All,
            ),
            query = "coldplay",
            suggestions = emptyList(),
            playingVideoId = null,
            onQueryChanged = {},
            onRetry = {},
            onLoadMore = {},
        )
    }

    @Test
    fun search_results_tracklist() = snapshot("search_results_tracklist") {
        val track = com.wearsic.next.engine.TrackDto(
            videoId = "vid_yellow",
            title = "Yellow",
            artists = listOf(Artist(name = "Coldplay", id = "a_coldplay")),
            thumbnails = listOf(Thumbnail(url = "https://example.com/yellow.jpg")),
            durationMs = 269_000L,
        )
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = com.wearsic.next.ui.search.SearchUiState.Results(
                query = "coldplay",
                filter = com.wearsic.next.ui.search.SearchFilter.All,
                results = listOf(com.wearsic.next.engine.MusicSearchResult.Track(track)),
                continuation = "token_123",
            ),
            query = "coldplay",
            suggestions = emptyList(),
            playingVideoId = "vid_yellow",
            onQueryChanged = {},
            onRetry = {},
            onLoadMore = {},
        )
    }

    @Test
    fun home_with_hero() = snapshot("home_with_hero") {
        HomeScreenContent(
            data = HomeScreenData(
                hasSong = true,
                title = "VXLTA GE — Undress (Rehearsal Hard)",
                artist = "Kakashi",
                artworkUrl = null,
                isPlaying = true,
                recent = fakeRecent(),
            ),
            onSearch = {}, onNowPlaying = {}, onQueue = {}, onFavorites = {},
            onMadeForYou = {}, onSound = {}, onPlaylists = {}, onDownloads = {},
            onSettings = {}, playRecent = {},
        )
    }

    @Test
    fun player_disc_playing() = snapshot("player_disc_playing") {
        ArtworkDisc(
            artworkUrl = null,
            hasTrack = true,
            positionFlow = kotlinx.coroutines.flow.MutableStateFlow(32_000L),
            durationMs = 106_000L,
            playing = true,
            colors = com.wearsic.next.ui.util.ArtworkColors.Default,
            size = 56.dp,
            rotateArtwork = false,
        )
    }

    @Test
    fun player_disc_compact_40mm() = snapshot("player_disc_compact_40mm") {
        ArtworkDisc(
            artworkUrl = null,
            hasTrack = true,
            positionFlow = kotlinx.coroutines.flow.MutableStateFlow(83_000L),
            durationMs = 106_000L,
            playing = true,
            colors = com.wearsic.next.ui.util.ArtworkColors.Default,
            size = 52.dp,
            rotateArtwork = false,
        )
    }

    @Test
    fun player_transport_playing() = snapshot("player_transport_playing") {
        TransportRow(
            state = com.wearsic.next.playback.PlaybackUiState(
                phase = com.wearsic.next.playback.PlaybackPhase.Playing,
                isPlaying = true,
                item = fakeItem(),
                durationMs = 106_000L,
                hasPrev = true,
                hasNext = true,
            ),
            playSize = 48.dp,
            onTogglePlayPause = {},
            onSkipPrevious = {},
            onSkipNext = {},
            colors = com.wearsic.next.ui.util.ArtworkColors.Default,
            enabled = true,
        )
    }

    private fun fakeRecent(): List<PlaybackItem> = listOf(
        PlaybackItem(
            videoId = "vid_yellow",
            title = "Yellow",
            artists = listOf(Artist(name = "Coldplay", id = "a_coldplay")),
            thumbnails = listOf(Thumbnail(url = "https://example.com/yellow.jpg")),
            durationMs = 269_000L,
        ),
        PlaybackItem(
            videoId = "vid_parachute",
            title = "Parachutes",
            artists = listOf(Artist(name = "Coldplay", id = "a_coldplay")),
            thumbnails = listOf(Thumbnail(url = "https://example.com/parachutes.jpg")),
            durationMs = 210_000L,
        ),
        PlaybackItem(
            videoId = "vid_vxlt",
            title = "VXLTA GE — Undress (Rehearsal Hard)",
            artists = listOf(Artist(name = "Kakashi", id = "a_kakashi")),
            thumbnails = emptyList(),
            durationMs = 106_000L,
        ),
    )

    private fun fakeItem(): PlaybackItem = fakeRecent().first()
}
