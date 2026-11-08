package com.wearsic.next.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.common.util.UnstableApi
import com.wearsic.next.engine.NetworkEngineException
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.engine.StreamDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Bridges the Rust engine (Phase 5 resolution) and Media3 playback.
 *
 * Owns the queue, resolves streams lazily, and recovers from expired streams
 * by asking Rust for a fresh descriptor once (bounded retry).
 */
@UnstableApi
class PlaybackController(
    private val context: Context,
    private val engine: RustEngine,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var controller: MediaController? = null
    private var connectJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** Queue snapshot for the Queue screen (emitted only on mutations). */
    private val _queue = MutableStateFlow<List<PlaybackItem>>(emptyList())
    val queue: StateFlow<List<PlaybackItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val queueItems = mutableListOf<PlaybackItem>()
    private var currentIndex = -1
    private var resumeAfterRefresh = false

    private val requestIds = AtomicLong(1000)

    init {
        connect()
    }

    private fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            val token = SessionToken(context, android.content.ComponentName(context, WearsicPlaybackService::class.java))
            val future = MediaController.Builder(context, token).buildAsync()
            // ListenableFuture (Guava) → suspend via coroutine.
            val controller = suspendCoroutine<MediaController> { cont ->
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.resumeWithException(it) }
                    },
                    java.util.concurrent.Executor { r -> r.run() },
                )
            }
            this@PlaybackController.controller = controller
            controller.addListener(this@PlaybackController)
            // A fresh controller starts with an Idle state even while the
            // service's player keeps playing (screen off/on, process restart,
            // re-entry from the launcher). Adopt the session's live item and
            // the persisted queue snapshot immediately.
            hydrateFromSession(controller)
        }
    }

    @kotlinx.serialization.Serializable
    private data class QueueSnapshot(val index: Int, val items: List<PlaybackItem>)

    private val itemJson by lazy {
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }

    /** Persists the queue so an app restart shows the whole list, not one song. */
    private fun persistQueueSnapshot() {
        runCatching {
            val text = itemJson.encodeToString(
                QueueSnapshot(
                    index = currentIndex,
                    items = queueItems,
                )
            )
            context.getSharedPreferences(QueueStore, Context.MODE_PRIVATE)
                .edit().putString(QueueKey, text).apply()
        }
    }

    private fun restoreQueueSnapshot() {
        if (queueItems.isNotEmpty()) return
        runCatching {
            val text = context.getSharedPreferences(QueueStore, Context.MODE_PRIVATE)
                .getString(QueueKey, null) ?: return
            val snap = itemJson.decodeFromString<QueueSnapshot>(text)
            queueItems.addAll(snap.items)
            currentIndex = if (snap.items.isEmpty()) -1 else snap.index.coerceIn(0, snap.items.lastIndex)
        }
    }

    private fun hydrateFromSession(c: MediaController) {
        val mi = c.currentMediaItem ?: return
        val raw = runCatching { mi.mediaMetadata.extras?.getString(KEY_ITEM_JSON) }.getOrNull()
        val decoded = raw?.let { text ->
            runCatching { itemJson.decodeFromString<PlaybackItem>(text) }.getOrNull()
        }
        // Fallback: rebuild a minimal item straight from MediaMetadata.
        val fromSession: PlaybackItem = decoded ?: run {
            val vid = mi.mediaId
            if (vid.isBlank()) return
            val md = mi.mediaMetadata
            PlaybackItem(
                videoId = vid,
                title = md.title?.toString().orEmpty(),
                artists = md.artist?.toString()?.let { a ->
                    listOf(com.wearsic.next.engine.Artist(name = a))
                }.orEmpty(),
                album = md.albumTitle?.toString()?.let { al ->
                    com.wearsic.next.engine.Album(name = al)
                },
                thumbnails = md.artworkUri?.toString()?.let { u ->
                    listOf(com.wearsic.next.engine.Thumbnail(url = u))
                }.orEmpty(),
            )
        }

        restoreQueueSnapshot()

        val found = queueItems.indexOfFirst { it.videoId == fromSession.videoId }
        val item = if (found >= 0) {
            // Trust the session's own copy for the stream (it is playing); merge display fields it knows better.
            queueItems[found] = queueItems[found].copy(stream = fromSession.stream ?: queueItems[found].stream)
            currentIndex = found
            queueItems[found]
        } else {
            queueItems.add(fromSession)
            currentIndex = queueItems.lastIndex
            fromSession
        }
        publishQueue()

        if (_state.value.item?.videoId == item.videoId && _state.value.phase != PlaybackPhase.Idle) {
            return // UI already mirrors this song; avoid flicker.
        }
        val phase = when {
            c.isPlaying -> PlaybackPhase.Playing
            c.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            c.playbackState == Player.STATE_READY -> PlaybackPhase.Paused
            else -> _state.value.phase.takeIf { it != PlaybackPhase.Idle } ?: PlaybackPhase.Paused
        }
        _state.value = _state.value.copy(
            phase = phase,
            item = item,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition,
            durationMs = if (c.duration > 0L) c.duration else item.durationMs ?: 0L,
            bufferedMs = c.bufferedPosition,
            shuffleEnabled = c.shuffleModeEnabled,
            repeatMode = c.repeatMode,
            hasPrev = currentIndex > 0,
            hasNext = currentIndex < queueItems.lastIndex,
            errorMessage = null,
        )
    }

    // --- Queue / selection -------------------------------------------------

    fun play(items: List<PlaybackItem>, startIndex: Int) {
        if (items.isEmpty()) return
        queueItems.clear()
        queueItems.addAll(items)
        currentIndex = startIndex.coerceIn(0, items.lastIndex)
        publishQueue()
        playCurrent()
    }

    fun playNext(item: PlaybackItem) {
        queueItems.add(currentIndex + 1, item)
        if (currentIndex + 1 == queueItems.lastIndex) _state.value = _state.value.copy(hasNext = true)
        publishQueue()
    }

    private fun playCurrent() {
        val item = queueItems.getOrNull(currentIndex) ?: return
        _state.value = _state.value.copy(phase = PlaybackPhase.Resolving, item = item)
        // Local history for the home screen's "Recently Played".
        com.wearsic.next.ui.data.RecentStore.record(item)
        scope.launch {
            val descriptor = resolveOrRefresh(item)
            if (descriptor == null) {
                _state.value = _state.value.copy(
                    phase = PlaybackPhase.Error,
                    errorMessage = "Could not resolve a playable stream.",
                )
                return@launch
            }
            val fresh = item.copy(stream = descriptor)
                queueItems[currentIndex] = fresh
            _state.value = _state.value.copy(
                phase = PlaybackPhase.Preparing,
                item = fresh,
                durationMs = fresh.durationMs ?: 0L,
            )
            val mediaItem = toMediaItem(fresh)
            val c = controller ?: return@launch
            c.setMediaItems(listOf(mediaItem), 0, 0L)
            c.prepare()
            c.play()
        }
    }

    private suspend fun resolveOrRefresh(item: PlaybackItem): StreamDescriptor? {
        item.stream?.let {
            // Already resolved: trust it (10-min Rust cache covers expiry).
            return it
        }
        return withContext(Dispatchers.IO) {
            runCatching {
                engine.resolveStream(item.videoId, requestIds.incrementAndGet())
            }.getOrNull()
        }
    }

    // --- Controls ----------------------------------------------------------

    fun togglePlayPause() {
        val c = controller ?: return
        if (_state.value.phase == PlaybackPhase.Error) {
            retry()
            return
        }
        if (c.isPlaying) c.pause() else c.play()
    }

    /** Used by the sleep timer to pause after N minutes. */
    fun pause() {
        controller?.pause()
    }

    fun skipNext() {
        if (currentIndex < queueItems.lastIndex) {
            currentIndex++
            publishQueueIndex()
            playCurrent()
        } else {
            controller?.pause()
        }
    }

    fun skipPrevious() {
        if (currentIndex > 0) {
            currentIndex--
            publishQueueIndex()
            playCurrent()
        } else {
            controller?.seekTo(0L)
        }
    }

    /** Jump to a queue entry from the Queue screen. */
    fun playQueueIndex(index: Int) {
        if (index in queueItems.indices && index != currentIndex) {
            currentIndex = index
            publishQueueIndex()
            playCurrent()
        } else if (index == currentIndex) {
            controller?.seekTo(0L)
        }
    }

    /** Remove a track from the queue (never the currently playing one). */
    fun removeFromQueue(index: Int) {
        if (index in queueItems.indices && index != currentIndex) {
            queueItems.removeAt(index)
            if (index < currentIndex) {
                currentIndex--
                publishQueueIndex()
            }
            publishQueue()
        }
    }

    private fun publishQueue() {
        _queue.value = queueItems.toList()
        _queueIndex.value = currentIndex
        persistQueueSnapshot()
    }

    private fun publishQueueIndex() {
        _queueIndex.value = currentIndex
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun setShuffle(enabled: Boolean) {
        controller?.shuffleModeEnabled = enabled
    }

    /** Re-attempts resolution + playback after a failure (user taps play). */
    fun retry() {
        if (_state.value.phase == PlaybackPhase.Error) {
            resumeAfterRefresh = false
            playCurrent()
        }
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    // --- Player.Listener ---------------------------------------------------

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        _state.value = _state.value.copy(
            phase = if (isPlaying) PlaybackPhase.Playing else PlaybackPhase.Paused,
            isPlaying = isPlaying,
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        val phase = when (playbackState) {
            Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            Player.STATE_READY -> if (controller?.isPlaying == true) PlaybackPhase.Playing else PlaybackPhase.Paused
            Player.STATE_ENDED -> PlaybackPhase.Ended
            else -> PlaybackPhase.Idle
        }
        _state.value = _state.value.copy(phase = phase)
        if (phase == PlaybackPhase.Ended) skipNext()
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        // Best-effort refresh-once for signed-URL failures (403/410).
        val item = queueItems.getOrNull(currentIndex) ?: return
        if (!resumeAfterRefresh) {
            resumeAfterRefresh = true
            scope.launch {
                val fresh = withContext(Dispatchers.IO) {
                    runCatching {
                        engine.resolveStream(item.videoId, requestIds.incrementAndGet())
                    }.getOrNull()
                }
                if (fresh != null) {
                    val updated = item.copy(stream = fresh)
                    queueItems[currentIndex] = updated
                    controller?.setMediaItem(toMediaItem(updated), 0L)
                    controller?.prepare()
                    controller?.play()
                    return@launch
                }
                _state.value = _state.value.copy(
                    phase = PlaybackPhase.Error,
                    errorMessage = "Playback failed: ${error.errorCodeName}",
                )
            }
        } else {
            _state.value = _state.value.copy(
                phase = PlaybackPhase.Error,
                errorMessage = "Playback failed: ${error.errorCodeName}",
            )
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        resumeAfterRefresh = false
    }

    override fun onEvents(player: Player, events: Player.Events) {
        // Track position at ~500ms granularity driven by UI polling instead of
        // flooding state on every frame.
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
        ) {
            _state.value = _state.value.copy(
                positionMs = player.currentPosition,
                durationMs = player.duration,
                bufferedMs = player.bufferedPosition,
                hasPrev = currentIndex > 0,
                hasNext = currentIndex < queueItems.lastIndex,
                shuffleEnabled = player.shuffleModeEnabled,
                repeatMode = player.repeatMode,
            )
        }
    }

    /**
     * Cheap position tick for the UI (no flood). Emissions are SKIPPED while
     * nothing changes — and the POLL RATE drops to 3s while paused or idle
     * (only the 500ms cadence is needed for the moving progress ring). A
     * paused or idle player therefore costs almost nothing, keeping the
     * watch's CPU asleep.
     */
    fun startPositionTicker() {
        scope.launch {
            while (true) {
                val c = controller ?: run { delay(3000); continue }
                val active = c.isPlaying || _state.value.phase == PlaybackPhase.Buffering ||
                    _state.value.phase == PlaybackPhase.Resolving
                delay(if (active) 500 else 3000)
                val position = c.currentPosition
                val duration = if (c.duration > 0L) c.duration else _state.value.durationMs
                val buffered = c.bufferedPosition
                val current = _state.value
                if (current.positionMs != position ||
                    current.durationMs != duration ||
                    current.bufferedMs != buffered
                ) {
                    _state.value = current.copy(
                        positionMs = position,
                        durationMs = duration,
                        bufferedMs = buffered,
                    )
                }
            }
        }
    }

    // --- Media item construction -------------------------------------------

    private fun toMediaItem(item: PlaybackItem): MediaItem {
        val stream = requireNotNull(item.stream)
        // The full item rides inside the metadata extras (JSON): a restarted
        // UI re-reads it via [hydrateFromSession] and shows the live song
        // instead of "Nothing playing" while audio keeps going.
        val extras = android.os.Bundle().apply {
            putString(KEY_ITEM_JSON, runCatching { itemJson.encodeToString(item) }.getOrNull())
        }
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistLabel)
            .setAlbumTitle(item.albumLabel)
            .setArtworkUri(android.net.Uri.parse(item.artworkUrl))
            .setExtras(extras)
            .build()
        return MediaItem.Builder()
            .setMediaId(item.videoId)
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
            .setMediaMetadata(metadata)
            .build()
    }

    fun release() {
        controller?.removeListener(this)
        controller?.release()
        controller = null
    }

    companion object {
        private const val QueueStore = "wearsic_next_queue"
        private const val QueueKey = "queue_snapshot_v1"
        private const val KEY_ITEM_JSON = "wearsic_item_json_v1"

        fun create(context: Context, engine: RustEngine): PlaybackController {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            return PlaybackController(context, engine, scope)
        }
    }
}
