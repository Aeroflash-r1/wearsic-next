package com.wearsic.next.ui.util

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import com.wearsic.next.ui.theme.WearsicPrefs
import kotlinx.coroutines.android.awaitFrame

/**
 * One motion system for the whole app — a port of the original Wearsic
 * motion spec.
 *
 * Every press, entrance and state change pulls its spec from [WearsicMotion]
 * so the app moves like a single product. Springs are used for anything the
 * user touches: they feel physical and stay interruptible.
 *
 * TUNED FOR WEAR: stiffness values are deliberately high and entrances short.
 * A watch renders at a small resolution on a weak SoC, so long fades and soft
 * springs read as lag rather than polish. Everything settles well under 200ms.
 */
object WearsicMotion {
    /** Quick, crisp — for taps, presses and toggles. */
    val Snappy = spring<Float>(dampingRatio = 0.80f, stiffness = 6000f)

    /** The standard press scale used by [wearsicClickable] and friends. */
    const val PRESS_SCALE = 0.95f

    /** Soft, settled — for layout and colour changes. */
    val Gentle = spring<Float>(dampingRatio = 0.90f, stiffness = 2400f)

    /** Light overshoot — for release animations that should feel springy. */
    val Bouncy = spring<Float>(dampingRatio = 0.62f, stiffness = 3200f)
}

/**
 * Springy press feedback: the element squishes down on touch and springs
 * back with a little overshoot when released.
 */
@Composable
fun Modifier.wearsicPress(
    pressed: Boolean,
    pressedScale: Float = 0.94f
): Modifier {
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) WearsicMotion.Snappy else WearsicMotion.Bouncy,
        label = "wearsicPress"
    )
    return this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Content entrance: the element scales up slightly and rises into place, with
 * an optional [delayMillis] so a column of things can stagger.
 *
 * DELIBERATELY DOES NOT FADE: an alpha pass read as a blank flash on the
 * watch and wasted a full-screen alpha layer. Scale + rise reads as an
 * entrance while keeping content visible from frame one. Runs once, layer
 * only (no recomposition) — battery-friendly.
 */
@Composable
fun Modifier.wearsicEntrance(
    delayMillis: Int = 0,
    fromScale: Float = 0.97f,
    riseDp: Float = 8f
): Modifier {
    var entered by remember { mutableStateOf(false) }
    val risePx = with(LocalDensity.current) { riseDp * density }
    LaunchedEffect(Unit) {
        awaitFrame()
        entered = true
    }

    val progress by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(
            durationMillis = 180,
            delayMillis = delayMillis,
            easing = FastOutSlowInEasing
        ),
        label = "wearsicEntrance"
    )

    return this.graphicsLayer {
        val s = fromScale + (1f - fromScale) * progress
        scaleX = s
        scaleY = s
        translationY = (1f - progress) * risePx
    }
}

/**
 * A clickable modifier with the app's standard press physics and haptics.
 * The spring runs on the render thread (deferred state read) so it never
 * forces recomposition of the row that owns it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Modifier.wearsicClickable(
    enabled: Boolean = true,
    pressedScale: Float = 0.95f,
    haptic: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = if (pressed) WearsicMotion.Snappy else WearsicMotion.Bouncy,
        label = "wearsicClickablePress"
    )
    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            role = Role.Button,
            onLongClick = onLongClick?.let { action ->
                {
                    if (haptic && WearsicPrefs.haptics) {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    action()
                }
            },
            onClick = {
                if (haptic && WearsicPrefs.haptics) {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onClick()
            }
        )
}
