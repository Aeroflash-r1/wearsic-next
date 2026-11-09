//! YouTube Music search service: orchestrates InnerTube calls, parsing,
//! caching and pagination on top of the Phase 3 network engine.

use std::collections::HashMap;
use std::sync::Arc;
use std::sync::Mutex;
use std::time::Instant;

use innertube::InnerTubeClient;
use network::{CancellationToken, HttpClient};

use crate::mapper::{
    find_continuation, parse_browse_response, parse_search_response, parse_suggestions,
    SearchFilter,
};
use crate::models::{BrowsePage, SearchPage};

/// Bounded TTL cache for search pages (in-memory only).
struct SearchCache {
    entries: Mutex<HashMap<String, (Instant, SearchPage)>>,
    ttl: std::time::Duration,
    max_entries: usize,
}

impl SearchCache {
    fn new(ttl: std::time::Duration, max_entries: usize) -> Self {
        Self { entries: Mutex::new(HashMap::new()), ttl, max_entries }
    }

    fn get(&self, key: &str) -> Option<SearchPage> {
        let mut guard = self.entries.lock().ok()?;
        let (at, page) = guard.get(key)?;
        if at.elapsed() > self.ttl {
            guard.remove(key);
            return None;
        }
        Some(page.clone())
    }

    fn put(&self, key: String, page: SearchPage) {
        if let Ok(mut guard) = self.entries.lock() {
            if guard.len() >= self.max_entries {
                // Simple oldest-first eviction.
                let oldest_key = guard
                    .iter()
                    .min_by_key(|(_, (at, _))| *at)
                    .map(|(k, _)| k.clone());
                if let Some(k) = oldest_key {
                    guard.remove(&k);
                }
            }
            guard.insert(key, (Instant::now(), page));
        }
    }
}

/// The YouTube Music discovery service.
#[derive(Clone)]
pub struct YtMusicService {
    innertube: InnerTubeClient,
    cache: Arc<SearchCache>,
}

impl YtMusicService {
    pub fn new(http: HttpClient) -> Self {
        Self {
            innertube: InnerTubeClient::new(http),
            cache: Arc::new(SearchCache::new(
                std::time::Duration::from_secs(300), // 5 min TTL
                64,
            )),
        }
    }

    /// Returns the first page of results for `query`. The service fetches
    /// the mixed InnerTube response once and classifies every row into typed
    /// results; the UI applies the chosen filter as a client-side view
    /// (verified live: InnerTube search `params` no longer narrow the
    /// response). Results are cached (keyed by normalized query).
    pub async fn search(
        &self,
        query: &str,
        _filter: SearchFilter,
        token: &CancellationToken,
    ) -> Result<SearchPage, String> {
        let key = format!("{}", query.trim());
        if let Some(page) = self.cache.get(&key) {
            return Ok(page);
        }

        let raw = self
            .innertube
            .search(query, None, token)
            .await
            .map_err(|e| e.to_string())?;

        let results = parse_search_response(&raw, 30);
        let continuation = find_continuation(&raw);
        let page = SearchPage { results: results.clone(), continuation: continuation.clone() };
        self.cache.put(key, page);
        Ok(SearchPage { results, continuation })
    }

    /// Fetch search suggestions for `input` (the live rows shown while
    /// typing). Suggestions are not cached at this layer (cheap calls).
    pub async fn suggestions(
        &self,
        input: &str,
        token: &CancellationToken,
    ) -> Result<Vec<String>, String> {
        let raw = self
            .innertube
            .suggestions(input, token)
            .await
            .map_err(|e| e.to_string())?;
        Ok(parse_suggestions(&raw, 8))
    }

    /// Fetches an album, playlist or artist page. One request per visit: the
    /// result is user-initiated and a page is cheap to re-fetch.
    pub async fn browse(
        &self,
        browse_id: &str,
        token: &CancellationToken,
    ) -> Result<BrowsePage, String> {
        let raw = self
            .innertube
            .browse(browse_id, None, token)
            .await
            .map_err(|e| e.to_string())?;
        let page = parse_browse_response(&raw, 300);
        // A non-existent id still returns HTTP 200 with an empty shell.
        if page.title.is_empty() && page.tracks.is_empty() && page.albums.is_empty() {
            return Err("this page is unavailable".to_string());
        }
        Ok(page)
    }

    /// Fetches the next page of a browse result (long playlists/albums).
    pub async fn browse_more(
        &self,
        continuation: &str,
        token: &CancellationToken,
    ) -> Result<BrowsePage, String> {
        let raw = self
            .innertube
            .browse_continuation(continuation, token)
            .await
            .map_err(|e| e.to_string())?;
        Ok(parse_browse_response(&raw, 300))
    }

    /// Fetches the next page via a continuation token. Not cached (pages are
    /// consumed once).
    pub async fn load_more(
        &self,
        continuation: &str,
        _filter: SearchFilter,
        token: &CancellationToken,
    ) -> Result<SearchPage, String> {
        let raw = self
            .innertube
            .continuation(continuation, token)
            .await
            .map_err(|e| e.to_string())?;
        let results = parse_search_response(&raw, 30);
        let next = find_continuation(&raw);
        Ok(SearchPage { results, continuation: next })
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn cache_hit_returns_same_page() {
        // Directly test cache logic (service requires network for a real call).
        let cache = Arc::new(SearchCache::new(
            std::time::Duration::from_secs(300),
            4,
        ));
        let page = SearchPage { results: vec![], continuation: None };
        cache.put("q|Songs".into(), page.clone());
        assert_eq!(cache.get("q|Songs"), Some(page));
        assert_eq!(cache.get("other|Songs"), None);
    }

    #[test]
    fn cache_evicts_when_full() {
        let cache = Arc::new(SearchCache::new(
            std::time::Duration::from_secs(300),
            2,
        ));
        cache.put("a".into(), SearchPage { results: vec![], continuation: None });
        cache.put("b".into(), SearchPage { results: vec![], continuation: None });
        cache.put("c".into(), SearchPage { results: vec![], continuation: None });
        assert!(cache.get("a").is_none());
        assert!(cache.get("b").is_some());
        assert!(cache.get("c").is_some());
    }
}
