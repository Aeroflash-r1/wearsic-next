package com.wearsic.next.ui.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wearsic.next.playback.PlaybackItem
import kotlinx.serialization.json.Json

/**
 * Recently Played — local play history (max [MAX_ITEMS]). Mirrors the
 * original's home section; records the start of a play so the home screen
 * can offer one-tap resume. Played items that were later favorited keep
 * both roles independently.
 */
object RecentStore {
    private var prefs: android.content.SharedPreferences? = null

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    var items: List<PlaybackItem> by mutableStateOf(emptyList())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences("wearsic_next_recent", Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_ITEMS, null)
        if (!saved.isNullOrBlank()) {
            runCatching { items = json.decodeFromString<List<PlaybackItem>>(saved) }
        }
    }

    /** Record a track as played (moves it to the front, dedupes). */
    fun record(item: PlaybackItem) {
        if (item.videoId.isBlank()) return
        val next = listOf(item) + items.filterNot { it.videoId == item.videoId }
        items = next.take(MAX_ITEMS)
        runCatching {
            prefs?.edit()?.putString(KEY_ITEMS, json.encodeToString(items))?.apply()
        }
    }

    private const val KEY_ITEMS = "recent_items"
    private const val MAX_ITEMS = 6
}
