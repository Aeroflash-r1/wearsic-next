# Phase 2 Test Report

Date: 2026-11-07
Environment: aarch64 Linux host, JDK 17 (toolchain 21), Gradle 9.3.1,
Android SDK platform 36.1, NDK 27.2.12479018, Rust 1.99.1.

## What was verified

| Item | Result |
|---|---|
| Rust engine-core unit tests | ✅ 7/7 passed |
| Rust workspace build (host, debug) | ✅ |
| Rust ARM64 build (`aarch64-linux-android`, release) | ✅ `libwearsic_engine.so` ELF64 AArch64 DYN |
| `JNI_OnLoad` exported GLOBAL | ✅ (readelf) |
| Kotlin unit tests | ✅ 10/10 passed |
| — `EngineProbeHostSmokeTest` (JVM JNI, host .so) | ✅ 7/7 |
| — `RustEngineContractTest` (pure Kotlin) | ✅ 3/3 |
| APK assembly | ✅ `BUILD SUCCESSFUL` |
| APK contains embedded engine | ✅ `lib/arm64-v8a/libwearsic_engine.so` (307,256 bytes) |
| Engine init/shutdown/re-init/idempotency | ✅ (host JNI tests) |
| Panic never crosses JNI | ✅ (`probePanicBarrier`, observed contained panic) |
| No main-thread blocking | ✅ UI loads engine in LaunchedEffect (Default dispatcher) |

## Host JNI smoke test details (real JVM ↔ Rust)

`EngineProbeHostSmokeTest` loads the **host-built** engine
(`rust/target/debug/libwearsic_engine.so`) into the test JVM and exercises
the exact native method table registered by `JNI_OnLoad`:

1. initialize returns true
2. version == "0.2.0"
3. status is compact JSON with running phase
4. init is idempotent
5. shutdown then re-init works (idempotent shutdown too)
6. intentional panic is contained (VM survives, engine still usable)
7. repeated init/shutdown sequences are stable

## Known limitations (NOT verified)

- **On-device verification: not performed.** No physical Galaxy Watch 7 was
  available/connected during this phase. Loading `System.loadLibrary` and the
  full JNI round-trip are verified against a real JVM on the host, and the
  ARM64 `.so` is verified as valid ELF64 AArch64 with the correct export; the
  actual on-watch load + UI render remains to be verified on hardware
  (Phase 10 / when a watch is reachable).
- aarch64 host workaround: NDK's `llvm-strip`/`clang` are x86_64-only; the
  engine links with Rust's `rust-lld` + NDK sysroot, and AGP is told to keep
  native debug symbols (Rust release already strips). On x86_64 hosts the
  standard `cargo-ndk` path is expected to work unchanged.
- `assembleRelease` (R8/shrink) has not been exercised yet; Phase 2 targets
  the debug APK.
- The `minified` variant approach from the original repo was not
  re-implemented.

## Commands that produced the artifacts

```sh
# Rust workspace (host + ARM64)
cd rust
cargo build                                   # host libwearsic_engine.so
cargo test                                    # 7/7 engine-core tests
export ANDROID_NDK_HOME=/root/android-sdk/ndk/27.2.12479018
RUSTFLAGS="... (rust-lld + NDK sysroot recipe, see BUILD_INSTRUCTIONS) ..." \
  cargo build --target aarch64-linux-android --release

# Android
cd ..
./gradlew :app:testDebugUnitTest :app:assembleDebug
# -> BUILD SUCCESSFUL; tests 10/10; APK bundles lib/arm64-v8a/libwearsic_engine.so
```
