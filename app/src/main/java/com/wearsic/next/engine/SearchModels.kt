package com.wearsic.next.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Domain models mirroring the Rust `ytmusic::models` (snake_case JSON). */

@Serializable
data class Thumbnail(
    val url: String,
    val width: Int? = null,
    val height: Int? = null,
)

@Serializable
data class Artist(
    val name: String,
    val id: String? = null,
)

@Serializable
data class Album(
    val name: String,
    val id: String? = null,
)

@Serializable
data class TrackDto(
    @SerialName("video_id") val videoId: String,
    val title: String,
    val artists: List<Artist> = emptyList(),
    val album: Album? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
data class ArtistDto(
    val id: String? = null,
    val name: String,
    val thumbnails: List<Thumbnail> = emptyList(),
    val subscribers: String? = null,
)

@Serializable
data class AlbumDto(
    val id: String? = null,
    val title: String,
    val artist: String? = null,
    val year: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
)

@Serializable
data class PlaylistDto(
    val id: String? = null,
    val title: String,
    val author: String? = null,
    @SerialName("track_count") val trackCount: String? = null,
    val thumbnails: List<Thumbnail> = emptyList(),
)

/**
 * Mirrors the Rust externally-tagged enum `MusicSearchResult`: exactly one
 * of these fields is present.
 */
@Serializable
data class MusicSearchResultDto(
    val track: TrackDto? = null,
    val artist: ArtistDto? = null,
    val album: AlbumDto? = null,
    val playlist: PlaylistDto? = null,
) {
    fun toDomain(): MusicSearchResult = when {
        track != null -> MusicSearchResult.Track(track)
        artist != null -> MusicSearchResult.Artist(artist)
        album != null -> MusicSearchResult.Album(album)
        playlist != null -> MusicSearchResult.Playlist(playlist)
        else -> MusicSearchResult.Unknown
    }
}

/** Kotlin-friendly sealed result. */
sealed interface MusicSearchResult {
    data class Track(val track: TrackDto) : MusicSearchResult
    data class Artist(val artist: ArtistDto) : MusicSearchResult
    data class Album(val album: AlbumDto) : MusicSearchResult
    data class Playlist(val playlist: PlaylistDto) : MusicSearchResult
    data object Unknown : MusicSearchResult
}

@Serializable
data class SearchEnvelope(
    val ok: Boolean,
    val results: List<MusicSearchResultDto> = emptyList(),
    val continuation: String? = null,
    val error: NetworkErrorPublic? = null,
)

@Serializable
data class NetworkErrorPublic(
    val kind: String,
    val status: Int,
    val message: String,
)
