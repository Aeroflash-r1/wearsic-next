package com.wearsic.next.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.wearsic.next.engine.Album
import com.wearsic.next.engine.Artist
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.engine.StreamDescriptor
import com.wearsic.next.engine.Thumbnail
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * Bridges the Rust engine (stream resolution) and Media3 playback.
 *
 * Owns the queue, resolves streams lazily, and recovers from expired streams
 * by asking Rust for a fresh descriptor once (bounded retry).
 *
 * Design notes that matter for feel and battery:
 *  · The queue is the single source of truth. Shuffle and repeat reorder /
 *    wrap OUR list — Media3 only ever holds the one item that is playing, so
 *    its own shuffle/repeat would do nothing.
 *  · The next track's descriptor is resolved in the background while the
 *    current one plays. A skip therefore starts instantly instead of showing
 *    "Resolving" and waiting on the network.
 *  · Each play attempt carries a token; a resolution that lands after the
 *    user already skipped elsewhere is dropped instead of clobbering state.
 *  · Position lives in its own flow so a moving progress ring never
 *    recomposes the player screen, and the ticker parks completely while the
 *    UI is not visible (screen off) instead of polling the CPU awake.
 */
@UnstableApi
class PlaybackController(
    private val context: Context,
    private val engine: RustEngine,
    private val scope: CoroutineScope,
) : Player.Listener {

    private var controller: MediaController? = null
    private var connectJob: Job? = null
    private var prefetchJob: Job? = null

    private val _state = MutableStateFlow(PlaybackUiState())
    val state: StateFlow<PlaybackUiState> = _state.asStateFlow()

    /** Elapsed position, ticked separately so the UI can stay draw-only. */
    private val _positionMs = MutableStateFlow(0L)
    val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    /** Queue snapshot for the Queue screen (emitted only on mutations). */
    private val _queue = MutableStateFlow<List<PlaybackItem>>(emptyList())
    val queue: StateFlow<List<PlaybackItem>> = _queue.asStateFlow()

    private val _queueIndex = MutableStateFlow(-1)
    val queueIndex: StateFlow<Int> = _queueIndex.asStateFlow()

    private val queueItems = mutableListOf<PlaybackItem>()

    /** Un-shuffled order, so turning shuffle off restores the real queue. */
    private var baseOrder: List<PlaybackItem> = emptyList()
    private var currentIndex = -1
    private var shuffleEnabled = false
    private var repeatMode = Player.REPEAT_MODE_OFF

    /** Video id already refreshed once after a play failure (no retry loops). */
    private var refreshedVideoId: String? = null

    /** Bumped on every play attempt; stale resolutions compare against it. */
    private var playToken = 0

    private val requestIds = AtomicLong(1000)

    /** True while a screen is on; the position ticker parks when false. */
    private val uiActive = MutableStateFlow(false)

    init {
        // Show the last queue even when the service is gone (app killed, no
        // playback): the snapshot is the queue of record.
        restoreQueueSnapshot()
        _queue.value = queueItems.toList()
        _queueIndex.value = currentIndex
        connect()
    }

    private fun connect() {
        connectJob?.cancel()
        connectJob = scope.launch {
            val token = SessionToken(
                context,
                android.content.ComponentName(context, WearsicPlaybackService::class.java),
            )
            val future = MediaController.Builder(context, token).buildAsync()
            // ListenableFuture (Guava) → suspend via coroutine.
            val mediaController = suspendCoroutine<MediaController> { cont ->
                future.addListener(
                    {
                        runCatching { future.get() }
                            .onSuccess { cont.resume(it) }
                            .onFailure { cont.resumeWithException(it) }
                    },
                    java.util.concurrent.Executor { r -> r.run() },
                )
            }
            this@PlaybackController.controller = mediaController
            mediaController.addListener(this@PlaybackController)
            // A fresh controller starts with an Idle state even while the
            // service's player keeps playing (screen off/on, process restart,
            // re-entry from the launcher). Adopt the session's live item and
            // the persisted queue snapshot immediately.
            hydrateFromSession(mediaController)
            if (uiActive.value) tickPosition(mediaController)
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
            baseOrder = snap.items
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
                    listOf(Artist(name = a))
                }.orEmpty(),
                album = md.albumTitle?.toString()?.let { al ->
                    Album(name = al)
                },
                thumbnails = md.artworkUri?.toString()?.let { u ->
                    listOf(Thumbnail(url = u))
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
            baseOrder = baseOrder + fromSession
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
        _positionMs.value = c.currentPosition
        _state.value = _state.value.copy(
            phase = phase,
            item = item,
            isPlaying = c.isPlaying,
            durationMs = if (c.duration > 0L) c.duration else item.durationMs ?: 0L,
            shuffleEnabled = shuffleEnabled,
            repeatMode = repeatMode,
            hasPrev = QueueNavigation.previous(currentIndex, queueItems.size, repeatMode) != null,
            hasNext = QueueNavigation.next(currentIndex, queueItems.size, repeatMode) != null,
            errorMessage = null,
        )
    }

    // --- Queue / selection -------------------------------------------------

    fun play(items: List<PlaybackItem>, startIndex: Int) {
        if (items.isEmpty()) return
        baseOrder = items
        queueItems.clear()
        queueItems.addAll(items)
        currentIndex = startIndex.coerceIn(0, items.lastIndex)
        if (shuffleEnabled) reshuffleAroundCurrent()
        publishQueue()
        playCurrent()
    }

    fun playNext(item: PlaybackItem) {
        val insertAt = (currentIndex + 1).coerceIn(0, queueItems.size)
        queueItems.add(insertAt, item)
        baseOrder = baseOrder.toMutableList().apply { add(insertAt.coerceIn(0, size), item) }
        publishQueue()
    }

    /**
     * Shuffle ON: keep the playing track first and shuffle everything after
     * it. Shuffle OFF: restore the order the queue was created with.
     */
    fun setShuffle(enabled: Boolean) {
        if (shuffleEnabled == enabled) return
        shuffleEnabled = enabled
        val playing = queueItems.getOrNull(currentIndex)
        if (enabled) {
            reshuffleAroundCurrent()
        } else {
            val byId = queueItems.associateBy { it.videoId }
            val restored = baseOrder.mapNotNull { byId[it.videoId] }
            val extras = queueItems.filter { item -> restored.none { it.videoId == item.videoId } }
            queueItems.clear()
            queueItems.addAll(restored + extras)
            currentIndex = playing?.let { p ->
                queueItems.indexOfFirst { it.videoId == p.videoId }
            } ?: -1
        }
        publishQueue()
        publishTransportFlags()
        prefetchNext()
    }

    private fun reshuffleAroundCurrent() {
        val playing = queueItems.getOrNull(currentIndex)
        val rest = queueItems.filterIndexed { i, _ -> i != currentIndex }.shuffled()
        queueItems.clear()
        if (playing != null) {
            queueItems.add(playing)
            queueItems.addAll(rest)
            currentIndex = 0
        } else {
            queueItems.addAll(rest)
            currentIndex = if (queueItems.isEmpty()) -1 else 0
        }
    }

    private fun playCurrent() {
        val index = currentIndex
        val item = queueItems.getOrNull(index) ?: return
        val token = ++playToken
        refreshedVideoId = null
        _state.update {
            it.copy(
                phase = PlaybackPhase.Resolving,
                item = item,
                isPlaying = false,
                errorMessage = null,
                durationMs = item.durationMs ?: 0L,
                hasPrev = QueueNavigation.previous(currentIndex, queueItems.size, repeatMode) != null,
                hasNext = QueueNavigation.next(currentIndex, queueItems.size, repeatMode) != null,
            )
        }
        _positionMs.value = 0L
        // Local history for the home screen's "Recently Played".
        com.wearsic.next.ui.data.RecentStore.record(item)

        scope.launch {
            val cachedFile = com.wearsic.next.ui.data.DownloadStore.fileFor(item.videoId)
            val cacheMeta = com.wearsic.next.ui.data.DownloadStore.items[item.videoId]
            val canPlayCached = com.wearsic.next.ui.data.DownloadStore.isDownloaded(item.videoId) &&
                cachedFile != null && cachedFile.exists()
            if (canPlayCached) {
                // Saved copy preferred: no network for a track the user explicitly
                // chose to keep offline.
                if (token != playToken) return@launch
                val fresh = item.copy(durationMs = cacheMeta?.durationMs ?: item.durationMs)
                queueItems[index] = fresh
                _state.update {
                    it.copy(
                        phase = PlaybackPhase.Preparing,
                        item = fresh,
                        durationMs = fresh.durationMs ?: 0L,
                    )
                }
                val c0 = controller ?: return@launch
                c0.setMediaItem(
                    mediaItemFromFile(fresh, cachedFile, cacheMeta?.mimeType ?: "audio/mp4")
                )
                c0.prepare()
                c0.play()
                prefetchNext()
                return@launch
            }

            val descriptor = item.stream ?: resolve(item.videoId)
            if (token != playToken) return@launch // user skipped elsewhere
            if (descriptor == null) {
                _state.update {
                    it.copy(
                        phase = PlaybackPhase.Error,
                        errorMessage = "Could not resolve a playable stream.",
                    )
                }
                return@launch
            }
            // The queue may have been edited while this resolved; write only
            // if the slot still holds the same track.
            if (queueItems.getOrNull(index)?.videoId != item.videoId) return@launch
            val fresh = item.copy(stream = descriptor)
            queueItems[index] = fresh
            _state.update {
                it.copy(
                    phase = PlaybackPhase.Preparing,
                    item = fresh,
                    durationMs = fresh.durationMs ?: 0L,
                )
            }
            val c = controller ?: return@launch
            c.setMediaItem(toMediaItem(fresh))
            c.prepare()
            c.play()
            // Resolve the following track while this one plays: the next skip
            // is then network-free.
            prefetchNext()
        }
    }

    private suspend fun resolve(videoId: String): StreamDescriptor? =
        withContext(Dispatchers.IO) {
            runCatching {
                engine.resolveStream(videoId, requestIds.incrementAndGet())
            }.getOrNull()
        }

    /**
     * Warms the descriptor for the next queue entry. Runs at most one at a
     * time, writes only if the entry is still unresolved, and never touches
     * playback state — so a prefetch can never cause a stutter.
     */
    private fun prefetchNext() {
        prefetchJob?.cancel()
        val nextIndex = currentIndex + 1
        if (nextIndex !in queueItems.indices) return
        val target = queueItems[nextIndex]
        if (target.stream != null) return
        prefetchJob = scope.launch {
            val descriptor = resolve(target.videoId) ?: return@launch
            val index = queueItems.indexOfFirst { it.videoId == target.videoId }
            if (index < 0 || queueItems[index].stream != null) return@launch
            queueItems[index] = queueItems[index].copy(stream = descriptor)
            _queue.value = queueItems.toList()
        }
    }

    // --- Controls ----------------------------------------------------------

    fun togglePlayPause() {
        val c = controller ?: return
        when (_state.value.phase) {
            PlaybackPhase.Error -> retry()
            PlaybackPhase.Ended -> {
                c.seekTo(0L)
                _positionMs.value = 0L
                c.play()
            }
            // Nothing loaded yet (fresh process + restored queue).
            PlaybackPhase.Idle -> if (queueItems.isNotEmpty()) playCurrent()
            else -> if (c.isPlaying) c.pause() else c.play()
        }
    }

    /** Used by the sleep timer to pause after N minutes. */
    fun pause() {
        controller?.pause()
    }

    fun skipNext() {
        val next = QueueNavigation.next(currentIndex, queueItems.size, repeatMode)
        if (next != null) {
            currentIndex = next
            publishQueueIndex()
            playCurrent()
        } else {
            controller?.pause()
            _state.update { it.copy(phase = PlaybackPhase.Ended, isPlaying = false) }
        }
    }

    fun skipPrevious() {
        val previous = QueueNavigation.previous(currentIndex, queueItems.size, repeatMode)
        if (previous != null) {
            currentIndex = previous
            publishQueueIndex()
            playCurrent()
        } else {
            controller?.seekTo(0L)
            _positionMs.value = 0L
        }
    }

    /** Jump to a queue entry from the Queue screen. */
    fun playQueueIndex(index: Int) {
        if (index !in queueItems.indices) return
        val isTheLoadedTrack = index == currentIndex &&
            _state.value.item?.videoId == queueItems[index].videoId &&
            _state.value.phase != PlaybackPhase.Idle
        if (isTheLoadedTrack) {
            // Same track: restart it rather than re-resolving.
            controller?.seekTo(0L)
            _positionMs.value = 0L
            controller?.play()
        } else {
            // Covers a restored queue with no player loaded (phase Idle), too.
            currentIndex = index
            publishQueueIndex()
            playCurrent()
        }
    }

    /** Remove a track from the queue (never the currently playing one). */
    fun removeFromQueue(index: Int) {
        if (index in queueItems.indices && index != currentIndex) {
            val removed = queueItems.removeAt(index)
            baseOrder = baseOrder.filterNot { it.videoId == removed.videoId }
            if (index < currentIndex) {
                currentIndex--
                publishQueueIndex()
            }
            publishQueue()
            publishTransportFlags()
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

    private fun publishTransportFlags() {
        _state.update {
            it.copy(
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                hasPrev = QueueNavigation.previous(currentIndex, queueItems.size, repeatMode) != null,
                hasNext = QueueNavigation.next(currentIndex, queueItems.size, repeatMode) != null,
            )
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
        // Immediate feedback: the ring must move on touch, not on the next tick.
        _positionMs.value = positionMs
    }

    /** Re-attempts resolution + playback after a failure (user taps play). */
    fun retry() {
        if (_state.value.phase == PlaybackPhase.Error) {
            refreshedVideoId = null
            playCurrent()
        }
    }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        publishTransportFlags()
    }

    // --- Player.Listener ---------------------------------------------------

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        val c = controller
        val phase = when {
            isPlaying -> PlaybackPhase.Playing
            c?.playbackState == Player.STATE_BUFFERING -> PlaybackPhase.Buffering
            c?.playbackState == Player.STATE_READY -> PlaybackPhase.Paused
            else -> _state.value.phase
        }
        _state.update { it.copy(phase = phase, isPlaying = isPlaying) }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_BUFFERING -> _state.update { it.copy(phase = PlaybackPhase.Buffering) }
            Player.STATE_READY -> _state.update {
                it.copy(
                    phase = if (controller?.isPlaying == true) {
                        PlaybackPhase.Playing
                    } else {
                        PlaybackPhase.Paused
                    },
                    durationMs = controller?.duration?.takeIf { d -> d > 0L } ?: it.durationMs,
                )
            }
            Player.STATE_ENDED -> onTrackEnded()
            // Transient player states must not clobber Resolving/Preparing/
            // Error with Idle (that was the brief flicker on every skip).
            else -> {
                val phase = _state.value.phase
                if (phase != PlaybackPhase.Error &&
                    phase != PlaybackPhase.Resolving &&
                    phase != PlaybackPhase.Preparing
                ) {
                    _state.update { it.copy(phase = PlaybackPhase.Idle) }
                }
            }
        }
    }

    /** Repeat one replays; otherwise advance, wrapping only with repeat-all. */
    private fun onTrackEnded() {
        if (repeatMode == Player.REPEAT_MODE_ONE) {
            controller?.seekTo(0L)
            _positionMs.value = 0L
            controller?.play()
            _state.update { it.copy(phase = PlaybackPhase.Playing, isPlaying = true) }
            return
        }
        val next = QueueNavigation.next(currentIndex, queueItems.size, repeatMode)
        if (next != null) {
            currentIndex = next
            publishQueueIndex()
            playCurrent()
        } else {
            _state.update { it.copy(phase = PlaybackPhase.Ended, isPlaying = false) }
            controller?.pause()
        }
    }

    override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
        // Best-effort refresh-once per track for signed-URL failures
        // (403/410). A permanent failure surfaces instead of looping.
        val item = queueItems.getOrNull(currentIndex) ?: return
        if (refreshedVideoId == item.videoId) {
            _state.update {
                it.copy(
                    phase = PlaybackPhase.Error,
                    errorMessage = "Playback failed: ${error.errorCodeName}",
                )
            }
            return
        }
        refreshedVideoId = item.videoId
        val token = playToken
        scope.launch {
            val fresh = resolve(item.videoId)
            if (token != playToken) return@launch
            if (fresh == null) {
                _state.update {
                    it.copy(
                        phase = PlaybackPhase.Error,
                        errorMessage = "Playback failed: ${error.errorCodeName}",
                    )
                }
                return@launch
            }
            val updated = item.copy(stream = fresh)
            queueItems[currentIndex] = updated
            controller?.setMediaItem(toMediaItem(updated), 0L)
            controller?.prepare()
            controller?.play()
        }
    }

    override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_POSITION_DISCONTINUITY) ||
            events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
        ) {
            _state.update {
                it.copy(
                    durationMs = player.duration.takeIf { d -> d > 0L } ?: it.durationMs,
                )
            }
        }
    }

    // --- Position ticker ---------------------------------------------------

    /** Called by the app shell: false when no UI is on screen. */
    fun setUiActive(active: Boolean) {
        uiActive.value = active
    }

    /**
     * Cheap position tick for the UI (no flood). Emissions are SKIPPED while
     * nothing changes, and the cadence drops to 3s while paused or idle so a
     * paused watch stays asleep. While no UI is visible the loop PARKS on the
     * flow instead of polling — screen-off playback costs zero wakeups here.
     */
    fun startPositionTicker() {
        scope.launch {
            while (true) {
                uiActive.first { it }
                val c = controller
                if (c == null) {
                    delay(1000)
                    continue
                }
                val active = c.isPlaying || _state.value.phase == PlaybackPhase.Buffering ||
                    _state.value.phase == PlaybackPhase.Resolving ||
                    _state.value.phase == PlaybackPhase.Preparing
                delay(if (active) 500 else 3000)
                if (!uiActive.value) continue
                val position = c.currentPosition
                if (position != _positionMs.value) _positionMs.value = position
                val duration = if (c.duration > 0L) c.duration else _state.value.durationMs
                if (duration != _state.value.durationMs) {
                    _state.update { it.copy(durationMs = duration) }
                }
            }
        }
    }

    private fun tickPosition(c: MediaController) {
        _positionMs.value = c.currentPosition
    }

    // --- Media item construction -------------------------------------------

    private fun mediaMetadataFor(item: PlaybackItem): MediaMetadata {
        val extras = android.os.Bundle().apply {
            putString(KEY_ITEM_JSON, runCatching { itemJson.encodeToString(item) }.getOrNull())
        }
        return MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistLabel)
            .setAlbumTitle(item.albumLabel)
            .setExtras(extras)
            .apply {
                item.artworkUrl?.let { setArtworkUri(android.net.Uri.parse(it)) }
            }
            .build()
    }

    private fun toMediaItem(item: PlaybackItem): MediaItem {
        val stream = requireNotNull(item.stream)
        return MediaItem.Builder()
            .setMediaId(item.videoId)
            .setUri(stream.url)
            .setMimeType(stream.mimeType)
            .setMediaMetadata(mediaMetadataFor(item))
            .build()
    }

    private fun mediaItemFromFile(item: PlaybackItem, file: java.io.File, mimeType: String): MediaItem {
        return MediaItem.Builder()
            .setMediaId(item.videoId)
            .setUri(android.net.Uri.fromFile(file))
            .setMimeType(mimeType)
            .setMediaMetadata(mediaMetadataFor(item))
            .build()
    }

    fun release() {
        prefetchJob?.cancel()
        connectJob?.cancel()
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
