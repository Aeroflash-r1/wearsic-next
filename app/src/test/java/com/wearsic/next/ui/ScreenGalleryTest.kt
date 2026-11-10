package com.wearsic.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.wearsic.next.engine.Artist
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.engine.TrackDto
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSettingsActionPill
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.playback.ArtworkDisc
import com.wearsic.next.ui.playback.TransportRow
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicBlack
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicTextWhite60
import com.wearsic.next.ui.theme.WearsicVibrantLavender
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackUiState
import com.wearsic.next.ui.search.SearchScreenContent
import com.wearsic.next.ui.search.SearchUiState
import com.wearsic.next.ui.search.SearchFilter
import androidx.compose.runtime.CompositionLocalProvider
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the app's real UI at true round 44mm dimensions. One PNG per
 * fixture under app/src/test/screenshots/. Robolectric runs the Compose
 * runtime for real; only artwork URLs are omitted (network I/O is not done).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.WearOSLargeRound, sdk = [36])
class ScreenGalleryTest {

    @get:Rule val composeTestRule = createComposeRule()

    private fun shoot(name: String, content: @Composable () -> Unit) {
        composeTestRule.setContent {
            CompositionLocalProvider(LocalWearsicAccent provides WearsicVibrantLavender) {
                MaterialTheme {
                    ScreenScaffold(scrollState = androidx.wear.compose.foundation.lazy.rememberScalingLazyListState()) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(WearsicBlack)
                                .padding(horizontal = WearsicDimens.ScreenPaddingH),
                        ) {
                            content()
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/$name.png")
    }

    private fun track(id: String, title: String, artist: String, durationMs: Long = 240_000L) =
        PlaybackItem(
            videoId = id,
            title = title,
            artists = listOf(Artist(name = artist, id = artist)),
            thumbnails = emptyList(),
            durationMs = durationMs,
        )

    private val recentTracks = listOf(
        track("t1", "Kesariya", "Arijit Singh", 250_000),
        track("t2", "Tum Hi Ho", "Arijit Singh", 261_000),
        track("t3", "A Sky Full of Stars", "Coldplay", 267_000),
        track("t4", "Photograph", "Ed Sheeran", 258_000),
    )

    private val currentTrack: PlaybackItem = recentTracks[2]

    @Test
    fun home_playing() = shoot("01-home-playing") {
        HomeScreenContent(
            data = HomeScreenData(
                hasSong = true,
                title = currentTrack.title,
                artist = currentTrack.artistLabel,
                artworkUrl = null,
                isPlaying = true,
                recent = recentTracks,
            ),
            onSearch = {}, onNowPlaying = {}, onQueue = {}, onFavorites = {},
            onMadeForYou = {}, onSound = {}, onPlaylists = {}, onDownloads = {},
            onSettings = {}, playRecent = {},
        )
    }

    @Test
    fun home_empty() = shoot("02-home-empty") {
        HomeScreenContent(
            data = HomeScreenData(
                hasSong = false, title = "", artist = "", artworkUrl = null,
                isPlaying = false, recent = emptyList(),
            ),
            onSearch = {}, onNowPlaying = {}, onQueue = {}, onFavorites = {},
            onMadeForYou = {}, onSound = {}, onPlaylists = {}, onDownloads = {},
            onSettings = {}, playRecent = {},
        )
    }

    @Test
    fun player_disc_playing() = shoot("03-player-disc-playing") {
        ArtworkDisc(
            artworkUrl = null,
            hasTrack = true,
            positionFlow = MutableStateFlow(84_000L),
            durationMs = 267_000L,
            playing = true,
            colors = com.wearsic.next.ui.util.ArtworkColors.Default,
            size = 56.dp,
            rotateArtwork = false,
        )
    }

    @Test
    fun player_transport_playing() = shoot("04-player-transport-playing") {
        TransportRow(
            state = PlaybackUiState(
                phase = PlaybackPhase.Playing,
                isPlaying = true,
                item = currentTrack,
                durationMs = 267_000L,
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

    @Test
    fun search_idle() = shoot("05-search-idle") {
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = SearchUiState.Idle,
            query = "cold",
            suggestions = listOf("coldplay", "coldplay yellow", "coldplay paradise"),
            playingVideoId = null,
            onQueryChanged = {},
            onRetry = {},
            onLoadMore = {},
        )
    }

    @Test
    fun search_loading() = shoot("06-search-loading") {
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = SearchUiState.Loading(
                query = "coldplay",
                filter = SearchFilter.All,
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
    fun search_results() = shoot("07-search-results") {
        val track = TrackDto(
            videoId = "vid_yellow",
            title = "Yellow",
            artists = listOf(Artist(name = "Coldplay", id = "a_coldplay")),
            thumbnails = emptyList(),
            durationMs = 269_000L,
        )
        SearchScreenContent(
            onResultSelected = { _, _ -> },
            state = SearchUiState.Results(
                query = "coldplay",
                filter = SearchFilter.All,
                results = listOf(
                    com.wearsic.next.engine.MusicSearchResult.Track(track),
                ),
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
    fun header() = shoot("08-header") {
        WearsicScreenHeader(title = "Music", subtitle = "Your music, everywhere")
    }

    @Test
    fun primary_pill() = shoot("09-primary-pill") {
        WearsicPrimaryPillButton(label = "Search Music", icon = Icons.Rounded.Search, onClick = {})
    }

    @Test
    fun secondary_pill() = shoot("10-secondary-pill") {
        WearsicSecondaryPillButton(label = "Queue", icon = Icons.AutoMirrored.Rounded.QueueMusic, onClick = {})
    }

    @Test
    fun settings_pill() = shoot("11-settings-pill") {
        WearsicSettingsActionPill(label = "Settings", icon = Icons.Rounded.Settings, onClick = {})
    }

    @Test
    fun song_row() = shoot("12-song-row") {
        WearsicSongRow(title = "A Sky Full of Stars", artist = "Coldplay • 267", artworkUrl = null, onClick = {})
    }

    @Test
    fun section_title() = shoot("13-section-title") {
        WearsicSectionTitle(
            label = "Recently Played",
            trailing = {
                Text(text = "6", color = WearsicTextWhite60, fontSize = 10.sp)
            },
        )
    }

    @Test
    fun empty_state() = shoot("14-empty-state") {
        WearsicEmptyState(title = "Queue is empty", message = "Play a song from Search and it will show up here.", icon = Icons.Rounded.MusicNote)
    }

    @Test
    fun shimmer() = shoot("15-shimmer") { WearsicSearchShimmerRow() }
}
