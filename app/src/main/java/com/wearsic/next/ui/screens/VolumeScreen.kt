package com.wearsic.next.ui.screens

import android.media.AudioManager
import android.os.Handler
import android.os.Looper
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.wearsic.next.ui.components.WearsicScreenHeader
import com.wearsic.next.ui.components.WearsicSectionTitle
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll
import kotlinx.coroutines.delay

/**
 * SOUND — the standalone equivalent of the original Wearsic VolumeScreen:
 * music-stream volume lives here, with the circular −/+ buttons and a
 * segmented bar, plus a sleep timer (pause after N minutes).
 */
@Composable
fun VolumeScreen(
    onPausePlayback: () -> Unit = {},
) {
    val context = LocalContext.current
    val audioManager = remember {
        context.getSystemService(android.content.Context.AUDIO_SERVICE) as AudioManager
    }
    val accent = LocalWearsicAccent.current
    val listState = rememberScalingLazyListState()

    var volume by remember { mutableIntStateOf(currentVolume(audioManager)) }
    var maxVolume by remember { mutableIntStateOf(audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)) }

    // Sleep timer (minutes). 0 = off.
    var sleepMinutes by remember { mutableIntStateOf(0) }
    var sleepRemainingMs by remember { mutableLongStateOf(0L) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(sleepMinutes) {
        if (sleepMinutes <= 0) {
            sleepRemainingMs = 0L
            return@LaunchedEffect
        }
        sleepRemainingMs = sleepMinutes * 60_000L
        while (sleepRemainingMs > 0L) {
            delay(1000)
            sleepRemainingMs -= 1000L
        }
        sleepRemainingMs = 0L
        sleepMinutes = 0
        onPausePlayback()
    }

    // Register to observe changes so the bar reflects wear-UI side effects.
    val receiver = remember {
        object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                volume = currentVolume(audioManager)
            }
        }
    }
    DisposableEffect(Unit) {
        // VOLUME_CHANGED_ACTION is a protected system broadcast: a plain
        // registration is legal on every supported API level.
        context.registerReceiver(receiver, android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    fun step(delta: Int) {
        val next = (volume + delta).coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, next, 0)
        volume = next
    }

    fun formatRemaining(ms: Long): String {
        val totalMin = ms / 60_000
        val sec = (ms % 60_000) / 1000
        return "$totalMin:${sec.toString().padStart(2, '0')}"
    }

    ScreenScaffold(
        scrollState = listState,
    ) { contentPadding ->
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .wearsicRotaryScroll(listState)
                .wearsicEntrance(),
            contentPadding = wearsicListContentPadding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item { WearsicScreenHeader("Sound") }
            item { WearsicSectionTitle("Volume") }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    VolButton(icon = Icons.Rounded.Remove, label = "Decrease Volume") { step(-1) }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        // Percentage inside the disc.
                        Box(
                            modifier = Modifier
                                .size(58.dp)
                                .clip(RoundedCornerShape(50))
                                .background(accent)
                                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(50)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${(volume * 100 / maxVolume.coerceAtLeast(1))}%",
                                color = Color(0xFF17161A),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    VolButton(icon = Icons.Rounded.Add, label = "Increase Volume") { step(1) }
                }
            }
            item {
                // Segmented bar — cheap, no animation while idle.
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(maxVolume) { i ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (i < volume) accent else Color.White.copy(alpha = 0.10f)
                                    )
                            )
                        }
                    }
                }
            }
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        imageVector = when {
                            volume == 0 -> Icons.Rounded.VolumeOff
                            volume <= maxVolume / 3 -> Icons.Rounded.VolumeMute
                            volume <= maxVolume * 2 / 3 -> Icons.Rounded.VolumeDown
                            else -> Icons.Rounded.VolumeUp
                        },
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$volume of $maxVolume",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
            item {
                Text(
                    text = "Volume affects the music stream on this watch only.",
                    color = Color.White.copy(alpha = 0.45f),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            item { WearsicSectionTitle("Sleep timer") }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    listOf(0 to "Off", 15 to "15m", 30 to "30m", 60 to "60m").forEach { (mins, label) ->
                        val selected = sleepMinutes == mins
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(CircleShape)
                                .background(if (selected) accent else Color.White.copy(alpha = 0.10f))
                                .border(1.dp, if (selected) accent else WearsicSurfaceBorder, CircleShape)
                                .wearsicClickable(haptic = false) { sleepMinutes = mins }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = if (selected) Color(0xFF17161A) else Color.White.copy(alpha = 0.85f),
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }
            }

            if (sleepMinutes > 0) {
                item {
                    Text(
                        text = "Pauses in ${formatRemaining(sleepRemainingMs)}",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun VolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.size(WearsicDimens.TouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(50))
                .background(Color.White.copy(alpha = 0.10f))
                .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(50))
                .wearsicClickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(imageVector = icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

private fun currentVolume(audioManager: AudioManager): Int =
    audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
