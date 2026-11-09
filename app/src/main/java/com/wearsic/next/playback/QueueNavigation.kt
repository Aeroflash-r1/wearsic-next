package com.wearsic.next.playback

import androidx.media3.common.Player

/**
 * Pure queue index arithmetic — no Android, no player, no coroutines.
 *
 * The controller drives playback itself (one Media3 item at a time, resolved
 * lazily through the Rust engine), so next/previous/repeat are OUR rules, not
 * the player's. Keeping them here makes them testable on the JVM.
 *
 * A `null` result means "there is nowhere to go": end of queue with repeat
 * off, or an empty queue.
 */
internal object QueueNavigation {

    /** Index to play after [currentIndex], or null at the end of the queue. */
    fun next(currentIndex: Int, size: Int, repeatMode: Int): Int? = when {
        size <= 0 -> null
        currentIndex < 0 -> 0
        currentIndex < size - 1 -> currentIndex + 1
        repeatMode == Player.REPEAT_MODE_ALL -> 0
        else -> null
    }

    /** Index to play before [currentIndex], or null at the top of the queue. */
    fun previous(currentIndex: Int, size: Int, repeatMode: Int): Int? = when {
        size <= 0 -> null
        currentIndex < 0 -> 0
        currentIndex > 0 -> currentIndex - 1
        repeatMode == Player.REPEAT_MODE_ALL -> size - 1
        else -> null
    }

    /** True when the last entry has been reached (no wrap with repeat off). */
    fun isAtEnd(currentIndex: Int, size: Int, repeatMode: Int): Boolean =
        next(currentIndex, size, repeatMode) == null
}
