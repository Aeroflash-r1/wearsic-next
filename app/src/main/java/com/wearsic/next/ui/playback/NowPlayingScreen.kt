package com.wearsic.next.ui.playback

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassEmpty
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.util.Locale
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import com.wearsic.next.playback.PlaybackPhase
import com.wearsic.next.playback.PlaybackUiState
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.playback.PlaybackViewModel
import com.wearsic.next.ui.components.WearsicMarqueeText
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.ui.theme.WearsicBlack
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicError
import com.wearsic.next.ui.theme.WearsicPrefs
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.WearsicTextMuted
import com.wearsic.next.ui.theme.watchScaled
import com.wearsic.next.ui.theme.wearsicListContentPadding
import com.wearsic.next.ui.util.ArtworkColors
import com.wearsic.next.ui.util.extractArtworkColors
import com.wearsic.next.ui.util.wearsicClickable
import com.wearsic.next.ui.util.wearsicEntrance
import com.wearsic.next.ui.util.wearsicRotaryScroll

/**
 * NOW PLAYING — a faithful port of the original Wearsic player screen,
 * recomposed for the standalone Rust engine.
 *
 * Layout rules that only make sense on a circle:
 *   · The widest part of the display is its MIDDLE, so the album art disc —
 *     not the title and not the transport — owns the centre. Title, artist
 *     and time sit in the readable mid-band below it, and the compact
 *     transport hugs the lower third. Nothing important is placed in the
 *     clipped top or the curved bottom edge.
 *   · Everything is measured from the space actually available
 *     ([BoxWithConstraints]) and switches to a compact scale on short
 *     viewports, so the composition can never clip or push a control
 *     off-face. (The 44mm Galaxy Watch 7 reports ~226dp — it runs compact.)
 *   · The backdrop is the artwork itself, darkened with a fixed scrim plus a
 *     cheap radial tint — no blur shaders.
 *   · Playback progress is a ring AROUND the disc, drawn in the draw phase
 *     only: position ticks never recompose the screen.
 *   · The disc DRAGS along its ring to scrub, and rotates one turn per track
 *     while playing — frozen when paused, so nothing animates while you
 *     simply listen.
 *   · Actions live on a swipeable second page, reachable by swipe or the
 *     edge page dots.
 */
