package com.wearsic.next.playback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.next.engine.RustEngine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * UI-facing playback ViewModel. Owns the controller and exposes the
 * throttled playback state to Compose.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val engine: RustEngine by lazy { RustEngine.create().also { it.networkInit() } }
    private val controller: PlaybackController by lazy {
        PlaybackController.create(application, engine).also { it.startPositionTicker() }
    }

    val state: StateFlow<PlaybackUiState> get() = controller.state

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
        // Resolution is handled lazily inside the controller.
        val item = PlaybackItem(
            videoId = track.track.videoId,
            title = track.track.title,
            artists = track.track.artists,
            album = track.track.album,
            durationMs = track.track.durationMs,
            thumbnails = track.track.thumbnails,
            stream = null,
        )
        controller.play(listOf(item), 0)
    }

    fun togglePlayPause() = controller.togglePlayPause()
    fun skipNext() = controller.skipNext()
    fun skipPrevious() = controller.skipPrevious()
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun setShuffle(on: Boolean) = controller.setShuffle(on)
    fun cycleRepeatMode() = controller.cycleRepeatMode()
    fun retry() = controller.retry()

    override fun onCleared() {
        controller.release()
        super.onCleared()
    }
}
