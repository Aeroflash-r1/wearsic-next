use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let f = std::env::args().nth(1).unwrap_or_else(|| "Albums".into());
    let params = match f.as_str() {
        "Artists" => Some("EgWKAQIKAWoMEA4QChADEAQQCRAF"),
        "Albums" => Some("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
        "Playlists" => Some("EgWKAQILAWoMEA4QChADEAQQCRAF"),
        _ => Some("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    };
    let raw = c.search("coldplay", params, &token).await.unwrap();
    // Count rows whose first flex column has a browseEndpoint vs watchEndpoint
    let mut count = 0usize;
    let mut browse = 0usize;
    let mut watch = 0usize;
    fn inspect(v: &serde_json::Value, count: &mut usize, browse: &mut usize, watch: &mut usize) {
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                *count += 1;
                if let Some(fc) = r.get("flexColumns").and_then(|x| x.as_array()).and_then(|a| a.first()) {
                    if let Some(text) = fc.get("musicResponsiveListItemFlexColumnRenderer").and_then(|x| x.get("text")) {
                        let s = text.to_string();
                        if s.contains("browseEndpoint") { *browse += 1; }
                        if s.contains("watchEndpoint") { *watch += 1; }
                    }
                }
            }
            for val in m.values() { inspect(val, count, browse, watch); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { inspect(x, count, browse, watch); } }
    }
    inspect(&raw, &mut count, &mut browse, &mut watch);
    println!("{f}: rows={count} browseTitle={browse} watchTitle={watch}");
    // Show the first browse-title renderer fully if any
    let mut shown = 0;
    fn show(v: &serde_json::Value, shown: &mut i32, target: &str) {
        if *shown >= 1 { return; }
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                if let Some(fc) = r.get("flexColumns").and_then(|x| x.as_array()).and_then(|a| a.first()) {
                    if let Some(text) = fc.get("musicResponsiveListItemFlexColumnRenderer").and_then(|x| x.get("text")) {
                        if text.to_string().contains(target) {
                            *shown += 1;
                            println!("FIRST BROWSE RENDERER: {r}");
                        }
                    }
                }
            }
            for val in m.values() { show(val, shown, target); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { show(x, shown, target); } }
    }
    show(&raw, &mut shown, "browseEndpoint");
}
