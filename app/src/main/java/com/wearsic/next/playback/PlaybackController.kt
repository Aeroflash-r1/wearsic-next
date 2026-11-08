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
        }
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
    }

    private fun playCurrent() {
        val item = queueItems.getOrNull(currentIndex) ?: return
        _state.value = _state.value.copy(phase = PlaybackPhase.Resolving, item = item)
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
     * nothing changes — a paused or idle player costs no state churn, which
     * keeps the Compose pipeline (and the watch's CPU) asleep.
     */
    fun startPositionTicker() {
        scope.launch {
            while (true) {
                delay(500)
                val c = controller ?: continue
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
        val metadata = MediaMetadata.Builder()
            .setTitle(item.title)
            .setArtist(item.artistLabel)
            .setAlbumTitle(item.albumLabel)
            .setArtworkUri(android.net.Uri.parse(item.artworkUrl))
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
        fun create(context: Context, engine: RustEngine): PlaybackController {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            return PlaybackController(context, engine, scope)
        }
    }
}
