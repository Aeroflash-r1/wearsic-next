//! Network error taxonomy.
//!
//! Errors are deliberately coarse and user-facing-safe: the UI never sees
//! raw transport details. Cancellation is a distinct category, never a
//! normal failure.

use std::fmt;

/// Error categories surfaced to Kotlin.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub enum NetworkErrorKind {
    /// Invalid/unsupported URL (e.g. non-https).
    InvalidUrl,
    /// DNS resolution failure.
    Dns,
    /// Connection failed (refused, unreachable, reset before response).
    Connect,
    /// Read/request timed out.
    Timeout,
    /// TLS/certificate failure.
    Tls,
    /// HTTP error status (4xx/5xx), with status.
    Http,
    /// Response body exceeded the configured limit.
    BodyTooLarge,
    /// Response was not valid UTF-8 / JSON.
    InvalidResponse,
    /// Operation was cancelled by the caller.
    Cancelled,
    /// Engine was shut down mid-operation.
    Shutdown,
    /// Internal engine error.
    Internal,
}

/// Structured network error.
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct NetworkError {
    pub kind: NetworkErrorKind,
    pub status: u16,
    pub message: String,
}

impl NetworkError {
    pub fn new(kind: NetworkErrorKind, message: impl Into<String>) -> Self {
        Self { kind, status: 0, message: message.into() }
    }

    pub fn http(status: u16) -> Self {
        Self { kind: NetworkErrorKind::Http, status, message: format!("HTTP {status}") }
    }

    pub fn is_retryable(&self) -> bool {
        match self.kind {
            NetworkErrorKind::Connect | NetworkErrorKind::Timeout
            | NetworkErrorKind::Dns | NetworkErrorKind::Tls => true,
            NetworkErrorKind::Http => self.status == 429 || self.status >= 500,
            _ => false,
        }
    }

    pub fn is_permanent(&self) -> bool {
        matches!(
            self.kind,
            NetworkErrorKind::InvalidUrl
                | NetworkErrorKind::BodyTooLarge
                | NetworkErrorKind::InvalidResponse
                | NetworkErrorKind::Cancelled
                | NetworkErrorKind::Shutdown
                | NetworkErrorKind::Internal
        ) || (self.kind == NetworkErrorKind::Http && self.status < 500 && self.status != 429)
    }
}

impl fmt::Display for NetworkError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{:?}: {}", self.kind, self.message)
    }
}

impl std::error::Error for NetworkError {}