@Composable
fun NowPlayingScreen(
    viewModel: PlaybackViewModel,
    onNavigateToQueue: () -> Unit,
    onNavigateToVolume: () -> Unit,
    onNavigateToDownloads: () -> Unit = {},
    onArtworkColor: (ArtworkColors) -> Unit = {},
    onDownloadTrack: ((onResult: (Boolean) -> Unit) -> Unit)? = null,
    onNavigateToPickPlaylist: () -> Unit = {},
) {
    val playbackState by viewModel.state.collectAsState()
    val track = playbackState.item
    val haptic = LocalHapticFeedback.current
    var artworkColors by remember(track?.artworkUrl) { mutableStateOf(ArtworkColors.Default) }

    val durationMs: Long = if (playbackState.durationMs > 0L) {
        playbackState.durationMs
    } else {
        track?.durationMs ?: 0L
    }


    Box(modifier = Modifier.fillMaxSize()) {

        // ── The artwork IS the background ────────────────────────────────
        ArtworkBackdrop(
            artworkUrl = track?.artworkUrl,
            glowColor = artworkColors.accent,
            onColors = {
                artworkColors = it
                onArtworkColor(it)
            }
        )

        val pagerState = rememberPagerState(pageCount = { 2 })

        // The page dots flip THROUGH this mirror: the pager's own
        // currentPage reads can lag the real scroll position.
        var pageIndex by remember { mutableIntStateOf(0) }
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.currentPage }.collect { pageIndex = it }
        }

        // Natural screen-open: a soft zoom + fade. Runs once per entry,
        // layer-only (no recomposition).
        var opened by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) {
            withFrameNanos { }
            opened = true
        }
        val openProgress by animateFloatAsState(
            targetValue = if (opened) 1f else 0f,
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            label = "playerOpen"
        )

        // The player sits inside one layer that animates the open.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = openProgress
                    val s = 0.96f + 0.04f * p
                    scaleX = s
                    scaleY = s
                    alpha = 0.7f + 0.3f * p
                }
        ) {
            ScreenScaffold(modifier = Modifier.fillMaxSize()) { contentPadding: PaddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding)
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        when (page) {
                            0 -> NowPlayingPage(
                                item = track,
                                state = playbackState,
                                durationMs = durationMs,
                                positionFlow = viewModel.positionMs,
                                onTogglePlayPause = viewModel::togglePlayPause,
                                onSkipPrevious = viewModel::skipPrevious,
                                onSkipNext = viewModel::skipNext,
                                onSeekTo = viewModel::seekTo,
                                haptic = haptic,
                                colors = artworkColors,
                                rotateArtwork = WearsicPrefs.rotateArtwork,
                            )
                            else -> ActionsPage(
                                state = playbackState,
                                isFavorite = track?.let { FavoritesStore.isFavorite(it.videoId) } == true,
                                onToggleFavorite = { track?.let { FavoritesStore.toggle(it) } },
                                onNavigateToQueue = onNavigateToQueue,
                                onNavigateToVolume = onNavigateToVolume,
                                onNavigateToDownloads = onNavigateToDownloads,
                                onAddToPlaylist = onNavigateToPickPlaylist,
                                onCacheTrack = onDownloadTrack
                                    ?: viewModel::cacheCurrentTrack,
                                active = pageIndex == 1,
                                onShuffle = viewModel::setShuffle,
                                onCycleRepeat = viewModel::cycleRepeatMode,
                            )
                        }
                    }
                }
            }
        }

        // Page dots hug the VERY EDGE of the round face — outside the
        // scaffold's inset. The 44dp touch strip spans inward from the edge;
        // tapping it flips the page instantly (deterministic on a watch).
        val dotScope = rememberCoroutineScope()
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .widthIn(min = WearsicDimens.TouchTarget)
                .semantics {
                    contentDescription =
                        if (pageIndex == 0) "Show player actions" else "Show now playing"
                }
                .wearsicClickable {
                    dotScope.launch {
                        pagerState.scrollToPage((pageIndex + 1) % 2)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(5.dp),
            horizontalAlignment = Alignment.End
        ) {
            repeat(2) { index ->
                val active = pageIndex == index
                Box(
                    modifier = Modifier
                        .padding(end = 3.dp)
                        .size(width = 4.dp, height = if (active) 11.dp else 5.dp)
                        .clip(CircleShape)
                        .background(
                            if (active) artworkColors.accent else Color.White.copy(alpha = 0.32f)
                        )
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Page 0 — Now Playing: artwork disc, metadata, transport.
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun NowPlayingPage(
    item: PlaybackItem?,
    state: PlaybackUiState,
    durationMs: Long,
    positionFlow: StateFlow<Long>,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    haptic: HapticFeedback,
    colors: ArtworkColors,
    rotateArtwork: Boolean,
) {
    val hasTrack = item != null
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compact = maxHeight < 236.dp
        // Slightly smaller discs on the tight 44mm face so that the fixed
        // stack can never clip on the curved edge.
        val discSize: Dp = (maxWidth * 0.34f).coerceIn(
            if (compact) 52.dp else 64.dp,
            if (compact) 64.dp else 80.dp
        )
        val playSize: Dp = if (compact) 48.dp else (maxWidth * 0.26f).coerceIn(50.dp, 60.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = watchScaled(WearsicDimens.ScreenPaddingH)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(0.7f))

            key(item?.videoId) {
                ArtworkDisc(
                    artworkUrl = item?.artworkUrl,
                    hasTrack = hasTrack,
                    positionFlow = positionFlow,
                    durationMs = durationMs,
                    playing = state.phase == PlaybackPhase.Playing,
                    colors = colors,
                    size = discSize,
                    rotateArtwork = rotateArtwork,
                    // Drag the disc along its ring to scrub: angular position
                    // maps to the track duration (top = 0, clockwise).
                    modifier = Modifier
                        .wearsicEntrance(fromScale = 0.90f, riseDp = 10f)
                        .pointerInput(hasTrack, item?.videoId, durationMs) {
                            if (!hasTrack || durationMs <= 0L) return@pointerInput
                            detectDragGestures(
                                onDragStart = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val boxSize = size
                                    val cx = boxSize.width / 2f
                                    val cy = boxSize.height / 2f
                                    val dx = change.position.x - cx
                                    val dy = change.position.y - cy
                                    val rad = kotlin.math.atan2(dy, dx)
                                    // -90° (up) = 0: clockwise fraction.
                                    val frac = (
                                        (rad * 180f / kotlin.math.PI.toFloat()) +
                                            90f + 360f
                                        ) % 360f / 360f
                                    onSeekTo(
                                        (durationMs * frac).toLong().coerceIn(0L, durationMs)
                                    )
                                }
                            )
                        }
                )
            }

            Spacer(modifier = Modifier.height(if (compact) 4.dp else 8.dp))

            // Single line, always: the full name slides sideways instead of
            // wrapping (a wrap pushed the artist line into the curved edge).
            WearsicMarqueeText(
                text = item?.title ?: "Nothing playing",
                color = if (state.phase == PlaybackPhase.Error) WearsicError else Color.White,
                fontSize = if (compact) 13.sp else 15.sp,
                lineHeight = if (compact) 15.sp else 18.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.2).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .wearsicEntrance(delayMillis = 50, fromScale = 0.98f, riseDp = 6f)
            )

            // One line: artist · elapsed/total. The time segment is dropped
            // when no duration is known; a blank artist never leaves a
            // dangling "·"; a failure turns the line into the retry hint.
            // The clock is its own composable reading its own flow, so each
            // 500ms tick recomposes THAT text only — not the page.
            val subtitleColor = Color.White.copy(alpha = 0.70f)
            val subtitleModifier = Modifier
                .fillMaxWidth()
                .padding(top = 3.dp)
                .wearsicEntrance(delayMillis = 90, fromScale = 0.98f, riseDp = 6f)

            when {
                item == null -> SubtitleText("Pick a song from Search", subtitleColor, subtitleModifier)
                state.phase == PlaybackPhase.Error -> SubtitleText(
                    state.errorMessage?.takeIf { it.isNotBlank() }
                        ?: "Playback failed — tap play to retry",
                    subtitleColor,
                    subtitleModifier
                )
                else -> Row(
                    modifier = subtitleModifier,
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val artist = item.artistLabel.split(", ").firstOrNull()
                        ?.takeIf { it.isNotBlank() }
                    if (artist != null) {
                        Text(
                            text = artist,
                            color = subtitleColor,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                    if (durationMs > 0L) {
                        if (artist != null) {
                            Text(
                                text = " · ",
                                color = subtitleColor,
                                fontSize = 11.sp,
                                lineHeight = 14.sp,
                                maxLines = 1
                            )
                        }
                        PlaybackTimeLabel(positionFlow, durationMs, subtitleColor)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            TransportRow(
                state = state,
                playSize = playSize,
                onTogglePlayPause = onTogglePlayPause,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                colors = colors,
                enabled = hasTrack
            )

            Spacer(modifier = Modifier.height(if (compact) 10.dp else 12.dp))
        }
    }
}

@Composable
private fun SubtitleText(text: String, color: Color, modifier: Modifier) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/**
 * "elapsed / total" — the only text that changes on a position tick, so it
 * lives in its own composable and recomposes alone.
 */
@Composable
private fun PlaybackTimeLabel(positionFlow: StateFlow<Long>, durationMs: Long, color: Color) {
    val position by positionFlow.collectAsState()
    Text(
        text = "${formatTime(position)} / ${formatTime(durationMs)}",
        color = color,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        maxLines = 1,
    )
}

/**
 * The artwork disc: radial accent halo behind it (depth without a blur
 * shader), the ring progress drawn in the DRAW phase, and the artwork
 * rotating one turn per track. The rotation is driven by the SAME animated
 * progress the ring uses, so the disc freezes when playback pauses — nothing
 * animates while you simply listen.
 */
@Composable
private fun ArtworkDisc(
    artworkUrl: String?,
    hasTrack: Boolean,
    positionFlow: StateFlow<Long>,
    durationMs: Long,
    playing: Boolean,
    colors: ArtworkColors,
    size: Dp,
    modifier: Modifier = Modifier,
    rotateArtwork: Boolean = true,
) {
    val ringStroke = 3f

    // Continuously animated progress, driven OFF the composition: the flow is
    // collected in a coroutine and the only State read is by the draw phase /
    // graphics layer. A 500ms tick therefore costs a repaint, never a
    // recomposition of the page.
    val progressAnim = remember { Animatable(0f) }
    LaunchedEffect(positionFlow, durationMs, playing) {
        if (durationMs <= 0L) {
            progressAnim.snapTo(0f)
            return@LaunchedEffect
        }
        fun fraction(pos: Long): Float =
            (pos.toFloat() / durationMs).coerceIn(0f, 1f)
        // Land on the real position first (opening the screen, scrubbing,
        // resuming) instead of sweeping from wherever the ring was.
        progressAnim.snapTo(fraction(positionFlow.value))
        if (!playing) return@LaunchedEffect
        positionFlow.collect { pos ->
            val target = fraction(pos)
            // A big jump (scrub / track change) snaps; normal play advances
            // linearly over one tick so the ring moves at real speed.
            if (kotlin.math.abs(target - progressAnim.value) > 0.2f) {
                progressAnim.snapTo(target)
            } else {
                progressAnim.animateTo(target, tween(500, easing = LinearEasing))
            }
        }
    }
    // `asState()` is remembered: reading it happens in draw/graphicsLayer.
    val progress: State<Float> = remember(progressAnim) { progressAnim.asState() }
    val context = LocalContext.current
    val request = remember(context, artworkUrl) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(160)
            .crossfade(200)
            .build()
    }

    Box(
        modifier = modifier
            .size(size + ringStroke.dp * 2),
        contentAlignment = Alignment.Center
    ) {
        // Radial accent halo behind the disc — depth without a blur shader.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(colors.accent.copy(alpha = 0.22f), Color.Transparent)
                    )
                )
        )
        // Ring progress (draw phase only).
        Box(
            modifier = Modifier
                .size(size + ringStroke.dp * 2)
                .drawBehind {
                    val stroke = Stroke(width = ringStroke.dp.toPx(), cap = StrokeCap.Round)
                    val inset = ringStroke.dp.toPx()
                    val arcSize = androidx.compose.ui.geometry.Size(
                        this.size.width - 2 * inset,
                        this.size.height - 2 * inset
                    )
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(
                        color = Color.White.copy(alpha = 0.14f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke
                    )
                    val sweep = if (hasTrack) progress.value * 360f else 0f
                    if (sweep > 0.5f) {
                        drawArc(
                            color = colors.accent,
                            startAngle = -90f,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke
                        )
                    }
                }
        )
        if (!artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .graphicsLayer {
                        rotationZ = if (rotateArtwork) discRotation(progress.value) else 0f
                    }
            )
        } else {
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(CircleShape)
                    .background(colors.accent.copy(alpha = 0.18f))
                    .border(1.dp, Color.White.copy(alpha = 0.14f), CircleShape)
                    .graphicsLayer {
                        rotationZ = if (rotateArtwork) discRotation(progress.value) else 0f
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.MusicNote,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.75f),
                    modifier = Modifier.size(size * 0.34f)
                )
            }
        }
    }
}

