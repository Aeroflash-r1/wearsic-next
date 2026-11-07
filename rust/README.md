# Wearsic Next — Rust Engine

Embedded native engine for the Wear OS app. Compiled to
`libwearsic_engine.so` for `aarch64-linux-android` and packaged inside the
APK.

## Workspace

```
crates/
├── engine-core/     engine lifecycle, version, status (dependency-free)
└── jni-bridge/      JNI exports (cdylib = libwearsic_engine.so)
```

Phase 2 intentionally keeps the workspace minimal. Later phases add
`network`, `innertube`, `ytmusic`, `metadata`, `stream-resolver`, `lyrics`,
`cache`, `storage`, `diagnostics` crates with a clear purpose each.

## Build

Host tests (JVM JNI smoke tests) require a host build:

```sh
cd rust
cargo build                 # host .so at target/debug/libwearsic_engine.so
cargo test                  # engine-core unit + integration tests
```

Android ARM64 build is driven by Gradle (`./gradlew :app:buildRustEngine`),
which invokes:

```sh
cargo ndk -t arm64-v8a --platform 26 -o app/build/rust-jniLibs build --release
```

Requires:
- `rustup target add aarch64-linux-android`
- `cargo install cargo-ndk`
- NDK `27.2.12479018` (see app/build.gradle.kts `ndkVersion`)
- `ANDROID_NDK_HOME` (set by Gradle from the SDK location)

## Safety rules (enforced here)

- No `unsafe` outside `jni-bridge`.
- Every JNI entry point wraps in `catch_unwind`; panics never cross JNI.
- Single process-wide engine behind a `Mutex` — no global mutable state
  beyond it.
- No Tokio, no threads, no busy loops in the engine core (async runtime
  comes in Phase 3, lazily).
