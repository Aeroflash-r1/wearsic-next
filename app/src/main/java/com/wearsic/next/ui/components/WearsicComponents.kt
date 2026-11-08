package com.wearsic.next.ui.components

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.Text
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.WearsicGlassBorder
import com.wearsic.next.ui.theme.WearsicGlassFill
import com.wearsic.next.ui.theme.WearsicSurface
import com.wearsic.next.ui.theme.WearsicSurfaceBorder
import com.wearsic.next.ui.theme.WearsicSurfaceBorderSubtle
import com.wearsic.next.ui.theme.WearsicSurfaceRaised
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.WearsicTextPrimaryDark
import com.wearsic.next.ui.theme.WearsicTextWhite60
import com.wearsic.next.ui.theme.WearsicTextWhite80
import com.wearsic.next.ui.theme.wearsicAccentContainer
import com.wearsic.next.ui.theme.watchScaled
import com.wearsic.next.ui.util.WearsicMotion
import com.wearsic.next.ui.util.wearsicClickable

/**
 * Shared Wearsic UI atoms — VERBATIM ports of the original 1.9.48 components
 * (WearsicComponents.kt / WearsicSongRow.kt). Sizes, colours, radii and
 * press physics match the stable release; only imports differ.
 */

@Composable
fun wearsicAccentContainer(): Color = LocalWearsicAccent.current

/** Secondary text tone: lavender with the static palette. */
@Composable
fun wearsicSecondaryText(): Color = WearsicTextPrimary

// ─────────────────────────────────────────────────────────────────────────
// Header / marquee / shimmer
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color? = null
) {
    val accent = accentColor ?: LocalWearsicAccent.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .width(22.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            color = WearsicTextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.4).sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                color = WearsicTextWhite60,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun WearsicMarqueeText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    modifier: Modifier = Modifier,
    fontWeight: FontWeight = FontWeight.Normal,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    startDelayMillis: Int = 900,
    gapDp: Int = 48,
    velocityDp: Int = 26
) {
    val shouldMarquee = text.length > 18
    val textModifier = if (shouldMarquee) {
        modifier.basicMarquee(
            iterations = Int.MAX_VALUE,
            initialDelayMillis = startDelayMillis,
            spacing = MarqueeSpacing(gapDp.dp),
            velocity = velocityDp.dp
        )
    } else {
        modifier
    }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        lineHeight = lineHeight,
        fontWeight = fontWeight,
        letterSpacing = letterSpacing,
        maxLines = 1,
        softWrap = false,
        textAlign = TextAlign.Center,
        modifier = textModifier
    )
}