/** One clockwise revolution of the disc per full track. */
private fun discRotation(progress: Float): Float = progress.coerceIn(0f, 1f) * 360f

/**
 * The artwork backdrop: one full-bleed crop under a fixed dark scrim, plus a
 * soft hue-matched "aurora" glow so the flat black scrim reads as depth.
 * Decoded once per artwork at 256px — cheap on memory. Palette extraction
 * runs off the main thread, once per artwork.
 */
@Composable
private fun ArtworkBackdrop(
    artworkUrl: String?,
    glowColor: Color,
    onColors: (ArtworkColors) -> Unit
) {
    var bitmap by remember(artworkUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(bitmap, artworkUrl) {
        onColors(withContext(Dispatchers.Default) { extractArtworkColors(bitmap) })
    }
    if (artworkUrl.isNullOrBlank()) {
        Box(modifier = Modifier.fillMaxSize().background(WearsicBlack))
        return
    }
    val context = LocalContext.current
    val request = remember(context, artworkUrl) {
        ImageRequest.Builder(context)
            .data(artworkUrl)
            .size(256)
            .allowHardware(false)
            .crossfade(180)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { bitmap = (it.result.drawable as? BitmapDrawable)?.bitmap },
        modifier = Modifier.fillMaxSize().background(WearsicBlack)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.78f),
                    0.45f to Color.Black.copy(alpha = 0.48f),
                    1f to Color.Black.copy(alpha = 0.88f)
                )
            )
    )
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val center = with(LocalDensity.current) {
            androidx.compose.ui.geometry.Offset(
                (maxWidth * 0.5f).toPx(),
                (maxHeight * 0.32f).toPx()
            )
        }
        val radius = with(LocalDensity.current) { (maxWidth * 0.95f).toPx() }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.15f), Color.Transparent),
                        center = center,
                        radius = radius
                    )
                )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Page 1 — Actions sub-screen (favorite / shuffle / repeat / queue / output)
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun ActionsPage(
    state: PlaybackUiState,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToVolume: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onCacheTrack: (onResult: (Boolean) -> Unit) -> Unit,
    active: Boolean,
    onShuffle: (Boolean) -> Unit,
    onCycleRepeat: () -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val hasTrack = state.item != null
    var cacheState by remember { mutableStateOf("idle") }
    val perKeyCached = state.item?.let { com.wearsic.next.ui.data.DownloadStore.isDownloaded(it.videoId) } == true
    ScalingLazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .wearsicRotaryScroll(listState, enabled = active),
        contentPadding = wearsicListContentPadding(PaddingValues(bottom = 44.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        item {
            ActionRow(
                icon = if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                label = if (isFavorite) "Favorited" else "Favorite",
                enabled = hasTrack,
                onClick = onToggleFavorite,
                testTag = "player_favorite_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.Rounded.Shuffle,
                label = if (state.shuffleEnabled) "Shuffle on" else "Shuffle off",
                enabled = hasTrack,
                onClick = { onShuffle(!state.shuffleEnabled) },
                testTag = "player_shuffle_button"
            )
        }
        item {
            ActionRow(
                icon = when (state.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Rounded.RepeatOne
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> Icons.Rounded.Repeat
                    else -> Icons.Rounded.Sync
                },
                label = when (state.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeat one"
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> "Repeat queue"
                    else -> "Repeat off"
                },
                enabled = hasTrack,
                onClick = onCycleRepeat,
                testTag = "player_repeat_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.Rounded.CloudDownload,
                label = when {
                    cacheState == "busy" -> "Saving…"
                    cacheState == "failed" -> "Save failed — retry"
                    perKeyCached -> "Saved offline"
                    else -> "Save offline"
                },
                enabled = hasTrack && cacheState != "busy",
                onClick = {
                    cacheState = "busy"
                    onCacheTrack { ok -> cacheState = if (ok) "done" else "failed" }
                },
                testTag = "player_save_offline_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.Rounded.Folder,
                label = "Saved tracks",
                enabled = true,
                onClick = onNavigateToDownloads,
                testTag = "player_saved_tracks_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.Rounded.Add,
                label = "Add to playlist",
                enabled = hasTrack,
                onClick = onAddToPlaylist,
                testTag = "player_add_to_playlist_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                label = "Queue",
                enabled = true,
                onClick = onNavigateToQueue,
                testTag = "player_queue_shortcut_button"
            )
        }
        item {
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                label = "Audio Output",
                enabled = true,
                onClick = onNavigateToVolume,
                testTag = "player_output_button"
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Transport
// ─────────────────────────────────────────────────────────────────────────

/**
 * Previous / play / next. Skip targets keep a full 44dp touch box (they are
 * the controls most often hit blind, while walking) but a quieter visual
 * disc, so the play button still carries the weight.
 */
@Composable
private fun TransportRow(
    state: PlaybackUiState,
    playSize: Dp,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    colors: ArtworkColors,
    enabled: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        SkipButton(
            icon = Icons.Rounded.SkipPrevious,
            contentDescription = "Previous Track",
            onClick = onSkipPrevious,
            accent = colors.accent,
            enabled = enabled && state.hasPrev
        )
        Spacer(modifier = Modifier.weight(1f))
        PlayButton(
            state = state,
            size = playSize,
            onClick = onTogglePlayPause,
            colors = colors,
            enabled = enabled && !(state.isBuffering && state.phase != PlaybackPhase.Buffering)
        )
        Spacer(modifier = Modifier.weight(1f))
        SkipButton(
            icon = Icons.Rounded.SkipNext,
            contentDescription = "Next Track",
            onClick = onSkipNext,
            accent = colors.accent,
            enabled = enabled && state.hasNext
        )
    }
}

@Composable
private fun SkipButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    accent: Color,
    enabled: Boolean
) {
    Box(
        modifier = Modifier.size(WearsicDimens.TouchTarget),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = if (enabled) 0.20f else 0.08f))
                .border(1.dp, Color.White.copy(alpha = if (enabled) 0.12f else 0.06f), CircleShape)
                .wearsicClickable(enabled = enabled, pressedScale = 0.88f, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = Color.White.copy(alpha = if (enabled) 0.92f else 0.40f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

/**
 * The primary control: an artwork-tinted disc with a near-black glyph. The
 * glyph pops in on every play/pause/buffer change (a keyed one-shot scale),
 * and the disc keeps a hairline ring so it never melts into the backdrop.
 */
@Composable
private fun PlayButton(
    state: PlaybackUiState,
    size: Dp,
    onClick: () -> Unit,
    colors: ArtworkColors,
    enabled: Boolean
) {
    val glyph = when {
        state.isBuffering -> Icons.Rounded.HourglassEmpty
        state.isPlaying -> Icons.Rounded.Pause
        else -> Icons.Rounded.PlayArrow
    }
    Box(
        modifier = Modifier
            .size(size)
            .wearsicClickable(enabled = true, pressedScale = 0.92f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(colors.blobTint.copy(alpha = if (enabled || state.isBuffering) 1f else 0.4f))
                .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            // Keyed so the glyph pops in on every play/pause/buffer change.
            key(glyph) {
                Icon(
                    imageVector = glyph,
                    contentDescription = when {
                        state.isBuffering -> "Buffering"
                        state.isPlaying -> "Pause"
                        else -> "Play"
                    },
                    tint = WearsicBlack,
                    modifier = Modifier
                        .size(size * 0.40f)
                        .wearsicEntrance(fromScale = 0.70f, riseDp = 0f)
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Actions page rows
// ─────────────────────────────────────────────────────────────────────────

/**
 * One row of the Actions sub-screen: 44dp+ touch box, icon + label, the same
 * flat glass language as the list rows.
 */
@Composable
private fun ActionRow(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = WearsicDimens.SheetRowMinHeight)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(Color.White.copy(alpha = 0.10f))
            .border(1.dp, WearsicSurfaceBorder, RoundedCornerShape(WearsicDimens.CardRadius))
            .wearsicClickable(enabled = enabled, pressedScale = 0.98f, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) Color.White.copy(alpha = 0.92f) else WearsicTextMuted,
            modifier = Modifier.size(21.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            color = if (enabled) Color.White.copy(alpha = 0.94f) else WearsicTextMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────

/** True while a track is being resolved or buffered (spinner glyph, etc.). */
internal val PlaybackUiState.isBuffering: Boolean
    get() = phase == PlaybackPhase.Buffering ||
        phase == PlaybackPhase.Resolving ||
        phase == PlaybackPhase.Preparing

/** m:ss (or h:mm:ss) for the elapsed/total read-out. */
internal fun formatTime(ms: Long): String {
    if (ms <= 0L) return "--:--"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    // Locale.US, not the default: `%d` under an Arabic/Persian locale emits
    // Eastern-Arabic digits, and a clock read-out flipping numeral systems is
    // a bug, not localisation.
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
