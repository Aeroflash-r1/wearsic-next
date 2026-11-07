use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig, Request};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let token = CancellationToken::new();
    let video_id = std::env::args().nth(1).unwrap_or_else(|| "H3Kzh6RrnMc".into());
    let payload = format!(
        r#"{{"videoId":"{video_id}","contentCheckOk":true,"racyCheckOk":true,"context":{{"client":{{"clientName":"VISIONOS","clientVersion":"1.02","deviceModel":"RealityDevice14,1","deviceMake":"Apple","osName":"visionOS","osVersion":"25.6.0.0","hl":"en","gl":"US"}},"user":{{}}}}}}"#
    );
    let resp = http.execute(
        Request::post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", payload.into_bytes())
            .with_header("content-type", "application/json")
            .with_header("user-agent", "Mozilla/5.0 (Apple TV; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1")
            .with_header("accept", "*/*"),
        &token,
    ).await.unwrap();
    let body = resp.text().unwrap();
    println!("STATUS={} LEN={}", resp.status, body.len());
    std::fs::write("/tmp/opencode/player_response.json", &body).unwrap();
    // Summarize
    let v: serde_json::Value = serde_json::from_str(&body).unwrap();
    let play = v.pointer("/playabilityStatus/status").and_then(|s| s.as_str()).unwrap_or("?");
    let reason = v.pointer("/playabilityStatus/reason").and_then(|s| s.as_str()).unwrap_or("");
    println!("PLAYABILITY={play} REASON={reason}");
    if let Some(fmts) = v.pointer("/streamingData/adaptiveFormats").and_then(|a| a.as_array()) {
        println!("ADAPTIVE_FORMATS={}", fmts.len());
        let mut audio = 0;
        for f in fmts {
            let mime = f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("");
            if mime.contains("audio") {
                audio += 1;
                let url = f.get("url").and_then(|u| u.as_str()).unwrap_or("NO_URL").chars().take(60).collect::<String>();
                let br = f.get("bitrate").and_then(|b| b.as_i64()).unwrap_or(0);
                let itag = f.get("itag").and_then(|i| i.as_i64()).unwrap_or(0);
                let len = f.get("contentLength").and_then(|l| l.as_str()).unwrap_or("");
                let sr = f.get("audioSampleRate").and_then(|s| s.as_str()).unwrap_or("");
                let ch = f.get("audioChannels").and_then(|c| c.as_i64()).unwrap_or(0);
                println!("  audio itag={itag} mime={mime} br={br} len={len} sr={sr} ch={ch} url={url}…");
            }
        }
        println!("AUDIO_FORMATS={audio}");
    }
}
