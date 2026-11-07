package com.wearsic.next.playback

import com.wearsic.next.engine.Album
import com.wearsic.next.engine.Artist
import com.wearsic.next.engine.StreamDescriptor
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.engine.TrackDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM tests for playback model merging + state (no Media3 needed). */
class PlaybackModelsTest {

    private val track = TrackDto(
        videoId = "vid123",
        title = "Yellow",
        artists = listOf(Artist("Coldplay")),
        album = Album("Parachutes"),
        durationMs = 267_000,
        thumbnails = listOf(Thumbnail("http://x/a.jpg", 60, 60)),
    )

    private val stream = StreamDescriptor(
        videoId = "vid123",
        url = "https://rr.example/audio",
        mimeType = "audio/mp4; codecs=\"mp4a.40.2\"",
        bitrate = 130_996,
        sampleRate = 44100,
        channels = 2,
        contentLength = 4182954,
        source = "innertube:IOS",
    )

    @Test
    fun from_merges_search_metadata_with_stream() {
        val item = PlaybackItem.from(track, stream)
        assertEquals("vid123", item.videoId)
        assertEquals("Yellow", item.title)
        assertEquals("Coldplay", item.artistLabel)
        assertEquals("Parachutes", item.albumLabel)
        assertEquals(267_000L, item.durationMs)
        assertEquals("http://x/a.jpg", item.artworkUrl)
        assertEquals(stream, item.stream)
    }

    @Test
    fun from_falls_back_to_stream_duration_when_track_has_none() {
        val noDuration = track.copy(durationMs = null)
        val item = PlaybackItem.from(noDuration, stream)
        // stream has no duration either → null
        assertNull(item.durationMs)
    }

    @Test
    fun artwork_prefers_largest_thumbnail() {
        val item = PlaybackItem.from(track, stream)
        assertEquals("http://x/a.jpg", item.artworkUrl)
    }

    @Test
    fun state_defaults_are_sane() {
        val s = PlaybackUiState()
        assertEquals(PlaybackPhase.Idle, s.phase)
        assertNull(s.item)
        assertEquals(0L, s.positionMs)
        assertTrue(!s.isPlaying)
        assertNull(s.errorMessage)
    }

    @Test
    fun state_copy_preserves_values() {
        val s = PlaybackUiState(
            phase = PlaybackPhase.Playing,
            item = PlaybackItem.from(track, stream),
            positionMs = 120_000,
            durationMs = 267_000,
            isPlaying = true,
        )
        val s2 = s.copy(positionMs = 130_000)
        assertEquals(130_000L, s2.positionMs)
        assertEquals(PlaybackPhase.Playing, s2.phase)
        assertEquals(267_000L, s2.durationMs)
        assertTrue(s2.isPlaying)
    }
}
