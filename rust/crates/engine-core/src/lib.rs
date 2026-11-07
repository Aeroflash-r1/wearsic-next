//! Wearsic Next — engine core.
//!
//! Phase 2 scope: a minimal, safe, single-instance engine lifecycle that
//! will later host the network/extraction stack (Phase 3+). Deliberately
//! dependency-free right now: no Tokio, no HTTP, no logging providers. The
//! async runtime and CR networking arrive in Phase 3.
//!
//! Concurrency model: a process-wide engine instance guarded by a `Mutex`.
//! The engine itself is cheap and synchronous; all state is interior-mutable
//! atomics. There are no background threads and no busy loops.

use std::fmt::Write as _;
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::time::Instant;

/// Engine name, reported in status/version strings.
pub const ENGINE_NAME: &str = "wearsic-engine";

/// Engine version. Bump per release; surfaced via `getEngineVersion`.
pub const ENGINE_VERSION: &str = "0.2.0";

/// Lifecycle phase of the engine.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum EnginePhase {
    /// Engine created but not yet initialized.
    Created,
    /// Engine initialized and operational.
    Running,
    /// Engine explicitly shut down; a fresh `init` may restart it.
    Shutdown,
}

impl EnginePhase {
    pub fn as_str(self) -> &'static str {
        match self {
            EnginePhase::Created => "created",
            EnginePhase::Running => "running",
            EnginePhase::Shutdown => "shutdown",
        }
    }
}

/// The engine singleton state.
pub struct Engine {
    phase: AtomicBool, // false = shutdown/created, true = running
    ops: AtomicU64,
    started_at: Instant,
}

impl Engine {
    /// Creates an engine in the `Created` phase (not yet running).
    pub fn new() -> Self {
        Self {
            phase: AtomicBool::new(false),
            ops: AtomicU64::new(0),
            started_at: Instant::now(),
        }
    }

    /// Initializes (or re-initializes) the engine. Idempotent: calling
    /// twice keeps the engine running and is not an error.
    pub fn init(&self) {
        self.phase.store(true, Ordering::Release);
        self.ops.fetch_add(1, Ordering::Relaxed);
    }

    /// Shuts the engine down. Safe to call repeatedly.
    pub fn shutdown(&self) {
        self.phase.store(false, Ordering::Release);
        self.ops.fetch_add(1, Ordering::Relaxed);
    }

    /// Returns the current phase.
    pub fn phase(&self) -> EnginePhase {
        if self.phase.load(Ordering::Acquire) {
            EnginePhase::Running
        } else {
            EnginePhase::Shutdown
        }
    }

    /// Engine version string.
    pub fn version(&self) -> &'static str {
        ENGINE_VERSION
    }

    /// Records one user-visible operation (search/resolve/… later).
    pub fn tick_op(&self) {
        self.ops.fetch_add(1, Ordering::Relaxed);
    }

    /// Operation counter since creation.
    pub fn op_count(&self) -> u64 {
        self.ops.load(Ordering::Relaxed)
    }

    /// Uptime in whole seconds since creation.
    pub fn uptime_secs(&self) -> u64 {
        self.started_at.elapsed().as_secs()
    }

    /// Compact JSON status string, hand-formatted to keep this crate
    /// dependency-free. Later phases may switch to serde_json.
    pub fn status_json(&self) -> String {
        let mut s = String::with_capacity(160);
        let _ = write!(
            s,
            r#"{{"name":"{}","version":"{}","phase":"{}","ops":{},"uptime_s":{}}}"#,
            ENGINE_NAME,
            self.version(),
            self.phase().as_str(),
            self.op_count(),
            self.uptime_secs(),
        );
        s
    }
}

impl Default for Engine {
    fn default() -> Self {
        Self::new()
    }
}

// `AtomicBool`/`AtomicU64`/`Instant` are `Send + Sync`; the engine must be
// shareable behind a `Mutex` across the JNI boundary.
unsafe impl Send for Engine {}
unsafe impl Sync for Engine {}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn fresh_engine_is_shutdown() {
        let e = Engine::new();
        assert_eq!(e.phase(), EnginePhase::Shutdown);
    }

    #[test]
    fn init_then_running() {
        let e = Engine::new();
        e.init();
        assert_eq!(e.phase(), EnginePhase::Running);
    }

    #[test]
    fn init_is_idempotent() {
        let e = Engine::new();
        e.init();
        e.init();
        assert_eq!(e.phase(), EnginePhase::Running);
    }

    #[test]
    fn shutdown_then_can_restart() {
        let e = Engine::new();
        e.init();
        e.shutdown();
        assert_eq!(e.phase(), EnginePhase::Shutdown);
        e.init();
        assert_eq!(e.phase(), EnginePhase::Running);
    }

    #[test]
    fn shutdown_is_idempotent() {
        let e = Engine::new();
        e.shutdown();
        e.shutdown();
        assert_eq!(e.phase(), EnginePhase::Shutdown);
    }

    #[test]
    fn version_is_stable() {
        let e = Engine::new();
        assert_eq!(e.version(), ENGINE_VERSION);
    }

    #[test]
    fn status_json_is_well_formed() {
        let e = Engine::new();
        e.init();
        e.tick_op();
        let s = e.status_json();
        assert!(s.contains("\"name\":\"wearsic-engine\""));
        assert!(s.contains("\"version\":\"0.2.0\""));
        assert!(s.contains("\"phase\":\"running\""));
        assert!(s.contains("\"ops\":2"));
        assert!(s.contains("\"uptime_s\":"));
    }
}
