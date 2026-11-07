# Media3 Integration (Phase 6)

## Dependencies (app)

```kotlin
implementation(libs.media3.exoplayer)          // 1.5.1
implementation(libs.media3.session)             // 1.5.1
implementation(libs.media3.common)              // 1.5.1
implementation(libs.media3.datasource.okhttp)   // 1.5.1
```

No `media3-ui` (we draw our own Compose controls — lighter than the bundled
UI and round-screen-friendly).

## ExoPlayer construction (see service)

- `ExoPlayer.Builder` with:
  - `DefaultLoadControl` (watch-tuned buffers, documented)
  - audio attributes (media/music) with `handleAudioFocus = true`
  - `setHandleAudioBecomingNoisy(true)`
  - `setWakeMode(WAKE_MODE_NETWORK)`
  - `DefaultMediaSourceFactory(DefaultDataSource.Factory(ctx, OkHttpDataSource.Factory(http)))`

## MediaItem construction

```
MediaItem.Builder()
  .setMediaId(videoId)
  .setUri(stream.url)            // validated https
  .setMimeType(stream.mimeType)  // audio/mp4 or audio/webm (Media3 decodes)
  .setMediaMetadata(title/artist/album/artwork)
```

`PlaybackItem` merges Phase 4 metadata with the Phase 5 descriptor; search
metadata wins for display fields.

## What Media3 owns

- HTTP streaming (OkHttp data source), buffering, decode (AAC-LC/Opus),
  rendering, seek, track transitions, focus, media session, notification.

## What Rust still owns

- Discovery, resolution, validation, descriptor cache, refresh on error.

No audio crosses JNI. No second network stack for playback.
