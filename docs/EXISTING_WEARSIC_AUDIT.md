# Existing Wearsic Audit

**Repository:** `https://github.com/Aeroflash-r1/wearsic`
**Snapshot audited:** `main` @ `c2ae6c8` (v1.9.48, versionCode 78)
**Date:** 2026-11-07

This audit is the Phase 1 deliverable of Wearsic Next. It documents the
existing application as it actually exists, so Wearsic Next can preserve its
important behavior, respect its priorities, and avoid repeating its mistakes.

---

## 1. Existing application architecture (watch app)

- **Language/UI:** Kotlin 2.2.10, Jetpack Compose for Wear OS (M3 1.6.1,
  BOM 2026.06.00), single module `app`, package `com.wearsic.app`.
- **Application shell:** `WearsicApp` composable with a
  `SwipeDismissableNavHost` inside a Wear M3 `AppScaffold(timeText = …)`.
  **Start destination is the Library screen** (no tab hub in v1.9.48).
- **Navigation:** sealed `Screen` routes (`library`, `search`, `player`,
  `volume`, `settings`, `downloads`, `queue`, `playlists`, `albums`,
  `storage`, `server-dashboard`, `favorites`, `artists`, `made-for-you`,
  `playlist/{id}/{name}`). Swipe-to-dismiss back stack. Deep links via
  `MainActivity` extras (`EXTRA_OPEN_PLAYER`) and tile actions.
- **ViewModel graph:** `WearsicPlayerViewModel` (root) composes 5 children —
  `PlaybackViewModel`, `LibraryViewModel`, `DownloadsViewModel`,
  `SettingsViewModel`, `LyricsViewModel`. Position ticks are projected via
  `derivedStateOf` so 2s updates do not recompose the whole UI.
- **Persistent mini-player:** in v1.9.48 this is the `NowPlayingMiniCard`
  rendered inside the Library screen (only when a track is current). There
  is no overlay pill across screens.
- **Theming:** static `WearsicColorScheme`, accent composition locals
  (`LocalWearsicAccent`, `LocalWearsicDynamicAccent`), 14-color accent
  catalog, glass tokens, `watchScaled()` density-aware sizing
  (`BaselineViewportDp=240`), canonical `wearsicListContentPadding()`.
- **Motion:** spring-based `WearsicMotion`, `wearsicClickable` press
  physics + haptics, `wearsicEntrance` (scale+rise) — no heavy blur.

### Screens
`LibraryScreen` (home hub), `PlayerScreen` (2-page pager: NowPlaying +
Actions; fullscreen lyrics), `SearchScreen`, `VolumeScreen`, `SettingsScreen`,
`ServerDashboardScreen`, `QueueScreen`, `DownloadsScreen`, `PlaylistsScreen`,
`PlaylistDetailScreen`, `FavoritesScreen`, `AlbumsScreen`, `ArtistsScreen`,
`RecommendationsScreen`, `StorageStatsContent`.

---

## 2. Existing server architecture (external, phone/box)

- **Runtime:** standalone JVM app (Ktor server), deployed via Termux or a
  Linux box. The watch connects over WiFi/LAN or Cloudflare tunnel.
- **Ktor 2.3.12 CIO engine**, host `0.0.0.0` (default), port from `PORT`
  env (default 8080). Plugins: ContentNegotiation (JSON), CallLogging,
  PartialContent, Compression (gzip allow-listed), StatusPages.
- **Auth:** optional shared secret via `X-Wearsic-Key` (or Bearer),
  constant-time compare; `/health`, `/ready`, `/diag`, `/` public.
- **DB:** sqlite-jdbc (single connection, WAL, synchronized), tables:
  favorites, playlists, playlist_tracks, settings, surrogate_matches,
  stream_targets, stream_marks.
