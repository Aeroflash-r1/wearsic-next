# Wearsic Next — Architecture Decisions

Status legend: **Accepted** · **Proposed** · **Superseded**.

This log records engineering decisions and their rationale, per the brief's
"Engineering Decision Requirements". It will be appended to as development
proceeds.

---

## AD-001: Rust as the embedded engine (Accepted)

**Decision:** Implement the former server's responsibilities as a Rust
library compiled for `aarch64-linux-android`, packaged as
`libwearsic_engine.so` in the APK, called via a typed JNI bridge.

**Rationale:**
- The former server is a JVM process with dependencies that cannot run in an
  Android/Wear process (Ktor CIO server, JDBC, `java.net.http`, ffmpeg via
  ProcessBuilder, TLS cipher reorder) — audit §13.
- Rust gives small binaries, no runtime/VM, precise control over concurrency
  and memory — relevant for a 2GB watch.
- The engine is a first-class component, not a utility; a dedicated language
  boundary keeps Kotlin focused on platform/UI.

**Constraints:** No localhost HTTP server, no sockets/IPC between layers; no
Tokio runtime per request; idle engine is not kept alive.

---

## AD-002: JNI over localhost HTTP (Accepted)

**Decision:** Kotlin ↔ Rust communication uses a small, typed, versioned JNI
interface. No loopback HTTP server, no other IPC.

**Rationale:**
- Avoids an entire HTTP server lifecycle (port binding, auth, request
  parsing) inside the app — saves memory, CPU, and attack surface.
- JNI calls are cheap enough when batched; no need for a network stack
  between two in-process components.

**Risks:** JNI complexity (marshalling, memory management). Mitigations:
serde_json ↔ protobuf-style structs over a small constant ABI, panic
barriers, and test coverage of the bridge.

---

## AD-003: Preserve InnerTube-first extraction (Accepted)

**Decision:** Keep the original priority: InnerTube player/search primary;
NewPipe/fallback secondary. Do not swap NewPipe to primary.

**Rationale:** The audit confirmed the existing hierarchy is deliberate and
works (YTM search primary, lean player clients primary, NewPipe fallback).
Swapping would risk service behavior and licensing without a measured gain.

---

## AD-004: NewPipe fallback as optional in-app adapter (Proposed)

**Decision:** Investigate whether NewPipeExtractor (JVM) can run in-process
in the Android app for fallback search/stream resolution.

**Rationale:** NewPipeExtractor is a JVM library; Android can run JVM code
in-process. If it loads and passes tests, it is the fastest way to retain
parity with minimal Rust porting risk.

**Open question:** size/API compatibility on Wear OS, and whether keeping a
JVM extraction dependency conflicts with the "one embedded engine" vision.
If infeasible, degrade gracefully (documented limitations).

---

## AD-005: No transcoding — play native formats only (Accepted)

**Decision:** The Rust engine resolves audio formats Media3 can play
directly (mp4/AAC, webm/Opus). No ffmpeg transcoding path.

**Rationale:** Bundling ffmpeg is heavy (APK size, ARM64 licensing,
CPU/heat on the watch). Media3 already decodes AAC/Opus natively. The
original transcoder exists to serve odd formats; skipping it removes a
major battery/CPU cost.

---

## AD-006: Single managed async runtime, on-demand (Accepted)

**Decision:** One Tokio runtime (or lighter alternative measured against
it) for the engine; created lazily, dropped to standby when idle.

**Rationale:** Multiple runtimes waste memory/threads; keeping a runtime
alive at idle burns CPU. On-demand activation matches the watch's
"no unnecessary background work" constraint.

---

## AD-007: Room for watch-local data; Rust for ephemeral caches (Accepted)

**Decision:** Room/DataStore remain the source of truth for user data
(recents, favorites, downloads, playlists, lyrics cache, settings). The
Rust engine keeps bounded in-memory caches for metadata/streams/search;
persistent cache policy is decided per-cache (storage via Kotlin/Room).

**Rationale:** Reuses proven, tested persistence; avoids duplicating a
database layer in Rust; keeps the engine focused on network/extraction.

---

## AD-008: UI shell — 4-tab hub (Accepted)

**Decision:** Wearsic Next opens on a four-tab hub (Home / Library / Search /
Mix) rather than the v1.9.48 single-screen Library shell.

**Rationale:** The scrapped 1.9.49 prototype already validated the tab hub,
mini-player, and round-safe geometry. It reduces navigation depth and gives
a more native Wear feel. (See parity matrix section F.)

---

## AD-009: Battery efficiency as architecture filter (Accepted)

**Decision:** Every component passes a battery review: radio activity,
threads, polling, animation cost, cache growth, and background execution are
all gated by measured necessity.

**Rationale:** Explicit requirement of the brief; audit shows the original
app already models many good practices (2s position ticks, derived state,
draw-phase animations, hysteresis) — these are carried forward.

---

## AD-010: Search filters are client-side views (Accepted)

**Decision:** InnerTube search `params` are not used; the service fetches
the mixed result set once and filters are applied client-side over typed
results.

**Rationale (verified live 2026-11-07):** the current service returns the
same mixed set for every filter param (Songs/Videos/Albums/Artists/Playlists
all returned watch≈20 + artist≈3 + other≈6). Sending one request and typing
rows client-side is more battery-efficient (one radio round-trip serves all
filters) and matches the current web app behavior. The original "primary =
InnerTube" priority is preserved; the removed params are documented in
`INNERTUBE_IMPLEMENTATION.md`.
