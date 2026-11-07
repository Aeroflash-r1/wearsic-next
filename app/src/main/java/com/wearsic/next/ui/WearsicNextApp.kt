package com.wearsic.next.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.playback.NowPlayingScreen
import com.wearsic.next.ui.search.SearchScreen
import com.wearsic.next.ui.settings.SettingsScreen

/**
 * App root (Phase 6): Home → Search → Now Playing → Settings.
 * No server configuration anywhere.
 */
@Composable
fun WearsicNextApp(
    playbackViewModel: PlaybackViewModel = viewModel(),
) {
    var screen by remember { mutableStateOf("home") }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        when (screen) {
            "search" -> SearchScreen(
                onResultSelected = { result ->
                    if (result is MusicSearchResult.Track) {
                        playbackViewModel.playSearchResult(result)
                        screen = "player"
                    }
                },
                onBack = { screen = "home" },
            )
            "settings" -> SettingsScreen(onBack = { screen = "home" })
            "player" -> {
                NowPlayingScreen(playbackViewModel)
                // Tap/back to return home.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(enabled = false) {},
                )
            }
            else -> HomeScreen(
                onSearch = { screen = "search" },
                onSettings = { screen = "settings" },
                onNowPlaying = { if (playbackViewModel.state.value.item != null) screen = "player" },
                nowPlayingAvailable = playbackViewModel.state.value.item != null,
            )
        }
    }
}

@Composable
private fun HomeScreen(
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onNowPlaying: () -> Unit,
    nowPlayingAvailable: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E1C22)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color(0xFFC4B0FF),
                    modifier = Modifier.size(26.dp),
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "WEARSIC NEXT",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Phase 6 — Media3 playback",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))

            Row {
                HomeAction(
                    icon = Icons.Rounded.Search,
                    label = "Search",
                    onClick = onSearch,
                )
                Spacer(Modifier.size(14.dp))
                HomeAction(
                    icon = Icons.Rounded.MusicNote,
                    label = if (nowPlayingAvailable) "Now playing" else "Player",
                    onClick = onNowPlaying,
                    tint = if (nowPlayingAvailable) Color(0xFFC4B0FF) else Color.White.copy(alpha = 0.5f),
                )
                Spacer(Modifier.size(14.dp))
                HomeAction(
                    icon = Icons.Rounded.Settings,
                    label = "Settings",
                    onClick = onSettings,
                )
            }
        }
    }
}

@Composable
private fun HomeAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color(0xFFC4B0FF),
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFF232027))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 9.sp,
        )
    }
}
