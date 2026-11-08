package com.wearsic.next.ui.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Local favorites — the standalone equivalent of the original Wearsic
 * favorites (which lived on the server). Video IDs are stored in a
 * SharedPreferences set; the Compose state mirrors it for instant UI.
 *
 * Long-press a search row or tap Favorite on the actions page.
 */
object FavoritesStore {
    private var prefs: android.content.SharedPreferences? = null

    /** Backing set of favorited video IDs (observable). */
    var ids: Set<String> by mutableStateOf(emptySet())
        private set

    /** Must be called before first composition. */
    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("wearsic_next_favorites", Context.MODE_PRIVATE)
        ids = prefs?.getStringSet(KEY_IDS, emptySet())?.toSet() ?: emptySet()
    }

    fun isFavorite(videoId: String): Boolean = ids.contains(videoId)

    fun toggle(videoId: String) {
        if (videoId.isBlank()) return
        val next = if (videoId in ids) ids - videoId else ids + videoId
        ids = next
        prefs?.edit()?.putStringSet(KEY_IDS, next)?.apply()
    }

    fun listFavorites(map: (String) -> Unit) {
        ids.forEach(map)
    }

    private const val KEY_IDS = "favorite_ids"
}
