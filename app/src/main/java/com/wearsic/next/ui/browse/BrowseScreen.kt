package com.wearsic.next.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.engine.BrowsePageDto
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowArtwork
import com.wearsic.next.ui.playback.formatTime
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.watchScaled
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * One album / playlist / artist page, driven by the Rust engine.
 *
 * Layout follows the original Wearsic detail screens: cover, title and
 * metadata header, a single "Play" action, an optional album list on artist
 * pages, then the playable rows. Tapping any row queues the whole page from
 * that row, so skips keep walking the collection.
 */
@Composable
fun BrowseScreen(
    browseId: String,
    kind: BrowseKind,
    fallbackTitle: String,
    playbackViewModel: PlaybackViewModel,
    onOpenPlayer: () -> Unit,
    onOpenBrowse: (BrowseKind, String, String) -> Unit,
    viewModel: BrowseViewModel = viewModel(key = "browse:$browseId"),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(browseId) { viewModel.load(browseId) }
    val listState = rememberScalingLazyListState()

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
            when (val current = state) {
                BrowseUiState.Loading -> {
                    item {
                        WearsicScreenHeader(
                            title = fallbackTitle,
                            subtitle = when (kind) {
                                BrowseKind.Album -> "Album"
                                BrowseKind.Playlist -> "Playlist"
                                BrowseKind.Artist -> "Artist"
                            }
                        )
                    }
                    items(3) { WearsicSearchShimmerRow() }
                }

                is BrowseUiState.Error -> {
                    item { WearsicScreenHeader(title = fallbackTitle) }
                    item {
                        WearsicEmptyState(
                            title = "Couldn't load this page",
                            message = current.message,
                            icon = Icons.Rounded.CloudOff
                        )
                    }
                    item {
                        WearsicSecondaryPillButton(
                            label = "Retry",
                            icon = Icons.Rounded.KeyboardArrowDown,
                            onClick = viewModel::retry,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                is BrowseUiState.Content -> {
                    val page = current.page
                    if (!page.artworkUrl.isNullOrBlank()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 2.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                WearsicSongRowArtwork(
                                    artworkUrl = page.artworkUrl,
                                    contentDescription = "Cover art",
                                    size = watchScaled(88.dp)
                                )
                            }
                        }
                    }
                    item {
                        WearsicScreenHeader(
                            title = page.title.ifBlank { fallbackTitle },
                            subtitle = headerLine(page)
                        )
                    }

                    if (page.tracks.isNotEmpty()) {
                        item {
                            WearsicPrimaryPillButton(
                                label = "Play",
                                icon = Icons.Rounded.PlayArrow,
                                subtitle = "${page.tracks.size} " +
                                    if (page.tracks.size == 1) "song" else "songs",
                                onClick = {
                                    playbackViewModel.playTrackDtos(page.tracks, 0)
                                    onOpenPlayer()
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (kind == BrowseKind.Artist && page.albums.isNotEmpty()) {
                        item {
                            WearsicSectionTitle(
                                label = "Albums",
                                icon = Icons.Rounded.MusicNote,
                                trailing = {
                                    Text(
                                        text = "${page.albums.size}",
                                        color = wearsicSecondaryText(),
                                        fontSize = 10.sp
                                    )
                                }
                            )
                        }
                        items(page.albums.size) { index ->
                            val album = page.albums[index]
                            val albumId = album.id
                            WearsicSongRow(
                                title = album.title,
                                artist = listOfNotNull(album.artist, album.year)
                                    .filter { it.isNotBlank() }
                                    .joinToString(" • ")
                                    .ifBlank { "Album" },
                                artworkUrl = album.thumbnails.lastOrNull()?.url,
                                onClick = {
                                    if (albumId != null) {
                                        onOpenBrowse(BrowseKind.Album, albumId, album.title)
                                    }
                                }
                            )
                        }
                    }

                    if (page.tracks.isNotEmpty()) {
                        item {
                            WearsicSectionTitle(
                                label = if (kind == BrowseKind.Artist) "Top songs" else "Songs",
                                icon = Icons.Rounded.MusicNote,
                                trailing = {
                                    Text(
                                        text = "${page.tracks.size}",
                                        color = wearsicSecondaryText(),
                                        fontSize = 10.sp
                                    )
                                }
                            )
                        }
                        items(page.tracks.size) { index ->
                            val track = page.tracks[index]
                            WearsicSongRow(
                                title = track.title,
                                artist = track.artists.joinToString(", ") { it.name }
                                    .ifBlank { "—" },
                                artworkUrl = track.thumbnails.lastOrNull()?.url,
                                onClick = {
                                    playbackViewModel.playTrackDtos(page.tracks, index)
                                    onOpenPlayer()
                                },
                                trailing = {
                                    track.durationMs?.takeIf { it > 0L }?.let { ms ->
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

                    if (page.tracks.isEmpty() && page.albums.isEmpty()) {
                        item {
                            WearsicEmptyState(
                                title = "Nothing here",
                                message = "This page has no playable songs.",
                                icon = Icons.Rounded.MusicNote
                            )
                        }
                    }

                    if (page.continuation != null) {
                        item {
                            WearsicSecondaryPillButton(
                                label = if (current.loadingMore) "Loading…" else "Load more",
                                icon = Icons.Rounded.KeyboardArrowDown,
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

/** "Coldplay • 10 songs • 42 minutes" from whatever the page offers. */
private fun headerLine(page: BrowsePageDto): String? {
    val parts = listOfNotNull(
        page.strapline?.takeIf { it.isNotBlank() },
        page.secondSubtitle?.takeIf { it.isNotBlank() }
            ?: page.subtitle?.takeIf { it.isNotBlank() }
    ).distinct()
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
}
