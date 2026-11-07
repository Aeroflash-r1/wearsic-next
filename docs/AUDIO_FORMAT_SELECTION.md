# Audio Format Selection (Phase 5)

## Compatibility target

Media3/ExoPlayer on Wear OS decodes natively:

- **AAC-LC** in MP4 (`audio/mp4; codecs="mp4a.40.2"`, itag 140 family) —
  hardware-decoded on wearables → lowest power.
- **Opus** in WebM (`audio/webm; codecs="opus"`, itag 249/250/251).
- ADTS AAC (`audio/aac`).

## Selection policy (documented order)

1. `mimeType` must contain `audio` (never video/combined).
2. Codec list must contain no video codec
   (`avc1`, `av01`, `vp9`, `vp8`, `h264`, `hev1`).
3. Only Media3-supported containers are eligible.
4. Among eligible:
   - Prefer `audio/mp4` (AAC-LC) over WebM/Opus.
   - Within the same container, pick the format whose bitrate is closest to
     the 128 kbps tier (standard AAC-LC; hardware-friendly).
5. Opus/webm is the acceptable fallback when no AAC format exists.

## Rationale

- Battery: AAC-LC hardware decode << software decode; choosing near-128 kbps
  avoids high bitrate waste.
- Reliability: MP4/AAC has the broadest Media3 support.
- No transcoding anywhere; unsupported formats are rejected, not converted.

## Verified (live, 2026-11-07)

For a typical track the player response contained:
`itag 140 audio/mp4 (AAC-LC, 44100 Hz, stereo, 130996 bps, 4.18 MB)`,
`itag 139 audio/mp4 (22050 Hz)`, `itag 249/250/251 audio/webm (Opus)`.

The policy selected **itag 140** (AAC-LC, 44.1kHz, stereo) — exactly the
Media3-compatible, battery-friendly choice.
