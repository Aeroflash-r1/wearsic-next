package com.wearsic.next.ui

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.ui.components.WearsicEmptyState
import com.wearsic.next.ui.components.WearsicPrimaryPillButton
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSearchShimmerRow
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.components.WearsicSecondaryPillButton
import com.wearsic.next.ui.components.WearsicSettingsActionPill
import com.wearsic.next.ui.components.WearsicSongRow
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicBlack
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicTextWhite60
import com.wearsic.next.ui.theme.WearsicVibrantLavender
import androidx.compose.runtime.CompositionLocalProvider
import org.junit.Rule
import org.junit.Test

/** Galaxy Watch 7 44mm-ish round fixture: 480x480 px. */
val Watch44Config: DeviceConfig = DeviceConfig(
    screenWidth = 480,
    screenHeight = 480,
    xdpi = 340,
    ydpi = 340,
    density = com.android.resources.Density.XXXHIGH,
    screenRound = com.android.resources.ScreenRound.ROUND,
)

/** Compact 40mm-ish round fixture: 396x396 px. */
val Watch40Config: DeviceConfig = DeviceConfig(
    screenWidth = 396,
    screenHeight = 396,
    xdpi = 320,
    ydpi = 320,
    density = com.android.resources.Density.XXHIGH,
    screenRound = com.android.resources.ScreenRound.ROUND,
)

@PaparazziTest
class ComponentsScreenshotTest {

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = Watch44Config,
        theme = "android:Theme.Material.Light.NoActionBar",
    )

    private fun snapshot(name: String, body: @Composable () -> Unit) {
        paparazzi.snapshot(name) {
            CompositionLocalProvider(LocalWearsicAccent provides WearsicVibrantLavender) {
                MaterialTheme {
                    ScreenScaffold {
                        Column(
                            modifier = Modifier
                                .background(WearsicBlack)
                                .padding(horizontal = WearsicDimens.ScreenPaddingH),
                        ) {
                            body()
                        }
                    }
                }
            }
        }
    }

    @Test
    fun header() = snapshot("header") {
        WearsicScreenHeader(title = "Music", subtitle = "Your music, everywhere")
    }

    @Test
    fun primary_pill() = snapshot("primary_pill") {
        WearsicPrimaryPillButton(
            label = "Search Music",
            icon = Icons.Rounded.Search,
            onClick = {},
        )
    }

    @Test
    fun secondary_pill() = snapshot("secondary_pill") {
        WearsicSecondaryPillButton(
            label = "Queue",
            icon = Icons.AutoMirrored.Rounded.QueueMusic,
            onClick = {},
        )
    }

    @Test
    fun settings_pill() = snapshot("settings_pill") {
        WearsicSettingsActionPill(label = "Settings", icon = Icons.Rounded.Settings, onClick = {})
    }

    @Test
    fun song_row() = snapshot("song_row") {
        WearsicSongRow(
            title = "VXLTA GE — Undress (Rehearsal Hard)",
            artist = "Kakashi • 1:46",
            artworkUrl = null,
            onClick = {},
            modifier = Modifier,
        )
    }

    @Test
    fun section_title() = snapshot("section_title") {
        WearsicSectionTitle(
            label = "Recently Played",
            trailing = {
                Text(text = "6", color = WearsicTextWhite60, fontSize = 10.sp)
            },
        )
    }

    @Test
    fun empty_state() = snapshot("empty_state") {
        WearsicEmptyState(
            title = "Queue is empty",
            message = "Play a song from Search and it will show up here.",
            icon = Icons.Rounded.MusicNote,
        )
    }

    @Test
    fun shimmer() = snapshot("shimmer") { WearsicSearchShimmerRow() }
}
