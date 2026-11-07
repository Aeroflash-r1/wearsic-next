# Playback Error Handling (Phase 6)

## Error categories → action

| Error | Action |
|---|---|
| Invalid stream URL (resolution already validated) | never occurs from Rust; guard anyway |
| Expired URL (403/410 on play) | invalidate descriptor → resolve fresh (once) → replace item → resume |
| HTTP 429 / 5xx during streaming | Media3 retries at data-source layer; on repeated failure → refresh-once |
| Network disconnection | buffering → error; resume when network returns (user retry) |
| Decoder error (bad codec / MIME) | surface friendly error; no retry (permanent) |
| Unsupported MIME/codec | filtered out in Phase 5 selection; surfaced if it still occurs |
| Buffering timeout | surface error; allow retry |
| Cancelled (user/track switch) | not an error; no notification |

## Refresh policy

- **Exactly once** per item per failure: `resolveStream(videoId)` again,
  replace media item, `prepare()` + `play()`.
- If the refreshed resolve fails → `Error` state with a clear message.
- No infinite loops; permanent failures are never retried.

## User-facing messages

- "Could not resolve a playable stream."
- "Playback failed: <code>" (no raw bodies / signed URLs).
