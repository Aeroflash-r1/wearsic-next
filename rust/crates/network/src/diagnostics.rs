//! Cheap, bounded network diagnostics. No request history, no URLs, no
//! bodies — counters + averages only.

use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Instant;

#[derive(Debug, Clone, serde::Serialize)]
#[serde(rename_all = "snake_case")]
pub struct DiagnosticsSnapshot {
    pub total_requests: u64,
    pub success_requests: u64,
    pub failed_requests: u64,
    pub cancelled_requests: u64,
    pub timeouts: u64,
    pub retries: u64,
    pub bytes_received: u64,
    pub active_requests: u64,
    pub avg_duration_ms: u64,
    pub last_error: Option<String>,
    pub connections_created: u64,
    pub pool_hits: u64,
}

/// Atomic counters, cheap to update, no locks on the hot path.
#[derive(Debug, Default)]
pub struct Diagnostics {
    total: AtomicU64,
    success: AtomicU64,
    failed: AtomicU64,
    cancelled: AtomicU64,
    timeouts: AtomicU64,
    retries: AtomicU64,
    bytes: AtomicU64,
    active: AtomicU64,
    duration_ns: AtomicU64,
    conns: AtomicU64,
    pool_hits: AtomicU64,
    last_error: std::sync::Mutex<Option<String>>,
}

impl Diagnostics {
    pub fn new() -> Self {
        Self::default()
    }

    pub fn request_started(&self) -> ActiveRequestGuard<'_> {
        self.active.fetch_add(1, Ordering::Relaxed);
        self.total.fetch_add(1, Ordering::Relaxed);
        ActiveRequestGuard { diag: self, started: Instant::now() }
    }

    pub fn record_success(&self, bytes: usize) {
        self.success.fetch_add(1, Ordering::Relaxed);
        self.bytes.fetch_add(bytes as u64, Ordering::Relaxed);
    }

    pub fn record_failure(&self, kind: &str) {
        self.failed.fetch_add(1, Ordering::Relaxed);
        if let Ok(mut last) = self.last_error.lock() {
            *last = Some(kind.to_string());
        }
    }

    pub fn record_cancelled(&self) {
        self.cancelled.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_timeout(&self) {
        self.timeouts.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_retry(&self) {
        self.retries.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_connection_created(&self) {
        self.conns.fetch_add(1, Ordering::Relaxed);
    }

    pub fn record_pool_hit(&self) {
        self.pool_hits.fetch_add(1, Ordering::Relaxed);
    }

    pub fn snapshot(&self) -> DiagnosticsSnapshot {
        let total = self.total.load(Ordering::Relaxed);
        let active = self.active.load(Ordering::Relaxed);
        let done = total.saturating_sub(active);
        let avg_ns = if done > 0 {
            self.duration_ns.load(Ordering::Relaxed) / done
        } else {
            0
        };
        let last = self.last_error.lock().ok().and_then(|g| g.clone());
        DiagnosticsSnapshot {
            total_requests: total,
            success_requests: self.success.load(Ordering::Relaxed),
            failed_requests: self.failed.load(Ordering::Relaxed),
            cancelled_requests: self.cancelled.load(Ordering::Relaxed),
            timeouts: self.timeouts.load(Ordering::Relaxed),
            retries: self.retries.load(Ordering::Relaxed),
            bytes_received: self.bytes.load(Ordering::Relaxed),
            active_requests: active,
            avg_duration_ms: avg_ns / 1_000_000,
            last_error: last,
            connections_created: self.conns.load(Ordering::Relaxed),
            pool_hits: self.pool_hits.load(Ordering::Relaxed),
        }
    }

    pub fn to_json(&self) -> String {
        serde_json::to_string(&self.snapshot()).unwrap_or_else(|_| "{}".into())
    }
}

pub struct ActiveRequestGuard<'a> {
    diag: &'a Diagnostics,
    started: Instant,
}

impl Drop for ActiveRequestGuard<'_> {
    fn drop(&mut self) {
        self.diag.active.fetch_sub(1, Ordering::Relaxed);
        self.diag
            .duration_ns
            .fetch_add(self.started.elapsed().as_nanos() as u64, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn counters_and_json() {
        let d = Diagnostics::new();
        let g = d.request_started();
        d.record_success(10);
        drop(g);
        let snap = d.snapshot();
        assert_eq!(snap.total_requests, 1);
        assert_eq!(snap.success_requests, 1);
        assert_eq!(snap.active_requests, 0);
        assert!(snap.avg_duration_ms >= 0);
        let json = d.to_json();
        assert!(json.contains("\"total_requests\":1"));
    }

    #[test]
    fn no_unbounded_history() {
        let d = Diagnostics::new();
        for _ in 0..1000 {
            d.record_failure("timeout");
        }
        // Snapshot stays small; no history retained.
        assert!(d.to_json().len() < 512);
    }
}
