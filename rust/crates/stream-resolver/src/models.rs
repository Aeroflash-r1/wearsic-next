//! Stream descriptor + audio format models.

use serde::{Deserialize, Serialize};

/// A validated, playable audio stream.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct StreamDescriptor {
    pub video_id: String,
    pub url: String,
    pub mime_type: String,
    /// Codec string when the service provided one (e.g. `mp4a.40.2`).
    pub codec: Option<String>,
    /// Approximate bitrate (bits/s, from the service).
    pub bitrate: Option<u64>,
    pub sample_rate: Option<u64>,
    pub channels: Option<u64>,
    /// Content length in bytes (0 = unknown).
    pub content_length: u64,
    pub duration_ms: Option<u64>,
    /// Source / method used to obtain this stream.
    pub source: String,
}

/// Raw audio format extracted from a player response.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct AudioFormat {
    pub url: String,
    pub mime_type: String,
    pub codec: Option<String>,
    pub bitrate: u64,
    pub sample_rate: Option<u64>,
    pub channels: Option<u64>,
    pub content_length: u64,
    pub itag: Option<u64>,
}
