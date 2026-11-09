package com.wearsic.next.ui.settings

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Radio
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Vibration
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.theme.ACCENT_AUTO
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicAppColorChoices
import com.wearsic.next.ui.theme.WearsicPrefs
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicRotaryScroll
import com.wearsic.next.ui.util.wearsicEntrance

/**
 * Settings — deliberately has NO server URL/API key configuration: Wearsic
 * Next is fully standalone (Rust engine in-process). Preferences:
 *
 *   · App colour (fixed palette or dynamic-off-artwork "Auto (artwork)")
 *   · Album-art disc rotation (off = battery savings while listening)
 *   · Haptic feedback
 *   · About card
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit = {},
) {
    val listState = rememberScalingLazyListState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
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
            item { WearsicScreenHeader("Settings", subtitle = "Make it yours") }

            item { SectionTitle("App colour") }

            item {
                val accent = LocalWearsicAccent.current
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Palette,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = WearsicPrefs.accentName,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            item {
                // Colour dot grid: Auto first, then the fixed palette.
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Auto row (dynamic artwork accent).
                    val autoSelected = WearsicPrefs.accentName == ACCENT_AUTO
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AccentChip(
                            selected = autoSelected,
                            color = LocalWearsicAccent.current.copy(alpha = if (autoSelected) 1f else 0.35f),
                            label = ACCENT_AUTO,
                            onPick = { WearsicPrefs.chooseAccent(ACCENT_AUTO) }
                        )
                    }
                    for (row in WearsicAppColorChoices.chunked(7)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { (name, color) ->
                                AccentChip(
                                    selected = WearsicPrefs.accentName == name,
                                    color = color,
                                    label = name,
                                    onPick = { WearsicPrefs.chooseAccent(name) }
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle("Playback") }

            item {
                ToggleRow(
                    icon = Icons.Rounded.Radio,
                    label = "Rotate artwork disc",
                    sublabel = "Off saves a little battery",
                    checked = WearsicPrefs.rotateArtwork,
                    onChange = WearsicPrefs::setRotateEnabled,
                )
            }

            item {
                ToggleRow(
                    icon = Icons.Rounded.Vibration,
                    label = "Haptic feedback",
                    sublabel = "Ticks on taps and scrub",
                    checked = WearsicPrefs.haptics,
                    onChange = WearsicPrefs::setHapticsEnabled,
                )
            }

            item {
                ToggleRow(
                    icon = Icons.Rounded.Replay,
                    label = "Bezel scroll direction",
                    sublabel = "Invert the crown/bezel",
                    checked = WearsicPrefs.rotaryInverted,
                    onChange = WearsicPrefs::setRotaryDirection,
                )
            }

            item { SectionTitle("About") }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(WearsicDimens.CardRadius))
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(WearsicDimens.CardRadius))
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = LocalWearsicAccent.current,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "WEARSIC NEXT",
                            color = LocalWearsicAccent.current,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Rust-powered standalone music player. No server, no phone, no API key.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Search & streams resolve in-process (Rust InnerTube engine); " +
                            "audio is Media3. Favorites and settings live on this watch.",
                        color = Color.White.copy(alpha = 0.45f),
                        fontSize = 10.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AccentChip(
    selected: Boolean,
    color: Color,
    label: String,
    onPick: () -> Unit,
) {
    val accent = LocalWearsicAccent.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (selected) color else color.copy(alpha = 0.72f))
            .border(
                2.dp,
                if (selected) Color.White else Color.Transparent,
                CircleShape
            )
            .wearsicClickable(haptic = false, onClick = onPick),
        contentAlignment = Alignment.Center
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = label,
                tint = Color(0xFF17161A),
                modifier = Modifier.size(13.dp)
            )
        }
    }
}

@Composable
private fun ToggleRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    sublabel: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val accent = LocalWearsicAccent.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(WearsicDimens.CardRadius))
            .wearsicClickable(pressedScale = 0.98f) { onChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                // Two lines on the 226dp face: "Rotate artwork disc" simply
                // does not fit one line beside icon + switch; a hard ellipsis
                // read as broken, not compact.
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 15.sp
            )
            Text(
                text = sublabel,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 12.sp
            )
        }
        // Switch pill: knob slides left/right with the state.
        Box(
            modifier = Modifier
                .size(width = 34.dp, height = 20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) accent else Color.White.copy(alpha = 0.15f)),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.5.dp)
                    .size(15.dp)
                    .clip(CircleShape)
                    .background(if (checked) Color(0xFF17161A) else Color.White.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
private fun SectionTitle(label: String) {
    Box(Modifier.padding(top = 10.dp)) {
        Text(
            text = label.uppercase(),
            color = Color.White.copy(alpha = 0.45f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
