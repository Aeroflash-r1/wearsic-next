# Feature Parity Matrix — Wearsic → Wearsic Next

Legend: **Not started** · **Planned** · **Implemented** · **Tested** · **Needs improvement**

This matrix is live-updated as Wearsic Next develops. "Implemented" and
"Tested" are only claimed when code exists and tests have actually run.

## A. Playback & media

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Media3 ExoPlayer playback | Tested | **Implemented (wired; device-untested)** | Service+controller+UI compile; JVM tests pass |
| Media session + notification | Tested | **Implemented (wired; device-untested)** | MediaSessionService + manifest |
| Audio focus / becoming-noisy | Tested | Planned | Media3-managed |
| Wake mode network | Tested | Planned | `WAKE_MODE_NETWORK` |
| Load control (15s/90s, 3MB) | Tested | Planned | Reuse values |
| Ring progress around disc | Tested | Planned | Keep (UI) |
| Drag-to-scrub on disc | Tested | Planned | Keep (UI) |
| Queue + reorder + shuffle/repeat | Tested | Planned | Keep |
| Crossfade engine | Tested | Planned | Keep (optional) |
| Volume dial + crown steps | Tested | Planned | Via `SystemVolumeState` |
| Sleep timer + volume fade | Tested | Planned | Keep |
| Auto-cache after 45s listening | Tested | Planned | Keep |
| Playback | Direct stream playback (no proxy) | — | **Implemented (resolution + Media3 wiring)** | Rust resolves validated URL; Media3 plays it (device playback untested) |
| Local-file playback (downloads) | Tested | Planned | Keep |

## B. Search & discovery

| Feature | Original | Next status | Notes |
|---|---|---|---|
| InnerTube YTM search (primary) | Tested | **Implemented + live-tested** | `innertube` + `ytmusic` crates |
| NewPipe search fallback | Tested | Not ported (documented) | Rust engine uses InnerTube only; NewPipe remains a possible Kotlin adapter (Phase 5) |
| Suggestions | Tested | Planned | Next phase |
| Related/radio | Tested | Planned | Next phase |
| Recommendations ("Made for you") | Tested | Planned | Next phase |
| Albums/Artists browsing | Tested | **Implemented** | typed results; client-side filter views |
| Search history | Tested | Planned | Room (next phases) |
| Empty/error/retry search states | Tested | **Implemented** | SearchScreen states |
| Pagination (continuation) | Tested | **Implemented** | bounded, per-page |
| Search cache | Tested | **Implemented** | 64-entry / 5min TTL in-memory |

## C. Lyrics

| Feature | Original | Next status | Notes |
|---|---|---|---|
| LRCLIB fetch | Tested | Planned | Port to Rust |
| LRC parsing | Tested | Planned | Port to Rust (client-side now) |
| Synced highlighting | Tested | Planned | Keep (Kotlin UI) |
| Plain fallback | Tested | Planned | Keep |
| Offline lyrics cache (Room) | Tested | Planned | Keep |
| Lyrics download modes | Tested | Planned | Keep |

## D. Library & offline

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Favorites (local-first + backup) | Tested | Planned | Keep Room model |
| Playlists (create/list/detail/remove) | Tested | Planned | Local — no server needed |
| Albums page | Tested | Planned | Rust-backed |
| Artists (local grouping) | Tested | Planned | Keep |
| Recently played (identity-safe) | Tested | Planned | Keep |
| Downloads with ownership (AUTO/MANUAL) | Tested | Planned | Keep |
| Offline playback of downloads | Tested | Planned | Keep |
| Storage breakdown UI | Tested | Planned | Keep |
| Pairing file import | Tested | Obsolete | No server to pair with; remove |

## E. Server/connectivity features

| Feature | Original | Next status | Notes |
|---|---|---|---|
| External Ktor server | Tested | **Eliminated** | Targeted by Rust engine |
| Termux/phone hosting | Tested | **Eliminated** | — |
| Tailscale/Cloudflare tunnel | Tested | **Eliminated** | — |
| Server profiles + failover | Tested | **Eliminated** | No server to fail over |
| API key auth | Tested | **Eliminated** | No manual config |
| Status dashboard (`/health`) | Tested | Replaced | Engine diagnostics screen |
| Self-heal/canary | Tested | Replaced | Rust diagnostics |

## F. UI & platform

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Library-home shell | Tested | Planned | Next will adopt 4-tab hub (Home/Library/Search/Mix) |
| Now Playing (2-page pager) | Tested | Planned | Keep |
| Fullscreen synced lyrics | Tested | Planned | Keep |
| Round-safe layouts | Tested | Planned | Keep; `watchScaled`/padding |
| Rotary scroll + haptics | Tested | Planned | Keep framework-delegated |
| Persistent mini-player | Scrapped (1.9.49) | Planned | Reintroduce from prototype |
| Voice search / tri-mode search | Scrapped (1.9.49) | Planned | Reintroduce |
| Artist collage avatars | Scrapped (1.9.49) | Planned | Reintroduce |
| Dynamic accent from artwork | Tested | Planned | Keep |
| Settings screens | Tested | Planned | Adapt (no server fields) |
| Wear tile | Tested | Planned | Keep |

## G. Engineering/performance

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Battery-conscious patterns | Tested (design) | Implemented | 2s ticks, deferred recomposition, draw-phase anims |
| Bounded caches | Tested | Planned | Rust caches bounded (Phase 4+) |
| Request dedupe/single-flight | Tested | **Implemented** | network crate coalesces duplicate in-flight GETs |
| Retry/backoff policies | Tested | **Implemented** | bounded, jittered, category-aware (network crate) |
| Timeouts/limits | Tested | **Implemented** | connect/request/TLS deadlines, 1MiB body cap |
| Cancellation | Tested | **Implemented** | CancellationToken + JNI cancel-by-id |
| Network diagnostics | — | **Implemented** | counters/averages JSON, no URL/body retention |
| Rust unit/integration tests | — | **Implemented** | 7 engine-core + 25 network (11 unit, 14 mock-server integration) |
| JNI boundary | — | **Implemented** | Phase 2 + network methods; host-JVM probes |
| Kotlin tests incl. Robolectric | Tested | Implemented | Phase 2 + network JNI smoke tests |
| Integration tests (no server) | — | Implemented | mock-server tests are test-local only |
| Performance benchmarks | None on watch | Planned | `docs/PHASE_3_PERFORMANCE_REPORT.md` (Phase 3) |

---

## Explicitly NOT carried over

- Ktor server / CIO engine / JDBC / `java.net.http` / ffmpeg transcode /
  TLS cipher-suite reorder / env-var config — all replaced or eliminated for
  Wear OS feasibility (see audit §13).
- NewPipe as a required dependency — only as an optional in-app fallback
  adapter if it runs on Android; otherwise documented graceful degradation.
