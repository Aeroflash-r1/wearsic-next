//! Stream resolution orchestration: player request → parse → select →
//! validate → cached descriptor.

use std::collections::HashMap;
use std::sync::{Arc, Mutex};
use std::time::Instant;

use innertube::InnerTubeClient;
use network::{CancellationToken, HttpClient};

use crate::error::{ResolveError, ResolveErrorKind};
use crate::format::select_best_format;
use crate::models::StreamDescriptor;
use crate::player_response::{parse_adaptive_audio_formats, playability_error};
use crate::validation::{validate_mime, validate_stream_url, validate_video_id};

/// Conservative lifetime for resolved stream URLs (they expire server-side;
/// we never trust one longer than this).
const STREAM_CACHE_TTL: std::time::Duration = std::time::Duration::from_secs(10 * 60);
const STREAM_CACHE_MAX: usize = 64;

struct DescriptorCache {
    entries: Mutex<HashMap<String, (Instant, StreamDescriptor)>>,
}

impl DescriptorCache {
    fn new() -> Self {
        Self { entries: Mutex::new(HashMap::new()) }
    }

    fn get(&self, key: &str) -> Option<StreamDescriptor> {
        let mut map = self.entries.lock().ok()?;
        let (at, desc) = map.get(key)?;
        if at.elapsed() > STREAM_CACHE_TTL {
            map.remove(key);
            return None;
        }
        Some(desc.clone())
    }

    fn put(&self, key: String, desc: StreamDescriptor) {
        if let Ok(mut map) = self.entries.lock() {
            if map.len() >= STREAM_CACHE_MAX {
                if let Some(oldest) =
                    map.iter().min_by_key(|(_, (at, _))| *at).map(|(k, _)| k.clone())
                {
                    map.remove(&oldest);
                }
            }
            map.insert(key, (Instant::now(), desc));
        }
    }

    fn invalidate(&self, key: &str) {
        if let Ok(mut map) = self.entries.lock() {
            map.remove(key);
        }
    }
}

/// The stream resolver. Cheap to clone; shares the Phase 3 client.
#[derive(Clone)]
pub struct StreamResolver {
    inner: InnerTubeClient,
    cache: Arc<DescriptorCache>,
}

/// Clients tried in order until one yields playable formats. IOS works
/// cookie-less (verified live); ANDROID_VR is a documented secondary.
const PLAYER_CLIENTS: &[(&str, &str, &str)] = &[
    (
        "IOS",
        "21.03.2",
        "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)",
    ),
    (
        "ANDROID_VR",
        "1.60.19",
        "Mozilla/5.0 (Linux; Android 12; Quest 3) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36",
    ),
];

impl StreamResolver {
    pub fn new(http: HttpClient) -> Self {
        Self {
            inner: InnerTubeClient::new(http),
            cache: Arc::new(DescriptorCache::new()),
        }
    }

    /// Resolves the best playable audio stream for `video_id`.
    pub async fn resolve(
        &self,
        video_id: &str,
        token: &CancellationToken,
    ) -> Result<StreamDescriptor, ResolveError> {
        validate_video_id(video_id)?;
        token.check().map_err(|_| ResolveError::new(ResolveErrorKind::Cancelled, "cancelled"))?;

        if let Some(cached) = self.cache.get(video_id) {
            return Ok(cached);
        }

        for (name, version, ua) in PLAYER_CLIENTS {
            token.check().map_err(|_| {
                ResolveError::new(ResolveErrorKind::Cancelled, "cancelled")
            })?;
            match self.request_player(video_id, name, version, ua, token).await {
                Ok(descriptor) => {
                    self.cache.put(video_id.to_string(), descriptor.clone());
                    return Ok(descriptor);
                }
                Err(e) if e.kind == ResolveErrorKind::Cancelled => return Err(e),
                Err(_) => continue, // try next client
            }
        }

        Err(ResolveError::new(
            ResolveErrorKind::TrackUnavailable,
            "no client returned a playable stream",
        ))
    }

    async fn request_player(
        &self,
        video_id: &str,
        client_name: &str,
        client_version: &str,
        user_agent: &str,
        token: &CancellationToken,
    ) -> Result<StreamDescriptor, ResolveError> {
        let payload = serde_json::json!({
            "videoId": video_id,
            "contentCheckOk": true,
            "racyCheckOk": true,
            "context": {
                "client": {
                    "clientName": client_name,
                    "clientVersion": client_version,
                    "deviceModel": "iPhone16,2",
                    "deviceMake": "Apple",
                    "osName": "iOS",
                    "osVersion": "18.7.2",
                    "hl": "en",
                    "gl": "US"
                },
                "user": {}
            }
        });

        let url = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false";
        let mut req = network::Request::post(url, serde_json::to_vec(&payload).unwrap_or_default())
            .with_header("content-type", "application/json")
            .with_header("accept", "*/*")
            .with_header("user-agent", user_agent);
        if client_name == "IOS" {
            req = req
                .with_header("x-youtube-client-name", "5")
                .with_header("x-youtube-client-version", client_version);
        }

        let response = self
            .inner
            .post_raw(&req, token)
            .await
            .map_err(|e| match e {
                innertube::InnerTubeError::Network(n) => ResolveError::from(n),
                other => ResolveError::new(
                    ResolveErrorKind::MalformedResponse,
                    other.to_string(),
                ),
            })?;

        // playability
        if let Some(err) = playability_error(&response, video_id) {
            return Err(err);
        }

        let formats = parse_adaptive_audio_formats(&response);
        if formats.is_empty() {
            return Err(ResolveError::new(
                ResolveErrorKind::MissingStream,
                "no audio formats in player response",
            ));
        }

        let format = select_best_format(&formats)?;
        validate_stream_url(&format.url)?;
        validate_mime(&format.mime_type)?;

        Ok(StreamDescriptor {
            video_id: video_id.to_string(),
            url: format.url,
            mime_type: format.mime_type,
            codec: format.codec,
            bitrate: Some(format.bitrate).filter(|b| *b > 0),
            sample_rate: format.sample_rate,
            channels: format.channels,
            content_length: format.content_length,
            duration_ms: None, // player response doesn't reliably carry it here
            source: format!("innertube:{}", client_name),
        })
    }

    /// Clears a cached descriptor (e.g. after a playback failure).
    pub fn invalidate(&self, video_id: &str) {
        self.cache.invalidate(video_id);
    }
}
