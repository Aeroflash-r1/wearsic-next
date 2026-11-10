package com.wearsic.next.ui.data

import com.wearsic.next.playback.PlaybackItem

/**
 * Local library derived from what the user actually has on the watch:
 * saved (offline) tracks, favorites and recently played.
 *
 * The original Wearsic exposed Albums / Artists / Playlists as server
 * library sections. Standalone Next has no server, so the same navigation
 * shape is produced from local data — grouped by album and by artist.
 */
object LocalLibrary {

    /** Every track the user owns locally, de-duplicated. */
    fun tracks(): List<PlaybackItem> {
        val all = LinkedHashMap<String, PlaybackItem>()
        DownloadStore.list().forEach { meta ->
            all.putIfAbsent(
                meta.videoId,
                PlaybackItem(
                    videoId = meta.videoId,
                    title = meta.title,
                    artists = listOf(com.wearsic.next.engine.Artist(name = meta.artist)),
                    durationMs = meta.durationMs,
                ),
            )
        }
        FavoritesStore.listFavorites().forEach { all.putIfAbsent(it.videoId, it) }
        RecentStore.items.forEach { all.putIfAbsent(it.videoId, it) }
        return all.values.toList()
    }

    data class AlbumGroup(
        val key: String,
        val title: String,
        val artist: String,
        val tracks: List<PlaybackItem>,
    )

    data class ArtistGroup(
        val key: String,
        val name: String,
        val tracks: List<PlaybackItem>,
    )

    /** Albums, keyed by album name (+ artist when known). */
    fun albums(): List<AlbumGroup> =
        tracks()
            .filter { !it.albumLabel.isNullOrBlank() }
            .groupBy { "${it.albumLabel}|${it.artistLabel}" }
            .map { (key, list) ->
                AlbumGroup(
                    key = key,
                    title = list.first().albumLabel.orEmpty(),
                    artist = list.first().artistLabel,
                    tracks = list,
                )
            }
            .sortedBy { it.title.lowercase() }

    /** Artists, keyed by artist name; all their local tracks. */
    fun artists(): List<ArtistGroup> =
        tracks()
            .filter { it.artistLabel.isNotBlank() }
            .flatMap { item -> item.artists.map { artist -> item to artist } }
            .groupBy { (_, artist) -> artist.name }
            .map { (name, pairs) ->
                ArtistGroup(
                    key = name,
                    name = name,
                    tracks = pairs.map { it.first }.distinctBy { it.videoId },
                )
            }
            .sortedBy { it.name.lowercase() }
}
