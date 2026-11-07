# Stream Resolution API (Phase 5)

## JNI

| Native | Signature | Description |
|---|---|---|
| `nativeResolveStream` | `(Ljava/lang/String;)Ljava/lang/String;` | request JSON → envelope JSON (blocking worker thread) |
| `nativeSearchCancel` / `nativeNetworkCancel` | `(J)V` | cancel by request id (shared registry) |

## Request JSON

```json
{ "video_id": "H3Kzh6RrnMc", "request_id": 11 }
```

## Success envelope

```json
{
  "ok": true,
  "stream": {
    "video_id": "H3Kzh6RrnMc",
    "url": "https://rr…googlevideo.com/videoplayback?…",
    "mime_type": "audio/mp4; codecs=\"mp4a.40.2\"",
    "codec": null,
    "bitrate": 130996,
    "sample_rate": 44100,
    "channels": 2,
    "content_length": 4182954,
    "duration_ms": null,
    "source": "innertube:IOS"
  }
}
```

## Error envelope

```json
{
  "ok": false,
  "error": { "kind": "track_unavailable", "status": 0, "message": "…" }
}
```

`kind` values: `invalid_track`, `track_unavailable`, `unsupported_format`,
`network`, `malformed_response`, `missing_stream`, `stream_expired`,
`cancelled`, `internal`.

## Kotlin

```kotlin
val engine = RustEngine.create()
engine.networkInit()

val descriptor = engine.resolveStream("H3Kzh6RrnMc", requestId = 11)
// descriptor.url / mimeType / bitrate / contentLength / source

// On failure a NetworkEngineException with .error.kind is thrown.
```

## Playback readiness

The descriptor is ready for Media3 (Phase 6): a direct HTTPS audio URL,
audio-only MIME the player can decode, content length, and bitrate metadata.
