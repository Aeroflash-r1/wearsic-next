use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let raw = c.search("coldplay", None::<&str>, &token).await.unwrap();
    let mut artist_shown = 0;
    let mut other_shown = 0;
    fn inspect(v: &serde_json::Value, artist_shown: &mut i32, other_shown: &mut i32) {
        if let serde_json::Value::Object(m) = v {
            if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                let s = serde_json::to_string(r).unwrap();
                if s.contains("MUSIC_PAGE_TYPE_ARTIST") && *artist_shown < 1 {
                    *artist_shown += 1;
                    let title = r.pointer("/flexColumns/0/musicResponsiveListItemFlexColumnRenderer/text").unwrap();
                    let nav = r.get("navigationEndpoint").unwrap_or(&serde_json::Value::Null);
                    println!("ARTIST ROW\ntitle={title}\nnav={nav}");
                } else if !s.contains("\"watchEndpoint\"") && !s.contains("MUSIC_PAGE_TYPE_ARTIST") && *other_shown < 2 {
                    *other_shown += 1;
                    println!("OTHER ROW #{other_shown}\n{}", s.chars().take(800).collect::<String>());
                }
            }
            for val in m.values() { inspect(val, artist_shown, other_shown); }
        }
        if let serde_json::Value::Array(a) = v { for x in a { inspect(x, artist_shown, other_shown); } }
    }
    inspect(&raw, &mut artist_shown, &mut other_shown);
}
