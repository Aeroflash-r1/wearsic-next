use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig, Request};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let token = CancellationToken::new();
    let video_id = std::env::args().nth(1).unwrap_or_else(|| "H3Kzh6RrnMc".into());

    // Try IOS client (fallback in original).
    let payload = format!(
        r#"{{"videoId":"{video_id}","contentCheckOk":true,"racyCheckOk":true,"context":{{"client":{{"clientName":"IOS","clientVersion":"21.03.2","deviceModel":"iPhone16,2","deviceMake":"Apple","osName":"iOS","osVersion":"18.7.2","hl":"en","gl":"US"}},"user":{{}}}}}}"#
    );
    let resp = http.execute(
        Request::post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", payload.into_bytes())
            .with_header("content-type", "application/json")
            .with_header("user-agent", "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)")
            .with_header("x-youtube-client-name", "5")
            .with_header("x-youtube-client-version", "21.03.2")
            .with_header("accept", "*/*"),
        &token,
    ).await.unwrap();
    let body = resp.text().unwrap();
    println!("STATUS={} LEN={}", resp.status, body.len());
    std::fs::write("/tmp/opencode/player_ios.json", &body).unwrap();
    let v: serde_json::Value = serde_json::from_str(&body).unwrap();
    let play = v.pointer("/playabilityStatus/status").and_then(|s| s.as_str()).unwrap_or("?");
    let reason = v.pointer("/playabilityStatus/reason").and_then(|s| s.as_str()).unwrap_or("");
    println!("PLAYABILITY={play} REASON={reason}");
    if let Some(fmts) = v.pointer("/streamingData/adaptiveFormats").and_then(|a| a.as_array()) {
        println!("FORMATS={} AUDIO={}", fmts.len(), fmts.iter().filter(|f| f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("").contains("audio")).count());
        for f in fmts.iter().filter(|f| f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("").contains("audio")).take(6) {
            let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
            let br = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
            let itag = f.get("itag").and_then(|i| i.as_i64()).unwrap_or(0);
            let len = f.get("contentLength").and_then(|l| l.as_str()).unwrap_or("");
            let sr = f.get("audioSampleRate").and_then(|s| s.as_str()).unwrap_or("");
            let ch = f.get("audioChannels").and_then(|c| c.as_i64()).unwrap_or(0);
            let url = f.get("url").and_then(|u| u.as_str()).unwrap_or("NO").chars().take(50).collect::<String>();
            println!("  itag={itag} {mime} br={br} len={len} sr={sr} ch={ch} {url}…");
        }
    }
}
