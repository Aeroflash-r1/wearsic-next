package com.wearsic.next.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.data.LocalLibrary
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.wearsicRotaryScroll

/** Which library grouping is on screen. */
enum class LibraryKind(val title: String, val emptyTitle: String, val emptyMessage: String) {
    Albums(
        title = "Albums",
        emptyTitle = "No albums yet",
        emptyMessage = "Save a few songs and their albums show up here.",
    ),
    Artists(
        title = "Artists",
        emptyTitle = "No artists yet",
        emptyMessage = "Save a few songs and their artists show up here.",
    ),
}

/**
 * ALBUMS / ARTISTS — the standalone equivalent of the original's library
 * sections, built from the songs the watch actually has (saved offline,
 * favorites, recently played).
 */
@Composable
fun LibraryScreen(
    kind: LibraryKind,
    viewModel: PlaybackViewModel,
    onOpenPlayer: () -> Unit,
    onBack: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()
    var expanded by remember { mutableStateOf<String?>(null) }

    val albums = remember { LocalLibrary.albums() }
    val artists = remember { LocalLibrary.artists() }
    val groups: List<Triple<String, String, List<PlaybackItem>>> = remember(kind, albums, artists) {
        when (kind) {
            LibraryKind.Albums -> albums.map { Triple(it.key, it.title, it.tracks) }
            LibraryKind.Artists -> artists.map { Triple(it.key, it.name, it.tracks) }
        }
    }

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
                    title = kind.title,
                    subtitle = if (groups.isEmpty()) "Your library" else "${groups.size} on this watch"
                )
            }

            if (groups.isEmpty()) {
                item {
                    WearsicEmptyState(
                        title = kind.emptyTitle,
                        message = kind.emptyMessage,
                        icon = if (kind == LibraryKind.Albums) Icons.Rounded.Album else Icons.Rounded.Person,
                    )
                }
                return@ScalingLazyColumn
            }

            item {
                WearsicSectionTitle(label = kind.title, trailing = {
                    Text(
                        text = "${groups.size}",
                        color = wearsicSecondaryText(),
                        fontSize = 10.sp
                    )
                })
            }

            groups.forEach { (key, title, tracks) ->
                item(key = "group_$key") {
                    val isOpen = expanded == key
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(WearsicDimens.CardRadius))
                            .background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            imageVector = if (kind == LibraryKind.Albums) Icons.Rounded.Album
                            else Icons.Rounded.Person,
                            contentDescription = null,
                            tint = LocalWearsicAccent.current,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = "  $title",
                            color = WearsicTextPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${tracks.size}",
                            color = WearsicTextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
                // Tapping a group plays its first track and queues the rest.
                items(tracks.size) { index ->
                    val track = tracks[index]
                    WearsicSongRow(
                        title = track.title,
                        artist = track.artistLabel.ifBlank { "—" },
                        artworkUrl = track.artworkUrl,
                        onClick = {
                            viewModel.playTracks(tracks, index)
                            onOpenPlayer()
                        },
                    )
                }
            }
        }
    }
}
