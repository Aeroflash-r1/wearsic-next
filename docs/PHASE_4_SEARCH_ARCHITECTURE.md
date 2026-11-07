# Phase 4 — Music Discovery Architecture

## Objective

Direct music search from the watch using the embedded Rust engine and the
Phase 3 networking foundation. No phone, no server, no extra HTTP stack.

## Modules

```
rust/crates/
├── innertube/     InnerTube client: context, request payloads, POST via
│                  shared network engine, safe JSON traversal helpers
├── ytmusic/       domain models + mapper + service (search, pagination,
│                  bounded cache)
└── jni-bridge/    nativeSearchMusic / nativeSearchCancel
```

## InnerTube client (`innertube`)

- `InnerTubeClient::new(HttpClient)` — borrows the shared Phase 3 client
  (connection pool, TLS, timeouts, retries, coalescing all reused).
- Request: `POST https://music.youtube.com/youtubei/v1/search?alt=json&key=…`
  with `context.client = { clientName: WEB_REMIX, clientVersion: 1.YYYYMMDD.01.00, hl: en, gl: US }`.
- No auth/cookies/keys beyond the public web key.
- Responses parsed with total (never-panicking) traversal helpers.

## Search filters — VERIFIED BEHAVIOUR

Live testing against the current service showed the InnerTube search
`params` values (Songs/Videos/Albums/Artists/Playlists) **no longer narrow
the response** — each filter returns the same mixed set (watch=20…23,
artist≈3, other≈6). The web app filters client-side.

**Decision:** Wearsic Next sends no `params` (one request) and classifies
every row into typed results (`Track`/`Artist`/`Album`/`Playlist`). Filters
become client-side views over those typed results. This is documented in
`INNERTUBE_IMPLEMENTATION.md` and `ARCHITECTURE_DECISIONS.md` (AD-010).

## Row classification (mapper)

| Shape | Result type |
|---|---|
| Title run has `watchEndpoint.videoId` | Track |
| Overlay play button or `playlistItemData.videoId` | Track |
| Renderer-root `navigationEndpoint.browseEndpoint` pageType ALBUM | Album |
| … pageType ARTIST (or browseId `UC…`) | Artist |
| … pageType PLAYLIST (or browseId `VL…`) | Playlist |
| Other browse types (profiles, etc.) | skipped |

Artist/year/author are read from subtitle runs after stripping the leading
type label ("Album"/"Artist"/"Playlist"/"Song"/…). Duration strings are
parsed (`M:SS` / `H:MM:SS`); duration-like runs are never mistaken for an
artist. Incomplete rows are skipped safely — one bad row never fails a page.

## Pagination

- First page: up to 30 rows + `continuation` token (extracted depth-first,
  preferring the `continuation` key, length > 10).
- `load_more` fetches the next page via the continuation token; the next
  token is returned (bounded, page-by-page, never unlimited).

## Caching

- In-memory bounded TTL cache (64 entries, 5 min), keyed by normalized query.
- Only successful pages are cached; errors and continuation pages are not.
- No persistent storage writes on search.

## Cancellation & coordination

- JNI search runs on the engine runtime (blocking worker thread, never
  main). A `request_id` is registered in the in-flight token registry;
  `nativeSearchCancel(requestId)` cancels the token and the JNI call
  returns a cancelled error envelope.
- Kotlin `SearchViewModel` debounces input (350ms), cancels in-flight jobs
  when the query changes, and rejects outdated responses by comparing the
  current query against the response query.

## Battery behaviour

- One network request per debounced query (plus optional explicit
  pagination).
- No polling, no background refresh, no repeated identical requests
  (cache + coalescing).
- Lazy list + Coil thumbnails (cached, small resolution).
- Search work only occurs while the user interacts.

## JNI + Kotlin

| Native | Meaning |
|---|---|
| `nativeSearchMusic(requestJson)` | first page or continuation; JSON envelope |
| `nativeSearchCancel(requestId)` | cancel in-flight search |

Kotlin models (`SearchModels.kt`) mirror the Rust `ytmusic::models` with an
externally-tagged `MusicSearchResultDto` → sealed `MusicSearchResult`.

## Wear UI

`SearchScreen` (round-aware):
- `BasicTextField` search input (debounced), placeholder.
- Filter chips: All / Songs / Albums / Artists / Playlists.
- States: idle, loading, results (LazyColumn), empty, error.
- Result rows: thumbnail (Coil), title, artist/album/duration subtitle.
- "Show more" row when a continuation exists.
- Selection emits `MusicSearchResult` (playback wiring is Phase 5/6).
