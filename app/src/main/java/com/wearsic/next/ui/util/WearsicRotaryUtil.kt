package com.wearsic.next.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.wear.compose.foundation.requestFocusOnHierarchyActive
import androidx.wear.compose.foundation.rememberActiveFocusRequester
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import kotlinx.coroutines.delay

/**
 * Bezel/crown scrolling for list screens — a port of the original Wearsic
 * rotary util.
 *
 * DELEGATES to the framework's [rotaryScrollable]: hand-rolled focusable()
 * plumbing intermittently ends up with no focus target, so a notch produces
 * only the system's touch-bezel tick and NO scroll on the real Galaxy Watch.
 * The framework owns focus acquisition, event routing and rotary haptics —
 * every notch scrolls like a built-in Wear app.
 *
 * FOCUS: the framework's own [rememberActiveFocusRequester] +
 * [Modifier.requestFocusOnHierarchyActive] pair claims focus whenever the
 * screen hierarchy becomes active (cold entry, screen off/on, resume after
 * navigation), with a small bounded retry covering the first-frame edge
 * cases.
 *
 * BEHAVIOR/HAPTICS: continuous [RotaryScrollableDefaults.behavior] with NO
 * fling — one scroll increment per notch and the framework's per-step
 * haptic.
 */
val LocalRotaryDirectionInverted = staticCompositionLocalOf { false }

@Composable
fun Modifier.wearsicRotaryScroll(
    listState: ScalingLazyListState,
    focusRequester: FocusRequester = rememberActiveFocusRequester(),
    enabled: Boolean = true,
    reverseDirection: Boolean = LocalRotaryDirectionInverted.current,
): Modifier {
    LaunchedEffect(focusRequester, enabled) {
        if (!enabled) return@LaunchedEffect
        // Fallback retry for the one-frame windows the coordinator misses
        // (the node may not be attached on the very first composition pass).
        repeat(4) {
            if (runCatching { focusRequester.requestFocus() }.getOrNull() == true) {
                return@LaunchedEffect
            }
            delay(60)
        }
    }
    val armed = this.rotaryScrollable(
        RotaryScrollableDefaults.behavior(
            listState,
            null,          // no fling: notch = one measured increment
            true,          // per-step short tick haptics
        ),
        focusRequester,
        reverseDirection,
    )
    // Arm the hierarchy-active coordinator ONLY while this screen actually
    // wants the crown: an armed coordinator on a non-visible list would claim
    // focus for an off-screen list (invisible bezel scrolling / focus fights).
    return if (enabled) armed.requestFocusOnHierarchyActive() else armed
}
