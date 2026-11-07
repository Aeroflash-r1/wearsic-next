# Phase 5 Test Report

Date: 2026-11-07. Environment: aarch64 Linux host, JDK 17, Gradle 9.3.1, Rust 1.99.1.

## Summary

| Suite | Passed | Failed |
|---|---|---|
| Rust `stream-resolver` unit | 11 | 0 |
| Rust `innertube` unit (Phase 4) | 6 | 0 |
| Rust `ytmusic` unit (Phase 4) | 9 | 0 |
| Rust `network` unit + integration (Phase 3) | 28 | 0 |
| Rust `engine-core` (Phase 2) | 7 | 0 |
| Kotlin `RustEngineContractTest` | 3 | 0 |
| Kotlin `EngineProbeHostSmokeTest` (Phase 2) | 7 | 0 |
| Kotlin `EngineProbeNetworkSmokeTest` (Phase 3) | 6 | 0 |
| Kotlin `EngineProbeLiveSearchTest` (live) | 3 | 0 |
| Kotlin `EngineProbeLiveResolveTest` (**live**) | 2 | 0 |
| **Total (automated, non-live)** | **77** | **0** |
| **Total (incl. live)** | **82** | **0** |

## Phase 5 coverage

Rust unit:
- Player response parsing: audio formats (mime/bitrate/sampleRate/channels/
  contentLength), missing streamingData, playability OK/LOGIN_REQUIRED.
- Format selection: AAC-preference, video/combined rejection, MIME rejection,
  empty-list error, Opus fallback.
- Validation: videoId shape, URL https-only, MIME allowlist.

Live (network-dependent, separate suite):
- `live_resolve_returns_playable_stream`: real video id → descriptor with
  https URL, audio MIME, source `innertube:IOS`, content length.
- `live_resolve_bad_id_is_error`: invalid id → kind `invalid_track`.
- Host live probe: resolution in ~305 ms; cache hit ~15 µs; selected itag
  140 (AAC-LC 44.1kHz stereo 130996 bps, 4.18 MB).

## Build

- `cargo build` + workspace tests: ✅
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: ✅
- APK includes `lib/arm64-v8a/libwearsic_engine.so`.

## Limitations

- No physical Wear OS test (JNI verified on real JVM; ARM64 ELF verified).
- Live tests are network-dependent and excluded from the always-green gate.
- Duration (`duration_ms`) is not populated from the player response (it is
  carried by the search result instead); Phase 6 can merge the two.
- Service may change player response shapes; parser is defensive but may
  need fixture updates.
