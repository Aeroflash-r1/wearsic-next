# Feature Parity Matrix — Wearsic → Wearsic Next

Legend: **Not started** · **Planned** · **Implemented** · **Tested** · **Needs improvement**

This matrix is live-updated as Wearsic Next develops. "Implemented" and
"Tested" are only claimed when code exists and tests have actually run.
An "(device-untested)" tag means the code compiles and the JVM suite passes,
but it has not been exercised on a physical watch yet.

**Last verified: 2026-11-09** — 40/40 JVM tests pass (`:app:testDebugUnitTest`,
including live InnerTube search, stream-resolution, and browse probes for
album/playlist/artist pages; bad browse ids error), Rust workspace tests pass
(`cargo test --workspace --offline`), `:app:lintDebug` reports 0 errors,
`:app:assembleDebug` builds (`app-debug.apk`, 29M).

## A. Playback & media

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Media3 ExoPlayer playback | Tested | **Implemented (device-untested)** | Service + controller + UI; plays resolved Rust descriptor URLs |
| Media session + notification | Tested | **Implemented (device-untested)** | `WearsicPlaybackService` (`MediaSessionService`) |
| Audio focus / becoming-noisy | Tested | **Implemented (device-untested)** | `handleAudioFocus = true`, `setHandleAudioBecomingNoisy(true)` |
| Wake mode network | Tested | **Implemented** | `C.WAKE_MODE_NETWORK` |
| Load control (15s/90s, 3MB) | Tested | **Implemented** | Watch-tuned to 15s min / 60s max / 1.2s play / 4 MiB cap |
| Ring progress around disc | Tested | **Implemented (device-untested)** | Progress on the Now Playing disc |
| Drag-to-scrub on disc | Tested | **Implemented (device-untested)** | `detectDragGestures` → `seekTo` |
| Queue + reorder + shuffle/repeat | Tested | **Needs improvement** | Queue, jump, remove, shuffle and repeat are real (shuffle reorders the queue with the playing track kept first; repeat off/all/one). Index rules are JVM-tested. Drag-reorder not yet |
| Instant skip (next-track prefetch) | Tested | **Implemented (device-untested)** | The next queue entry's stream is resolved in the background while the current track plays, so a skip starts without a network wait |
| Crossfade engine | Tested | Planned | Not ported |
| Volume dial + crown steps | Tested | **Implemented (device-untested)** | Segmented bar + crown delta, system stream volume |
| Sleep timer + volume fade | Tested | **Needs improvement** | Sleep timer pauses after N minutes; volume fade not ported |
| Auto-cache after 45s listening | Tested | Planned | — |
| Playback | Direct stream playback (no proxy) | **Implemented (device-untested)** | Rust resolves and validates URL; Media3 plays it |
| Local-file playback (downloads) | Tested | Planned | Blocked on the downloads subsystem |

## B. Search & discovery

| Feature | Original | Next status | Notes |
|---|---|---|---|
| InnerTube YTM search (primary) | Tested | **Implemented + live-tested** | `innertube` + `ytmusic` crates |
| NewPipe search fallback | Tested | Not ported (documented) | Rust engine uses InnerTube only; optional Kotlin adapter remains possible |
| Suggestions | Tested | Planned | Next phase |
| Related/radio | Tested | Planned | Not implemented: live `browse` with `params=wAEB` returned HTTP 400 and `/next` returned no usable queue during verification; no fake fallback added |
| Recommendations ("Made for you") | Tested | Planned | Original used its server; no real engine-backed equivalent added without invented data |
| Albums/Artists/Playlists browsing | Tested | **Implemented + live-tested** | Rust engine browse (album `MPREb…`, playlist `VL…`, artist `UC…`) via InnerTube `browse`/continuation; parsed headers, tracks, artist album cards, continuation; browse UI screens with play-all, song rows, nested album navigation, load-more, retry. Network engine gained a per-request response cap (browse pages exceed the 1 MiB default; clamped to 6 MiB). |
| Search history | Tested | Planned | Needs a local store |
| Empty/error/retry search states | Tested | **Implemented** | SearchScreen states |
| Pagination (continuation) | Tested | **Implemented** | Bounded, per-page |
| Search cache | Tested | **Implemented** | 64-entry / 5 min TTL in-memory |

## C. Lyrics

| Feature | Original | Next status | Notes |
|---|---|---|---|
| LRCLIB fetch | Tested | Planned | Port to Rust |
| LRC parsing | Tested | Planned | Port to Rust (client-side now) |
| Synced highlighting | Tested | Planned | Keep (Kotlin UI) |
| Plain fallback | Tested | Planned | Keep |
| Offline lyrics cache | Tested | Planned | Keep |
| Lyrics download modes | Tested | Planned | Keep |

No lyrics code exists in Wearsic Next yet.

