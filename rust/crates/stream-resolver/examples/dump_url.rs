use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use stream_resolver::StreamResolver;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let resolver = StreamResolver::new(http);
    let token = CancellationToken::new();
    let video_id = std::env::args().nth(1).unwrap_or_else(|| "H3Kzh6RrnMc".into());
    match resolver.resolve(&video_id, &token).await {
        Ok(desc) => {
            println!("URL={}", desc.url);
            println!("MIME={}", desc.mime_type);
        }
        Err(e) => println!("FAILED: {e:?}"),
    }
}
