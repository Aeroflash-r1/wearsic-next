package com.wearsic.next.ui.lyrics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.WearsicVibrantLavender
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * LYRICS — the missing original-Wearsic feature, self-contained via LRCLIB.
 *
 * Synced lyrics scroll/highlight with playback. Plain lyrics are shown
 * statically. Not-found state shows a dignified empty state.
 */
@Composable
fun LyricsScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val positionMs by viewModel.positionMs.collectAsState()
    val item = state.item
    val listState = rememberScalingLazyListState()

    var lyrics by remember(item?.videoId) { mutableStateOf<Lyrics?>(null) }
    var finished by remember(item?.videoId) { mutableStateOf(false) }

    LaunchedEffect(item?.videoId) {
        if (item == null) return@LaunchedEffect
        lyrics = null
        finished = false
        lyrics = LyricsRepository.fetch(item)
        finished = true
    }

    // Auto-scroll for synced lyrics: jump to the active line (index 0 when non-synced).
    LaunchedEffect(positionMs) {
        val data = lyrics as? Lyrics.Synced ?: return@LaunchedEffect
        val idx = data.lines.indexOfLast { it.timeMs <= positionMs }.coerceAtLeast(0)
        if (idx >= 0 && idx < data.lines.size) {
            listState.scrollToItem(idx)
        }
    }

    ScreenScaffold(scrollState = listState) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicRotaryScroll(listState),
            contentPadding = wearsicListContentPadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                WearsicScreenHeader(
                    title = "Lyrics",
                    subtitle = item?.title?.takeIf { it.isNotBlank() }
                )
            }

            if (item == null) {
                item {
                    WearsicEmptyState(
                        title = "Nothing playing",
                        message = "Play a song to see its lyrics here.",
                        icon = Icons.Rounded.MusicNote
                    )
                }
                return@ScalingLazyColumn
            }

            if (!finished) {
                item { WearsicSearchShimmerRow() }
                item { WearsicSearchShimmerRow() }
                return@ScalingLazyColumn
            }

            when (val result = lyrics) {
                is Lyrics.Synced -> {
                    item {
                        Text(
                            item?.artistLabel?.takeIf { it.isNotBlank() } ?: "",
                            color = WearsicTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    items(result.lines.size) { i ->
                        val line = result.lines[i]
                        val active = line.timeMs <= positionMs &&
                            (result.lines.getOrNull(i + 1)?.timeMs?.let { positionMs < it }
                                ?: true)
                        LyricLineView(line = line.text, active = active)
                    }
                }
                is Lyrics.Plain -> {
                    item {
                        Text(
                            item?.artistLabel?.takeIf { it.isNotBlank() } ?: "",
                            color = WearsicTextMuted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    item {
                        Text(
                            result.text,
                            color = WearsicTextPrimary.copy(alpha = 0.92f),
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                Lyrics.Instrumental -> {
                    item {
                        WearsicEmptyState(
                            title = "Instrumental",
                            message = "No lyrics for this track.",
                            icon = Icons.Rounded.MusicNote
                        )
                    }
                }
                Lyrics.NotFound, null -> {
                    item {
                        WearsicEmptyState(
                            title = "No lyrics found",
                            message = "LRCLIB has no synced or plain lyrics for this track yet.",
                            icon = Icons.Rounded.MusicNote
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineView(
    line: String,
    active: Boolean,
) {
    Text(
        text = if (line.isBlank()) "·" else line,
        color = if (active) LocalWearsicAccent.current else WearsicTextPrimary.copy(alpha = 0.55f),
        fontSize = if (active) 15.sp else 12.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        lineHeight = if (active) 18.sp else 16.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    )
}
