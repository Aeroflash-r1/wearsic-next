use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig, Request};

fn extract_visitor(body: &str) -> Option<String> {
    // sw.js_data contains "visitorData":"Cg..." snippets.
    let marker = "\"visitorData\":\"";
    let start = body.find(marker)? + marker.len();
    let rest = &body[start..];
    let end = rest.find('"')?;
    Some(rest[..end].to_string())
}

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let token = CancellationToken::new();
    let video_id = std::env::args().nth(1).unwrap_or_else(|| "H3Kzh6RrnMc".into());

    // 1) Fetch visitor data.
    let vis = {
        let r = http.execute(
            Request::get("https://www.youtube.com/sw.js_data")
                .with_header("user-agent", "Mozilla/5.0 (Apple TV; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1")
                .with_header("accept", "*/*"),
            &token,
        ).await;
        match r {
            Ok(resp) => extract_visitor(&resp.text().unwrap_or_default()),
            Err(_) => None,
        }
    };
    println!("VISITOR={:?}", vis.as_ref().map(|v| v.chars().take(24).collect::<String>()));

    let visitor_field = vis.map(|v| format!(r#","visitorData":"{v}""#)).unwrap_or_default();
    let payload = format!(
        r#"{{"videoId":"{video_id}","contentCheckOk":true,"racyCheckOk":true,"context":{{"client":{{"clientName":"VISIONOS","clientVersion":"1.02","deviceModel":"RealityDevice14,1","deviceMake":"Apple","osName":"visionOS","osVersion":"25.6.0.0","hl":"en","gl":"US"{visitor_field}}},"user":{{}}}}}}"#
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
    std::fs::write("/tmp/opencode/player_response2.json", &body).unwrap();
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
