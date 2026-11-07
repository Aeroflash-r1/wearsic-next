# Phase 6 — Playback Architecture

## Roles

| Layer | Responsibility |
|---|---|
| Rust (existing) | discovery, stream resolution, networking, descriptor cache, diagnostics |
| Kotlin/Media3 | actual audio playback, decode, output, MediaSession, focus, notification, UI |

Media3 owns playback. No Rust audio decoder, no audio over JNI, no byte-array
audio routing. The resolved `StreamDescriptor.url` is handed directly to
Media3's pipeline.

## Handoff flow

```
Search result (Phase 4 Track)
      │
      ▼
Rust StreamResolver (Phase 5) ──► StreamDescriptor
      │
      ▼
PlaybackController (Kotlin) ──► MediaItem (URI + MIME + metadata)
      │
      ▼
ExoPlayer ──► audio output
```

## Media3 integration

- `media3-exoplayer`, `media3-session`, `media3-datasource-okhttp`
  (all 1.5.1, matches the app catalog).
- One `ExoPlayer` per process, built in `WearsicPlaybackService`
  (`MediaSessionService`), exposed via one `MediaSession`.
- Data source: `DefaultDataSource.Factory` + `OkHttpDataSource.Factory`
  (connection reuse, timeouts, retries at the HTTP layer).

## Watch-tuned buffering (documented reasoning)

| Parameter | Value | Why |
|---|---|---|
| minBufferMs | 15 000 | hides small network jitter; not excessive |
| maxBufferMs | 60 000 | caps RAM; avoids downloading half a song up front |
| bufferForPlaybackMs | 1 200 | quick start |
| bufferForPlaybackAfterRebufferMs | 3 000 | recovers fast after a hiccup |
| targetBufferBytes | 4 MiB | bounded memory on the watch |
| prioritizeTimeOverSize | true | time-based > byte-based |

No desktop defaults: those assume big RAM and fast networks.

## Playback service

- `WearsicPlaybackService : MediaSessionService`, foreground type
  `mediaPlayback`, declared in the manifest.
- Lifecycle: session created on first controller connect; resources released
  in `onDestroy`; `onTaskRemoved` stops when nothing is playing.
- No artificial always-on foreground service.

## State model

`PlaybackPhase { Idle, Resolving, Preparing, Buffering, Playing, Paused,
Ended, Error }` + `PlaybackUiState { item, positionMs, durationMs,
bufferedMs, isPlaying, errorMessage, hasPrev, hasNext, shuffle, repeat }`.

Updates are throttled (controller-driven + a 500 ms position ticker); the UI
collects the StateFlow without per-frame recomposition.

## Queue

- In-memory queue of `PlaybackItem` (search metadata merged with stream
  descriptor).
- Streams are resolved **on demand** per item (no ahead-of-time extraction
  for the whole queue).
- Previous/next navigate the queue; repeat/shuffle delegate to Media3.

## Expired-stream recovery

- On `onPlayerError` (signed-URL/network class), the controller invalidates
  the descriptor and asks Rust for a fresh one exactly once; replaces the
  media item and resumes. Permanent errors stop retrying.
- Bounded: one refresh per item per failure.

## Audio focus / lifecycle

- Media3 handles focus via `setAudioAttributes(handleAudioFocus = true)`.
- `setHandleAudioBecomingNoisy(true)` pauses on headphone unplug.
- `WAKE_MODE_NETWORK` keeps the radio alive only during playback.

## UI

- `NowPlayingScreen`: full-bleed artwork backdrop, rotating disc with ring
  progress sweep, marquee title, spring-fed transport (play/pause/prev/next),
  buffering glyph — AMOLED dark, draw-phase-only animation, matching the
  original Wearsic motion language.
- `SettingsScreen`: **no server URL / API key UI** (standalone). Only local
  preferences + about.
- Home shell navigates Home → Search → Player → Settings.
