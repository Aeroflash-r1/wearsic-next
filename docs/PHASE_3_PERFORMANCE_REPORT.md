# Phase 3 Performance Report

Date: 2026-11-07
Host: aarch64 Linux (same CPU class as the target watch, but NOT the watch
itself). All numbers are from the host build/test runs — **label as
unverified for the device** until measured on hardware.

## Binary size

| Artifact | Phase 2 | Phase 3 | Delta |
|---|---|---|---|
| `libwearsic_engine.so` (arm64 release) | 307,336 B | 2,690,632 B | +2.38 MB |
| Debug APK | ~29.99 MB | 29,985,489 B | ~equal (debug dex dominates) |

Size drivers: tokio runtime, hyper + hyper-clientutil + rustls(ring) +
webpki-roots + serde_json. Release APK (R8 + strip) is expected to be
smaller; not yet measured (Phase 10).

## Runtime

- One Tokio multi-threaded runtime, **2 worker threads** (`wearsic-net`).
- Idle: no pollers, no hidden refreshes, no background tasks; workers park.
- Shutdown: cancels registered in-flight tokens; no leaks observed
  (`block_on` external-thread tests).

## Request latencies (host, loopback mock)

| Scenario | Observed |
|---|---|
| Success GET (mock) | ~0.35 s total (includes test/JNI overhead) |
| 404 (no retry) | ~0.35 s |
| 429 → retry → success | ~0.7 s |
| 500 → 2 attempts → fail | ~0.8 s |
| Coalesced duplicate (2 callers) | 1 server hit; ~0.5 s each |

These are host loopback numbers; real watch/network figures require device
measurement.

## Concurrency & memory behaviour

- `max_concurrency = 4` semaphore enforced (8-concurrent test passes).
- Response body cap 1 MiB; oversized responses rejected without buffering
  beyond the cap.
- Diagnostics: counters/averages only; no URL/body retention
  (snapshot < 512 B).

## Cancellation

- Token-based; cancellation observed to stop an in-flight request promptly
  (mock server that never responds; test completes immediately after
  `cancel()`).
- Distinct `cancelled` counter; not counted as failures.

## CPU

- Active phase: async I/O, no busy waits; timer sleeps used between retries.
- Worker count stable at 2 threads regardless of request volume.

## Untested / future measurement

- Actual watch CPU%, RAM, battery drain, thermal behaviour.
- Live-internet latency (YouTube etc.) — Phase 4+.
- TLS handshake cost on-device.
- Release APK size after R8.
