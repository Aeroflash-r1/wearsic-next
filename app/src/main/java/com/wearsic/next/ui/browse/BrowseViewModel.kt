package com.wearsic.next.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.next.engine.BrowsePageDto
import com.wearsic.next.engine.RustEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What kind of collection a browse page is. */
enum class BrowseKind { Album, Playlist, Artist }

sealed interface BrowseUiState {
    data object Loading : BrowseUiState
    data class Content(val page: BrowsePageDto, val loadingMore: Boolean = false) : BrowseUiState
    data class Error(val message: String) : BrowseUiState
}

/**
 * Loads album / playlist / artist pages from the Rust engine.
 *
 * One request per screen visit; long playlists append a page at a time via
 * [loadMore]. Stale responses are dropped by comparing the requested
 * browse id, so leaving a page mid-request can never paint it onto another.
 */
class BrowseViewModel(
    private val engine: RustEngine = RustEngine.create(),
) : ViewModel() {

    private val _state = MutableStateFlow<BrowseUiState>(BrowseUiState.Loading)
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private var browseId: String? = null
    private var continuation: String? = null
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    fun load(id: String, force: Boolean = false) {
        if (!force && id == browseId && _state.value is BrowseUiState.Content) return
        browseId = id
        continuation = null
        loadJob?.cancel()
        loadMoreJob?.cancel()
        _state.value = BrowseUiState.Loading
        loadJob = viewModelScope.launch {
            val result = fetch(id, null)
            if (browseId != id) return@launch
            result
                .onSuccess { page ->
                    continuation = page.continuation
                    _state.value = BrowseUiState.Content(page)
                }
                .onFailure { error ->
                    _state.value = BrowseUiState.Error(
                        error.message ?: "Could not load this page"
                    )
                }
        }
    }

    fun loadMore() {
        val current = _state.value as? BrowseUiState.Content ?: return
        val token = continuation ?: return
        val id = browseId ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        loadMoreJob = viewModelScope.launch {
            val result = fetch(id, token)
            val latest = _state.value as? BrowseUiState.Content
            if (latest == null || browseId != id) return@launch
            result
                .onSuccess { next ->
                    continuation = next.continuation
                    _state.value = latest.copy(
                        page = latest.page.copy(
                            tracks = (latest.page.tracks + next.tracks)
                                .distinctBy { it.videoId },
                            continuation = next.continuation,
                        ),
                        loadingMore = false,
                    )
                }
                .onFailure {
                    // Keep what is already on screen; the button can retry.
                    _state.value = latest.copy(loadingMore = false)
                }
        }
    }

    fun retry() {
        browseId?.let { load(it, force = true) }
    }

    private suspend fun fetch(id: String, continuation: String?): Result<BrowsePageDto> =
        withContext(Dispatchers.IO) {
            runCatching { engine.browse(id, continuation) }
        }
}
