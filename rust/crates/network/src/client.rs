//! The central HTTP client.
//!
//! One shared `hyper` client with a bounded connection pool, rustls TLS,
//! per-request timeouts, bounded bodies, bounded redirects, conservative
//! retries and request coalescing.

use std::collections::HashMap;
use std::sync::Arc;
use std::time::Duration;

use bytes::Bytes;
use http::header::{CONTENT_LENGTH, CONTENT_TYPE, LOCATION};
use http::Request as HyperRequest;
use hyper::body::Incoming;
use hyper::Method as HttpMethod;
use hyper_rustls::HttpsConnector;
use hyper_util::client::legacy::connect::HttpConnector;
use hyper_util::client::legacy::Client as LegacyClient;
use hyper_util::rt::TokioExecutor;
use http_body_util::{BodyExt, Full, Limited};
use tokio::sync::watch;
use tokio::sync::Semaphore;
use webpki_roots::TLS_SERVER_ROOTS;

use crate::cancellation::CancellationToken;
use crate::config::NetworkConfig;
use crate::diagnostics::Diagnostics;
use crate::error::{NetworkError, NetworkErrorKind};
use crate::request::{HttpMethod as ReqMethod, Request};
use crate::response::Response;
use crate::retry;

type BoxBody = http_body_util::combinators::BoxBody<Bytes, std::io::Error>;
type Connector = HttpsConnector<HttpConnector>;

/// Hard ceiling for a single response, even with a per-request override.
/// Browse pages legitimately exceed the 1 MiB default; nothing should ever
/// buffer more than this on a watch.
const ABSOLUTE_MAX_RESPONSE_BYTES: usize = 6 * 1024 * 1024;

fn upgrade_connector() -> Connector {
    let mut http = HttpConnector::new();
    http.enforce_http(false);
    http.set_connect_timeout(Some(Duration::from_millis(10_000)));
    hyper_rustls::HttpsConnectorBuilder::new()
        .with_provider_and_webpki_roots(rustls::crypto::ring::default_provider())
        .expect("ring provider install")
        .https_or_http()
        .enable_http1()
        .wrap_connector(http)
}

/// Shared result of an in-flight coalesced request.
struct Coalesced {
    tx: watch::Sender<Option<Result<Response, NetworkError>>>,
    rx: watch::Receiver<Option<Result<Response, NetworkError>>>,
}

/// Shared, cheap-to-clone client handle.
#[derive(Clone)]
pub struct HttpClient {
    inner: LegacyClient<Connector, BoxBody>,
    config: Arc<NetworkConfig>,
    diag: Arc<Diagnostics>,
    semaphore: Arc<Semaphore>,
    coalescer: Arc<tokio::sync::Mutex<HashMap<String, Arc<Coalesced>>>>,
}

impl HttpClient {
    pub fn new(config: NetworkConfig, diag: Arc<Diagnostics>) -> Self {
        let config = Arc::new(config);
        let semaphore = Arc::new(Semaphore::new(config.max_concurrency));

        let inner = LegacyClient::builder(TokioExecutor::new())
            .pool_max_idle_per_host(config.max_idle_connections)
            .pool_idle_timeout(Duration::from_millis(config.idle_connection_ttl_ms))
            .build(upgrade_connector());

        Self {
            inner,
            config,
            diag,
            semaphore,
            coalescer: Arc::new(tokio::sync::Mutex::new(HashMap::new())),
        }
    }

    /// Executes one request with retries/redirects/timeouts/cancellation.
    pub async fn execute(&self, req: Request, token: &CancellationToken) -> Result<Response, NetworkError> {
        token.check()?;

        // Coalesce identical in-flight GETs into one network transaction.
        if self.config.coalesce_duplicates && req.method == ReqMethod::Get {
            let key = format!("{:?} {}", req.method, req.url);
            let (shared, am_leader) = {
                let mut map = self.coalescer.lock().await;
                if let Some(existing) = map.get(&key) {
                    (existing.clone(), false)
                } else {
                    let (tx, rx) = watch::channel(None);
                    let shared = Arc::new(Coalesced { tx, rx });
                    map.insert(key.clone(), shared.clone());
                    (shared, true)
                }
            };

            let result = if am_leader {
                // Leader: run the request, then broadcast + self-remove.
                let result = {
                    let _permit = self
                        .semaphore
                        .acquire()
                        .await
                        .map_err(|_| {
                            NetworkError::new(NetworkErrorKind::Internal, "semaphore closed")
                        })?;
                    token.check()?;
                    self.execute_with_retries(&req, token).await
                };
                let _ = shared.tx.send(Some(result.clone()));
                let mut map = self.coalescer.lock().await;
                if let Some(entry) = map.get(&key) {
                    if Arc::ptr_eq(entry, &shared) {
                        map.remove(&key);
                    }
                }
                drop(map);
                result
            } else {
                self.await_coalesced(&shared, token).await
            };
            return result;
        }

        let _permit = self
            .semaphore
            .acquire()
            .await
            .map_err(|_| NetworkError::new(NetworkErrorKind::Internal, "semaphore closed"))?;
        token.check()?;

        self.execute_with_retries(&req, token).await
    }

