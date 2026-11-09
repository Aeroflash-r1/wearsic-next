use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig, Request};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let token = CancellationToken::new();
    let video_id = "H3Kzh6RrnMc";

    // 1) Fetch sw.js_data, log size + visitor parse.
    let sw = http.execute(
        Request::get("https://www.youtube.com/sw.js_data")
            .with_header("user-agent", "Mozilla/5.0 (Apple TV; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1")
            .with_header("accept", "*/*"),
        &token,
    ).await.unwrap();
    let sw_text = sw.text().unwrap_or_default();
    println!("SW_JS_DATA status={} len={}", sw.status, sw_text.len());
    let marker = "\"visitorData\":\"";
    let visitor = sw_text.find(marker)
        .map(|i| i + marker.len())
        .and_then(|s| sw_text[s..].find('"').map(|e| sw_text[s..s+e].to_string()));
    println!("VISITOR={:?}", visitor.as_ref().map(|v| v.chars().take(30).collect::<String>()));

    // 2) VISIONOS player with visitor.
    let mut client = serde_json::json!({
        "clientName": "VISIONOS",
        "clientVersion": "1.02",
        "deviceModel": "RealityDevice14,1",
        "deviceMake": "Apple",
        "osName": "visionOS",
        "osVersion": "25.6.0.0",
        "hl": "en", "gl": "US"
    });
    if let Some(v) = &visitor {
        client["visitorData"] = serde_json::Value::String(v.clone());
    }
    let payload = serde_json::json!({
        "videoId": video_id,
        "contentCheckOk": true,
        "racyCheckOk": true,
        "context": { "client": client, "user": {} }
    });
    let resp = http.execute(
        Request::post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", serde_json::to_vec(&payload).unwrap())
            .with_header("content-type", "application/json")
            .with_header("user-agent", "Mozilla/5.0 (Apple TV; CPU OS 15_3 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.3 Mobile/15E148 Safari/604.1"),
        &token,
    ).await.unwrap();
    let body = resp.text().unwrap();
    let v: serde_json::Value = serde_json::from_str(&body).unwrap();
    let play = v.pointer("/playabilityStatus/status").and_then(|s| s.as_str()).unwrap_or("?");
    let reason = v.pointer("/playabilityStatus/reason").and_then(|s| s.as_str()).unwrap_or("");
    println!("VISIONOS PLAYABILITY={play} reason={reason} len={}", body.len());
    if let Some(fmts) = v.pointer("/streamingData/adaptiveFormats").and_then(|a| a.as_array()) {
        let audio: Vec<_> = fmts.iter().filter(|f| f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("").contains("audio")).collect();
        println!("VISIONOS AUDIO_FORMATS={}", audio.len());
        if let Some(f) = audio.first() {
            let url = f.get("url").and_then(|u| u.as_str()).unwrap_or("NO").to_string();
            std::fs::write("/tmp/opencode/vision_url.txt", &url).ok();
            println!("VISION_URL_START={}", url.chars().take(90).collect::<String>());
        }
    }
}
