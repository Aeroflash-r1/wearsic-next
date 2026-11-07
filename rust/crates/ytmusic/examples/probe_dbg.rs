use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;
use innertube::context::{InnerTubeContext, SearchBody};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let body = SearchBody {
        context: InnerTubeContext::web_remix(),
        query: "coldplay".into(),
        params: Some("EgWKAQIIAWoMEA4QChADEAQQCRAF".into()),
    };
    let payload = serde_json::to_string(&body).unwrap();
    println!("URL: {}", innertube::context::search_url());
    println!("BODY: {payload}");
    let token = CancellationToken::new();
    match c.post_json(&innertube::context::search_url(), &body, &token).await {
        Ok(v) => println!("OK: {}", &serde_json::to_string(&v).unwrap()[..200]),
        Err(e) => println!("ERR: {e:?}"),
    }
}
