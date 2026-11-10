package com.wearsic.next.ui.theme

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The Wearsic dimension system — tuned for round Wear OS faces and derived
 * from the LIVE viewport so one codebase fits 40mm, 44mm and 46mm watches.
 *
 * Two ideas do the work:
 *
 *  1. [WatchProfile] classifies the face (Compact / Standard / Large) from the
 *     real logical size. Every type size, row height and artwork size is then
 *     read from the profile instead of being hardcoded, so a 40mm watch gets
 *     smaller content and a 46mm watch gets larger content — automatically,
 *     with no second layout.
 *
 *  2. [rememberWatchScale] provides a continuous multiplier for the cases
 *     where a smooth ratio is better than three buckets (padding, gaps).
 *
 * Round-screen safe area: the usable width of a circle SHRINKS toward the top
 * and bottom, so full-width rows must be inset. The design padding below is
 * merged with the scaffold insets (never smaller than either, never doubled).
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

    // ---- Content sizes (scaled per profile) ----
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
 * Face class derived from the real logical viewport.
 *
 *  · Compact  — 40mm-class (≈192–200dp): everything tight, type one step down
 *  · Standard — 44mm-class (≈220–232dp): the reference layout
 *  · Large    — 46mm-class (≥240dp): a touch more air
 */
enum class WatchProfile(
    /** Multiplier applied to every metric in the profile. */
    val metricScale: Float,
    /** Title size for the screen header. */
    val headerTitle: TextUnit,
    /** Title size for a song row. */
    val rowTitle: TextUnit,
    /** Artist line size for a song row. */
    val rowArtist: TextUnit,
    /** Artwork size for a song row. */
    val rowArtwork: Dp,
    /** Height of the big accent pill. */
    val pillHeight: Dp,
    /** Height of a secondary glass pill. */
    val secondaryPillHeight: Dp,
    /** Diameter of the player artwork disc. */
    val playerDisc: Dp,
    /** Diameter of the player play button. */
    val playerPlay: Dp,
) {
    Compact(
        metricScale = 0.88f,
        headerTitle = 21.sp,
        rowTitle = 13.sp,
        rowArtist = 11.sp,
        rowArtwork = 36.dp,
        pillHeight = 48.dp,
        secondaryPillHeight = 44.dp,
        playerDisc = 52.dp,
        playerPlay = 46.dp,
    ),
    Standard(
        metricScale = 1f,
        headerTitle = 24.sp,
        rowTitle = 14.sp,
        rowArtist = 12.sp,
        rowArtwork = 40.dp,
        pillHeight = 52.dp,
        secondaryPillHeight = 48.dp,
        playerDisc = 60.dp,
        playerPlay = 50.dp,
    ),
    Large(
        metricScale = 1.06f,
        headerTitle = 26.sp,
        rowTitle = 15.sp,
        rowArtist = 13.sp,
        rowArtwork = 44.dp,
        pillHeight = 56.dp,
        secondaryPillHeight = 52.dp,
        playerDisc = 66.dp,
        playerPlay = 54.dp,
    ),
}

/** The face profile for THIS screen — recomputed on configuration change. */
@Composable
fun rememberWatchProfile(): WatchProfile {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val heightDp = config.screenHeightDp
    val smallest = minOf(widthDp, heightDp)
    return when {
        smallest < 210 -> WatchProfile.Compact
        smallest < 240 -> WatchProfile.Standard
        else -> WatchProfile.Large
    }
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
