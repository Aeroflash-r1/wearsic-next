# Stream URL Lifecycle (Phase 5)

## Facts

- YouTube streaming URLs (`googlevideo.com/videoplayback?…`) are temporary:
  they carry an `expire` epoch and can be invalidated early (CDN/rotation).
- The player response provides `contentLength` but no explicit expiry inside
  the format entries; the URL's `expire` query parameter exists but is not
  a guarantee.

## Wearsic Next policy

| Concern | Policy |
|---|---|
| Cache lifetime | conservative **10 minutes**, in-memory only |
| Cache size | bounded: 64 descriptors (oldest-first eviction) |
| Key | video id (track-specific) |
| Expiry | never trust a URL beyond the TTL; do not refresh continuously |
| Invalidation | `invalidate(video_id)` after playback failures |
| Persistence | never persist temporary URLs to disk |
| Logging | never log URLs / signed query parameters |
| Re-resolution | on demand only (track selection, or after invalidation) |

## Failure handling

- If playback later fails with a network/403/410 on a cached URL, the caller
  calls `invalidate(video_id)` then resolves again (one retry).
- `stream_expired` error kind exists for explicit expiry signals.
