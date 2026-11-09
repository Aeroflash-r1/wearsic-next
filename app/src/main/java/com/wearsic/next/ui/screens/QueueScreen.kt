package com.wearsic.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MusicNote
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.playback.formatTime
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicRotaryScroll
import com.wearsic.next.ui.util.wearsicEntrance

/**
 * QUEUE — the now-running list. Jumps on tap; shows the live track with the
 * artwork accent, removes tracks with a trailing "×". A port of the original
 * Wearsic queue design in the standalone data model.
 */
@Composable
fun QueueScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
) {
    val queue by viewModel.queue.collectAsState()
    val currentIndex by viewModel.queueIndex.collectAsState()
    val state by viewModel.state.collectAsState()
    val listState = rememberScalingLazyListState()

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
            item { WearsicScreenHeader("Queue", subtitle = "${queue.size} track" + if (queue.size == 1) "" else "s") }

            if (queue.isEmpty()) {
                item { EmptyQueue() }
            } else {
                items(queue.size) { index ->
                    val item = queue[index]
                    QueueRow(
                        item = item,
                        isCurrent = index == currentIndex && state.phase != PlaybackPhase.Idle,
                        onClick = {
                            viewModel.playQueueIndex(index)
                            onBack()
                        },
                        onRemove = { viewModel.removeFromQueue(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueRow(
    item: PlaybackItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    val accent = LocalWearsicAccent.current
    WearsicSongRow(
        title = item.title,
        artist = item.artistLabel.ifBlank { "—" },
        artworkUrl = item.thumbnails.lastOrNull()?.url,
        onClick = onClick,
        titleColor = if (isCurrent) accent else WearsicTextPrimary,
        artistColor = if (isCurrent) accent.copy(alpha = 0.8f) else WearsicTextPrimary.copy(alpha = 0.8f),
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.durationMs?.takeIf { it > 0L }?.let { ms ->
                    Text(
                        text = formatTime(ms),
                        color = WearsicTextMuted,
                        fontSize = 10.sp
                    )
                }
                // The playing track cannot be removed from its own queue.
                if (!isCurrent) {
                    WearsicSongRowActionButton(
                        icon = Icons.Rounded.Close,
                        contentDescription = "Remove from queue",
                        onClick = onRemove,
                        tint = WearsicTextMuted,
                        visualSize = 14.dp,
                    )
                }
            }
        }
    )
}

@Composable
private fun EmptyQueue() {
    WearsicEmptyState(
        title = "Queue is empty",
        message = "Play a song from Search and it will show up here.",
        icon = Icons.Rounded.MusicNote
    )
}

