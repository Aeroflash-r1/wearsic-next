//! Client configuration. Conservative values for a smartwatch: small
//! pools, tight timeouts, bounded retries and bounded bodies.

/// Defaults tuned for Wear OS (low radio activity, bounded memory).
#[derive(Debug, Clone, serde::Serialize, serde::Deserialize)]
#[serde(rename_all = "snake_case", default)]
pub struct NetworkConfig {
    /// Connect timeout.
    pub connect_timeout_ms: u64,
    /// Per-request overall timeout.
    pub request_timeout_ms: u64,
    /// TLS handshake deadline.
    pub tls_timeout_ms: u64,
    /// Max response body bytes (default 1 MiB).
    pub max_response_bytes: usize,
    /// Max redirects followed (0 = none).
    pub max_redirects: u32,
    /// Max concurrent in-flight requests across the whole engine.
    pub max_concurrency: usize,
    /// Max idle keep-alive connections per host.
    pub max_idle_connections: usize,
    /// Idle connection lifetime (keep-alive expiry).
    pub idle_connection_ttl_ms: u64,
    /// Base retry delay (ms) for retryable failures.
    pub retry_base_delay_ms: u64,
    /// Maximum retry delay (ms) after backoff.
    pub retry_max_delay_ms: u64,
    /// Maximum number of attempts (including the first) for retryable
    /// failures. 1 = no retries.
    pub max_attempts: u32,
    /// Whether to include random jitter in backoff.
    pub retry_jitter: bool,
    /// Deduplicate identical in-flight requests (request coalescing).
    pub coalesce_duplicates: bool,
}

impl Default for NetworkConfig {
    fn default() -> Self {
        Self {
            connect_timeout_ms: 10_000,
            request_timeout_ms: 25_000,
            tls_timeout_ms: 8_000,
            max_response_bytes: 1_048_576, // 1 MiB
            max_redirects: 3,
            max_concurrency: 4,
            max_idle_connections: 4,
            idle_connection_ttl_ms: 30_000,
            retry_base_delay_ms: 400,
            retry_max_delay_ms: 5_000,
            max_attempts: 2,
            retry_jitter: true,
            coalesce_duplicates: true,
        }
    }
}

impl NetworkConfig {
    pub fn retry_attempts(&self) -> u32 {
        self.max_attempts.max(1)
    }
}
