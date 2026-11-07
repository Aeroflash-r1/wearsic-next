package com.wearsic.next.ui.playback

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackUiState
import com.wearsic.next.playback.PlaybackViewModel

/**
 * Now Playing — styled after the original Wearsic: full-bleed artwork
 * backdrop, rotating disc with a ring progress sweep, marquee title,
 * spring-fed transport row. AMOLED dark, minimal, no heavy blur.
 */
@Composable
fun NowPlayingScreen(viewModel: PlaybackViewModel) {
    val state by viewModel.state.collectAsState()
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color(0xFF0A0A0A))) {
        val width = maxWidth
        val safe = minOf(width, maxHeight)

        // Artwork backdrop (full-bleed, cropped, darkened).
        val artwork = state.item?.artworkUrl
        if (artwork != null) {
            AsyncImage(
                model = artwork,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.5f), Color.Black.copy(alpha = 0.85f)))),
            )
        }

        Column(
            modifier = Modifier
                .size(safe)
                .align(Alignment.Center)
                .padding(horizontal = 20.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(0.4f))
            Disc(
                artworkUrl = artwork,
                progress = state.progressFraction,
                playing = state.phase == PlaybackPhase.Playing,
                size = (width * 0.36f).coerceIn(64.dp, 92.dp),
            )
            Spacer(Modifier.height(10.dp))

            // Marquee title (single line, slides for long titles).
            MarqueeText(
                text = state.item?.title ?: "Nothing playing",
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = state.item?.artistLabel ?: "Pick a song",
                color = Color.White.copy(alpha = 0.65f),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.weight(1f))

            TransportRow(
                state = state,
                onPlayPause = viewModel::togglePlayPause,
                onNext = viewModel::skipNext,
                onPrev = viewModel::skipPrevious,
            )
            Spacer(Modifier.height(10.dp))

            Text(
                text = state.timeLabel,
                color = Color.White.copy(alpha = 0.45f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun Disc(artworkUrl: String?, progress: Float, playing: Boolean, size: androidx.compose.ui.unit.Dp) {
    val rotation by animateFloatAsState(
        targetValue = if (playing) ((System.currentTimeMillis() % 120_000L) / 120_000f * 360f) else 0f,
        animationSpec = tween(0, easing = LinearEasing),
        label = "discSpin",
    )
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(0xFF1E1C22))
            .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
            .rotate(rotation),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkUrl != null) {
            AsyncImage(
                model = artworkUrl,
                contentDescription = null,
                modifier = Modifier.size(size * 0.86f).clip(CircleShape),
            )
        }
        // Center hole.
        Box(
            modifier = Modifier
                .size(size * 0.16f)
                .background(Color(0xFF0A0A0A), CircleShape),
        )
        // Ring progress around the disc (draw phase only).
        Canvas(modifier = Modifier.size(size)) {
            val stroke = 2.5.dp.toPx()
            drawArc(
                color = Color.White.copy(alpha = 0.18f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawArc(
                color = Color(0xFFC4B0FF),
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Minimal single-line marquee: scrolls when the text overflows. */
@Composable
private fun MarqueeText(text: String, modifier: Modifier = Modifier) {
    var scroll by remember { mutableStateOf(0f) }
    val shouldScroll = text.length > 24
    if (shouldScroll) {
        LaunchedEffect(text) {
            while (true) {
                kotlinx.coroutines.delay(50)
                scroll += 0.5f
                if (scroll > 120f) scroll = 0f
            }
        }
    }
    Box(modifier = modifier.height(24.dp).clip(RoundedCornerShape(4.dp)), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            modifier = Modifier.offset(x = if (shouldScroll) (-scroll).dp else 0.dp),
        )
    }
}

@Composable
private fun TransportRow(
    state: PlaybackUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(enabled = state.hasPrev, onClick = onPrev, icon = "prev")
        CircleButton(
            enabled = true,
            onClick = onPlayPause,
            icon = when {
                state.isPlaying -> "pause"
                state.phase == PlaybackPhase.Buffering -> "buffering"
                else -> "play"
            },
            primary = true,
        )
        CircleButton(enabled = state.hasNext, onClick = onNext, icon = "next")
    }
}

@Composable
private fun CircleButton(
    enabled: Boolean,
    onClick: () -> Unit,
    icon: String,
    primary: Boolean = false,
) {
    val scale by animateFloatAsState(
        targetValue = if (primary) 1f else 0.85f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
    )
    Box(
        modifier = Modifier
            .size(if (primary) 56.dp else 44.dp)
            .clip(CircleShape)
            .background(if (primary) Color(0xFFC4B0FF) else Color(0xFF232027))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (icon) {
            "play" -> PlayGlyph()
            "pause" -> PauseGlyph()
            "buffering" -> BufferingGlyph()
            "prev" -> PrevGlyph()
            "next" -> NextGlyph()
        }
    }
}

@Composable
private fun PlayGlyph() {
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(20.dp)) {
            drawPath(
                path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.2f)
                    lineTo(size.width * 0.82f, size.height * 0.5f)
                    lineTo(size.width * 0.28f, size.height * 0.8f)
                    close()
                },
                color = Color(0xFF1C1A22),
            )
        }
    }
}

@Composable
private fun PauseGlyph() {
    Row(
        modifier = Modifier.size(20.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(6.dp).height(18.dp).background(Color(0xFF1C1A22), RoundedCornerShape(2.dp)))
        Box(Modifier.width(6.dp).height(18.dp).background(Color(0xFF1C1A22), RoundedCornerShape(2.dp)))
    }
}

@Composable
private fun PrevGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.8f, size.height * 0.15f),
            end = Offset(size.width * 0.3f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.8f, size.height * 0.85f),
            end = Offset(size.width * 0.3f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.25f, size.height * 0.15f),
            end = Offset(size.width * 0.25f, size.height * 0.85f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun NextGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.2f, size.height * 0.15f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.2f, size.height * 0.85f),
            end = Offset(size.width * 0.7f, size.height * 0.5f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = Offset(size.width * 0.75f, size.height * 0.15f),
            end = Offset(size.width * 0.75f, size.height * 0.85f),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun BufferingGlyph() {
    var angle by remember { mutableStateOf(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(16)
            angle = (angle + 6f) % 360f
        }
    }
    Canvas(modifier = Modifier.size(20.dp)) {
        drawArc(
            color = Color(0xFF1C1A22),
            startAngle = angle,
            sweepAngle = 120f,
            useCenter = false,
            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
        )
    }
}

private val PlaybackUiState.progressFraction: Float
    get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

private val PlaybackUiState.timeLabel: String
    get() = buildString {
        append(formatClock(positionMs))
        if (durationMs > 0L) {
            append(" / ")
            append(formatClock(durationMs))
        }
    }

private fun formatClock(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}
