# Wearsic Next — Architecture

**One APK. One watch. One embedded Rust engine. No external backend.**

This document defines the target architecture. It is the result of the
Phase 1 audit of the original Wearsic (`docs/EXISTING_WEARSIC_AUDIT.md`)
and the feature-parity matrix (`docs/FEATURE_PARITY_MATRIX.md`).

---

## 1. Principles

1. **Battery efficiency is the #1 priority** — every decision must justify
   its CPU/radio/thermal cost.
2. **No external backend** — phone server, Termux, Ktor, tunnels, API keys:
   all eliminated. The watch is self-contained (Wi-Fi/LTE to the internet only).
3. **No localhost HTTP / IPC** — Kotlin and Rust talk through a typed JNI
   bridge, not a mini HTTP server.
4. **Rust = embedded engine; Kotlin = platform/UI.** Clean separation; no
   duplicating Media3 or Compose.
5. **Existing important features are preserved** (see parity matrix), and the
   original extraction priority (InnerTube primary, NewPipe fallback) is
   retained unless the audit justifies a change.
6. **Measure, don't assume.** Claims like "Rust is faster" are only made with
   numbers from `docs/PERFORMANCE_BENCHMARKS.md`.

---

## 2. System diagram

```
┌─────────────────────────────────────────────────────────────┐
│  Wearsic Next (single Wear OS APK, arm64-v8a)              │
│                                                             │
│  ┌───────────────────┐    JNI (typed, batched)    ┌──────┐ │
│  │ Kotlin            │ ◄────────────────────────► │ Rust │ │
│  │                    │                            │engine │ │
│  │ • Compose UI      │  initialize/search/resolve/ │      │ │
│  │ • Wear navigation │  lyrics/cancel/diagnostics/ │      │ │
│  │ • Media3 player   │  clearCache                 │      │ │
│  │ • Media session   │                            │      │ │
│  │ • Room/DataStore  │  libwearsic_engine.so      │      │ │
│  │ • Coil artwork    │                            │      │ │
│  │ • Notifications   │                            │      │ │
│  └───────────────────┘                            └──────┘ │
│                                                             │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS (TLS)
                               ▼
                    Internet music services
                    (YouTube Music InnerTube, LRCLIB, CDNs)
```

Key invariant: **Media3 plays resolved stream URLs directly**. Rust resolves
metadata/streams/lyrics; it never relays audio and never runs an HTTP server.

---

## 3. Kotlin layer (platform)

- **UI:** Jetpack Compose for Wear OS (M3), round-first layouts,
  `watchScaled` sizing, rotary input via framework `rotaryScrollable`.
- **Screens:** 4-tab hub (Home / Library / Search / Mix), Now Playing
  (2-page pager + fullscreen lyrics), Settings, Queue, Downloads, Details.
- **Playback:** Android Media3 — `WearsicPlaybackService` (MediaSessionService)
  owns one ExoPlayer + MediaSession; `PlaybackController` bridges Rust stream
  descriptors → MediaItems, queue, controls, refresh-once error recovery;
  `NowPlayingScreen` (original-Wearsic-style UI) drives it. No audio crosses
  JNI.
- **Persistence:** Room (recents, favorites, downloads, lyrics, playlists)
  + DataStore preferences.
- **Artwork:** Coil with bounded caches and artwork palette extraction.
- **Error surface:** user-friendly strings; no raw native stack traces.

---

## 4. Rust engine

Workspace layout (may be tuned after the audit):

```
rust/
├── Cargo.toml              (workspace)
├── crates/
│   ├── engine-core/        lifecycle, coordination, cancellation, errors
│   ├── network/            HTTP client: pooling, TLS, retries, limits
│   ├── innertube/          InnerTube request construction + response helpers
│   ├── ytmusic/            search service, domain models, mapper, cache
│   └── jni-bridge/         JNI exports, marshalling, panic barriers
└── android/
    └── jni-bridge/         JNI exports, marshalling, panic barriers
```

### Decision: async runtime
One managed runtime (Tokio) for the whole engine, with a cheap runtime
handle reused across requests. The engine is **not** kept alive at idle:
it activates on demand (search/play) and idles/stops when the app has no
work. Batched request handling avoids spawning per-request tasks.

