package com.wearsic.next.ui.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.wearsic.next.engine.MusicSearchResult
import com.wearsic.next.engine.RustEngine
import com.wearsic.next.playback.PlaybackItem
import com.wearsic.next.ui.data.FavoritesStore
import com.wearsic.next.ui.data.RecentStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface MadeForYouUiState {
    data object Loading : MadeForYouUiState
    data class Content(
        /** What we built the recs from — shown as the subtitle line. */
        val seedLabel: String,
        val items: List<PlaybackItem>,
    ) : MadeForYouUiState
    data object Empty : MadeForYouUiState
    data class Error(val message: String) : MadeForYouUiState
}

/**
 * "Made for you" — the standalone equivalent of the original screen, with
 * NO server. Recs are derived from your local taste: your most recently
 * played and favorited artists are used as search seeds; InnerTube's mixed
 * response supplies the catalogue. One search per seed artist (2 max), so
 * the whole page costs two requests per visit.
 */
class RecommendationsViewModel(
    private val engine: RustEngine = RustEngine.create(),
) : ViewModel() {

    private val _state = MutableStateFlow<MadeForYouUiState>(MadeForYouUiState.Loading)
    val state: StateFlow<MadeForYouUiState> = _state.asStateFlow()

    private var loaded = false

    /** Refresh on every entry (like the original: one burst per visit). */
    fun refresh(force: Boolean = false) {
        if (loaded && !force) return
        loaded = true
        _state.value = MadeForYouUiState.Loading
        viewModelScope.launch {
            val recent = RecentStore.items
            val favorites = FavoritesStore.listFavorites()
            val taste = (recent + favorites)
                .filter { it.artistLabel.isNotBlank() }
                .distinctBy { it.videoId }
            if (taste.isEmpty()) {
                _state.value = MadeForYouUiState.Empty
                return@launch
            }
            // Rank artists by frequency in your taste; search top 2.
            val seeds = taste
                .flatMap { item -> item.artists.take(2).map { it.name.trim() } }
                .filter { it.isNotBlank() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
                .take(2)
            if (seeds.isEmpty()) {
                _state.value = MadeForYouUiState.Empty
                return@launch
            }
            val excluded = taste.mapTo(HashSet()) { it.videoId }
            val collected = mutableListOf<PlaybackItem>()
            val seen = HashSet<String>()
            for (seed in seeds) {
                if (collected.size >= 25) break
                val result = withContext(Dispatchers.IO) {
                    runCatching { engine.searchMusic(seed) }
                        .getOrNull()
                }
                result?.results
                    ?.mapNotNull { it as? MusicSearchResult.Track }
                    ?.forEach { track ->
                        val dto = track.track
                        if (dto.videoId in excluded || dto.videoId in seen) return@forEach
                        seen.add(dto.videoId)
                        collected.add(
                            PlaybackItem(
                                videoId = dto.videoId,
                                title = dto.title,
                                artists = dto.artists,
                                album = dto.album,
                                durationMs = dto.durationMs,
                                thumbnails = dto.thumbnails,
                            )
                        )
                    }
            }
            if (collected.isEmpty()) {
                _state.value = MadeForYouUiState.Error(
                    "Couldn't build recommendations — check your connection."
                )
            } else {
                val seedLabel = seeds.joinToString(" · ")
                _state.value = MadeForYouUiState.Content(seedLabel, collected)
            }
        }
    }
}
