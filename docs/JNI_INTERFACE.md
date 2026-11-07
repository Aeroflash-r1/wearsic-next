# Wearsic Next — JNI Interface

This document defines the Kotlin ↔ Rust JNI contract (Phase 2).

## Library

- Filename: `libwearsic_engine.so`
- Target: `aarch64-linux-android` (arm64-v8a), packaged under
  `lib/arm64-v8a/` in the APK.
- Loading: Kotlin `System.loadLibrary("wearsic_engine")` (see `RustEngine.load()`).
- Registration: `JNI_OnLoad` registers method tables via
  `RegisterNatives` (no c-side name mangling).

## Classes

### `com.wearsic.next.engine.RustEngine` (app)

| Native method | Signature | Semantics |
|---|---|---|
| `nativeInitialize` | `()Z` | Initialize the process-wide engine. Returns `true` on success (idempotent). |
| `nativeGetVersion` | `()Ljava/lang/String;` | Engine version, e.g. `"0.2.0"`. |
| `nativeGetStatus` | `()Ljava/lang/String;` | Compact JSON status (see below). |
| `nativeShutdown` | `()V` | Shut down. Safe to call repeatedly. |

### `com.wearsic.next.engine.EngineProbe` (host-JVM test mirror)

Same contract under `probeInit` / `probeVersion` / `probeStatus` /
`probeShutdown`, plus:

| Native method | Signature | Semantics |
|---|---|---|
| `probePanicBarrier` | `()Z` | Deliberately panics inside native code. Returns `true` when the panic was contained by the JNI barrier (i.e. the VM survived). |

## Status JSON

```json
{
  "name": "wearsic-engine",
  "version": "0.2.0",
  "phase": "running",
  "ops": 2,
  "uptime_s": 12
}
```

`phase` is one of `"created"`, `"running"`, `"shutdown"`.

## Correctness & safety contract

- **No panics cross JNI.** Every exported native body runs inside
  `std::panic::catch_unwind`; a panic yields the fallback sentinel.
- **Process-wide single engine** behind a `Mutex`; no other global mutable
  state.
- **No blocking the main thread**: Phase 2 natives are synchronous and
  trivial; the app calls them off the main thread (launched effect on
  `Dispatchers.Default`). Asynchronous search/extraction (Phase 3+) will use
  an opaque engine handle + request ids and a suspend wrapper in Kotlin.
- **Thread safety**: initialization/shutdown are idempotent and safe under
  concurrency.
- **No localhost HTTP, no sockets, no IPC** — JNI is the only bridge.

## Future extension (Phase 3+)

The interface will add:

```text
search(query, requestId) -> status
resolveStream(videoId, requestId) -> status
getMetadata(videoId, requestId) -> status
getLyrics(track, requestId) -> status
cancelRequest(requestId)
getEngineDiagnostics()
clearCache()
```

Likely shaped as a handle-based API (`jlong` engine handle returned by
`initializeEngine`) with per-request ids for cancellation, then a batch
result polling or callback-based delivery. The Phase 2 static-method style
is intentionally minimal and is expected to evolve behind `RustEngine` /
`EngineProbe` as stable, tested surfaces.
