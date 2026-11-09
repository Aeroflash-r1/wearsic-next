package com.wearsic.next.ui.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.wearsic.next.engine.StreamDescriptor
import com.wearsic.next.playback.PlaybackItem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Local audio cache for offline playback. One file per track (m4a bytes)
 * under `filesDir/wearsic_downloads/`; metadata in SharedPreferences as JSON.
 *
 * Downloads resolve through the same InnerTube URL path the player uses.
 * URLs are requested as bytes=0- and cached fully; a downloaded file plays
 * permanently offline.
 */
object DownloadStore {

    @Serializable
    data class DownloadMeta(
        val videoId: String,
        val title: String,
        val artist: String,
        val mimeType: String = "audio/mp4",
        val sizeBytes: Long,
        val durationMs: Long? = null,
        val artworkUrl: String? = null,
        val savedAtMs: Long,
    )

    private const val PREFS = "wearsic_next_downloads"
    private const val KEY_ITEMS = "download_items_v1"

    private var prefs: android.content.SharedPreferences? = null
    private var dir: File? = null

    /** Observable map for the UI (keyed by videoId). */
    var items: Map<String, DownloadMeta> by mutableStateOf(emptyMap())
        private set

    fun init(context: Context) {
        if (prefs != null) return
        val base = File(context.filesDir, "wearsic_downloads").apply { mkdirs() }
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        dir = base
        val saved = prefs?.getString(KEY_ITEMS, null)
        items = if (!saved.isNullOrBlank()) {
            runCatching {
                json.decodeFromString<List<DownloadMeta>>(saved).associateBy { it.videoId }
            }.getOrDefault(emptyMap())
        } else {
            emptyMap()
        }
        // Prune entries whose file disappeared.
        val missing = items.filterValues { fileFor(it.videoId)?.exists() != true }
        if (missing.isNotEmpty()) {
            items = items - missing.keys
            persist()
        }
    }

    fun fileFor(videoId: String): File? = dir?.let { File(it, "$videoId.m4a") }

    fun isDownloaded(videoId: String): Boolean = items.containsKey(videoId)

    fun list(): List<DownloadMeta> = items.values.sortedByDescending { it.savedAtMs }

    fun totalBytes(): Long = items.values.sumOf { it.sizeBytes }

    /**
     * Downloads [descriptor]'s byte stream into local storage. Safe to call
     * from IO threads; returns true on success.
     */
    fun save(descriptor: StreamDescriptor, item: PlaybackItem): Boolean {
        val out = fileFor(item.videoId) ?: return false
        // If already present, report success (idempotent).
        if (items.containsKey(item.videoId) && out.exists()) return true
        return try {
            val url = java.net.URL(descriptor.url)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Range", "bytes=0-")
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.connect()
            val code = connection.responseCode
            if (code != 200 && code != 206) return false
            connection.inputStream.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            connection.disconnect()
            val size = out.length()
            if (size <= 0L) { out.delete(); return false }
            val meta = DownloadMeta(
                videoId = item.videoId,
                title = item.title,
                artist = item.artistLabel.ifBlank { "—" },
                mimeType = descriptor.mimeType?.takeIf { it.isNotBlank() } ?: "audio/mp4",
                sizeBytes = size,
                durationMs = item.durationMs,
                artworkUrl = item.artworkUrl,
                savedAtMs = System.currentTimeMillis(),
            )
            items = items + (item.videoId to meta)
            persist()
            true
        } catch (_: Exception) {
            runCatching { out.delete() }
            false
        }
    }

    fun remove(videoId: String) {
        fileFor(videoId)?.let { runCatching { it.delete() } }
        items = items - videoId
        persist()
    }

    fun clearAll() {
        items.keys.forEach { fileFor(it)?.let { f -> runCatching { f.delete() } } }
        items = emptyMap()
        persist()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private fun persist() {
        val txt = json.encodeToString(items.values.toList())
        prefs?.edit()?.putString(KEY_ITEMS, txt)?.apply()
    }
}
