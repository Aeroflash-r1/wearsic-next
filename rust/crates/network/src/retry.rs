//! Conservative retry policy with exponential backoff + jitter.

use crate::NetworkError;

/// Computes the sleep before attempt `attempt` (0-based index of the
/// retry). Uses base delay doubled per attempt, capped, plus optional
/// jitter in [0, delay).
pub fn backoff_delay_ms(config: &crate::NetworkConfig, attempt: u32) -> u64 {
    if attempt == 0 {
        return 0;
    }
    let exp = 1u64 << attempt.min(10);
    let base = config.retry_base_delay_ms.saturating_mul(exp);
    let capped = base.min(config.retry_max_delay_ms);
    if config.retry_jitter {
        let jitter = (rand::random::<u64>() % (capped + 1)).min(capped / 2 + 1);
        capped.saturating_add(jitter)
    } else {
        capped
    }
}

/// Whether a retry is allowed for this error under the given policy.
pub fn should_retry(error: &NetworkError, attempts_used: u32, max_attempts: u32) -> bool {
    attempts_used < max_attempts && error.is_retryable()
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::{NetworkConfig, NetworkError, NetworkErrorKind};

    #[test]
    fn first_attempt_no_delay() {
        let cfg = NetworkConfig::default();
        assert_eq!(backoff_delay_ms(&cfg, 0), 0);
    }

    #[test]
    fn backoff_grows_within_cap() {
        let mut cfg = NetworkConfig::default();
        cfg.retry_jitter = false;
        assert!(backoff_delay_ms(&cfg, 1) >= cfg.retry_base_delay_ms);
        assert!(backoff_delay_ms(&cfg, 2) >= backoff_delay_ms(&cfg, 1));
        assert!(backoff_delay_ms(&cfg, 20) <= cfg.retry_max_delay_ms);
    }

    #[test]
    fn retryable_classification() {
        let conn = NetworkError::new(NetworkErrorKind::Connect, "refused");
        let to = NetworkError::new(NetworkErrorKind::Timeout, "late");
        let dns = NetworkError::new(NetworkErrorKind::Dns, "nxdomain");
        let tls = NetworkError::new(NetworkErrorKind::Tls, "cert");
        let http500 = NetworkError::http(500);
        let http429 = NetworkError::http(429);
        let http404 = NetworkError::http(404);
        let cancelled = NetworkError::new(NetworkErrorKind::Cancelled, "cancelled");
        assert!(conn.is_retryable() && !conn.is_permanent());
        assert!(to.is_retryable() && !to.is_permanent());
        assert!(dns.is_retryable());
        assert!(tls.is_retryable());
        assert!(http500.is_retryable());
        assert!(http429.is_retryable());
        assert!(!http404.is_retryable() && http404.is_permanent());
        assert!(!cancelled.is_retryable() && cancelled.is_permanent());
    }

    #[test]
    fn attempt_limits() {
        let cfg = NetworkConfig::default();
        let err = NetworkError::http(500);
        assert!(should_retry(&err, 0, cfg.retry_attempts()));
        assert!(!should_retry(&err, cfg.retry_attempts(), cfg.retry_attempts()));
    }
}
