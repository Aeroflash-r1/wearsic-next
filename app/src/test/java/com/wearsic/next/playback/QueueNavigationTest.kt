package com.wearsic.next.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

/** The player drives next/previous itself; these are its rules. */
class QueueNavigationTest {

    @Test
    fun next_moves_forward() {
        assertEquals(1, QueueNavigation.next(0, 3, Player.REPEAT_MODE_OFF))
        assertEquals(2, QueueNavigation.next(1, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun next_stops_at_the_end_when_repeat_is_off() {
        assertNull(QueueNavigation.next(2, 3, Player.REPEAT_MODE_OFF))
        assertTrue(QueueNavigation.isAtEnd(2, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun next_wraps_to_the_top_with_repeat_all() {
        assertEquals(0, QueueNavigation.next(2, 3, Player.REPEAT_MODE_ALL))
        assertFalse(QueueNavigation.isAtEnd(2, 3, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun next_from_an_unset_index_starts_at_the_top() {
        assertEquals(0, QueueNavigation.next(-1, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun empty_queue_has_no_next_or_previous() {
        assertNull(QueueNavigation.next(-1, 0, Player.REPEAT_MODE_ALL))
        assertNull(QueueNavigation.previous(-1, 0, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun previous_moves_back() {
        assertEquals(1, QueueNavigation.previous(2, 3, Player.REPEAT_MODE_OFF))
        assertEquals(0, QueueNavigation.previous(1, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun previous_at_the_top_restarts_instead_of_wrapping() {
        assertNull(QueueNavigation.previous(0, 3, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun previous_wraps_to_the_end_with_repeat_all() {
        assertEquals(2, QueueNavigation.previous(0, 3, Player.REPEAT_MODE_ALL))
    }

    @Test
    fun single_track_queue_has_no_skips_without_repeat() {
        assertNull(QueueNavigation.next(0, 1, Player.REPEAT_MODE_OFF))
        assertNull(QueueNavigation.previous(0, 1, Player.REPEAT_MODE_OFF))
    }

    @Test
    fun repeat_one_still_advances_on_an_explicit_skip() {
        // Repeat-one is handled by the ENDED callback (replay the track);
        // an explicit skip follows the same no-wrap rule as repeat-off.
        assertNull(QueueNavigation.next(0, 1, Player.REPEAT_MODE_ONE))
        assertEquals(2, QueueNavigation.next(1, 3, Player.REPEAT_MODE_ONE))
    }
}
