use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use ytmusic::{SearchFilter, YtMusicService};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let svc = YtMusicService::new(http);
    let token = CancellationToken::new();
    for f in [SearchFilter::Albums, SearchFilter::Artists, SearchFilter::Playlists, SearchFilter::Videos] {
        let started = std::time::Instant::now();
        match svc.search("coldplay", f, &token).await {
            Ok(page) => {
                println!("{:?}: {} results in {:?} (continuation={})", f, page.results.len(), started.elapsed(), page.continuation.is_some());
                for r in page.results.iter().take(3) {
                    println!("   {r:?}");
                }
            }
            Err(e) => println!("{:?} FAILED: {e}", f),
        }
    }
}
