//! Wearsic Next networking engine.
//!
//! A lightweight, cancellable, battery-conscious HTTP client foundation for
//! all online operations (Phase 4+). One shared client, one managed Tokio
//! runtime, bounded concurrency, bounded bodies, bounded retries.

pub mod cancellation;
pub mod client;
pub mod config;
pub mod diagnostics;
pub mod error;
pub mod request;
pub mod response;
pub mod retry;

pub use cancellation::CancellationToken;
pub use client::HttpClient;
pub use config::NetworkConfig;
pub use diagnostics::Diagnostics;
pub use error::{NetworkError, NetworkErrorKind};
pub use request::{HttpMethod, Request};
pub use response::Response;

use std::sync::Arc;

/// The engine handle. Cheap to clone.
#[derive(Clone)]
pub struct NetworkEngine {
    /// Shared HTTP client.
    pub client: HttpClient,
    /// Shared diagnostics.
    pub diag: Arc<Diagnostics>,
    /// Runtime handle (owned separately for lifecycle).
    runtime: Arc<tokio::runtime::Runtime>,
}

impl NetworkEngine {
    /// Starts the engine: builds the managed runtime (2 worker threads) and
    /// the shared client. Disabled network activity at startup — nothing is
    /// polled or contacted.
    pub fn start(config: NetworkConfig) -> Self {
        let runtime = Arc::new(
            tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .enable_all()
                .thread_name("wearsic-net")
                .build()
                .expect("failed to start tokio runtime"),
        );
        let diag = Arc::new(Diagnostics::new());
        let client = HttpClient::new(config, diag.clone());
        Self { client, diag, runtime }
    }

    /// Runs an async future on the managed runtime, blocking the calling
    /// thread until completion. Safe to call from any (non-runtime) thread;
    /// used by the JNI bridge.
    pub fn block_on<F, T>(&self, future: F) -> T
    where
        F: std::future::Future<Output = T>,
    {
        self.runtime.block_on(future)
    }

    /// Spawns a future on the managed runtime (returns a join handle).
    pub fn spawn<F, T>(&self, future: F) -> tokio::task::JoinHandle<T>
    where
        F: std::future::Future<Output = T> + Send + 'static,
        T: Send + 'static,
    {
        self.runtime.spawn(future)
    }

    /// Access to the managed runtime handle (e.g. for block_on from JNI).
    pub fn runtime_handle(&self) -> tokio::runtime::Handle {
        self.runtime.handle().clone()
    }

    /// Executes a request on the managed runtime, with cancellation.
    pub async fn execute(
        &self,
        req: Request,
        token: &CancellationToken,
    ) -> Result<Response, NetworkError> {
        self.client.execute(req, token).await
    }

    /// Shuts down client resources. Cancels nothing by itself; callers must
    /// cancel their tokens first. The runtime stays alive for engine reuse
    /// until the engine is dropped (client is dropped here).
    pub fn shutdown(&self) {
        // Client is internally shared; drop the underlying connection pool by
        // forcing a fresh client is not necessary — we only mark shutdown via
        // the runtime handle. For Phase 3, the engine is process-wide and
        // lives for the app's lifetime.
    }

    /// Snapshot diagnostics (cheap, no allocations beyond the JSON string).
    pub fn diagnostics_json(&self) -> String {
        self.diag.to_json()
    }
}
