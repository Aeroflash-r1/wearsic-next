package com.wearsic.next.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Refresh
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.browse.MadeForYouUiState
import com.wearsic.next.ui.browse.RecommendationsViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.ui.playback.formatTime
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.wearsicRotaryScroll
import androidx.compose.ui.Alignment

/**
 * MADE FOR YOU — the standalone equivalent of the original Recommendations
 * screen. Recs derive from your local taste (recent plays + favorites) and
 * InnerTube's catalog — no server, no tracking, one search per seed artist.
 */
@Composable
fun RecommendationsScreen(
    playbackViewModel: PlaybackViewModel,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit = {},
    viewModel: RecommendationsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicRotaryScroll(listState),
            contentPadding = wearsicListContentPadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val current = state) {
                MadeForYouUiState.Loading -> {
                    item {
                        WearsicScreenHeader(title = "Made for you", subtitle = "From your taste")
                    }
                    repeat(4) { item(key = "rec_shimmer_$it") { WearsicSearchShimmerRow() } }
                }
                is MadeForYouUiState.Error -> {
                    item {
                        WearsicScreenHeader(title = "Made for you")
                    }
                    item {
                        WearsicEmptyState(
                            title = "Couldn't load",
                            message = current.message,
                            icon = Icons.Rounded.CloudOff
                        )
                    }
                    item {
                        WearsicSecondaryPillButton(
                            label = "Retry",
                            icon = Icons.Rounded.Refresh,
                            onClick = { viewModel.refresh(force = true) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                MadeForYouUiState.Empty -> {
                    item {
                        WearsicScreenHeader(title = "Made for you", subtitle = "From your taste")
                    }
                    item {
                        WearsicEmptyState(
                            title = "Nothing to recommend yet",
                            message = "Favorite a few songs first — recommendations come from your taste.",
                            icon = Icons.Rounded.Favorite
                        )
                    }
                }
                is MadeForYouUiState.Content -> {
                    item {
                        WearsicScreenHeader(title = "Made for you", subtitle = "Because you play ${current.seedLabel}")
                    }
                    item {
                        WearsicSectionTitle(label = "Songs", trailing = {
                            Text(
                                text = "${current.items.size}",
                                color = wearsicSecondaryText(),
                                fontSize = 10.sp
                            )
                        })
                    }
                    items(current.items.size) { index ->
                        val itemTrack = current.items[index]
                        WearsicSongRow(
                            title = itemTrack.title,
                            artist = itemTrack.artistLabel.ifBlank { "—" },
                            artworkUrl = itemTrack.artworkUrl,
                            onClick = {
                                playbackViewModel.playTracks(current.items, index)
                                onOpenPlayer()
                            },
                            titleColor = WearsicTextPrimary,
                            trailing = {
                                itemTrack.durationMs?.takeIf { it > 0L }?.let { ms ->
                                    Text(
                                        text = formatTime(ms),
                                        color = WearsicTextMuted,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
