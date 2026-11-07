# InnerTube Implementation (Wearsic Next)

## Endpoint

```
POST https://music.youtube.com/youtubei/v1/search?alt=json&key=AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30
```

The key is YouTube's public web key (same one the YTM web app and the
original Wearsic server use). No account, cookie or OAuth is required.

## Context

```json
{
  "context": {
    "client": {
      "clientName": "WEB_REMIX",
      "clientVersion": "1.20261107.01.00",
      "hl": "en",
      "gl": "US"
    }
  },
  "query": "coldplay"
}
```

- `clientVersion` follows `1.YYYYMMDD.01.00` (today's date, BASIC format).
- The original Kotlin server found `encodeDefaults=true` was mandatory so
  `clientName`/`hl`/`gl` stayed in the body. serde always serializes these
  fields (no defaults dropped), so this is safe by construction — verified
  live (the 400-on-malformed-version failure mode was reproduced and fixed).

## Search filter `params`

The YTM web app historically used per-type `params` (Songs, Videos, Albums,
Artists, Playlists). **Live verification (2026-11-07) shows the current
service returns the same mixed result set regardless of these params**, so:

- Wearsic Next sends **no `params`**.
- Rows are classified by navigation shape (see the search architecture doc).
- Filters are client-side views. This is a documented deviation from the
  original server's approach (which only ever searched songs and relied on
  the params). Behavior-priority (InnerTube primary, client-side typing) is
  preserved; no NewPipe path is used for search in the Rust engine.

## Response parsing

- Total traversal: `collect_renderers` gathers every
  `musicResponsiveListItemRenderer` anywhere in the JSON (layout-agnostic:
  `musicShelfRenderer`, `musicCardShelfRenderer`, future renames).
- Each row is typed per the table in the search architecture doc.
- Missing/malformed fields are skipped per-row; never fatal.
- Duration regex `^(\d+:)?\d{1,3}:\d{2}$` → ms.
- Thumbnails: widest available size chosen from `musicThumbnailRenderer`.
- Continuation: depth-first `continuation` string, length > 10.

## Verified live behavior (2026-11-07)

| Filter | Results (typed) |
|---|---|
| Songs ("coldplay yellow") | tracks with videoId/title/artist/duration/thumbnail |
| No filter ("coldplay") | track=14, artist=6, album=3, playlist=6 (via JNI) |
| First-page latency | ~0.9–1.4s (host, live service) |
| Continuation | present on multi-page responses |

All parsing tests use fixtures built from the **real observed shapes**
(identical keys/nesting to live responses); no fabricated live success data
is used in the always-green test suite. The live JNI test
(`EngineProbeLiveSearchTest`) is explicitly network-dependent and separate.
