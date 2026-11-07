# Playback Lifecycle (Phase 6)

## Service lifecycle

| Event | Behaviour |
|---|---|
| First controller connect | session + player built lazily (`onGetSession`) |
| Screen closed | playback continues (service owns player) |
| Task removed | if not playing → stop + clear; if playing → keep (media) |
| onDestroy | player release, session release, scope cancel |
| Device/headset disconnect | `onAudioBecomingNoisy` → pause (Media3) |

## Player lifecycle

- Created once per process.
- `prepare()` before `play()`; media items replaced for track changes.
- Errors → `onPlayerError` → refresh-once policy (see error doc).

## Position reporting

- Events (position discontinuity / state change) and a 500 ms ticker update
  `PlaybackUiState`.
- Compose collects the StateFlow; no per-frame recomposition.

## Idle after stop

- No background jobs; the service is not kept alive artificially.
- Player released when the service is destroyed.
