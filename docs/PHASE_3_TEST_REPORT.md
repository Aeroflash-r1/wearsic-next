# Phase 3 Test Report

Date: 2026-11-07
Environment: aarch64 Linux host, JDK 17, Gradle 9.3.1, Rust 1.99.1,
NDK 27.2.12479018.

## Summary

| Suite | Passed | Failed |
|---|---|---|
| Rust `engine-core` | 7 | 0 |
| Rust `network` unit tests | 11 | 0 |
| Rust `network` integration (mock HTTP server) | 14 | 0 |
| Rust `network` runtime/lifecycle extra tests | 3 | 0 |
| Kotlin `RustEngineContractTest` | 3 | 0 |
| Kotlin `EngineProbeHostSmokeTest` (Phase 2 JNI) | 7 | 0 |
| Kotlin `EngineProbeNetworkSmokeTest` (Phase 3 JNI + Java mock) | 6 | 0 |
| **Total** | **51** | **0** |

## Coverage

### Rust unit tests
- Client configuration defaults, retry/backoff math, error classification
  (retryable vs permanent), attempt limits.
- CancellationToken: initial state, cancel, idempotency, waiter wake.
- Diagnostics counters + JSON, bounded history.
- Runtime: `block_on`/`spawn` from an external thread (JNI simulation);
  timer construction inside the runtime context.

### Rust integration tests (local mock HTTP server, test-only)
1. Successful GET (status/JSON decode)
2. Successful POST
3. HTTP 404 → permanent error
4. HTTP 429 → retried, then success
5. HTTP 500 → retried to limit, then error
6. Malformed JSON → invalid_response
7. Empty response (204)
8. Oversized response → body_too_large
9. Same-origin redirect followed
10. Cross-origin redirect refused
11. Cancellation stops an in-flight request
12. 8 concurrent requests all succeed (bounded concurrency)
13. Duplicate in-flight GETs coalesced to one network transaction
14. Connection failure classified

### Kotlin JNI tests (host-built .so + Java mock server)
- Structured response envelope for a successful GET
- 404/500 error envelopes; retry count observed (500 → 2 hits)
- Invalid URL rejected without network access
- Diagnostics JSON available after traffic
- Shutdown then re-init safe; requests work again afterwards
- Phase 2 contract still intact (version/status/panic barrier, 7/7)

## Build results

- `cargo build` (host): ✅
- `cargo build --target aarch64-linux-android --release` (via Gradle
  `buildRustEngine`): ✅ — `libwearsic_engine.so` ELF64 AArch64 with
  `JNI_OnLoad` exported (verified with readelf)
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: ✅
  `BUILD SUCCESSFUL`
- APK contains `lib/arm64-v8a/libwearsic_engine.so` (2,690,632 bytes)

## Known limitations (honest)

- **Physical Wear OS device: not tested.** All JNI verification runs on a
  real JVM against the host-built `.so`, and the ARM64 `.so` is inspected as
  a valid ELF64 AArch64 shared library. Loading/running on actual Galaxy
  Watch 7 hardware remains for a later phase/hardware availability.
- TLS is verified against `webpki-roots` in the **host** tests
  (certificate validation code path is identical in the ARM64 build, but no
  live-HTTPS-on-watch test exists yet).
- Live internet (YouTube/YouTube Music/LRCLIB) is intentionally not
  exercised — Phase 3 is the networking foundation; Phase 4/5 add the
  service calls.
- APK is debug (R8/shrink not yet enabled); release APK size will differ.
