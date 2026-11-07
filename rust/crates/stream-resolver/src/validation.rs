//! Validation of externally supplied metadata.

use super::error::{ResolveError, ResolveErrorKind};

/// Minimal, strict video-id validation (YouTube's 11-char id alphabet).
pub fn validate_video_id(id: &str) -> Result<(), ResolveError> {
    if id.is_empty() || id.len() > 32 {
        return Err(ResolveError::new(ResolveErrorKind::InvalidTrack, "bad video id"));
    }
    let ok = id.bytes().all(|b| {
        b.is_ascii_alphanumeric() || b == b'-' || b == b'_'
    });
    if ok {
        Ok(())
    } else {
        Err(ResolveError::new(ResolveErrorKind::InvalidTrack, "bad video id"))
    }
}

/// Ensures a stream URL is https and well-formed.
pub fn validate_stream_url(url: &str) -> Result<(), ResolveError> {
    let parsed = url::Url::parse(url)
        .map_err(|_| ResolveError::new(ResolveErrorKind::MissingStream, "malformed stream URL"))?;
    if parsed.scheme() != "https" {
        return Err(ResolveError::new(
            ResolveErrorKind::MissingStream,
            "stream URL must be https",
        ));
    }
    Ok(())
}

pub fn validate_mime(mime: &str) -> Result<(), ResolveError> {
    if crate::format::is_media3_supported_mime(mime) {
        Ok(())
    } else {
        Err(ResolveError::new(
            ResolveErrorKind::UnsupportedFormat,
            "unsupported MIME",
        ))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn video_id_validation() {
        assert!(validate_video_id("H3Kzh6RrnMc").is_ok());
        assert!(validate_video_id("a-b_c123").is_ok());
        assert!(validate_video_id("").is_err());
        assert!(validate_video_id("has space").is_err());
        assert!(validate_video_id("../../x").is_err());
        assert!(validate_video_id(&"a".repeat(40)).is_err());
    }

    #[test]
    fn url_validation() {
        assert!(validate_stream_url("https://rr.example/video").is_ok());
        assert!(validate_stream_url("http://rr.example/video").is_err());
        assert!(validate_stream_url("not a url").is_err());
        assert!(validate_stream_url("file:///etc/passwd").is_err());
    }
}
