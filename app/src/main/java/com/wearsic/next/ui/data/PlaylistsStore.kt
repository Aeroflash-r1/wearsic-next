package com.wearsic.next.ui.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wearsic.next.playback.PlaybackItem
import kotlinx.serialization.json.Json

/**
 * Local playlists — the standalone equivalent of the original playlists
 * (they lived on the server). Names map to ordered, de-duplicated lists of
 * [PlaybackItem] metadata; playback is queued like any other list.
 */
object PlaylistsStore {

    private const val PREFS = "wearsic_next_playlists"
    private const val KEY_ITEMS = "playlists_v1"

    private var prefs: android.content.SharedPreferences? = null
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Observable map: playlist name → ordered tracks. */
    var playlists: Map<String, List<PlaybackItem>> by mutableStateOf(emptyMap())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val saved = prefs?.getString(KEY_ITEMS, null)
        playlists = if (!saved.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<Map<String, List<PlaybackItem>>>(saved)
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
    }

    fun names(): List<String> = playlists.keys.sorted()

    fun get(name: String): List<PlaybackItem> = playlists[name].orEmpty()

    fun create(name: String): Boolean {
        val key = name.trim()
        if (key.isEmpty() || playlists.containsKey(key)) return false
        playlists = playlists + (key to emptyList())
        persist()
        return true
    }

    fun delete(name: String) {
        playlists = playlists - name
        persist()
    }

    fun rename(from: String, to: String): Boolean {
        val key = to.trim()
        if (key.isEmpty() || playlists.containsKey(key) || !playlists.containsKey(from)) return false
        playlists = (playlists - from) + (key to playlists.getValue(from))
        persist()
        return true
    }

    /** Adds [item] to [name] (creates the playlist if missing). De-duplicated. */
    fun add(name: String, item: PlaybackItem) {
        val key = name.trim()
        if (key.isEmpty() || item.videoId.isBlank()) return
        val current = playlists[key].orEmpty()
        if (current.any { it.videoId == item.videoId }) return
        playlists = playlists + (key to current + item)
        persist()
    }

    fun removeTrack(name: String, videoId: String) {
        val current = playlists[name] ?: return
        playlists = playlists + (name to current.filterNot { it.videoId == videoId })
        persist()
    }

    fun clearPlaylist(name: String) {
        if (playlists.containsKey(name)) {
            playlists = playlists + (name to emptyList())
            persist()
        }
    }

    private fun persist() {
        val txt = json.encodeToString(playlists)
        prefs?.edit()?.putString(KEY_ITEMS, txt)?.apply()
    }
}
