//! Structured stream-resolution errors.

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum ResolveErrorKind {
    /// videoId failed validation (blank / wrong shape).
    InvalidTrack,
    /// Service says track is unavailable (playability != OK, age, geo…).
    TrackUnavailable,
    /// No audio format we can play (unsupported codec/container, or none).
    UnsupportedFormat,
    /// Network/transport failure (Phase 3 categories).
    Network,
    /// The player response was malformed / missing streamingData.
    MalformedResponse,
    /// No usable stream URL for the selected format.
    MissingStream,
    /// A previously resolved stream URL expired / was refused.
    StreamExpired,
    /// Operation cancelled.
    Cancelled,
    /// Internal engine error.
    Internal,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct ResolveError {
    pub kind: ResolveErrorKind,
    pub message: String,
}

impl ResolveError {
    pub fn new(kind: ResolveErrorKind, message: impl Into<String>) -> Self {
        Self { kind, message: message.into() }
    }
}

impl From<network::NetworkError> for ResolveError {
    fn from(e: network::NetworkError) -> Self {
        let kind = match e.kind {
            network::NetworkErrorKind::Timeout
            | network::NetworkErrorKind::Connect
            | network::NetworkErrorKind::Dns
            | network::NetworkErrorKind::Tls
            | network::NetworkErrorKind::Http => ResolveErrorKind::Network,
            network::NetworkErrorKind::Cancelled => ResolveErrorKind::Cancelled,
            _ => ResolveErrorKind::Internal,
        };
        ResolveError::new(kind, e.message)
    }
}
