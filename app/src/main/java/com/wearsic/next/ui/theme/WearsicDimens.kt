package com.wearsic.next.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The Wearsic dimension system — ported from the original, tuned for the 44mm
 * round Galaxy Watch 7 display (480px @ 340dpi ≈ 226dp circular viewport —
 * confirmed live on the device).
 *
 * Every screen shares one safe-area language:
 *   outer edge → ScreenPadding → content → ScreenPadding → outer edge
 *
 * Values are deliberately conservative: on a round display the usable width
 * shrinks toward the top and bottom of the circle.
 */
object WearsicDimens {

    // ---- Round-screen safe area ----
    /** Horizontal inset for full-width rows on the circular viewport. */
    val ScreenPaddingH: Dp = 14.dp

    /** Top/bottom content inset (before system insets are added). */
    val ScreenPaddingV: Dp = 18.dp

    // ---- Spacing scale ----
    val SpacingXs: Dp = 2.dp
    val SpacingS: Dp = 4.dp
    val SpacingM: Dp = 8.dp
    val SpacingL: Dp = 12.dp
    val SpacingXl: Dp = 16.dp

    // ---- Touch targets ----
    /** Minimum comfortable touch box for a tappable control on Wear OS. */
    val TouchTarget: Dp = 44.dp

    // ---- Corner radii ----
    /** The ONE radius for cards, pills and action rows. */
    val CardRadius: Dp = 20.dp

    /** Radius for square artwork thumbnails (hero chip, list rows). */
    val ArtRadius: Dp = 12.dp

    // ---- Content sizes ----
    /** Song-row / list thumbnail (also the Coil request size baseline). */
    val RowThumbnail: Dp = 40.dp

    /** Minimum row height inside action sheets and menus. */
    val SheetRowMinHeight: Dp = 44.dp

    // ---- Viewport-aware scaling ----
    /**
     * The logical width (dp) the tokens above were tuned for. The 44mm watch
     * reports ~226dp, so a face reporting less logical room gets everything
     * shrunk in lockstep instead of a second hand-maintained layout.
     */
    const val BaselineViewportDp = 240

    /** Floor so content never becomes fiddly on tiny faces. */
    const val MinWatchScale = 0.85f

    /** Anything larger than the baseline is left alone. */
    const val MaxWatchScale = 1f
}

/**
 * The ratio between this face's logical width and the baseline viewport
 * (clamped). 0.94f on the 44mm Galaxy Watch 7 (226dp / 240dp).
 */
@Composable
fun rememberWatchScale(): Float {
    val widthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    return (widthDp / WearsicDimens.BaselineViewportDp)
        .coerceIn(WearsicDimens.MinWatchScale, WearsicDimens.MaxWatchScale)
}

/** [base] scaled by [rememberWatchScale] — call inside a @Composable. */
@Composable
fun watchScaled(base: Dp): Dp = base * rememberWatchScale()

/**
 * Canonical content padding for scrolling screens: the Wearsic design padding
 * MERGED with the scaffold insets (never smaller than either, never doubled).
 */
@Composable
fun wearsicListContentPadding(scaffoldPadding: PaddingValues): PaddingValues {
    val direction = LocalLayoutDirection.current
    val scale = rememberWatchScale()
    val h = WearsicDimens.ScreenPaddingH * scale
    val v = WearsicDimens.ScreenPaddingV * scale
    val start = maxOf(h, scaffoldPadding.calculateStartPadding(direction))
    val end = maxOf(h, scaffoldPadding.calculateEndPadding(direction))
    val top = maxOf(v, scaffoldPadding.calculateTopPadding())
    val bottom = maxOf(v, scaffoldPadding.calculateBottomPadding())
    return PaddingValues(start = start, top = top, end = end, bottom = bottom)
}
