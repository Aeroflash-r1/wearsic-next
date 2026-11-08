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
    visitor: Arc<Mutex<Option<(String, Instant)>>>,
}

/// Clients tried in order until one yields playable formats.
/// VISIONOS is primary (its URLs stream reliably across networks; original
/// Wearsic made the same call — IOS URLs can 403/die at ~1MB on some
/// paths). VISIONOS needs a session visitor id. IOS works cookie-less.
const PLAYER_CLIENTS: &[(&str, &str, &str)] = &[
    (
        "VISIONOS",
        "1.02",
        "Mozilla/5.0 (Apple TV; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1",
    ),
    (
        "IOS",
        "21.03.2",
        "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)",
    ),
];

impl StreamResolver {
    pub fn new(http: HttpClient) -> Self {
        Self {
            inner: InnerTubeClient::new(http),
            cache: Arc::new(DescriptorCache::new()),
            visitor: Arc::new(Mutex::new(None)),
        }
    }

    /// Returns the session visitor id (from a prior player response), or
    /// bootstraps one via an IOS player call.
    async fn visitor_data(&self, video_id: &str, token: &CancellationToken) -> Option<String> {
        {
            let guard = self.visitor.lock().ok()?;
            if let Some((v, at)) = guard.as_ref() {
                if at.elapsed() < std::time::Duration::from_secs(6 * 3600) {
                    return Some(v.clone());
                }
            }
        }
        // Bootstrap: an IOS player response carries visitorData in its
        // responseContext (sw.js_data no longer yields one reliably).
        let visitor = match self.request_player_raw(video_id, "IOS", "21.03.2",
            "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)",
            None, token).await {
            Ok((_, response)) => response
                .pointer("/responseContext/visitorData")
                .and_then(serde_json::Value::as_str)
                .map(str::to_string),
            Err(_) => None,
        };
        if let Some(v) = visitor.clone() {
            if let Ok(mut guard) = self.visitor.lock() {
                *guard = Some((v.clone(), Instant::now()));
            }
        }
        visitor
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
            let visitor = if *name == "VISIONOS" {
                self.visitor_data(video_id, token).await
            } else {
                None
            };
            match self
                .request_player(video_id, name, version, ua, visitor.as_deref(), token)
                .await
            {
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
        visitor: Option<&str>,
        token: &CancellationToken,
    ) -> Result<StreamDescriptor, ResolveError> {
        let (_used, response) = self
            .request_player_raw(video_id, client_name, client_version, user_agent, visitor, token)
            .await?;

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

    /// Raw player request: returns the client name + parsed response JSON.
    async fn request_player_raw(
        &self,
        video_id: &str,
        client_name: &str,
        client_version: &str,
        user_agent: &str,
        visitor: Option<&str>,
        token: &CancellationToken,
    ) -> Result<(String, serde_json::Value), ResolveError> {
        // Client-specific context fields (mirrors the original Wearsic).
        let (device_model, device_make, os_name, os_version) = if client_name == "VISIONOS" {
            ("RealityDevice14,1", "Apple", "visionOS", "25.6.0.0")
        } else if client_name == "IOS" {
            ("iPhone16,2", "Apple", "iOS", "18.7.2")
        } else {
            ("Quest 3", "Oculus", "Android", "12")
        };
        let mut client = serde_json::json!({
            "clientName": client_name,
            "clientVersion": client_version,
            "deviceModel": device_model,
            "deviceMake": device_make,
            "osName": os_name,
            "osVersion": os_version,
            "hl": "en",
            "gl": "US"
        });
        if let Some(v) = visitor {
            client["visitorData"] = serde_json::Value::String(v.to_string());
        }
        let payload = serde_json::json!({
            "videoId": video_id,
            "contentCheckOk": true,
            "racyCheckOk": true,
            "context": {
                "client": client,
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

        Ok((client_name.to_string(), response))
    }

    /// Clears a cached descriptor (e.g. after a playback failure).
    pub fn invalidate(&self, video_id: &str) {
        self.cache.invalidate(video_id);
    }
}
