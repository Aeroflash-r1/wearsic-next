//! LIVE probe: runs a real search against YouTube Music InnerTube using the
//! Phase 3 network client. This is NOT part of the automated test suite — it
//! requires internet access and is explicitly network-dependent. Run with:
//!
//! ```sh
//! cargo run -p ytmusic --example live_probe -- "queen bohemian rhapsody"
//! ```

use std::sync::Arc;

use network::{CancellationToken, HttpClient, NetworkConfig};
use ytmusic::{SearchFilter, YtMusicService};

#[tokio::main]
async fn main() {
    let query = std::env::args().nth(1).unwrap_or_else(|| "queen".into());
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let service = YtMusicService::new(http);
    let token = CancellationToken::new();

    println!("LIVE PROBE: query={query:?} filter=Songs");
    let started = std::time::Instant::now();
    match service.search(&query, SearchFilter::Songs, &token).await {
        Ok(page) => {
            println!("OK in {:?}: {} results, continuation={}", started.elapsed(), page.results.len(), page.continuation.is_some());
            for (i, r) in page.results.iter().take(8).enumerate() {
                println!("  {i}: {r:?}");
            }
        }
        Err(e) => {
            println!("FAILED: {e}");
        }
    }
}
