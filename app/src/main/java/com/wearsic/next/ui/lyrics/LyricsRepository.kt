package com.wearsic.next.ui.lyrics

import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.playback.PlaybackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * LRC line: start time (ms) and text.
 */
data class LyricLine(val timeMs: Long, val text: String) {
    val isEmpty: Boolean get() = text.isBlank()
}

/**
 * Lyrics for a track. Synced lines when available; plain text as fallback.
 */
sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>, val source: String) : Lyrics
    data class Plain(val text: String, val source: String) : Lyrics
    data object Instrumental : Lyrics
    data object NotFound : Lyrics
}

/**
 * Fetches lyrics from LRCLIB (free, no key, no account). No Rust changes.
 * Exact-match on title + artist; falls back to search; caches nothing (calls
 * are user-initiated).
 *
 * Plain lyric lines and timed LRC share the [lines] structure: syncing
 * fallbacks just become "always the first line" (caller keeps it simple).
 */
object LyricsRepository {

    private const val BASE = "https://lrclib.net/api"
    private const val UA = "WearsicNext/1.0 (+https://github.com/Aeroflash-r1/wearsic-next)"

    suspend fun fetch(track: String, artist: String, durationMs: Long?): Lyrics =
        withContext(Dispatchers.IO) {
            runCatching {
                val exact = get(track, artist)
                if (exact != null) return@runCatching exact
                val searched = search(track, artist, durationMs)
                searched ?: Lyrics.NotFound
            }.getOrDefault(Lyrics.NotFound)
        }

    suspend fun fetch(item: PlaybackItem): Lyrics =
        fetch(item.title, item.artists.firstOrNull()?.name.orEmpty(), item.durationMs)

    private fun get(track: String, artist: String): Lyrics? =
        fetchJson("$BASE/get?track_name=${enc(track)}&artist_name=${enc(artist)}")
            ?.let { parseObject(it) }

    private fun search(track: String, artist: String, durationMs: Long?): Lyrics? {
        val arr = fetchJson("$BASE/search?track_name=${enc(track)}&artist_name=${enc(artist)}")
            ?: return null
        val items = runCatching { JSONArray(arr) }.getOrElse { return null }
        if (items.length() == 0) return null
        val expectedSec = durationMs?.div(1000)?.toInt()
        var best: JSONObject? = null
        var bestDelta = Int.MAX_VALUE
        for (i in 0 until items.length()) {
            val obj = items.getJSONObject(i)
            val secs = obj.optInt("duration", -1)
            val delta = if (expectedSec != null && secs >= 0) {
                kotlin.math.abs(secs - expectedSec)
            } else {
                0
            }
            if (delta < bestDelta) {
                bestDelta = delta
                best = obj
            }
        }
        return best?.let { parseObject(it.toString()) }
    }

    private fun fetchJson(url: String): String? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", UA)
            connection.setRequestProperty("Accept", "application/json")
            connection.connectTimeout = 12_000
            connection.readTimeout = 18_000
            connection.connect()
            val code = connection.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return null
            }
            connection.inputStream.bufferedReader().use { it.readText() }
                .also { connection.disconnect() }
        }.getOrNull()

    private fun enc(value: String): String = URLEncoder.encode(value.trim(), "UTF-8").replace("+", "%20")

    private fun parseObject(raw: String): Lyrics? {
        val obj = runCatching { JSONObject(raw) }.getOrElse { return null }
        if (obj.optBoolean("instrumental", false)) return Lyrics.Instrumental
        val synced = obj.optString("syncedLyrics", "").trim()
        if (synced.isNotEmpty()) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) {
                return Lyrics.Synced(lines, obj.optString("source", "lrclib.net"))
            }
        }
        val plain = obj.optString("plainLyrics", "").trim()
        if (plain.isNotEmpty()) {
            return Lyrics.Plain(plain, obj.optString("source", "lrclib.net"))
        }
        return null
    }

    /** Parses `[mm:ss.xx] text` lines; tolerant of fractional-seconds width. */
    private fun parseLrc(text: String): List<LyricLine> {
        val out = mutableListOf<LyricLine>()
        val timePattern = Regex("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?]")
        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val matches = timePattern.findAll(line).toList()
            if (matches.isEmpty()) continue
            val lineText = line.replace(timePattern, "").trim()
            for (m in matches) {
                val mm = m.groupValues[1].toLongOrNull() ?: continue
                val ss = m.groupValues[2].toLongOrNull() ?: continue
                val fracRaw = m.groupValues.getOrNull(3).orEmpty()
                val fracMs = if (fracRaw.isEmpty()) 0L else {
                    val digits = fracRaw.take(3).padEnd(3, '0')
                    digits.toLongOrNull() ?: 0L
                }
                out.add(LyricLine(mm * 60_000L + ss * 1000L + fracMs, lineText))
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
