# Wearsic Next

A standalone, battery-first Wear OS music streaming application.

**One APK. One watch. One embedded Rust engine. No external backend.**

> **Status: Phase 6 complete, plus UI/functionality and engine-browse rounds.**
> Search, album/playlist/artist browse, stream resolution and Media3 playback
> are wired end to end through the embedded Rust engine; 40/40 JVM tests pass
> (including live InnerTube search/resolve/browse) and the Rust workspace tests
> pass. The UI ports the stable Wearsic 1.9.48 screens for the 44 mm round face
> and adds engine-backed browse pages (cover/header, play-all, tracks, artist
> albums, continuation/load-more).
>
> The playback core drives its own queue: shuffle and repeat actually reorder /
> wrap it, tapping a search result queues the visible list or opens a browse
> page, the next track is prefetched for instant skips, and the position ticker
> parks while the screen is off.
>
> Still missing vs. original (and not faked): lyrics; downloads/offline playback
> and storage management (original was server-dependent); local playlist
> create/list/detail management; recommendations/radio (no real engine-backed
> source verified — live radio/browse params were unusable, so no invented
> data). Wear tile is out of scope for this port. See
> `docs/FEATURE_PARITY_MATRIX.md` for per-feature status.

## Layout

```
wearsic-next/
├── app/                    Android Wear OS app (Kotlin + Compose for Wear OS)
├── rust/                   Rust workspace (engine-core + jni-bridge)
├── docs/                   Phase deliverables + build/interface docs
├── gradle/                 wrapper + version catalog
├── settings.gradle.kts
└── build.gradle.kts
```

## Build (full APK with embedded engine)

```sh
./gradlew :app:assembleDebug
```

See `docs/BUILD_INSTRUCTIONS.md` for prerequisites and command details.

## Release signing

Credentials are never stored in the build script or the repository. Each value
is read from the environment first, then from `keystore.properties` in the
project root (gitignored):

```properties
storeFile=wearsic-next.jks
storePassword=…
keyAlias=wearsic
keyPassword=…
```

Equivalent environment variables: `WEARSIC_KEYSTORE_FILE`,
`WEARSIC_KEYSTORE_PASSWORD`, `WEARSIC_KEY_ALIAS`, `WEARSIC_KEY_PASSWORD`.
If none are configured, `assembleRelease` still runs (R8 + lint) but produces
an unsigned APK.

## Docs

- `docs/EXISTING_WEARSIC_AUDIT.md` — Phase 1 audit of the original Wearsic.
- `docs/FEATURE_PARITY_MATRIX.md` — feature parity plan.
- `docs/ARCHITECTURE.md` — target architecture.
- `docs/ARCHITECTURE_DECISIONS.md` — decision log.
- `docs/PROJECT_FOUNDATION.md` — Phase 2 foundation notes.
- `docs/JNI_INTERFACE.md` — the Kotlin↔Rust JNI contract.
- `docs/BUILD_INSTRUCTIONS.md` — exact build commands.
- `docs/PHASE_2_TEST_REPORT.md` — Phase 2 verification truth.
- `docs/PHASE_3_NETWORK_ARCHITECTURE.md` … `docs/PHASE_6_TEST_REPORT.md` —
  per-phase architecture, performance and verification notes.
- `docs/MEDIA3_INTEGRATION.md` — playback service/controller wiring.
