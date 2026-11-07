use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use stream_resolver::StreamResolver;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let resolver = StreamResolver::new(http);
    let token = CancellationToken::new();
    let video_id = std::env::args().nth(1).unwrap_or_else(|| "H3Kzh6RrnMc".into());
    let started = std::time::Instant::now();
    match resolver.resolve(&video_id, &token).await {
        Ok(desc) => {
            println!("OK in {:?}", started.elapsed());
            println!("  url: {}", &desc.url[..90]);
            println!("  mime: {}", desc.mime_type);
            println!("  codec: {:?} bitrate: {:?} sample_rate: {:?} channels: {:?}", desc.codec, desc.bitrate, desc.sample_rate, desc.channels);
            println!("  content_length: {} source: {}", desc.content_length, desc.source);
            let t2 = std::time::Instant::now();
            let _ = resolver.resolve(&video_id, &token).await.unwrap();
            println!("CACHED in {:?}", t2.elapsed());
        }
        Err(e) => println!("FAILED: {e:?}"),
    }
}
