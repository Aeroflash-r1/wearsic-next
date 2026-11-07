# Music Search API (Wearsic Next)

## JNI

| Native (RustEngine + EngineProbe) | Signature | Description |
|---|---|---|
| `nativeSearchMusic` | `(Ljava/lang/String;)Ljava/lang/String;` | Request JSON → envelope JSON (blocking worker thread) |
| `nativeSearchCancel` | `(J)V` | Cancel by request id |

## Request JSON

```json
{
  "query": "coldplay",
  "continuation": null,
  "request_id": 7
}
```

- `continuation`: optional token for the next page.
- `request_id`: optional; when set, registers a cancellation token.

## Response envelope

```json
{
  "ok": true,
  "results": [
    { "track": { "video_id": "…", "title": "…", "artists": [{"name":"…"}],
                 "album": {"name":"…"}, "duration_ms": 267000,
                 "thumbnails": [{"url":"…","width":60,"height":60}] } },
    { "artist": { "id": "UC…", "name": "…", "thumbnails": [],
                  "subscribers": "68.6M monthly audience" } },
    { "album": { "id": "MPREb_…", "title": "…", "artist": "…",
                 "year": "2000", "thumbnails": [] } },
    { "playlist": { "id": "VL…", "title": "…", "author": "…",
                    "track_count": "25 songs", "thumbnails": [] } }
  ],
  "continuation": "…"
}
```

Error envelope (same shape as Phase 3 network errors):

```json
{ "ok": false, "error": { "kind": "timeout", "status": 0, "message": "…" } }
```

## Kotlin

```kotlin
val engine = RustEngine.create()
engine.networkInit()

val page = engine.searchMusic("coldplay", requestId = 1)   // SearchEnvelope
page.results.forEach { it.toDomain() }                     // MusicSearchResult
val next = engine.searchMusic("coldplay", page.continuation, requestId = 2)

engine.searchCancel(1)  // cancels request id 1
```

`SearchViewModel` wraps this with debounce, stale-response rejection and
cancel-on-clear.

## Error kinds (search)

- `network` kinds from Phase 3 (timeout, connect, tls, http, …)
- `invalid_response` — malformed JSON/response
- `cancelled` — user/ViewModel cancellation
- `internal` — engine not initialised / task panic
