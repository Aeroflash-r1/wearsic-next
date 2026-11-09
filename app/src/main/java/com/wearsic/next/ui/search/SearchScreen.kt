package com.wearsic.next.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.fromSearch
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicError
import com.wearsic.next.ui.theme.WearsicGlassBorder
import com.wearsic.next.ui.theme.WearsicGlassFill
import com.wearsic.next.ui.theme.WearsicSurfaceActive
import com.wearsic.next.ui.theme.WearsicSurfaceBorderSubtle
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll
import java.util.Locale

/**
 * SEARCH — the standalone search screen in the Wearsic design language:
 * header, interactive search pill, filter chips, shimmer-shaped loading rows
 * and glass track rows with haptics + long-press favourites.
 *
 * Selecting a track plays it immediately (the Rust engine resolves the
 * stream); everything else still debounces as you type.
 */
@Composable
fun SearchScreen(
    onResultSelected: (MusicSearchResult, List<MusicSearchResult>) -> Unit,
    onBack: () -> Unit = {},
    playbackViewModel: PlaybackViewModel? = null,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val playingVideoId = playbackViewModel?.let { vm ->
        val s by vm.state.collectAsState()
        s.item?.videoId
    }
    val listState = rememberScalingLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
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
            item { WearsicScreenHeader("Search", subtitle = "Find your next favorite") }

            // Interactive search pill.
            item {
                var isFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = WearsicDimens.TouchTarget)
                        .clip(CircleShape)
                        .background(if (isFocused) WearsicSurfaceActive else WearsicGlassFill)
                        .border(
                            1.dp,
                            if (isFocused) LocalWearsicAccent.current else WearsicSurfaceBorderSubtle,
                            CircleShape
                        )
                        .padding(horizontal = 12.dp)
                        .wearsicClickable { keyboardController?.show() },
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Search Icon",
                            tint = if (isFocused) LocalWearsicAccent.current else WearsicTextMuted,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(
                                    text = "Type artist or song…",
                                    color = WearsicTextMuted,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = viewModel::onQueryChanged,
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(LocalWearsicAccent.current),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(
                                    onSearch = {
                                        keyboardController?.hide()
                                        viewModel.retry()
                                    },
                                    onDone = { keyboardController?.hide() },
                                    onGo = { keyboardController?.hide() }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(24.dp)
                                    .onFocusChanged { focusState -> isFocused = focusState.isFocused }
                            )
                        }
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Clear Search",
                                tint = WearsicTextMuted,
                                modifier = Modifier
                                    .size(30.dp)
                                    .wearsicClickable(haptic = false) { viewModel.onQueryChanged("") }
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }

            // Filter chips.
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SearchFilter.entries.forEach { f ->
                        val selected = f == filter
                        val accent = LocalWearsicAccent.current
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(
                                    when {
                                        selected -> accent
                                        else -> WearsicGlassFill
                                    }
                                )
                                .border(1.dp, if (selected) accent else WearsicGlassBorder, CircleShape)
                                .wearsicClickable(haptic = false) { viewModel.onFilterSelected(f) }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = f.label,
                                color = if (selected) Color(0xFF17161A) else Color.White.copy(alpha = 0.85f),
                                fontSize = 10.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            when (val s = state) {
                is SearchUiState.Idle -> {
                    item { HintLine("Search YouTube Music from your watch") }
                }
                is SearchUiState.Loading -> {
                    repeat(3) { i ->
                        item(key = "shimmer_$i") { WearsicSearchShimmerRow() }
                    }
                }
                is SearchUiState.Error -> {
                    item {
                        HintLine(
                            text = "Search failed — ${s.message}",
                            isError = true,
                            retry = viewModel::retry
                        )
                    }
                }
                is SearchUiState.Results -> {
                    if (s.results.isEmpty()) {
                        item {
                            HintLine(
                                text = "No results for \"${s.query}\"",
                                isError = false
                            )
                        }
                    } else {
                        val visible = when (filter) {
                            SearchFilter.All -> s.results
                            SearchFilter.Songs -> s.results.filterIsInstance<MusicSearchResult.Track>()
                            SearchFilter.Artists -> s.results.filterIsInstance<MusicSearchResult.Artist>()
                            SearchFilter.Albums -> s.results.filterIsInstance<MusicSearchResult.Album>()
                            SearchFilter.Playlists -> s.results.filterIsInstance<MusicSearchResult.Playlist>()
                        }
                        if (visible.isEmpty()) {
                            item { HintLine("No ${filter.label.lowercase(Locale.US)} in results") }
                        } else {
                            items(visible.size) { index ->
                                val result = visible[index]
                                SearchResultRow(
                                    result = result,
                                    isPlaying = result is MusicSearchResult.Track &&
                                        result.track.videoId == playingVideoId,
                                    isFavorite = result is MusicSearchResult.Track &&
                                        FavoritesStore.isFavorite(result.track.videoId),
                                    // The whole visible list becomes the queue, so
                                    // next/previous walk the search results (as in
                                    // the original Wearsic) instead of stopping
                                    // after one song.
                                    onPlay = { onResultSelected(result, visible) },
                                    onToggleFavorite = {
                                        (result as? MusicSearchResult.Track)?.let {
                                            FavoritesStore.toggle(PlaybackItem.fromSearch(it.track))
                                        }
                                    }
                                )
                            }
                            s.continuation?.let {
                                item(key = "load_more") {
                                    LoadMoreRow(
                                        isLoading = s.loadingMore,
                                        onClick = viewModel::loadMore
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    result: MusicSearchResult,
    isPlaying: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val accent = LocalWearsicAccent.current
    val isTrack = result is MusicSearchResult.Track
    val title = result.title()
    val subtitle = result.subtitle()
    WearsicSongRow(
        title = title,
        artist = subtitle,
        artworkUrl = result.thumbnail()?.url,
        onClick = onPlay,
        onLongClick = if (isTrack) onToggleFavorite else null,
        titleColor = if (isPlaying) accent else WearsicTextPrimary,
        artistColor = if (isPlaying) accent.copy(alpha = 0.8f) else WearsicTextPrimary.copy(alpha = 0.8f),
        trailing = {
            if (isTrack) {
                WearsicSongRowActionButton(
                    icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.MusicNote,
                    contentDescription = if (isFavorite) "Favorited" else "Favorite",
                    onClick = onToggleFavorite,
                    tint = if (isFavorite || isPlaying) accent else Color.White.copy(alpha = 0.30f),
                    visualSize = if (isFavorite) 15.dp else 12.dp,
                )
            }
        }
    )
}

@Composable
private fun LoadMoreRow(
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    val accent = LocalWearsicAccent.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(50))
            .background(WearsicGlassFill)
            .border(1.dp, WearsicGlassBorder, RoundedCornerShape(50))
            .wearsicClickable(haptic = false, onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        Text(
            if (isLoading) "Loading more…" else "Show more",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun HintLine(
    text: String,
    isError: Boolean = false,
    retry: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 14.dp)
    ) {
        Text(
            text = text,
            color = if (isError) WearsicError else Color.White.copy(alpha = 0.60f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            lineHeight = 15.sp
        )
        if (retry != null) {
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .wearsicClickable(onClick = retry)
                    .padding(horizontal = 18.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Retry",
                    color = LocalWearsicAccent.current,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ── Extensions ────────────────────────────────────────────────────────────

private fun MusicSearchResult.title(): String = when (this) {
    is MusicSearchResult.Track -> track.title
    is MusicSearchResult.Artist -> artist.name
    is MusicSearchResult.Album -> album.title
    is MusicSearchResult.Playlist -> playlist.title
    else -> ""
}

private fun MusicSearchResult.subtitle(): String = when (this) {
    is MusicSearchResult.Track -> buildString {
        track.artists.take(2).forEachIndexed { i, a ->
            if (i > 0) append(", ")
            append(a.name)
        }
        track.durationMs?.let { append(" • "); append(formatDuration(it)) }
    }
    is MusicSearchResult.Artist -> artist.subscribers ?: "Artist"
    is MusicSearchResult.Album -> listOfNotNull(album.artist, album.year).joinToString(" • ")
    is MusicSearchResult.Playlist -> listOfNotNull(playlist.author, playlist.trackCount).joinToString(" • ")
    else -> ""
}

private fun MusicSearchResult.thumbnail(): Thumbnail? = when (this) {
    is MusicSearchResult.Track -> track.thumbnails.lastOrNull()
    is MusicSearchResult.Artist -> artist.thumbnails.lastOrNull()
    is MusicSearchResult.Album -> album.thumbnails.lastOrNull()
    is MusicSearchResult.Playlist -> playlist.thumbnails.lastOrNull()
    else -> null
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format(Locale.US, "%d:%02d", m, s)
}