### Primary/fallback priority (preserved)
- Search: InnerTube YouTube Music (primary) → general YouTube fallback.
- Stream resolution: InnerTube player (`adaptiveFormats` plain URLs) →
  extractor fallback.
- Related: InnerTube `next` (WEB client) → extractor relatedItems.
- Suggestions/albums/playlists: single canonical path, ported.

### Network engine
- HTTP/1.1 + TLS via a maintained Rust client (e.g. `reqwest`/`hyper`
  stack), connection pooling, timeouts, exponential backoff, per-host
  limits, response-size caps, gzip, cancellation.
- No permanent keep-alive beyond active work; radio-friendly behavior.

### Stream resolution output
`ResolvedStream { url, mime_type, itag, content_length }` — returned to
Kotlin; Media3 plays it directly. No transcoding; formats are restricted to
those Media3 plays natively (mp4/AAC, webm/Opus).

### JNI bridge
- Typed, versioned interface (see `docs/ARCHITECTURE_DECISIONS.md` for the
  API sketch).
- All entry points are `noexcept`-safe: panics are caught at the boundary
  and surfaced as structured engine errors.
- Batched operations (e.g. lyrics + metadata) reduce crossing overhead.
- Cancellation is propagated from Kotlin coroutines into Rust tasks.

---

## 5. Data & state

- Room stays the source of truth for watch-local user data.
- Rust keeps **ephemeral, bounded caches** (metadata, streams, lyrics,
  search). Persistent caches may live in Room via Kotlin; decisions recorded
  in `ARCHITECTURE_DECISIONS.md`.
- No external config beyond app preferences (no server URL, no API key).

---

## 6. Battery & thermal strategy

| Area | Approach |
|---|---|
| Idle | Engine dropped to standby; no polling; no background refresh |
| Search | Single-flight, bounded, cancellable; cache results |
| Playback | Media3-native; no extra buffers; no transcoding |
| Lyrics | Fetch once per track; cache; avoid animation-frame queries |
| Artwork | Downscaled, cached; 256px max; palette at 32×32 |
| Network | Pooled reuse; cancel obsolete; backoff; size caps |
| CPU | Async I/O; no busy waits; draw-phase animations |

---

## 7. Reliability & error model

- Structured engine errors (typed codes) crossing JNI.
- No indefinite retries; bounded attempts with backoff.
- Cancellation on screen exit / track change.
- Recovery paths: no network, disconnected Wi-Fi, expired stream URLs,
  service response changes, engine restart, app backgrounding.
- Startup diagnostics journal retained (Kotlin).

---

## 8. Security & privacy

- HTTPS only for engine traffic; no TLS verification disabled.
- Validate and size-limit all external responses.
- No credentials stored; no hidden tracking; minimal logging.
- Rust `unsafe` kept to a documented minimum (JNI boundary only).

---

## 9. Build & packaging

- Gradle + Kotlin + Compose for Wear OS; Rust built with
  `cargo-ndk` for `aarch64-linux-android`.
- `libwearsic_engine.so` packaged under `lib/arm64-v8a/`.
- Release builds: optimized Rust (LTO + codegen-units 1), R8 for Kotlin.
- Reproducible pinned versions in `gradle/libs.versions.toml` and `Cargo.lock`.

---

## 10. Phases (from the brief)

1. ✅ Phase 1 — audit + docs (this set).
2. Phase 2 — project foundation (Wear app + Rust workspace + JNI load test).
3. Phase 3 — Rust networking engine.
4. Phase 4 — music discovery (search + UI).
5. Phase 5 — extraction/stream resolution (with fallback adapter decision).
6. Phase 6 — direct Media3 playback.
7. Phase 7 — lyrics + library.
8. Phase 8 — UI refinement (round, rotary, animations).
9. Phase 9 — performance optimization (measure).
10. Phase 10 — release readiness.

**Definition of done** (abridged): clean install on GW7 44mm; no companion/
server/tunnel; Rust loads; search works; stream resolves; Media3 plays;
lyrics work; library works; background playback works; lifecycle stable;
failures handled; memory bounded; CPU minimized; battery measured; docs
present.