- **Rate limiting:** token buckets per client key by endpoint family.
- **Caching:** bounded LRU (`BoundedCache`) + TTL caches
  (`TtlLoaderCache` with per-key single-flight), durable SQLite caches for
  stream targets/marks, LRU transcode disk cache (200MB).

---

## 3. Feature inventory

| Area | Feature | Where |
|---|---|---|
| Playback | Media3 ExoPlayer playback, session, notifications | app media/ |
| Playback | Ring progress around disc, drag-to-scrub | PlayerScreen |
| Playback | Queue with reorder, shuffle, repeat | QueueScreen |
| Playback | Crossfade engine | media/crossfade |
| Playback | Volume control (dial + crown steps) | VolumeScreen, SystemVolume.kt |
| Playback | Sleep timer with volume fade | WearsicPlayerViewModel |
| Playback | Auto-cache after 45s of listening | WearsicPlayerViewModel |
| Search | InnerTube YouTube Music search **primary** | server YTMusicService |
| Search | NewPipe fallback search | server YoutubeGateway |
| Search | Suggestions, search history (local) | SearchScreen, app |
| Search | Albums/Artists/Playlist APIs | server routes |
| Discovery | Recommendations ("Made for you") | server RecommendationEngine |
| Discovery | Related/radio | server /api/related |
| Lyrics | LRCLIB synced + plain lyrics, LRC parsing | server LrclibProvider/LrcParser |
| Lyrics | Synced highlighting, auto-center scrolling | app LyricsFullscreen |
| Library | Favorites, playlists, albums, artists, downloads | app + server |
| Offline | Downloads with ownership (AUTO/MANUAL), offline playback | app DownloadManager |
| Media | Coil artwork loading, palette extraction | app ui |
| Server | Health/readiness/dashboard, self-heal, canary | server |
| Server | Server profiles + automatic failover | app ServerFailover |
| Pairing | Import server URL/key via pairing file | app PairingImporter |
| UI | Round-screen safe layouts, rotary scroll, haptics | app ui |

---

## 4. Extraction flow (server)

