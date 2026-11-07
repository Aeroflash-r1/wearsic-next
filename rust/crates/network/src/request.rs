//! Typed request model.

use std::collections::BTreeMap;

/// HTTP methods the engine supports.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum HttpMethod {
    Get,
    Post,
}

impl Default for HttpMethod {
    fn default() -> Self {
        HttpMethod::Get
    }
}

/// A user-facing request. `id` is assigned by the engine.
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case")]
pub struct Request {
    #[serde(default)]
    pub id: Option<u64>,
    #[serde(default)]
    pub method: HttpMethod,
    pub url: String,
    #[serde(default)]
    pub headers: BTreeMap<String, String>,
    #[serde(default)]
    pub body: Option<Vec<u8>>,
    /// Optional per-request timeout override (ms).
    #[serde(default)]
    pub timeout_ms: Option<u64>,
    /// Per-request retry override; None = config default.
    #[serde(default)]
    pub max_attempts: Option<u32>,
    /// Whether the request is idempotent (retryable). GET/HEAD are
    /// idempotent by default; POST only when explicitly marked.
    #[serde(default)]
    pub idempotent: bool,
}

impl Request {
    pub fn get(url: impl Into<String>) -> Self {
        Self {
            id: None,
            method: HttpMethod::Get,
            url: url.into(),
            headers: BTreeMap::new(),
            body: None,
            timeout_ms: None,
            max_attempts: None,
            idempotent: true,
        }
    }

    pub fn post(url: impl Into<String>, body: Vec<u8>) -> Self {
        Self {
            method: HttpMethod::Post,
            body: Some(body),
            ..Self::get(url)
        }
        .with_idempotent(false)
    }

    pub fn with_header(mut self, name: &str, value: &str) -> Self {
        self.headers.insert(name.to_string(), value.to_string());
        self
    }

    pub fn with_idempotent(mut self, v: bool) -> Self {
        self.idempotent = v;
        self
    }

    pub fn with_timeout(mut self, ms: u64) -> Self {
        self.timeout_ms = Some(ms);
        self
    }
}
