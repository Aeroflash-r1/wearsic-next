# Phase 6 Test Report

Date: 2026-11-07. Environment: aarch64 Linux host, JDK 17, Gradle 9.3.1, Rust 1.99.1.

## Summary

| Suite | Passed | Failed |
|---|---|---|
| Rust `stream-resolver` unit | 11 | 0 |
| Rust `innertube` + `ytmusic` + `network` + `engine-core` | 50 | 0 |
| Kotlin `PlaybackModelsTest` (new, pure JVM) | 5 | 0 |
| Kotlin `RustEngineContractTest` | 3 | 0 |
| Kotlin `EngineProbeHostSmokeTest` | 7 | 0 |
| Kotlin `EngineProbeNetworkSmokeTest` | 6 | 0 |
| Kotlin `EngineProbeLiveSearchTest` (live) | 3 | 0 |
| Kotlin `EngineProbeLiveResolveTest` (live) | 2 | 0 |
| **Total** | **87** | **0** |

## Phase 6 coverage

- Playback model merge (`PlaybackItem.from`): search metadata wins; stream
  descriptor attached; artwork/duration behavior.
- State defaults + `copy` semantics.
- Full Phase 2–5 regression intact (Rust + Kotlin).

## Media3 / controller

- Media3 classes are Android-bound; JVM unit tests cover the pure logic
  (models, merge, state). The service/controller compile for ARM64 and are
  exercised via the APK build; **runtime playback requires a device** (see
  limitations).

## Build

- `cargo` workspace tests: ✅ (61 Rust tests across crates)
- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: ✅
- APK: 30.7 MB debug, `lib/arm64-v8a/libwearsic_engine.so` (2.93 MB).

## Limitations (honest)

- **No physical watch**: real audio playback, audio focus, notification and
  system media controls are NOT device-verified. Media3 wiring compiles and
  follows documented patterns, but "Galaxy Watch plays audio" is not claimed.
- Live tests (search/resolve) are network-dependent and separate from the
  always-green gate.
