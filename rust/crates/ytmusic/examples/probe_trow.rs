use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let raw = c.search("coldplay", None::<&str>, &token).await.unwrap();
    let mut shown = 0;
    fn show(v: &serde_json::Value, shown: &mut i32) {
        if *shown >= 1 { return; }
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                let s = serde_json::to_string(r).unwrap();
                if s.contains("\"watchEndpoint\"") {
                    *shown += 1;
                    let fc = r.get("flexColumns").unwrap();
                    println!("FLEXCOLS: {}", serde_json::to_string_pretty(fc).unwrap().chars().take(1600).collect::<String>());
                }
            }
            for val in m.values() { show(val, shown); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { show(x, shown); } }
    }
    show(&raw, &mut shown);
}
