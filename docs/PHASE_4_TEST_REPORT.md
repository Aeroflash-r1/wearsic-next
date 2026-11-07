# Phase 4 Test Report

Date: 2026-11-07
Environment: aarch64 Linux host, JDK 17, Gradle 9.3.1, Rust 1.99.1.

## Summary

| Suite | Passed | Failed |
|---|---|---|
| Rust `innertube` unit | 6 | 0 |
| Rust `ytmusic` unit (mapper/models/service) | 9 | 0 |
| Rust `network` unit + integration (Phase 3, unchanged) | 28 | 0 |
| Rust `engine-core` (Phase 2, unchanged) | 7 | 0 |
| Kotlin `RustEngineContractTest` | 3 | 0 |
| Kotlin `EngineProbeHostSmokeTest` (Phase 2 JNI) | 7 | 0 |
| Kotlin `EngineProbeNetworkSmokeTest` (Phase 3 JNI) | 6 | 0 |
| Kotlin `EngineProbeLiveSearchTest` (**live service, network-dependent**) | 3 | 0 |
| **Total (automated, excluding live)** | **66** | **0** |
| **Total (incl. live)** | **69** | **0** |

## Phase 4-specific coverage

Rust unit tests (fixtures built from real observed response shapes):
- Track row (watchEndpoint) → Track incl. artist/duration/thumbnail
- Album row (renderer-root browseEndpoint + type label subtitle) → Album
- Artist row → Artist; Playlist row → Playlist
- User-profile rows skipped; malformed rows → None (never panic)
- Duration parsing (M:SS, H:MM:SS, invalid)
- Continuation token extraction (deep, length-guarded)
- Filter param existence + request serialization (clientName/hl/gl present)
- Cache hit + bounded eviction

Kotlin live JNI (real YouTube Music service):
- `live_search_returns_tracks`: "coldplay yellow" → tracks with videoId/title
- `live_search_mixed_types`: "coldplay" → typed mix observed
  track=14, artist=6, album=3, playlist=6
- `live_search_handles_empty_query`: graceful envelope

## Build results

- `cargo build` + workspace tests: ✅
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: ✅
  `BUILD SUCCESSFUL`
- APK contains `lib/arm64-v8a/libwearsic_engine.so` (2,842,816 bytes)

## Known limitations (honest)

- **No physical Wear OS device test yet** (same as Phase 3): JNI verified on
  a real JVM; ARM64 .so verified as ELF64 AArch64.
- Live tests are explicitly network-dependent and can fail if the service
  changes or network is unavailable — they are not part of the always-green
  gate.
- Search results depend on YouTube Music's live response shapes; the mapper
  is defensive but a major service layout change may require updating
  fixtures.
- Suggestions, related/radio and recommendations are planned, not yet
  implemented.