1. **Search (primary):** YouTube Music InnerTube `search` POST
   (`WEB_REMIX` client, public web key, `SONGS_PARAMS`), up to 3 pages via
   continuation token. JDK HttpClient with reordered Chrome-style TLS
   cipher suites (Ktor CIO's TLS fingerprint gets walled).
2. **Search (fallback):** NewPipe `getSearchExtractor(query, ["music_songs"])`
   then unfiltered general search; merge + music-likeness gate.
3. **Stream resolution (primary):** YouTube InnerTube `player` POST with
   lean clients (VISIONOS → IOS → ANDROID_VR), parse
   `streamingData.adaptiveFormats` plain-URL audio entries, rank AAC/mp4
   first, then nearest to 128 kbps. Require visitor data for VISIONOS.
4. **Stream resolution (fallback):** NewPipe `getStreamExtractor` via
   `YoutubeStreamExtractor`, fetch page, filter `audioStreams.isUrl`.
5. **Wait/rotation:** `streamTarget` memory cache → SQLite → lean → (if
   allowed) extractor; single-flight; wall-throttle marks (20min);
   format-fallback ticket pool; extractor warm-up; canary probe
   ("Me at the zoo").
6. **Streaming:** `AudioProxy` handles Range requests; passthrough for
   mp4/AAC, else transcodes via ffmpeg to ADTS AAC; open-ended range
   upstream, closed 1MB chunk fallback with URL rotation up to 8 times.

---

## 5. Search flow (app)

- `SearchScreen` → `LibraryViewModel.searchState` → `musicRepository`
  (OkHttp + kotlinx-serialization) → server `/api/search` (+ suggestions,
  albums). Local search history is app-side.
- Result rows open the player or action sheet (favorite/download/queue).

---

## 6. Playback flow

- `PlaybackViewModel` → `WearsicPlaybackController` (Media3) → custom
  `WearsicStreamDataSource` fetches `{server}/api/stream/{videoId}` with
  Range support → ExoPlayer buffers/plays.
- Track changes warm `/api/prewarm`; auto-cache downloads the song after
  45s of continuous listening; hot-swap queue items to local files when
  offline.

---

## 7. Lyrics flow

- `LyricsViewModel` requests `/api/lyrics?v=&t=&a=&d=` (server hits LRCLIB,
  parses LRC, caches 24h hit / 1h miss).
- Synced lines index by timestamp; `LyricsFullscreen` auto-centers active
  line using measured geometry (pure `lyricsAutoCenterTarget`); plain
  fallback; unavailable state with retry.

---

## 8. Caching strategy (summary)

| Layer | Mechanism |
|---|---|
| Server search | BoundedCache 64–128 entries, 5min TTL |
| Server stream | memory 64 (1h) + SQLite (4000 rows, 1h) |
| Server related/suggestions/albums/playlists | 64/256/32/32 entries, TTL 5–30min |
| Server lyrics | 128 entries, 24h hit / 1h miss |
| Server transcode | disk LRU 200MB |
| Server recommendations | 32 entries, 30min |
| App | Room (recents/favorites/downloads), DataStore prefs, Coil memory/disk |

---

## 9. Existing optimisations (battery/memory/network)

- **Watch app:** only 2s position ticks; derivedStateOf to avoid
  recomposition; draw-phase-only animations (progress ring, shimmer);
  Coil 256px/160px downscaled images; no persistent background poll;
  background playback as media session (not a keep-alive service);
  hysteresis server-reachability (30s + health ping) to avoid rapid
  re-probes; request dedupe (favorites in-flight set, album resolve
  waiters); no fling on rotary; auto-cache is user-intent-gated.
- **Server:** connection pooling (Ktor CIO pools), keep-alive, gzip for
  JSON, rate limits, single-flight per key, wall-throttle to stop hammering
  a walled CDN, canary, self-heal, cached warm-up after boot.

---

## 10. Dependencies (important)

| App | Server |
|---|---|
| Compose BOM 2026.06.00, Wear Compose 1.6.1 | Ktor 2.3.12 (server+client CIO) |
| Media3 1.5.1 —exoplayer/session/common/datasource-okhttp) | NewPipeExtractor v0.26.5 (JitPack) |
| Room 2.7.0 (KSP) | kotlinx-serialization 1.7.3 |
| OkHttp 4.12.1 + kotlinx-serialization 1.8.1 | sqlite-jdbc 3.46.1.0 |
| Coil 2.7.0 | logback-classic 1.5.15 |
| DataStore-preferences 1.1.7 | JUnit 5 / Ktor test-host |
| androidx.tiles 1.4.1, wear-ongoing 1.0.0 | — |
| Robolectric 4.16.1, Roborazzi 1.59.0 (tests) | — |

---

## 11. Features that must be preserved

- InnerTube-first search and stream resolution, NewPipe fallback.
- LRCLIB synced/plain lyrics + LRC parsing + synced highlighting.
- Round-screen-safe UI, marquee titles, rotary scroll + haptics.
- Media3 playback with ring progress, scrub, queue/reorder.
- Downloads with ownership + offline playback.
- Favorites/playlists/albums/artists/recents.
- Server profiles + failover concept (becomes moot with embedded engine, but
  multi-source fallback behavior should map to "watch engine primary, nothing else").
- Error states everywhere (empty/loading/error/retry).

---

## 12. Features that can be improved

- **No tab shell** in v1.9.48 — navigation depth is deeper; a 4-tab hub
  (Home/Library/Search/Mix as prototyped in the scrapped 1.9.49 branch) is
  a better Watch UX. Wearsic Next should adopt the tab hub.
- Search latency depends on the phone server + network hop; embedding the
  engine removes a hop.
- Server requires user setup (Termux, URL, key, tunnel) — the whole point of
  Next is to remove this.
