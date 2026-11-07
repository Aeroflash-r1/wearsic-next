# Phase 2 — Project Foundation

Status: **implemented** (commit will follow this doc).

## What was built

- **Wear OS app skeleton** (`app/`): Kotlin, Jetpack Compose for Wear OS,
  M3, minSdk 26 / target 36, namespace `com.wearsic.next`.
  - `MainActivity` loads the native engine (errors surfaced, not crashes).
  - `WearsicNextApp` — minimal round-screen UI: Wearsic branding, engine
    loaded/version status card, diagnostics JSON summary, retry button.
  - Media3 dependencies declared (no playback yet).
- **Rust workspace** (`rust/`): `engine-core` + `jni-bridge`.
  - `engine-core`: dependency-free engine (lifecycle, version, status JSON).
  - `jni-bridge`: `cdylib` = `libwearsic_engine.so`; JNI_OnLoad registers
    method tables for `RustEngine` and `EngineProbe`.
  - Panic barrier on every native entry point.
  - Single process-wide engine behind a Mutex; no threads, no Tokio, no
    sockets, no localhost HTTP.
- **JNI contract**: `initialize / getVersion / getStatus / shutdown`
  (see `docs/JNI_INTERFACE.md`).
- **Build integration**: Gradle task `buildRustEngine` produces the ARM64
  .so and packages it into the APK under `lib/arm64-v8a/`.

## Key decisions honoured (from ARCHITECTURE_DECISIONS.md)

- JNI over localhost HTTP (AD-002) ✅
- Single engine instance, no runtime kept alive (AD-006) ✅ (no async yet)
- Room not needed for foundation; Room/DataStore come with data features.
- No NewPipe / extraction in this phase.
- Battery-conscious: no services, no polling, no background threads.

## Toolchain notes

- NDK 27.2.12479018 installed.
- aarch64 host: NDK clang is x86_64-only → engine links with `rust-lld` +
  NDK sysroot (+ empty libunwind.a). Documented in BUILD_INSTRUCTIONS.
- cargo-ndk present but not required on this host.

## Files

```
app/src/main/java/com/wearsic/next/MainActivity.kt
app/src/main/java/com/wearsic/next/engine/RustEngine.kt
app/src/main/java/com/wearsic/next/engine/EngineProbe.kt
app/src/main/java/com/wearsic/next/ui/WearsicNextApp.kt
rust/crates/engine-core/src/lib.rs
rust/crates/jni-bridge/src/lib.rs
docs/... (this set)
```
