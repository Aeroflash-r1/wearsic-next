# Network Security (Phase 3)

## TLS

- Backend: `rustls` 0.23 with the `ring` provider.
- Roots: `webpki-roots` (Mozilla's set, statically embedded).
- **Certificate-chain verification: ON.** Hostname verification: ON.
- No certificate checks are disabled; no custom trust store is installed;
  no `dangerous()` configuration is used anywhere.
- TLS handshake failure maps to `kind=tls` and is retryable (bounded).

## Redirects

- Redirects are followed only when the target keeps the **same scheme and
  host**. Any origin change is refused with `kind=invalid_response`.
- Redirect count capped at `max_redirects` (3).
- Sensitive headers are not forwarded across redirects (each hop builds a
  fresh request from the original header map; cross-origin is blocked
  outright).

## Response validation

- Response bodies are size-bounded (`max_response_bytes`, default 1 MiB),
  enforced by Content-Length pre-check and streaming `Limited` reader.
- Non-UTF-8 bodies are surfaced as `invalid_response` on `text()`; JSON
  decoding validates shape.
- Malformed JSON/responses never panic; they become structured errors.

## Sensitive-data handling

- Diagnostics retain **no URLs, headers, or bodies** — only counters and a
  last-error *kind*.
- No logging of request/response content anywhere in the engine.
- API keys are not part of the request model; if a caller adds a header, it
  is never recorded.

## Android compatibility

- Pure-Rust TLS avoids OpenSSL/NDK mismatch and works on `arm64-v8a`.
- The engine does not bind sockets, does not listen, and never runs a local
  server. All networking is outbound-only, HTTPS-first (HTTP is permitted
  only for test/localhost, never for production traffic).