    /// One request, full retry policy.
    async fn execute_with_retries(
        &self,
        req: &Request,
        token: &CancellationToken,
    ) -> Result<Response, NetworkError> {
        let _guard = self.diag.request_started();
        let attempts = req.max_attempts.unwrap_or(self.config.retry_attempts());
        let timeout = Duration::from_millis(req.timeout_ms.unwrap_or(self.config.request_timeout_ms));

        let mut attempt: u32 = 0;
        let mut last_err = NetworkError::new(NetworkErrorKind::Internal, "no attempt made");

        loop {
            token.check()?;
            match tokio::time::timeout(timeout, self.try_once(req, token)).await {
                Err(_) => {
                    self.diag.record_timeout();
                    last_err = NetworkError::new(NetworkErrorKind::Timeout, "request timed out");
                }
                Ok(Err(e)) => last_err = e,
                Ok(Ok(response)) => {
                    if response.is_success() {
                        self.diag.record_success(response.body.len());
                        return Ok(response);
                    }
                    last_err = NetworkError::http(response.status);
                    if !last_err.is_retryable() {
                        self.diag.record_failure(&format!("http_{}", response.status));
                        return Err(last_err);
                    }
                }
            }

            attempt += 1;
            if !retry::should_retry(&last_err, attempt, attempts) {
                if last_err.kind == NetworkErrorKind::Timeout {
                    self.diag.record_failure("timeout");
                } else {
                    self.diag.record_failure(&format!("{:?}", last_err.kind));
                }
                return Err(last_err);
            }
            self.diag.record_retry();
            let delay = retry::backoff_delay_ms(&self.config, attempt);
            tokio::time::sleep(Duration::from_millis(delay)).await;
        }
    }

