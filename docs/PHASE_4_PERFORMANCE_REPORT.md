# Phase 4 Performance Report

Date: 2026-11-07. **All numbers are host measurements (aarch64 Linux),
not the watch** — treat as unverified until measured on device.

## Live service timings (host, real YouTube Music)

| Scenario | Observed |
|---|---|
| First search (Songs), 20 results | ~0.9–1.4 s |
| Mixed typed search via JNI (3 tests) | total 2.6 s → ~0.9 s/search |
| Cache hit (repeat query) | ~0 s (no network) |

## Parsing

- 30-row mixed page parsed + typed in negligible time vs network (host).
- Malformed/skipped rows never block; single-row cost is small.

## JNI conversion

- Envelope JSON round-trip measured inside the live-test total; no separate
  benchmark. (Implementation keeps payloads compact: one JSON string per
  search, typed models on both sides.)

## Network behaviour

- One POST per debounced query; duplicate identical queries suppressed by the
  64-entry/5-min cache (and Phase 3 coalescing).
- No polling, no background refresh; radio active only during user-initiated
  search/pagination.
- Pagination: one extra POST per "Show more" tap.

## Memory

- Search cache bounded: 64 entries, 5-min TTL, in-memory only.
- Result lists bounded (30/page); UI renders lazily.
- Thumbnails via Coil (cached, resized); no download until visible.

## Convergence after search

- Idle after search: no background tasks, no repeated requests (verified:
  repeat search hits cache).

## Future measurement (device)

- CPU/RAM/battery during search on Galaxy Watch 7 44 mm.
- Real radio cost of one search.
- Thumbnail download count/bytes.
- JNI conversion cost on-device.
