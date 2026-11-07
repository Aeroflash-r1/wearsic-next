# Networking API (Phase 3)

## Rust types (`network` crate)

### `Request`

```rust
pub struct Request {
    pub id: Option<u64>,          // assigned by engine
    pub method: HttpMethod,        // get | post
    pub url: String,               // https (or http in tests)
    pub headers: BTreeMap<String, String>,
    pub body: Option<Vec<u8>>,
    pub timeout_ms: Option<u64>,   // per-request override
    pub max_attempts: Option<u32>, // retry cap override
    pub idempotent: bool,          // allow retries for POST
}
```

### `Response`

```rust
pub struct Response {
    pub status: u16,
    pub headers: BTreeMap<String, String>,
    pub body: Vec<u8>,
}
```

Helpers: `text()`, `json::<T>()`, `is_success()`.

### `NetworkError`

```rust
pub struct NetworkError {
    pub kind: NetworkErrorKind, // invalid_url | dns | connect | timeout | tls
                              // | http | body_too_large | invalid_response
                              // | cancelled | shutdown | internal
    pub status: u16,
    pub message: String,
}
```

`is_retryable()` / `is_permanent()` classifiers are public.

### `NetworkConfig` (defaults)

```rust
NetworkConfig {
    connect_timeout_ms: 10_000,
    request_timeout_ms: 25_000,
    max_response_bytes: 1_048_576,
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
```

### Cancellation

```rust
let token = CancellationToken::new();
token.cancel();            // idempotent
token.cancelled().await;   // waiter
token.check() -> Result<(), NetworkError>; // Cancelled if cancelled
```

## JNI JSON contract

### Request JSON (Kotlin `NetworkRequest`)

```json
{
  "method": "get",
  "url": "https://example.com/path?q=1",
  "headers": {},
  "body": null,
  "timeout_ms": null,
  "max_attempts": null,
  "idempotent": true
}
```

### Response envelope

```json
{ "ok": true, "status": 200, "headers": {}, "body": "<utf8 text>" }
```

### Error envelope

```json
{
  "ok": false,
  "error": { "kind": "timeout", "status": 0, "message": "request timed out" }
}
```

`kind` values match the enum above (lowercase).

### Diagnostics JSON

```json
{
  "total_requests": 4, "success_requests": 3, "failed_requests": 1,
  "cancelled_requests": 0, "timeouts": 0, "retries": 0,
  "bytes_received": 1234, "active_requests": 0, "avg_duration_ms": 12,
  "last_error": "http_500", "connections_created": 2, "pool_hits": 1
}
```

## Kotlin API

- `RustEngine.networkInit(): Boolean`
- `RustEngine.networkExecute(NetworkRequest): NetworkResponse` — throws
  `NetworkEngineException(NetworkError)` on failure/cancellation.
- `RustEngine.networkCancel(requestId: Long)`
- `RustEngine.networkDiagnostics(): String`
- `RustEngine.networkShutdown()`

All execute calls are **blocking on the calling thread** — call from a
coroutine on `Dispatchers.IO`, never from the Android main thread.

## Retry policy summary

| Category | Retried? |
|---|---|
| invalid_url / body_too_large / invalid_response | no |
| cancelled / shutdown | no |
| http 4xx (except 429) | no |
| http 429 / 5xx | yes (bounded) |
| connect / dns / tls / timeout | yes (bounded) |
| POST non-idempotent | never retried |
