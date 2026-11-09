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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.List
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.Icon
import androidx.compose.foundation.text.BasicTextField
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.data.PlaylistsStore
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicGlassBorder
import com.wearsic.next.ui.theme.WearsicGlassFill
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.theme.wearsicSecondaryText
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * PLAYLISTS — the standalone equivalent of the original playlists screens.
 * Two modes:
 *   [chooser] = true  → tap a playlist to add the current track to it
 *   [chooser] = false → manage view: create, inspect, delete
 */
@Composable
fun PlaylistsScreen(
    viewModel: PlaybackViewModel,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit = {},
    chooser: Boolean = false,
) {
    val currentItem by viewModel.state.collectAsState()
    val hasTrack = currentItem.item != null
    val list = PlaylistsStore.playlists
    val listState = rememberScalingLazyListState()
    var newName by remember { mutableStateOf("") }

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
                    title = if (chooser) "Add to playlist" else "Playlists",
                    subtitle = if (chooser)
                        (currentItem.item?.title ?: "Pick a playlist")
                    else "Your lists"
                )
            }

            // Create new playlist (manage mode only).
            if (!chooser) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp)
                            .clip(RoundedCornerShape(23.dp))
                            .background(WearsicGlassFill)
                            .border(1.dp, WearsicGlassBorder, RoundedCornerShape(23.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                tint = LocalWearsicAccent.current,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.fillMaxWidth()) {
                                if (newName.isEmpty()) {
                                    Text(
                                        text = "New playlist…",
                                        color = WearsicTextMuted,
                                        fontSize = 12.sp
                                    )
                                }
                                BasicTextField(
                                    value = newName,
                                    onValueChange = { newName = it },
                                    singleLine = true,
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 12.sp
                                    ),
                                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalWearsicAccent.current),
                                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            if (PlaylistsStore.create(newName)) newName = ""
                                        }
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }

            if (list.isEmpty()) {
                item {
                    WearsicEmptyState(
                        title = if (chooser) "No playlists yet" else "Nothing here yet",
                        message = if (chooser)
                            "Create a playlist in the manage view, then tap it here."
                        else "Create one above, then play or add songs to it.",
                        icon = Icons.Rounded.MusicNote
                    )
                }
            } else {
                item {
                    WearsicSectionTitle(label = "Lists", trailing = {
                        Text(text = "${list.size}", color = wearsicSecondaryText(), fontSize = 10.sp)
                    })
                }
                items(list.size) { index ->
                    val name = list.keys.sorted()[index]
                    val count = list.getValue(name).size
                    WearsicSongRow(
                        title = name,
                        artist = if (count == 0) "Empty" else "$count track${if (count == 1) "" else "s"}",
                        artworkUrl = null,
                        onClick = {
                            if (chooser) {
                                currentItem.item?.let { PlaylistsStore.add(name, it) }
                                onBack()
                            } else {
                                onOpenDetail(name)
                            }
                        },
                        trailing = {
                            if (!chooser) {
                                WearsicSongRowActionButton(
                                    icon = Icons.Rounded.Close,
                                    contentDescription = "Delete playlist",
                                    onClick = { PlaylistsStore.delete(name) },
                                    tint = WearsicTextMuted,
                                    visualSize = 18.dp,
                                )
                            } else if (hasTrack) {
                                Icon(
                                    imageVector = Icons.Rounded.List,
                                    contentDescription = null,
                                    tint = WearsicTextPrimary.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

/**
 * PLAYLIST DETAIL — cover-less list with a Play-all action, row × to remove
 * from the playlist, tap to play the whole list.
 */
@Composable
fun PlaylistDetailScreen(
    playlistName: String,
    viewModel: PlaybackViewModel,
    onPlayAll: () -> Unit,
    onBack: () -> Unit = {},
) {
    val list = PlaylistsStore.get(playlistName)
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
            item {
                WearsicScreenHeader(
                    title = playlistName,
                    subtitle = if (list.isEmpty()) "Empty" else "${list.size} track${if (list.size == 1) "" else "s"}"
                )
            }

            if (list.isEmpty()) {
                item {
                    WearsicEmptyState(
                        title = "Empty playlist",
                        message = "Add tracks from the player's actions page.",
                        icon = Icons.Rounded.MusicNote
                    )
                }
            } else {
                item {
                    WearsicPrimaryPillButton(
                        label = "Play all",
                        icon = Icons.Rounded.PlayArrow,
                        subtitle = "${list.size} song${if (list.size == 1) "" else "s"}",
                        onClick = {
                            viewModel.playTracks(list, 0)
                            onPlayAll()
                        }
                    )
                }
                items(list.size) { index ->
                    val track = list[index]
                    WearsicSongRow(
                        title = track.title,
                        artist = track.artistLabel.ifBlank { "—" },
                        artworkUrl = track.artworkUrl,
                        onClick = {
                            viewModel.playTracks(list, index)
                            onPlayAll()
                        },
                        trailing = {
                            WearsicSongRowActionButton(
                                icon = Icons.Rounded.Close,
                                contentDescription = "Remove from playlist",
                                onClick = { PlaylistsStore.removeTrack(playlistName, track.videoId) },
                                tint = WearsicTextMuted,
                                visualSize = 26.dp,
                            )
                        }
                    )
                }
            }
        }
    }
}
