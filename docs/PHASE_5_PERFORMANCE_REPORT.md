# Phase 5 Performance Report

Date: 2026-11-07. **Host measurements only (aarch64 Linux)** — device
numbers pending hardware.

## Resolution latency (live service)

| Metric | Value |
|---|---|
| First resolution (live, real track) | ~305 ms |
| Cached re-resolution | ~15 µs |
| Player response size (IOS) | ~83 KB |

## Selection & parsing

- 5 audio formats parsed from a typical response; selection cost negligible
  vs network.
- No transcoding — nothing to pay for conversion.

## Caching

- 64 descriptors, 10-min TTL, in-memory. Verified cache-hit path avoids a
  second network round-trip entirely.

## Cancellation

- Reuses Phase 3 token infrastructure; no extra machinery.

## Network

- One player POST per first resolution per track (per TTL window).
- No polling, no background refresh.
- Shared Phase 3 client (pooling/TLS/retries reused).

## Future device measurement

- Real watch CPU/RAM/battery during resolution.
- Radio cost per resolution.
- Memory footprint of the 64-entry descriptor cache on-device.
