package com.wearsic.next.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.data.DownloadStore
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.wearsicRotaryScroll
import androidx.compose.ui.Alignment

/**
 * SAVED OFFLINE — the standalone equivalent of the original Downloads
 * screen. Lists tracks whose full audio was written to local storage by the
 * player's "Save offline" action; tap to play, × to remove.
 *
 * All tracks play from files on disk: zero network per play.
 */
@Composable
fun DownloadsScreen(
    viewModel: PlaybackViewModel,
    onPlay: () -> Unit,
    onBack: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()
    val downloads = DownloadStore.list()
    val totalBytes = DownloadStore.totalBytes()

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
            item {
                WearsicScreenHeader(
                    title = "Saved offline",
                    subtitle = if (downloads.isEmpty()) "Nothing saved yet" else
                        "${downloads.size} track${if (downloads.size == 1) "" else "s"} · ${formatBytes(totalBytes)}"
                )
            }

            if (downloads.isEmpty()) {
                item {
                    WearsicEmptyState(
                        title = "No saved tracks",
                        message = "Open a song, swipe to actions and tap Save offline.",
                        icon = Icons.Rounded.Download
                    )
                }
            } else {
                items(downloads.size) { index ->
                    val meta = downloads[index]
                    WearsicSongRow(
                        title = meta.title,
                        artist = meta.artist.ifBlank { "—" },
                        artworkUrl = meta.artworkUrl,
                        onClick = {
                            viewModel.playTracks(downloads.map { it.toPlayable() }, index)
                            onPlay()
                        },
                        trailing = {
                            WearsicSongRowActionButton(
                                icon = Icons.Rounded.Close,
                                contentDescription = "Remove saved track",
                                onClick = { DownloadStore.remove(meta.videoId) },
                                tint = WearsicTextMuted,
                                visualSize = 26.dp,
                            )
                        }
                    )
                }
                item {
                    WearsicSectionTitle(label = "Notes", trailing = {
                        Text(
                            text = "Plays from disk",
                            color = wearsicSecondaryText(),
                            fontSize = 10.sp
                        )
                    })
                }
            }
        }
    }
}

private fun DownloadStore.DownloadMeta.toPlayable(): PlaybackItem = PlaybackItem(
    videoId = videoId,
    title = title,
    artists = listOf(com.wearsic.next.engine.Artist(name = artist)),
    thumbnails = artworkUrl?.let { listOf(Thumbnail(url = it)) } ?: emptyList(),
    durationMs = durationMs,
)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1_000_000L) return "${bytes / 1024} KB"
    val mb = bytes / 1_000_000.0
    return if (mb < 1000) String.format("%.1f MB", mb) else String.format("%.2f GB", mb / 1000.0)
}
