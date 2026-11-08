package com.wearsic.next.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.playback.formatTime
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

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
                        }
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
) {
    val accent = LocalWearsicAccent.current
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WearsicDimens.SheetRowMinHeight)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(
                if (isCurrent) {
                    accent.copy(alpha = 0.16f)
                } else {
                    Color.White.copy(alpha = 0.08f)
                }
            )
            .border(
                1.dp,
                if (isCurrent) accent.copy(alpha = 0.5f) else WearsicSurfaceBorder,
                RoundedCornerShape(WearsicDimens.CardRadius)
            )
            .wearsicClickable(pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .wearsicEntrance(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val thumb = item.thumbnails.lastOrNull()
        if (thumb != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(thumb.url)
                    .size(120)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(WearsicDimens.RowThumbnail)
                    .clip(RoundedCornerShape(WearsicDimens.ArtRadius))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(WearsicDimens.RowThumbnail)
                    .clip(RoundedCornerShape(WearsicDimens.ArtRadius))
                    .background(Color.White.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = WearsicTextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = if (isCurrent) accent else Color.White.copy(alpha = 0.94f),
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCurrent) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(accent)
                    )
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    text = item.artistLabel.ifBlank { "—" },
                    color = Color.White.copy(alpha = 0.60f),
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item.durationMs?.takeIf { it > 0L }?.let { ms ->
            Text(
                text = formatTime(ms),
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun EmptyQueue() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 30.dp)
    ) {
        Icon(
            imageVector = Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.40f),
            modifier = Modifier.size(30.dp)
        )
        Text(
            text = "Queue is empty — play a song first",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

