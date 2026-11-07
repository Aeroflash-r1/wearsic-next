use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let params = Some("EgWKAQIYAWoMEA4QChADEAQQCRAF"); // Albums
    let raw = c.search("coldplay", params, &token).await.unwrap();
    let mut shown = 0;
    fn show(v: &serde_json::Value, shown: &mut i32) {
        if *shown >= 2 { return; }
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                *shown += 1;
                println!("--- ROW #{shown} ---");
                println!("{}", serde_json::to_string_pretty(r).unwrap().chars().take(1800).collect::<String>());
            }
            for val in m.values() { show(val, shown); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { show(x, shown); } }
    }
    show(&raw, &mut shown);
}
