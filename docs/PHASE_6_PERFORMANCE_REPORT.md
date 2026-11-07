# Phase 6 Performance Report

Date: 2026-11-07. **Host measurements only** — no physical watch yet.

## What is measured (host)

| Metric | Value |
|---|---|
| Rust resolution latency (live) | ~305 ms first, ~15 µs cached |
| Playback model construction | negligible (pure JVM) |
| APK size | 30.7 MB debug (engine 2.93 MB) |

## Configured for the watch (not yet measured)

- ExoPlayer buffers: min 15 s / max 60 s, playback buffer 1.2 s, rebuffer
  3 s, 4 MiB byte cap.
- One player + one session per process; 500 ms position ticker (UI-throttled).
- No background jobs outside playback; wake mode only during playback.

## Not yet measurable without hardware

- CPU/RAM during actual streaming on Galaxy Watch 7.
- Cold/warm playback startup, buffering duration, battery draw.
- Notification/system-media integration behavior.

All such figures are explicitly **unverified** until device testing.
