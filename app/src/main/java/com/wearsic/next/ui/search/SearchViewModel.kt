package com.wearsic.next.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.next.engine.EngineException
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.engine.NetworkEngineException
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.engine.SearchEnvelope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/** Search result type filter (client-side view over the typed results). */
enum class SearchFilter(val label: String) {
    All("All"),
    Songs("Songs"),
    Albums("Albums"),
    Artists("Artists"),
    Playlists("Playlists"),
}

sealed interface SearchUiState {
    data object Idle : SearchUiState
    data class Loading(val query: String, val filter: SearchFilter) : SearchUiState
    data class Results(
        val query: String,
        val filter: SearchFilter,
        val results: List<MusicSearchResult>,
        val continuation: String?,
        val loadingMore: Boolean = false,
    ) : SearchUiState
    data class Error(val query: String, val message: String) : SearchUiState
}

class SearchViewModel(
    private val engine: RustEngine = RustEngine.create(),
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.All)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val requestCounter = AtomicLong(0)
    private var activeSearchJob: Job? = null
    private var currentRequestId = 0L

    /** Real-time input; debounces the actual search. */
    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
        // Debounce: only start a search after quiet typing.
        activeSearchJob?.cancel()
        activeSearchJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            if (newQuery.isBlank()) {
                _state.value = SearchUiState.Idle
            } else {
                runSearch(newQuery, _filter.value, continuation = null)
            }
        }
    }

    fun onFilterSelected(newFilter: SearchFilter) {
        if (_filter.value == newFilter) return
        _filter.value = newFilter
        val q = _query.value
        if (q.isNotBlank()) {
            activeSearchJob?.cancel()
            runSearch(q, newFilter, continuation = null)
        }
    }

    /** Loads the next page of the current query. */
    fun loadMore() {
        val current = _state.value as? SearchUiState.Results ?: return
        val continuation = current.continuation ?: return
        if (current.loadingMore) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            val result = executeSearch(current.query, continuation)
            val latest = _state.value as? SearchUiState.Results
            if (latest == null || latest.query != current.query) return@launch
            _state.value = latest.copy(
                results = latest.results + result.results.map { dto -> dto.toDomain() },
                continuation = result.continuation,
                loadingMore = false,
            )
        }
    }

    fun retry() {
        val q = _query.value
        if (q.isNotBlank()) runSearch(q, _filter.value, continuation = null)
    }

    private fun runSearch(query: String, filter: SearchFilter, continuation: String?) {
        val requestId = requestCounter.incrementAndGet()
        currentRequestId = requestId
        _state.value = SearchUiState.Loading(query, filter)
        viewModelScope.launch {
            val result = executeSearch(query, continuation, requestId)
            // Guard against outdated responses: the current query must match.
            if (_query.value != query) return@launch
            if (result.ok) {
                val mapped: List<MusicSearchResult> = result.results.map { dto -> dto.toDomain() }
                _state.value = SearchUiState.Results(
                    query = query,
                    filter = filter,
                    results = mapped,
                    continuation = result.continuation,
                )
            } else {
                _state.value = SearchUiState.Error(query, result.error?.message ?: "Search failed")
            }
        }
    }

    private suspend fun executeSearch(
        query: String,
        continuation: String?,
        requestId: Long = 0L,
    ): SearchEnvelope = withContext(Dispatchers.IO) {
        runCatching {
            engine.searchMusic(query, continuation, requestId)
        }.getOrElse { t ->
            SearchEnvelope(
                ok = false,
                error = com.wearsic.next.engine.NetworkErrorPublic(
                    kind = when (t) {
                        is NetworkEngineException -> t.error.kind
                        is EngineException -> "engine"
                        else -> "internal"
                    },
                    status = 0,
                    message = t.message ?: "Search failed",
                ),
            )
        }
    }

    /** Cancels the in-flight search when the view is destroyed. */
    fun cancelActiveSearch() {
        if (currentRequestId != 0L) {
            engine.searchCancel(currentRequestId)
        }
        activeSearchJob?.cancel()
    }

    override fun onCleared() {
        cancelActiveSearch()
        super.onCleared()
    }

    companion object {
        private const val DEBOUNCE_MS = 350L
    }
}
