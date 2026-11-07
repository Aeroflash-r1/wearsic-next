use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig};
use innertube::InnerTubeClient;

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let c = InnerTubeClient::new(http);
    let token = CancellationToken::new();
    let filters = [
        ("Songs", Some("EgWKAQIIAWoMEA4QChADEAQQCRAF")),
        ("Videos", Some("EgWKAQIQAWoMEA4QChADEAQQCRAF")),
        ("Albums", Some("EgWKAQIYAWoMEA4QChADEAQQCRAF")),
        ("Artists", Some("EgWKAQIKAWoMEA4QChADEAQQCRAF")),
        ("Playlists", Some("EgWKAQILAWoMEA4QChADEAQQCRAF")),
    ];
    for (name, params) in filters {
        let raw = c.search("coldplay", params, &token).await.unwrap();
        let mut watch = 0; let mut album = 0; let mut artist = 0; let mut playlist = 0; let mut other = 0;
        fn inspect(v: &serde_json::Value, w: &mut i32, al: &mut i32, ar: &mut i32, pl: &mut i32, o: &mut i32) {
            if let serde_json::Value::Object(m) = v {
                if let Some(r) = m.get("musicResponsiveListItemRenderer") {
                    let s = serde_json::to_string(r).unwrap();
                    if s.contains("\"watchEndpoint\"") { *w += 1; }
                    else if s.contains("MUSIC_PAGE_TYPE_ALBUM") { *al += 1; }
                    else if s.contains("MUSIC_PAGE_TYPE_ARTIST") { *ar += 1; }
                    else if s.contains("MUSIC_PAGE_TYPE_PLAYLIST") || s.contains("\"browseId\":\"VL") { *pl += 1; }
                    else { *o += 1; }
                }
                for val in m.values() { inspect(val, w, al, ar, pl, o); }
            }
            if let serde_json::Value::Array(a) = v { for x in a { inspect(x, w, al, ar, pl, o); } }
        }
        inspect(&raw, &mut watch, &mut album, &mut artist, &mut playlist, &mut other);
        println!("{name}: watch={watch} album={album} artist={artist} playlist={playlist} other={other}");
    }
}