## D. Library & offline

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Favorites (local-first + backup) | Tested | **Implemented (device-untested)** | `FavoritesStore` — SharedPreferences + JSON, no Room |
| Playlists (create/list/detail/remove) | Tested | Planned | Original playlists were server-backed; local create/list/detail management not ported yet. Engine-backed *browsing* of a YouTube Music playlist by id is implemented (browse). |
| Albums page | Tested | **Implemented (device-untested)** | Rust-backed browse screen (header, cover, tracks, play-all) |
| Artists (local grouping) | Tested | **Needs improvement** | Engine-backed artist browse page is implemented (immersive header, top songs, album rows → album browse). Local library grouping not ported |
| Recently played (identity-safe) | Tested | **Implemented** | `RecentStore`, bounded list keyed by `videoId` |
| Downloads with ownership (AUTO/MANUAL) | Tested | Planned | Original was server-dependent |
| Offline playback of downloads | Tested | Planned | Blocked on downloads |
| Storage breakdown UI | Tested | Planned | Keep |
| Pairing file import | Tested | Obsolete | No server to pair with; removed |

## E. Server/connectivity features

| Feature | Original | Next status | Notes |
|---|---|---|---|
| External Ktor server | Tested | **Eliminated** | Replaced by the Rust engine |
| Termux/phone hosting | Tested | **Eliminated** | — |
| Tailscale/Cloudflare tunnel | Tested | **Eliminated** | — |
| Server profiles + failover | Tested | **Eliminated** | No server to fail over |
| API key auth | Tested | **Eliminated** | No manual config |
| Status dashboard (`/health`) | Tested | Replaced | Network diagnostics in the Rust engine |
| Self-heal/canary | Tested | Replaced | Rust diagnostics |

## F. UI & platform

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Library-home shell | Tested | **Implemented** | Single Home stack (pills), not the 4-tab hub |
| Now Playing (2-page pager) | Tested | **Implemented (device-untested)** | `HorizontalPager` |
| Fullscreen synced lyrics | Tested | Planned | Depends on the lyrics subsystem |
| Round-safe layouts | Tested | **Implemented** | `watchScaled` + shared content padding |
| Rotary scroll + haptics | Tested | **Implemented** | `WearsicRotaryUtil`, inversion pref, haptic opt-out |
| Persistent mini-player | Scrapped (1.9.49) | **Implemented** | Home "now playing" hero card |
| Voice search / tri-mode search | Scrapped (1.9.49) | Planned | Reintroduce |
| Artist collage avatars | Scrapped (1.9.49) | Planned | Reintroduce |
| Dynamic accent from artwork | Tested | **Implemented** | `ArtworkPalette`, "Auto (artwork)" accent |
| Settings screens | Tested | **Implemented** | Accent, artwork rotation, haptics, rotary inversion |
| Wear tile | Tested | Planned | Keep |

## G. Engineering/performance

| Feature | Original | Next status | Notes |
|---|---|---|---|
| Battery-conscious patterns | Tested (design) | Implemented | Position ticker PARKS while no UI is visible (screen off = zero polls), 500 ms while playing / 3 s when paused; ring + disc animate in the draw phase only; rotation can be disabled |
| Bounded caches | Tested | Implemented | Rust caches bounded (Phase 4+) |
| Request dedupe/single-flight | Tested | **Implemented** | `network` crate coalesces duplicate in-flight GETs |
| Retry/backoff policies | Tested | **Implemented** | Bounded, jittered, category-aware |
| Timeouts/limits | Tested | **Implemented** | connect/request/TLS deadlines, 1 MiB body cap |
| Cancellation | Tested | **Implemented** | `CancellationToken` + JNI cancel-by-id |
| Network diagnostics | — | **Implemented** | Counters/averages JSON, no URL/body retention |
| Rust unit/integration tests | — | **Implemented** | engine-core + network (unit + mock-server integration) |
| JNI boundary | — | **Implemented** | Host-JVM probes + contract tests |
| Kotlin tests | Tested | **Implemented** | 36 JVM tests (engine probes, live search/resolve, playback models, queue navigation) |
| Integration tests (no server) | — | **Implemented** | Mock-server tests are test-local only |
| Lint | — | **Implemented** | `:app:lintDebug` clean of errors |
| Release build (R8) | — | **Implemented** | `assembleRelease` with ProGuard JNI/serialization rules |
| Performance benchmarks | None on watch | Planned | Phase reports exist for 3–6; no on-device benchmarks yet |

---

## Explicitly NOT carried over

- Ktor server / CIO engine / JDBC / `java.net.http` / ffmpeg transcode /
  TLS cipher-suite reorder / env-var config — all replaced or eliminated for
  Wear OS feasibility (see audit §13).
- NewPipe as a required dependency — only as an optional in-app fallback
  adapter if it runs on Android; otherwise documented graceful degradation.
