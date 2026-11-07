# Wearsic Next

A standalone, battery-first Wear OS music streaming application.

**One APK. One watch. One embedded Rust engine. No external backend.**

> **Status: Phase 2 in progress.** Phase 1 audit complete (see `docs/`).
> Phase 2 establishes the project foundation: Wear OS app skeleton, Rust
> engine + JNI bridge, minimal engine-status UI.

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

## Docs

- `docs/EXISTING_WEARSIC_AUDIT.md` — Phase 1 audit of the original Wearsic.
- `docs/FEATURE_PARITY_MATRIX.md` — feature parity plan.
- `docs/ARCHITECTURE.md` — target architecture.
- `docs/ARCHITECTURE_DECISIONS.md` — decision log.
- `docs/PROJECT_FOUNDATION.md` — Phase 2 foundation notes.
- `docs/JNI_INTERFACE.md` — the Kotlin↔Rust JNI contract.
- `docs/BUILD_INSTRUCTIONS.md` — exact build commands.
- `docs/PHASE_2_TEST_REPORT.md` — Phase 2 verification truth.
