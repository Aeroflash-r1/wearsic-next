package com.wearsic.next.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import com.wearsic.next.ui.theme.WearsicDimens
import com.wearsic.next.ui.theme.LocalWearsicAccent
import com.wearsic.next.ui.theme.WearsicTextPrimary
import com.wearsic.next.ui.theme.WearsicTextWhite60

/**
 * Shared Wearsic UI atoms — a port of the original components file, trimmed
 * to what Wearsic Next needs. One design language: accent bar + generous
 * type for headers, glass pills for rows, gated marquees for battery.
 */

/**
 * Canonical screen header: a small accent bar and generous type establish
 * hierarchy. One colour across every screen — never per-screen colours.
 * Icons are deliberately absent above the title.
 */
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
            // Long dynamic titles wrap to two lines max, then ellipsize —
            // a header must never push the list off-screen.
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

/** Quiet uppercase section separator used inside lists. */
@Composable
fun WearsicSectionTitle(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label.uppercase(),
        color = WearsicTextWhite60,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth()
    )
}

/**
 * THE marquee. Short texts never overflow a watch face — rendering them
 * through basicMarquee still keeps an infinite animation clock alive for as
 * long as the text is on screen (a real heat source on a 300mAh battery).
 * Gate it: static centered text unless it can actually overflow
 * (≈18+ chars on the 454px round face).
 */
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

/**
 * Expressive search loading: a shimmer placeholder shaped like a track row.
 * One infinite transition drives BOTH the breathing base and the sweeping
 * highlight band, whose alpha is read in the DRAW phase — the pulse
 * invalidates a draw, never a recomposition.
 */
@Composable
fun WearsicSearchShimmerRow(
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "searchShimmer")
    val sweep by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1150, easing = LinearEasing), RepeatMode.Restart),
        label = "searchShimmerSweep"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(
            tween(650, easing = LinearOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "searchShimmerPulse"
    )
    val base = LocalWearsicAccent.current.copy(alpha = 0.35f)
    val density = androidx.compose.ui.platform.LocalDensity.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(WearsicDimens.CardRadius))
            .background(base.copy(alpha = pulse))
    ) {
        // Sweeping highlight band, clipped to the card.
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