- ffmpeg transcoding is heavy; for a warm engine we should resolve
  native-playable formats only.
- JVM server keeps full JVM + process alive constantly; a Rust engine can be
  strictly scoped to active requests.

---

## 13. Features that cannot reasonably run on Wear OS (as-is)

- Ktor CIO server engine (JVM NIO) — not supported on Android.
- `java.net.http.HttpClient` (Java 11+) — unavailable on Android.
- JDBC / sqlite-jdbc native driver — use framework SQLite/Room.
- External ffmpeg via `ProcessBuilder` — needs bundled native binary
  (heavy, licensing) → must be eliminated.
- Custom TLS cipher-suite reordering (`SSLParameters`) — provider-managed on
  Android; InnerTube TLS fingerprinting countermeasures must be reworked.
- `ManagementFactory`/`Runtime.addShutdownHook`/JVM-only APIs.
- Environment-variable configuration (PORT/key) — use app config.

---

## 14. Known weaknesses (observed)

- Server onboarding friction (Termux, API key, tunnel) is the #1 barrier.
- Single watch-server link is fragile (WiFi roaming, phone power).
- Full JVM server resident memory; constant process even when idle.
- ffmpeg transcode path is CPU/IO heavy and adds latency (~1s+ startup).
- TLS fingerprinting countermeasures are patchy (hardcoded UA/cipher
  reorder) — brittle against YouTube changes.
- API key stored on watch; manual setup.
- App has no embedded cache for server-derived metadata; every fresh screen
  hits the network (mitigated by server caches).

---

## 15. Media + data layer detail (deep audit)

### Playback engine
- `WearsicMediaService : MediaSessionService` (foreground `mediaPlayback`).
  Session player: ExoPlayer with `WAKE_MODE_NETWORK`, audio focus,
  becoming-noisy handling, `DefaultLoadControl` (15s/90s buffers, 800ms
  playback buffer, prioritize time over size, 3MB), `DefaultLoadErrorHandlingPolicy(retryCount=8)`.
- `WearsicStreamDataSource`: OkHttp `DataSource` (connect 10s, callTimeout 0,
  read 60s), no persistent stream cache (in-memory ExoPlayer buffering only).
- Song URL = `{baseUrl}/api/stream/{videoId}` synthesized client-side.
- `WearsicPlaybackController`: `MediaController` with reconnect watchdog
  (15s init, 5 attempts), buffered pending play/tile ops, 2s position
  ticker with same-second dedupe, early-end guard, transient-error retry
  (3×), shuffle/repeat, queue swap to local files on download completion.
- Crossfade engine: dual players, lead 4s, focused ramp, abort rules.

### Networking
- Singleton OkHttp: connect 10s / read 30s / call 45s; interceptors:
  auth (`X-Wearsic-Key`), mutation-freshness, retry (GET/HEAD only, 429/503
  ×2, blips ×1); disk cache 16MB; no TLS pinning; cleartext allowed for LAN.
- `WearsicHttpApiClient` implements `WearsicApiClient`, `Result<T>` DTOs,
  URL/key validation, error mapping (`HostUnreachableError`, `TimeoutError`,
  `HttpError`).
- Failover: `ServerProfile` list in DataStore; `ServerFailoverCoordinator`
  (30s debounce, parallel /health probes, order-priority).
- Pairing file import (`wearsic-pairing.env`).

### Room (v6, schemas exported 4/5/6)
- Entities: downloads (autoCached, pendingDeletion), recent_tracks, favorite_tracks, lyrics_cache.
- Migrations 1→2 … 5→6 in code; `fallbackToDestructiveMigration(true)`.
- DAO grouping for recents: `MAX(rowid)` per uppercase title+artist, ordered by `playedAt DESC, rowid DESC` — **no playCount column in v1.9.48**.

### Repositories
- Remote: `WearsicMusicRepository` (search, suggestions, related, albums,
  recommendations, playlists, favorites, lyrics, prewarm).
