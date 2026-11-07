//! InnerTube player response parsing (trusted shapes, total access).

use serde_json::Value;

use super::error::{ResolveError, ResolveErrorKind};
use super::models::AudioFormat;

/// Parses the `adaptiveFormats` section into audio formats.
pub fn parse_adaptive_audio_formats(root: &Value) -> Vec<AudioFormat> {
    let Some(formats) = root
        .pointer("/streamingData/adaptiveFormats")
        .and_then(|a| a.as_array())
    else {
        return Vec::new();
    };

    formats
        .iter()
        .filter_map(|f| {
            let url = f.get("url")?.as_str()?;
            let mime_type = f.get("mimeType")?.as_str()?;
            if !mime_type.contains("audio") {
                return None;
            }
            let bitrate = f.get("bitrate").and_then(Value::as_u64).unwrap_or(0);
            let codec = f
                .get("codecs")
                .and_then(Value::as_str)
                .map(str::to_string);
            let sample_rate = f
                .get("audioSampleRate")
                .and_then(Value::as_str)
                .and_then(|s| s.parse::<u64>().ok());
            let channels = f.get("audioChannels").and_then(Value::as_u64);
            // contentLength arrives as a string.
            let content_length = f
                .get("contentLength")
                .and_then(Value::as_str)
                .and_then(|s| s.parse::<u64>().ok())
                .unwrap_or(0);
            let itag = f.get("itag").and_then(Value::as_u64);
            Some(AudioFormat {
                url: url.to_string(),
                mime_type: mime_type.to_string(),
                codec,
                bitrate,
                sample_rate,
                channels,
                content_length,
                itag,
            })
        })
        .collect()
}

/// Returns a structured error for a non-OK playability status.
pub fn playability_error(root: &Value, video_id: &str) -> Option<ResolveError> {
    let status = root
        .pointer("/playabilityStatus/status")
        .and_then(Value::as_str)?;
    if status == "OK" {
        return None;
    }
    let reason = root
        .pointer("/playabilityStatus/reason")
        .and_then(Value::as_str)
        .unwrap_or("unavailable");

    let kind = if status == "LOGIN_REQUIRED" || status == "AGE_CHECK_REQUIRED" {
        ResolveErrorKind::TrackUnavailable
    } else {
        ResolveErrorKind::TrackUnavailable
    };
    Some(ResolveError::new(
        kind,
        format!("{video_id}: {status} — {reason}"),
    ))
}

#[cfg(test)]
mod tests {
    use super::*;
    use serde_json::json;

    #[test]
    fn parses_audio_formats() {
        let root = json!({
            "streamingData": {
                "adaptiveFormats": [
                    { "itag": 140, "url": "https://x/u", "mimeType": "audio/mp4; codecs=\"mp4a.40.2\"",
                      "bitrate": 130996, "audioSampleRate": "44100", "audioChannels": 2,
                      "contentLength": "4182954" },
                    { "itag": 251, "url": "https://x/o", "mimeType": "audio/webm; codecs=\"opus\"",
                      "bitrate": 151464, "contentLength": "4394593" },
                    { "itag": 137, "url": "https://x/v", "mimeType": "video/mp4",
                      "bitrate": 3000000 }
                ]
            }
        });
        let formats = parse_adaptive_audio_formats(&root);
        assert_eq!(formats.len(), 2);
        assert_eq!(formats[0].url, "https://x/u");
        assert_eq!(formats[0].sample_rate, Some(44100));
        assert_eq!(formats[0].content_length, 4182954);
        assert_eq!(formats[1].mime_type, "audio/webm; codecs=\"opus\"");
    }

    #[test]
    fn handles_missing_streaming_data() {
        assert!(parse_adaptive_audio_formats(&json!({})).is_empty());
        assert!(parse_adaptive_audio_formats(&json!({"streamingData": {}})).is_empty());
    }

    #[test]
    fn ok_playability_is_none() {
        let root = json!({"playabilityStatus": {"status": "OK"}});
        assert!(playability_error(&root, "x").is_none());
    }

    #[test]
    fn login_required_yields_unavailable() {
        let root = json!({
            "playabilityStatus": {
                "status": "LOGIN_REQUIRED",
                "reason": "Sign in to confirm you’re not a bot"
            }
        });
        let err = playability_error(&root, "vid").unwrap();
        assert_eq!(err.kind, ResolveErrorKind::TrackUnavailable);
        assert!(err.message.contains("vid"));
    }
}
