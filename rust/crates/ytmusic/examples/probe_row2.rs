use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let params = Some("EgWKAQIYAWoMEA4QChADEAQQCRAF");
    let raw = c.search("coldplay", params, &token).await.unwrap();
    let mut shown = 0;
    fn show(v: &serde_json::Value, shown: &mut i32) {
        if *shown >= 1 { return; }
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                *shown += 1;
                let s = serde_json::to_string_pretty(r).unwrap();
                // Print only the parts we care about: thumbnail + overlay + first flex col + navigationEndpoint
                for key in ["thumbnail", "overlay", "navigationEndpoint", "playlistItemData", "flexColumns"] {
                    if let Some(part) = r.get(key) {
                        let text = serde_json::to_string_pretty(part).unwrap();
                        println!("== {key} ==\n{}", text.chars().take(900).collect::<String>());
                    }
                }
            }
            for val in m.values() { show(val, shown); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { show(x, shown); } }
    }
    show(&raw, &mut shown);
}
