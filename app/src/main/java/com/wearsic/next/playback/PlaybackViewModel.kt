package com.wearsic.next.playback

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.wearsic.next.engine.RustEngine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * UI-facing playback ViewModel. Owns the controller and exposes the
 * throttled playback state to Compose.
 *
 * [PlaybackController] is a Media3 session wrapper, so its surface is
 * annotated @UnstableApi; the opt-in is declared once here instead of
 * scattering annotations across every call site.
 */
@OptIn(UnstableApi::class)
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: RustEngine by lazy { RustEngine.create().also { it.networkInit() } }
    private val controller: PlaybackController by lazy {
        PlaybackController.create(application, engine).also { it.startPositionTicker() }
    }

    val state: StateFlow<PlaybackUiState> get() = controller.state

    /** Elapsed position in its own flow — see [PlaybackUiState]'s doc. */
    val positionMs: StateFlow<Long> get() = controller.positionMs

    /** Queue snapshot (emitted only on mutations — cheap to observe). */
    val queue: StateFlow<List<PlaybackItem>> get() = controller.queue

    /** Index of the currently playing entry in [queue]. */
    val queueIndex: StateFlow<Int> get() = controller.queueIndex

    fun playTracks(items: List<PlaybackItem>, startIndex: Int = 0) {
        controller.play(items, startIndex)
    }

    fun playQueueIndex(index: Int) = controller.playQueueIndex(index)

    fun removeFromQueue(index: Int) = controller.removeFromQueue(index)

    fun playSearchResult(track: com.wearsic.next.engine.MusicSearchResult.Track) {
        playSearchResults(listOf(track), 0)
    }

    /**
     * Queues a whole result list and starts at [startIndex]. Stream
     * resolution stays lazy inside the controller, and the following track is
     * prefetched while the current one plays.
     */
    fun playSearchResults(
        tracks: List<com.wearsic.next.engine.MusicSearchResult.Track>,
        startIndex: Int = 0,
    ) {
        if (tracks.isEmpty()) return
        controller.play(tracks.map { PlaybackItem.fromSearch(it.track) }, startIndex)
    }

    /** Queues a browse page's tracks (album / playlist / artist top songs). */
    fun playTrackDtos(tracks: List<com.wearsic.next.engine.TrackDto>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        controller.play(tracks.map { PlaybackItem.fromSearch(it) }, startIndex)
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun pause() = controller.pause()
    fun skipNext() = controller.skipNext()
    fun skipPrevious() = controller.skipPrevious()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun setShuffle(on: Boolean) = controller.setShuffle(on)
    fun cycleRepeatMode() = controller.cycleRepeatMode()
    fun retry() = controller.retry()

    /**
     * Tells the controller whether any UI is on screen. While hidden the
     * position ticker parks, so playback with the screen off costs no
     * periodic wakeups.
     */
    fun setUiActive(active: Boolean) = controller.setUiActive(active)

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
