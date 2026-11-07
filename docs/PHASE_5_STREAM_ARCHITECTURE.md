# Phase 5 — Stream Resolution Architecture

## Objective

Convert an eligible Phase 4 track identifier into a validated, playable
audio stream descriptor for Media3 (Phase 6) — efficiently, with bounded
memory and safe lifecycle management.

## Module

```
rust/crates/stream-resolver/
├── src/
│   ├── lib.rs            re-exports
│   ├── models.rs         StreamDescriptor + AudioFormat
│   ├── player_response.rs parse adaptiveFormats + playability
│   ├── format.rs         Media3-compatible audio format selection
│   ├── validation.rs     videoId / URL / MIME validation
│   ├── error.rs          structured ResolveError kinds
│   └── resolver.rs       orchestration + bounded descriptor cache
```

## Strategy (preserves the original priority)

- **Primary:** InnerTube player request (`youtubei/v1/player`) with the IOS
  client identity — verified live to work cookie-less and return playable
  audio formats.
- **Secondary:** ANDROID_VR client (same endpoint; another client identity).
- No NewPipe path in the Rust engine; no DRM/access-control bypass. If no
  client yields eligible formats, a structured `TrackUnavailable` error is
  returned.

Why IOS-first (deviation, documented): the original server used VISIONOS as
its primary lean client, which requires a session visitor id. Live testing in
Phase 5 showed VISIONOS answers `LOGIN_REQUIRED` without the visitor id, while
IOS answers `OK` cookie-less with identical request structure. IOS is
therefore the first client, preserving "InnerTube-primary" and the
money-fallback order.

## Selection policy (`format.rs`)

1. Audio-only formats only (`mimeType` contains "audio").
2. No combined/video codecs (avc1/av01/vp9/vp8/h264/hev1 rejected).
3. Media3-compatible containers only: `audio/mp4` (AAC-LC), `audio/webm`
   (Opus), `audio/aac`.
4. Prefer AAC-LC (`audio/mp4`) → within that container, nearest to the
   128 kbps tier (YouTube itag 140 ≈ 128 kbps) — low-power hardware decode.
5. Opus/webm is an acceptable fallback.

Never transcodes; never selects a format Media3 cannot decode.

## Stream descriptor

`StreamDescriptor { video_id, url, mime_type, codec?, bitrate?,
sample_rate?, channels?, content_length, duration_ms?, source }`.

All fields come from the service; nothing is invented. `source` records the
client used (`innertube:IOS`, `innertube:ANDROID_VR`).

## Lifecycle & caching

- In-memory bounded cache: 64 descriptors, conservative 10-minute TTL,
  keyed by video id.
- `invalidate(video_id)` clears a descriptor (used after playback failure).
- Temporary signed URLs are never persisted; never logged; never reused
  past the TTL.
- No background refresh; resolution happens only on demand.

## Cancellation & concurrency

- Reuses Phase 3 `CancellationToken` + shared Tokio runtime + shared HTTP
  client.
- JNI: `nativeResolveStream(requestJson)` registers `request_id` in the
  in-flight token registry; cancellation propagates to the player request.
- No per-request runtime, no per-request HTTP client.

## Security

- videoId validated (strict ASCII alnum + `-`/`_`, ≤32 chars) before any
  network activity.
- Stream URL must be `https://` and well-formed.
- MIME validated against the Media3-supported set.
- External player responses treated as untrusted: total parsing, malformed
  inputs → structured error, never panic.

## Error kinds

`invalid_track`, `track_unavailable`, `unsupported_format`, `network`,
`malformed_response`, `missing_stream`, `stream_expired`, `cancelled`,
`internal`.
