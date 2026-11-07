# Phase 3 — Network Architecture

## Objective

A reusable, lightweight, cancellable, battery-conscious HTTP subsystem that
will power Phase 4 (InnerTube search), Phase 5 (stream resolution) and
future online features. It is a **foundation** — no music search/extraction
is implemented in this phase.

## Stack decision (documented rationale)

| Concern | Choice | Why |
|---|---|---|
| HTTP/1.1 client | `hyper` 1.x + `hyper-util` legacy client | Lean, maintained, controllable pooling |
| TLS | `rustls` (ring provider) via `hyper-rustls` | Pure-Rust roots (webpki-roots), normal cert verification; no NDK/OpenSSL dependency |
| Async runtime | `tokio` multi-threaded, **2 worker threads** | Bounded concurrency; near-zero idle CPU; one shared runtime |
| JSON | `serde`/`serde_json` | Typed request/response models; no unstructured blobs |
| URL | `url` | Parse/validate/redirect handling |

Alternatives considered and rejected:
- `reqwest`: heavier, pulls more transitive deps; hyper gives the same
  behavior with less surface.
- `aws-lc-rs` TLS: needs cmake/go toolchains that are not present on this
  build host; `ring` builds with the CC available.
- One runtime per request: explicitly forbidden; we use one managed runtime.

## Runtime lifecycle

- `NetworkEngine::start(config)` builds a `tokio` runtime
  (`worker_threads(2)`, `enable_all`) and the shared client. Called lazily
  from `network_init()` — never during idle app start unless a feature needs
  it.
- The runtime is reused for all requests. No per-request runtimes.
- Shutdown cancels all registered in-flight tokens and clears the registry;
  the runtime/client are dropped when the engine is dropped (process scope).
- Idle: the engine performs **zero** network activity, no polling, no
  hidden health checks, no background refreshes.

## Client lifecycle & pooling

- One shared `HttpClient` (clone-cheap `Arc`-like handle).
- `hyper-util` pool: `pool_max_idle_per_host(4)`, `pool_idle_timeout(30s)`.
- Connect timeout 10s (HttpConnector); per-request timeout 25s default;
  TLS handshake bounded by the rustls connection attempt (via the request
  timeout).
- Concurrency bounded by `Semaphore(max_concurrency=4)` — conservative for
  a watch radio.
- Connection reuse is automatic via hypool; diagnostics track
  `connections_created` and `pool_hits`.

## Request flow

1. `Request { method, url, headers, body, timeout_ms?, max_attempts?, idempotent? }`
2. Coalescing: identical in-flight GETs share one network transaction
   (leader executes, waiters await the same result via a watch channel).
3. Semaphore permit (bounded concurrency).
4. Retry loop: attempt → validate → redirects (same-origin, ≤3) → bounded
   body read → success/error.
5. Retries only for retryable categories (connect/dns/tls/timeout/429/5xx),
   bounded by `max_attempts` (default 2), exponential backoff + jitter,
   honoring `Retry-After`-style pause via the same backoff budget. POST is
   only retried when explicitly `idempotent` (default false).

## Cancellation

- `CancellationToken` (watch-based): `cancel()` wakes waiters once.
- Every async boundary checks the token; `tokio::time::timeout` wraps each
  attempt; cancelled work stops promptly.
- JNI: `nativeNetworkCancel(requestId)` looks up the in-flight registry and
  cancels the token; the blocked JNI caller returns an error envelope with
  `kind=cancelled`.
- Distinct error category — cancellation is never treated as failure stats
  (separate `cancelled_requests` counter).

## Memory & response limits

- `max_response_bytes` default 1 MiB; Content-Length pre-check + streaming
  `Limited` body read.
- No unbounded buffers; responses are materialized only up to the limit.
- Diagnostics are counters/averages only — no URL/body retention.

## TLS & security

- rustls with `webpki-roots`; normal certificate-chain + hostname
  verification. No disabled checks, no custom trust bypass.
- Redirects: same scheme+host only; cross-origin redirects are refused.
- Sensitive headers are never logged; no response-body logging.

## JNI integration

New native methods on `RustEngine` (mirrored by `EngineProbe`):

| Native | Signature | Notes |
|---|---|---|
| `nativeNetworkInit` | `()Z` | lazily starts engine |
| `nativeNetworkExecute` | `(Ljava/lang/String;)Ljava/lang/String;` | request JSON → response/error JSON; blocks worker thread |
| `nativeNetworkCancel` | `(J)V` | cancels by id |
| `nativeNetworkDiagnostics` | `()Ljava/lang/String;` | JSON counters |
| `nativeNetworkShutdown` | `()V` | cancel-all + clear |

All entry points are panic-contained (`catch_unwind`). Requests are executed
with `tokio::spawn` + `Handle::block_on` on the calling JVM thread — never
the Android main thread (Kotlin dispatches via `Dispatchers.IO`).

## Diagnostics

Counters: total/success/failed/cancelled, timeouts, retries, bytes received,
active, avg duration, last error kind, connections created, pool hits.
`DiagnosticsSnapshot` is serialized to JSON on demand.
