package com.wearsic.next.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ScreenScaffold
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Favorite
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.components.WearsicSongRowActionButton
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * FAVORITES — the standalone equivalent of the original favorites screen.
 * Lists locally saved tracks (full metadata), plays on tap, un-favorites via
 * the trailing heart. Wearsic song rows throughout.
 */
@Composable
fun FavoritesScreen(
    viewModel: PlaybackViewModel,
    onBack: () -> Unit,
) {
    val favorites = FavoritesStore.listFavorites()
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
                    title = "Favorites",
                    subtitle = if (favorites.isEmpty()) "Save songs you love" else "${favorites.size} saved"
                )
            }

            if (favorites.isEmpty()) {
                item {
                    WearsicEmptyState(
                        title = "No favorites yet",
                        message = "Long-press a search result or tap the heart on the player.",
                        icon = Icons.Rounded.Favorite
                    )
                }
            } else {
                items(favorites.size) { index ->
                    val item = favorites[index]
                    WearsicSongRow(
                        title = item.title,
                        artist = item.artistLabel.ifBlank { "—" },
                        artworkUrl = item.artworkUrl,
                        onClick = {
                            viewModel.playTracks(favorites, index)
                            onBack()
                        },
                        trailing = {
                            WearsicSongRowActionButton(
                                icon = Icons.Rounded.Favorite,
                                contentDescription = "Remove favorite",
                                onClick = { FavoritesStore.remove(item.videoId) },
                                tint = Color.White.copy(alpha = 0.55f),
                                visualSize = 16.dp,
                            )
                        }
                    )
                }
            }
        }
    }
}