    /// One attempt: connect, redirect-loop, validate, read bounded body.
    async fn try_once(
        &self,
        req: &Request,
        token: &CancellationToken,
    ) -> Result<Response, NetworkError> {
        let mut url = url::Url::parse(&req.url)
            .map_err(|_| NetworkError::new(NetworkErrorKind::InvalidUrl, "malformed URL"))?;
        if url.scheme() != "https" && url.scheme() != "http" {
            return Err(NetworkError::new(
                NetworkErrorKind::InvalidUrl,
                format!("unsupported scheme '{}'", url.scheme()),
            ));
        }

        let original_scheme = url.scheme().to_string();
        let original_host = url.host_str().map(str::to_string);

        // Per-request cap, clamped to a hard ceiling so an override can never
        // turn into unbounded memory on the watch.
        let body_limit = req
            .max_response_bytes
            .unwrap_or(self.config.max_response_bytes)
            .clamp(1, ABSOLUTE_MAX_RESPONSE_BYTES);

        let mut redirects = 0u32;
        loop {
            token.check()?;
            let method = match req.method {
                ReqMethod::Get => HttpMethod::GET,
                ReqMethod::Post => HttpMethod::POST,
            };

            let mut builder = HyperRequest::builder().method(method).uri(url.as_str());
            for (name, value) in &req.headers {
                builder = builder.header(name.as_str(), value.as_str());
            }
            if let Some(len) = req.body.as_ref().map(|b| b.len()) {
                builder = builder.header(CONTENT_LENGTH, len);
            }
            if req.body.is_some() && !req.headers.contains_key("content-type") {
                builder = builder.header(CONTENT_TYPE, "application/json");
            }
            let body = req.body.clone().unwrap_or_default();
            let body = Full::new(Bytes::from(body)).map_err(|e| {
                std::io::Error::new(std::io::ErrorKind::Other, e.to_string())
            });
            let hyper_req = builder
                .body(BoxBody::new(body))
                .map_err(|e| NetworkError::new(NetworkErrorKind::Internal, format!("build: {e}")))?;

            let resp = self.inner.request(hyper_req).await.map_err(|e| self.map_transport_error(e))?;

            let status = resp.status();
            let headers = resp.headers().clone();

            if status.is_redirection() {
                if redirects >= self.config.max_redirects {
                    return Err(NetworkError::new(
                        NetworkErrorKind::InvalidResponse,
                        "too many redirects",
                    ));
                }
                let loc = headers
                    .get(LOCATION)
                    .and_then(|v| v.to_str().ok())
                    .ok_or_else(|| {
                        NetworkError::new(NetworkErrorKind::InvalidResponse, "redirect without Location")
                    })?;
                let next = url::Url::parse(loc)
                    .or_else(|_| url.join(loc))
                    .map_err(|_| NetworkError::new(NetworkErrorKind::InvalidUrl, "bad redirect"))?;
                // Refuse cross-origin redirects (scheme or host change).
                let same_origin = next.scheme() == original_scheme
                    && next.host_str().map(str::to_string) == original_host;
                if !same_origin {
                    return Err(NetworkError::new(
                        NetworkErrorKind::InvalidResponse,
                        "redirect to different origin refused",
                    ));
                }
                url = next;
                redirects += 1;
                continue;
            }

            let body = self.read_bounded(resp, body_limit).await?;
            let mut map = std::collections::BTreeMap::new();
            for (k, v) in headers.iter() {
                let ks = k.as_str();
                if let Ok(vs) = v.to_str() {
                    map.insert(ks.to_string(), vs.to_string());
                }
            }
            return Ok(Response { status: status.as_u16(), headers: map, body });
        }
    }

    async fn read_bounded(
        &self,
        resp: hyper::Response<Incoming>,
        limit: usize,
    ) -> Result<Vec<u8>, NetworkError> {
        let declared = resp
            .headers()
            .get(CONTENT_LENGTH)
            .and_then(|v| v.to_str().ok())
            .and_then(|v| v.parse::<usize>().ok());
        if let Some(len) = declared {
            if len > limit {
                resp.into_body().collect().await.ok();
                return Err(NetworkError::new(
                    NetworkErrorKind::BodyTooLarge,
                    format!("response {len} bytes exceeds limit"),
                ));
            }
        }
        let limited = Limited::new(resp.into_body(), limit);
        let collected = limited.collect().await.map_err(|e| {
            if e.to_string().contains("limit") {
                NetworkError::new(NetworkErrorKind::BodyTooLarge, "response body too large")
            } else {
                NetworkError::new(NetworkErrorKind::Connect, format!("body read: {e}"))
            }
        })?;
        Ok(collected.to_bytes().to_vec())
    }

    fn map_transport_error(&self, e: hyper_util::client::legacy::Error) -> NetworkError {
        let text = e.to_string().to_lowercase();
        let kind = if text.contains("dns") || text.contains("resolution") {
            NetworkErrorKind::Dns
        } else if text.contains("certificate") || text.contains("tls") || text.contains("handshake") {
            NetworkErrorKind::Tls
        } else if text.contains("timed out") || text.contains("timeout") {
            NetworkErrorKind::Timeout
        } else {
            NetworkErrorKind::Connect
        };
        self.diag.record_failure(&format!("{kind:?}"));
        NetworkError::new(kind, format!("transport: {e}"))
    }

    // --- Coalescing ---

    /// Waits for the leader's result. The leader broadcasts via the watch
    /// channel; waiters never make a network call themselves.
    async fn await_coalesced(
        &self,
        shared: &Arc<Coalesced>,
        token: &CancellationToken,
    ) -> Result<Response, NetworkError> {
        let mut rx = shared.rx.clone();
        loop {
            token.check()?;
            if let Some(value) = rx.borrow().clone() {
                return value;
            }
            if rx.changed().await.is_err() {
                return Err(NetworkError::new(
                    NetworkErrorKind::Internal,
                    "coalesced leader dropped",
                ));
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn client_can_be_constructed() {
        let cfg = NetworkConfig::default();
        let diag = Arc::new(Diagnostics::new());
        let client = HttpClient::new(cfg, diag);
        let _ = client;
    }
}
