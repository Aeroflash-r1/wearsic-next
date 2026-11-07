# Wearsic Next

A standalone, battery-first Wear OS music streaming application.

**One APK. One watch. One embedded Rust engine. No external backend.**

This is a new, independent project built on the lessons of the existing
Wearsic application (`https://github.com/Aeroflash-r1/wearsic`). The original
repository is a reference implementation and is **never modified** by this
project.

## Vision

- User installs a single Wear OS APK on the watch.
- User opens the app, searches, plays. Done.
- No companion phone app, no Termux, no Ktor server, no tunnel, no Tailscale,
  no Cloudflare, no localhost HTTP server, no API key configuration.

## Architecture

| Layer             | Tech                                                       |
|-------------------|------------------------------------------------------------|
| UI + Android      | Kotlin, Jetpack Compose for Wear OS, Media3 ExoPlayer, Room |
| Embedded engine    | Rust (arm64-v8a), compiled to `libwearsic_engine.so`, JNI  |
| Communication      | Typed JNI bridge — **no localhost HTTP, no sockets/IPC**   |

The Rust engine replaces the former external server's responsibilities:
YouTube Music InnerTube search, stream resolution (InnerTube primary,
NewPipe fallback via a Kotlin adapter), metadata, lyrics (LRCLIB + LRC
parsing), bounded caching, networking, retries, and diagnostics.

## Development phases (see docs/)

1. **Phase 1 — Repository audit** (current): `docs/EXISTING_WEARSIC_AUDIT.md`,
   `docs/FEATURE_PARITY_MATRIX.md`, `docs/ARCHITECTURE.md`.
2. Phase 2 — Project foundation (Wear app + Rust workspace + JNI loading).
3. Phase 3 — Rust networking engine.
4. Phase 4 — Music discovery (InnerTube search + UI).
5. Phase 5 — Stream resolution + extraction (with NewPipe fallback adapter).
6. Phase 6 — Direct Media3 playback.
7. Phase 7 — Lyrics + library.
8. Phase 8 — UI refinement for round display + rotary input.
9. Phase 9 — Performance optimisation (CPU/RAM/network/battery/thermal).
10. Phase 10 — Release readiness (verified clean install, ARM64 APK, docs).

## Hard constraints

- See `docs/ARCHITECTURE.md` and `#/DOCS/ARCHITECTURE_DECISIONS.md` for the
  full engineering-decision log and restrictions.
- Battery efficiency is the highest priority and drives every architectural
  decision.

## Environment / toolchain (Phase 2 prerequisites)

- Android SDK at `/root/android-sdk` (build-tools, platforms, cmdline-tools).
  **NDK not yet installed** — requires `sdkmanager "ndk;27.x"`.
- Java 17 (OpenJDK) present.
- **Rust toolchain not yet installed** — requires `rustup` with target
  `aarch64-linux-android` plus `cargo-ndk`.
- Network access to rustup/crates.io confirmed.

## Docs index

- `docs/EXISTING_WEARSIC_AUDIT.md` — audit of the original Wearsic repo.
- `docs/FEATURE_PARITY_MATRIX.md` — every original feature → Next status.
- `docs/ARCHITECTURE.md` — target architecture for Wearsic Next.
- `docs/ARCHITECTURE_DECISIONS.md` — decisions + rationale (the "why").
- `docs/PERFORMANCE_BENCHMARKS.md` — measured results (added in Phase 9).
