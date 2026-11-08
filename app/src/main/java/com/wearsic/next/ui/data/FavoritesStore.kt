package com.wearsic.next.ui.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wearsic.next.playback.PlaybackItem
import kotlinx.serialization.json.Json

/**
 * Local favorites — the standalone equivalent of the original Wearsic
 * favorites (which lived on the server). Full [PlaybackItem] metadata is
 * stored (JSON) so a favorite can be played instantly without a search; the
 * string-set facade keeps [isFavorite] checks cheap.
 *
 * Long-press a search row, tap Favorite on the player actions page, or use
 * the Favorites screen.
 */
object FavoritesStore {
    private var prefs: android.content.SharedPreferences? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Backing set of favorite video IDs (observable). */
    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    /** Full items cache, keyed by videoId (observable). */
    var items: Map<String, PlaybackItem> by mutableStateOf(emptyMap())
        private set

    /** Must be called before first composition. */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("wearsic_next_favorites", Context.MODE_PRIVATE)
        ids = prefs?.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
        val saved = prefs?.getString(KEY_ITEMS, null)
        if (!saved.isNullOrBlank()) {
            runCatching {
                val decoded = json.decodeFromString<List<PlaybackItem>>(saved)
                items = decoded.associateBy { it.videoId }
            }
        }
    }

    fun isFavorite(videoId: String): Boolean = ids.contains(videoId)

    fun listFavorites(): List<PlaybackItem> =
        items.values.filter { it.videoId in ids }.toList()

    fun toggle(item: PlaybackItem) {
        if (item.videoId.isBlank()) return
        val nextIds = if (item.videoId in ids) ids - item.videoId else ids + item.videoId
        val nextItems = if (item.videoId in ids) items - item.videoId else items + (item.videoId to item)
        ids = nextIds
        items = nextItems
        persist(nextIds, nextItems.values.toList())
    }

    fun toggle(videoId: String) {
        items[videoId]?.let { toggle(it) } ?: run {
            if (videoId in ids) {
                val nextIds = ids - videoId
                ids = nextIds
                persist(nextIds, items.values.toList())
            }
        }
    }

    fun remove(videoId: String) {
        if (videoId !in ids) return
        val nextIds = ids - videoId
        ids = nextIds
        persist(nextIds, items.values.toList())
    }

    private fun persist(nextIds: Set<String>, nextItems: List<PlaybackItem>) {
        val editor = prefs?.edit() ?: return
        editor.putStringSet(KEY_IDS, nextIds)
        runCatching { editor.putString(KEY_ITEMS, json.encodeToString(nextItems)) }
        editor.apply()
    }

    private const val KEY_IDS = "favorite_ids"
    private const val KEY_ITEMS = "favorite_items"
}
