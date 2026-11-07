package com.wearsic.next.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.engine.Thumbnail

/**
 * Polished Wear OS search screen (Galaxy Watch 7 44 mm round).
 * Search input (debounced), filter chips, typed results, loading/empty/error
 * states, and pagination. Selecting a result emits a structured event via
 * [onResultSelected] (playback is Phase 5/6).
 */
@Composable
fun SearchScreen(
    onResultSelected: (MusicSearchResult) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    val query by viewModel.query.collectAsState()
    val filter by viewModel.filter.collectAsState()

    LaunchedEffect(Unit) {
        // nothing to do on entry; ViewModel survives rotation
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
    ) {
        // --- Search input ---
        BasicTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF1E1C22))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    "Search music…",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 13.sp,
                )
            }
        }

        // --- Filter chips (horizontally scrollable) ---
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            SearchFilter.entries.forEach { f ->
                val selected = f == filter
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(
                            if (selected) Color(0xFFC4B0FF)
                            else Color(0xFF232027)
                        )
                        .clickableRow { viewModel.onFilterSelected(f) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = f.label,
                        color = if (selected) Color(0xFF1C1A22) else Color.White.copy(alpha = 0.8f),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        // --- Content area ---
        when (val s = state) {
            is SearchUiState.Idle -> Centered("Search YouTube Music from your watch")
            is SearchUiState.Loading -> Centered("Searching…")
            is SearchUiState.Error -> Centered("Search error: ${s.message}", error = true)
            is SearchUiState.Results -> {
                if (s.results.isEmpty()) {
                    Centered("No results")
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.results, key = { resultKey(it) }) { result ->
                            ResultRow(result) { onResultSelected(result) }
                        }
                        if (s.continuation != null) {
                            item(key = "load_more") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF232027))
                                        .clickableRow { viewModel.loadMore() }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        if (s.loadingMore) "Loading more…" else "Show more",
                                        color = Color(0xFFC4B0FF),
                                        fontSize = 12.sp,
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
private fun Centered(text: String, error: Boolean = false) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = if (error) Color(0xFFF2B8C6) else Color.White.copy(alpha = 0.6f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(20.dp),
        )
    }
}

@Composable
private fun ResultRow(result: MusicSearchResult, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickableRow(onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(result.thumbnail())
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = result.title(),
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                text = result.subtitle(),
                color = Color.White.copy(alpha = 0.55f),
                fontSize = 11.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Thumb(thumb: Thumbnail?) {
    if (thumb == null) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF232027)),
        )
    } else {
        AsyncImage(
            model = thumb.url,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
    }
}

private fun resultKey(r: MusicSearchResult): String = when (r) {
    is MusicSearchResult.Track -> "t:${r.track.videoId}"
    is MusicSearchResult.Artist -> "a:${r.artist.id ?: r.artist.name}"
    is MusicSearchResult.Album -> "al:${r.album.id ?: r.album.title}"
    is MusicSearchResult.Playlist -> "p:${r.playlist.id ?: r.playlist.title}"
    else -> "u:${System.identityHashCode(r)}"
}

private fun MusicSearchResult.thumbnail(): Thumbnail? = when (this) {
    is MusicSearchResult.Track -> track.thumbnails.lastOrNull()
    is MusicSearchResult.Artist -> artist.thumbnails.lastOrNull()
    is MusicSearchResult.Album -> album.thumbnails.lastOrNull()
    is MusicSearchResult.Playlist -> playlist.thumbnails.lastOrNull()
    else -> null
}

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
        track.album?.let { append(" • "); append(it.name) }
        track.durationMs?.let { append(" • "); append(formatDuration(it)) }
    }
    is MusicSearchResult.Artist -> artist.subscribers ?: "Artist"
    is MusicSearchResult.Album -> listOfNotNull(album.artist, album.year).joinToString(" • ")
    is MusicSearchResult.Playlist -> listOfNotNull(playlist.author, playlist.trackCount).joinToString(" • ")
    else -> ""
}

private fun formatDuration(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return "%d:%02d".format(m, s)
}

/** Minimal clickable modifier to avoid adding a dependency. */
private fun Modifier.clickableRow(onClick: () -> Unit): Modifier =
    this.clickable(onClick = onClick)
