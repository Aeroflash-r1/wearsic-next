//! Cancellation support.
//!
//! A lightweight, shareable cancellation token built on `tokio::sync::watch`.
//! No polling, no busy loops: `cancel()` wakes all waiters once.

use tokio::sync::watch;

/// Cloneable cancellation token.
#[derive(Clone)]
pub struct CancellationToken {
    tx: watch::Sender<bool>,
    rx: watch::Receiver<bool>,
}

impl CancellationToken {
    pub fn new() -> Self {
        let (tx, rx) = watch::channel(false);
        Self { tx, rx }
    }

    /// Request cancellation. Idempotent.
    pub fn cancel(&self) {
        let _ = self.tx.send(true);
    }

    pub fn is_cancelled(&self) -> bool {
        *self.rx.borrow()
    }

    /// Waits until the token is cancelled. Returns immediately if already
    /// cancelled.
    pub async fn cancelled(&self) {
        if *self.rx.borrow() {
            return;
        }
        let mut rx = self.rx.clone();
        // Skip the current value; wait for a *change* to true.
        if *rx.borrow_and_update() {
            return;
        }
        while rx.changed().await.is_ok() {
            if *rx.borrow() {
                return;
            }
        }
    }

    /// Returns `Cancelled` if cancelled, else the value.
    pub fn check(&self) -> Result<(), crate::NetworkError> {
        if self.is_cancelled() {
            Err(crate::NetworkError::new(
                crate::NetworkErrorKind::Cancelled,
                "request cancelled",
            ))
        } else {
            Ok(())
        }
    }
}

impl Default for CancellationToken {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn starts_uncancelled() {
        let t = CancellationToken::new();
        assert!(!t.is_cancelled());
        assert!(t.check().is_ok());
    }

    #[tokio::test]
    async fn cancel_sets_flag() {
        let t = CancellationToken::new();
        t.cancel();
        assert!(t.is_cancelled());
        assert!(t.check().is_err());
    }

    #[tokio::test]
    async fn cancel_is_idempotent() {
        let t = CancellationToken::new();
        t.cancel();
        t.cancel();
        assert!(t.is_cancelled());
    }

    #[tokio::test]
    async fn cancelled_wakes_waiter() {
        let t = CancellationToken::new();
        let t2 = t.clone();
        let handle = tokio::spawn(async move { t2.cancelled().await });
        tokio::task::yield_now().await;
        assert!(!handle.is_finished());
        t.cancel();
        handle.await.unwrap();
    }
}