- Local: recents, favorites (local-first + best-effort server backup),
  downloads, preferences (DataStore), lyrics store; artists derived locally
  from favorites+recents+downloads.

### Downloads
- One file per track (`filesDir/wearsic_downloads/<id>.m4a`); ownership via
  `autoCached` flag; 2 concurrent, FIFO queue; Range-resume with strict
  206 validation; StatFs guard 15MB; deferred deletion for in-use files;
  eviction to offline-limit; lyrics bundling per mode; auto-cache after 45s
  listening.

### Lyrics
- Client fetches `/api/lyrics` and caches in Room (`lyrics_cache`, 250 rows
  bound, never caches negatives). LRC parsing is server-side; client has no
  parser. Synced lines index by `activeIndexFor(positionMs)`.

### Artwork
- Coil: memory 10% heap, disk 64MB; player artwork 256px
  (`allowHardware(false)`, crossfade 180ms); palette extraction at 32×32
  downscale; thumbnails normalized to w256-h256.

### Startup/diagnostics
- Startup journal + crash log + ANR watchdog; `WearsicApp` warms OkHttp off
  main; `MainActivity` first-frame gate; media-notification deep link; tile
  actions buffered 1.5s.

---

## 16. Recommended migration strategy

1. Port the server's extraction/search/lyrics/networking/cache logic into a
   **Rust engine** (`libwearsic_engine.so`, aarch64) embedded in the APK.
2. Keep Kotlin for UI, Media3, Room, Coil, navigation, lifecycle.
3. Replace server HTTP client calls with typed JNI calls (no localhost HTTP).
4. Primary path: InnerTube (player+search) via Rust HTTP client; NewPipe
   fallback: evaluate whether NewPipeExtractor (JVM) can run in-process in
   the Android app as an optional adapter; if infeasible, document and
   implement graceful degradation.
5. No transcoding: resolve MP4/AAC/WebM/Opus formats that Media3 can play
   directly.
6. Keep the tab-hub design direction (from the prototype work) as the new
   UI shell.
7. Preserve Room/DataStore models for watch-local persistence.
8. Measure (see docs/PERFORMANCE_BENCHMARKS.md) rather than assume.

---

## Appendix A — Key files (named in the brief)

| Required file | In repo? | Notes |
|---|---|---|
| `YoutubeGateway.kt` | ✅ server | InnerTube player + NewPipe fallback, rotation, canary |
| `YTMusicService.kt` | ✅ server | YTM InnerTube search (primary) |
| `MetadataSearchOrchestrator.kt` | ✅ server | orchestration + fallback + album resolve |
| `MetadataSource.kt` | ✅ server | interface definitions |
| `LrclibProvider.kt` | ✅ server | LRCLIB HTTP provider |
| `LyricsService.kt` | ✅ server | cache + shaping |
| `LrcParser.kt` | ✅ server + app | regex parser (server) |
| `LyricsViewModel.kt` | ✅ app | UI state VM |
| `LyricsStore.kt` | ✅ app (data/lyrics) | local lyrics persistence |
| `PlayerScreen.kt` | ✅ app | 2-page pager + lyrics fullscreen |
| `WearsicHubScreen.kt` | ❌ (v1.9.48) | existed in the scrapped 1.9.49 redesign |
| `WearsicMiniPlayer.kt` | ❌ (v1.9.48) | same (1.9.49 redesign) |
| `HomeFeedLogic.kt` / `WearsicGeometry.kt` | ❌ (v1.9.48) | same |
| `WearsicRotaryVolume.kt` | ❌ (v1.9.48) | volume/crown lives in SystemVolume.kt |

> Note: the 1.9.49 branch (hub, mini-player, geometry, volume HUD, artist
> collages) was intentionally scrapped/rolled back; its ideas are recorded in
> `docs/FEATURE_PARITY_MATRIX.md` as "planned/revisit".