// ─────────────────────────────────────────────────────────────────────────
// Pill buttons — EXACT 1.9.48 ports
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicPrimaryPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    badgeText: String? = null,
    backgroundColor: Color = LocalWearsicAccent.current,
    contentColor: Color = WearsicTextPrimaryDark,
    iconBgColor: Color = Color(0x1A000000)
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (pressed) WearsicMotion.Snappy else WearsicMotion.Bouncy,
        label = "primaryPillPress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(backgroundColor, backgroundColor.copy(alpha = 0.86f))))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(iconBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = contentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = label,
                        color = contentColor,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (subtitle != null) {
                        Text(
                            text = subtitle,
                            color = contentColor.copy(alpha = 0.7f),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (badgeText != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0x26000000))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = contentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun WearsicSecondaryPillButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = if (pressed) WearsicMotion.Snappy else WearsicMotion.Bouncy,
        label = "secondaryPillPress"
    )

    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(WearsicSurfaceRaised)
            .border(1.dp, WearsicSurfaceBorderSubtle, RoundedCornerShape(WearsicDimens.CardRadius))
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = LocalWearsicAccent.current,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                color = WearsicTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Settings pill: circle, surface fill, white/60 icon, UPPERCASE 11sp label. */
@Composable
fun WearsicSettingsActionPill(
    label: String = "Settings",
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = if (pressed) WearsicMotion.Snappy else WearsicMotion.Bouncy,
        label = "settingsPillPress"
    )

    Box(
        modifier = modifier
            .heightIn(min = WearsicDimens.TouchTarget)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .background(WearsicSurface)
            .border(1.dp, WearsicSurfaceBorder, CircleShape)
            .clickable(interactionSource = interaction, indication = null) {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = WearsicTextWhite60,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                color = WearsicTextWhite80,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Section title — left aligned, optional icon + trailing (1.9.48)
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicSectionTitle(
    label: String,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WearsicTextWhite60,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label.uppercase(),
            color = WearsicTextWhite60,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Spacer(modifier = Modifier.width(6.dp))
            trailing()
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Song row — EXACT 1.9.48 port
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicSongRow(
    title: String,
    artist: String,
    artworkUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleColor: Color = WearsicTextPrimary,
    artistColor: Color = WearsicTextPrimary.copy(alpha = 0.8f),
    titleMaxLines: Int = 2,
    onLongClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(WearsicSurface)
            .border(1.dp, WearsicSurfaceBorderSubtle, RoundedCornerShape(WearsicDimens.CardRadius))
            .wearsicClickable(pressedScale = 0.975f, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                WearsicSongRowArtwork(artworkUrl = artworkUrl, contentDescription = title)
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = title,
                        color = titleColor,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = titleMaxLines,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = artist,
                        color = artistColor,
                        fontSize = 12.sp,
                        lineHeight = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            trailing()
        }
    }
}

/** 40dp ROUNDED-SQUARE artwork with hairline ring; accent music-note chip when absent. */
@Composable
fun WearsicSongRowArtwork(
    artworkUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = watchScaled(40.dp),
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(WearsicDimens.ArtRadius)
    val ring = Modifier.border(1.dp, WearsicSurfaceBorderSubtle, shape)
    if (!artworkUrl.isNullOrBlank()) {
        val request = remember(context, artworkUrl) {
            ImageRequest.Builder(context)
                .data(artworkUrl)
                .size(112)
                .build()
        }
        AsyncImage(
            model = request,
            contentDescription = contentDescription,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(size)
                .clip(shape)
                .then(ring)
        )
    } else {
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(wearsicAccentContainer())
                .then(ring),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = LocalWearsicAccent.current,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/** Small circular row action button (28dp visual, 40dp target). */
@Composable
fun WearsicSongRowActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visualSize: androidx.compose.ui.unit.Dp = 28.dp,
    tint: Color = WearsicTextWhite60,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .wearsicClickable(haptic = false, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(visualSize)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Empty state (canonical, 1.9.48)
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicEmptyState(
    title: String,
    message: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(watchScaled(48.dp))
                .clip(RoundedCornerShape(WearsicDimens.ArtRadius))
                .background(wearsicAccentContainer())
                .border(1.dp, WearsicGlassBorder, RoundedCornerShape(WearsicDimens.ArtRadius)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = LocalWearsicAccent.current,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            color = WearsicTextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        Text(
            text = message,
            color = WearsicTextWhite60,
            fontSize = 10.sp,
            lineHeight = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Search shimmer (kept from the earlier port — driven from the accent)
// ─────────────────────────────────────────────────────────────────────────

@Composable
fun WearsicSearchShimmerRow(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "searchShimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1150, easing = LinearEasing),
            RepeatMode.Restart
        ),
        label = "searchShimmerSweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = LinearOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "searchShimmerPulse"
    )
    val base = LocalWearsicAccent.current.copy(alpha = 0.35f)
    val density = LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(base.copy(alpha = pulse))
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .offset { IntOffset(x = (sweep * 800f * density.density).toInt(), y = 0) }
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.08f), Color.Transparent)
                    )
                )
        )
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(WearsicDimens.RowThumbnail)
                    .clip(RoundedCornerShape(WearsicDimens.ArtRadius))
                    .background(Color.White.copy(alpha = 0.10f))
            )
            Spacer(Modifier.width(10.dp))
            Column {
                Box(
                    Modifier
                        .fillMaxWidth(0.62f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    Modifier
                        .fillMaxWidth(0.4f)
                        .height(9.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                )
            }
        }
    }
}
