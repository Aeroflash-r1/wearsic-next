use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let f = std::env::args().nth(1).unwrap_or_else(|| "Artists".into());
    let params = match f.as_str() {
        "Artists" => Some("EgWKAQIKAWoMEA4QChADEAQQCRAF"),
        "Albums" => Some("EgWKAQIYAWoMEA4QChADEAQQCRAF"),
        "Playlists" => Some("EgWKAQILAWoMEA4QChADEAQQCRAF"),
        _ => Some("EgWKAQIIAWoMEA4QChADEAQQCRAF"),
    };
    let raw = c.search("coldplay", params, &token).await.unwrap();
    // Summarize renderer types present
    let mut types = std::collections::BTreeMap::<String, usize>::new();
    fn walk(v: &serde_json::Value, types: &mut std::collections::BTreeMap<String, usize>) {
        match v {
            serde_json::Value::Object(m) => {
                for (k, val) in m {
                    if k.ends_with("Renderer") {
                        *types.entry(k.clone()).or_insert(0) += 1;
                    }
                    walk(val, types);
                }
            }
            serde_json::Value::Array(a) => { for x in a { walk(x, types); } }
            _ => {}
        }
    }
    walk(&raw, &mut types);
    for (k, v) in &types { println!("{k}: {v}"); }
    // Dump first musicResponsiveListItemRenderer keys + nav shape
    let mut found = 0;
    fn dump(v: &serde_json::Value, found: &mut i32) {
        if *found >= 2 { return; }
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                *found += 1;
                println!("--- renderer #{found} ---");
                if let Some(fc) = r.get("flexColumns") {
                    if let Some(first) = fc.as_array().and_then(|a| a.first()) {
                        if let Some(text) = first.get("musicResponsiveListItemFlexColumnRenderer").and_then(|x| x.get("text")) {
                            println!("col0 text: {text}");
                        }
                    }
                }
                println!("navs: {}", r.pointer("/navigationEndpoint").unwrap_or(&serde_json::Value::Null));
            }
            for val in m.values() { dump(val, found); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { dump(x, found); } }
    }
    dump(&raw, &mut found);
}
