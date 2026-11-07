# Building Wearsic Next

Phase 2 build instructions.

## Prerequisites

- Linux (the CI/host images here are aarch64; see notes below).
- JDK 17+ (tested with OpenJDK 17).
- Android SDK with:
  - platform `android-36.1`
  - build-tools 36.x
  - NDK `27.2.12479018` installed at `$ANDROID_HOME/ndk/27.2.12479018`
- Rust toolchain:
  - `rustup target add aarch64-linux-android`
  - host linker `rust-lld` ships with the toolchain
  - `cargo-ndk` is *not* required (see "aarch64 host note" below)

## One-shot commands

```sh
# 1. Build the full debug APK with embedded ARM64 Rust engine.
./gradlew :app:assembleDebug

# 2. Build only the Rust engine (ARM64, release profile).
#     (Also invoked automatically as a dependency of assembleDebug via the
#      buildRustEngine task.)
./gradlew :app:buildRustEngine

# 3. Host-side verification (no Android device):
#     a. Build the host Rust engine for the JVM JNI smoke tests.
         ( cd rust && cargo build )
#     b. Run Kotlin unit tests (includes engine contract + host JNI smoke).
./gradlew :app:testDebugUnitTest
```

## How the Rust engine is produced

Gradle runs (task `buildRustEngine`):

```sh
cd rust
RUSTFLAGS="... (see below) ..." cargo build --target aarch64-linux-android --release
```

The resulting `rust/target/aarch64-linux-android/release/libwearsic_engine.so`
is copied (via `jniLibs.srcDir`) into the APK at
`lib/arm64-v8a/libwearsic_engine.so`.

### aarch64 host note

On aarch64 Linux hosts the NDK ships only an x86_64 `clang`, so
`cargo-ndk` cannot drive linking. Because the engine is pure Rust, linking is
done with Rust's own `rust-lld` plus the NDK sysroot for Android libc:

```sh
RUSTFLAGS="-C linker <toolchain>/lib/rustlib/aarch64-unknown-linux-gnu/bin/rust-lld \
  -C link-arg=--sysroot=<NDK>/toolchains/llvm/prebuilt/linux-x86_64/sysroot \
  -C link-arg=-L<NDK>/.../sysroot/usr/lib/aarch64-linux-android/26 \
  -C link-arg=-L<HOME>/.wearsic-link-libs"
```

An empty `libunwind.a` archive must exist in the extra `-L` dir (Rust's own
`libunwind` rlib provides the actual symbols; the empty archive satisfies the
`-lunwind` in rustc's link line). The Gradle task sets these up
automatically; `docs/PHASE_2_TEST_REPORT.md` records the exact verified
command lines.

On x86_64 hosts `cargo-ndk` remains the standard approach and is documented
as the reference path; the Gradle task currently encodes the aarch64 recipe.

## Verified artifacts (Phase 2)

- `ap/.../target/aarch64-linux-android/release/libwearsic_engine.so`
  ELF64, AArch64, DYN, exports `JNI_OnLoad` (GLOBAL).
- APK `app/build/outputs/apk/debug/app-debug.apk` includes
  `lib/arm64-v8a/libwearsic_engine.so`.
