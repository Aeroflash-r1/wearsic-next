//! Audio format selection policy.
//!
//! Goals: reliable playback + battery efficiency + Media3 compatibility.
//! - Media3 can decode AAC-LC (audio/mp4) and Opus (audio/webm) natively.
//! - Prefer AAC-LC near the standard 128 kbps tier (low-power hardware
//!   decode on the watch), then Opus/webm, then any audio-only format.
//! - Never pick video or combined (video+audio) formats.
//! - Never pick formats whose MIME the playback layer cannot decode.

use super::error::{ResolveError, ResolveErrorKind};
use super::models::AudioFormat;

/// Target bitrate tier for AAC-LC (YouTube itag 140 ≈ 128 kbps).
const TARGET_AAC_BITRATE: u64 = 128_000;

pub fn is_audio_mime(mime: &str) -> bool {
    mime.contains("audio")
}

pub fn is_media3_supported_mime(mime: &str) -> bool {
    let m = mime.to_lowercase();
    m.contains("audio/mp4") || m.contains("audio/webm") || m.contains("audio/aac")
}

/// A format is audio-only if its codec list has no known video codecs.
fn is_audio_only(format: &AudioFormat) -> bool {
    let codecs = format.codec.as_deref().unwrap_or("").to_lowercase();
    !codecs.contains("avc1")
        && !codecs.contains("av01")
        && !codecs.contains("vp9")
        && !codecs.contains("vp8")
        && !codecs.contains("h264")
        && !codecs.contains("hev1")
}

/// Rank two formats: returns `Ordering` where `Less` means a is preferred.
fn rank(a: &AudioFormat, b: &AudioFormat) -> std::cmp::Ordering {
    let a_aac = a.mime_type.to_lowercase().contains("audio/mp4");
    let b_aac = b.mime_type.to_lowercase().contains("audio/mp4");
    // AAC-LC container preferred over WebM/Opus.
    match (a_aac, b_aac) {
        (true, false) => return std::cmp::Ordering::Less,
        (false, true) => return std::cmp::Ordering::Greater,
        _ => {}
    }
    // Within same container, prefer nearer the target bitrate.
    let a_dist = a.bitrate.abs_diff(TARGET_AAC_BITRATE);
    let b_dist = b.bitrate.abs_diff(TARGET_AAC_BITRATE);
    a_dist.cmp(&b_dist)
}

/// Selects the single best audio format from a list.
///
/// Only audio-only formats supported by Media3 are considered. Returns
/// `UnsupportedFormat` if nothing eligible exists.
pub fn select_best_format(formats: &[AudioFormat]) -> Result<AudioFormat, ResolveError> {
    let eligible: Vec<&AudioFormat> = formats
        .iter()
        .filter(|f| {
            is_audio_mime(&f.mime_type)
                && is_media3_supported_mime(&f.mime_type)
                && is_audio_only(f)
        })
        .collect();

    let best = eligible
        .into_iter()
        .min_by(|a, b| rank(a, b))
        .ok_or_else(|| {
            ResolveError::new(
                ResolveErrorKind::UnsupportedFormat,
                "no Media3-compatible audio format in the player response",
            )
        })?;

    Ok(best.clone())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn fmt(url: &str, mime: &str, br: u64) -> AudioFormat {
        AudioFormat {
            url: url.into(),
            mime_type: mime.into(),
            codec: None,
            bitrate: br,
            sample_rate: None,
            channels: None,
            content_length: 0,
            itag: None,
        }
    }

    #[test]
    fn prefers_aac_near_128k() {
        let formats = vec![
            fmt("u1", "audio/webm; codecs=\"opus\"", 150_000),
            fmt("u2", "audio/mp4; codecs=\"mp4a.40.2\"", 130_000),
            fmt("u3", "audio/mp4; codecs=\"mp4a.40.2\"", 48_000),
        ];
        let best = select_best_format(&formats).unwrap();
        assert_eq!(best.url, "u2");
    }

    #[test]
    fn rejects_video_and_combined() {
        let formats = vec![
            fmt("v", "video/mp4; codecs=\"avc1\"", 1_000_000),
            AudioFormat {
                url: "c".into(),
                mime_type: "audio/mp4; codecs=\"mp4a.40.2,avc1\"".into(),
                codec: Some("mp4a.40.2,avc1".into()),
                bitrate: 200_000,
                sample_rate: None,
                channels: None,
                content_length: 0,
                itag: None,
            },
        ];
        assert!(select_best_format(&formats).is_err());
    }

    #[test]
    fn rejects_unknown_mime() {
        let formats = vec![fmt("u", "audio/x-something", 128_000)];
        assert!(select_best_format(&formats).is_err());
    }

    #[test]
    fn empty_list_is_unsupported() {
        assert!(select_best_format(&[]).is_err());
    }

    #[test]
    fn opus_webm_is_acceptable_fallback() {
        let formats = vec![fmt("u", "audio/webm; codecs=\"opus\"", 78_000)];
        let best = select_best_format(&formats).unwrap();
        assert_eq!(best.url, "u");
    }
}
