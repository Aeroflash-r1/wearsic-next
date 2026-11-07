package com.wearsic.next.playback

import com.wearsic.next.engine.Album
import com.wearsic.next.engine.Artist
import com.wearsic.next.engine.StreamDescriptor
import com.wearsic.next.engine.Thumbnail
import com.wearsic.next.engine.TrackDto
import kotlinx.serialization.Serializable

/**
 * A unified playback item: Phase 4 search metadata merged with the Phase 5
 * stream descriptor. Search metadata is preferred for display fields the
 * player response does not carry.
 */
@Serializable
data class PlaybackItem(
    val videoId: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    val durationMs: Long? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
    val stream: StreamDescriptor? = null,
) {
    val artworkUrl: String? get() = thumbnails.lastOrNull()?.url
    val artistLabel: String get() = artists.joinToString(", ") { it.name }
    val albumLabel: String? get() = album?.name
}

/** Builds a playback item from a search track + resolved stream. */
fun PlaybackItem.Companion.from(track: TrackDto, stream: StreamDescriptor): PlaybackItem =
    PlaybackItem(
        videoId = track.videoId,
        title = track.title,
        artists = track.artists,
        album = track.album,
        durationMs = track.durationMs ?: stream.durationMs,
        thumbnails = track.thumbnails,
        stream = stream,
    )

/** Playback states exposed to the UI (throttled). */
enum class PlaybackPhase {
    Idle,
    Resolving,
    Preparing,
    Buffering,
    Playing,
    Paused,
    Ended,
    Error,
}

@Serializable
data class PlaybackUiState(
    val phase: PlaybackPhase = PlaybackPhase.Idle,
    val item: PlaybackItem? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedMs: Long = 0L,
    val isPlaying: Boolean = false,
    val errorMessage: String? = null,
    val hasPrev: Boolean = false,
    val hasNext: Boolean = false,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
)
