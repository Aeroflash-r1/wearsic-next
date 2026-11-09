use std::sync::Arc;
use network::{CancellationToken, HttpClient, NetworkConfig, Request};

#[tokio::main]
async fn main() {
    let http = HttpClient::new(NetworkConfig::default(), Arc::new(network::Diagnostics::new()));
    let token = CancellationToken::new();
    let video_id = "H3Kzh6RrnMc";

    // 1) IOS player → get formats + visitor from responseContext.
    let ios_payload = serde_json::json!({
        "videoId": video_id,
        "contentCheckOk": true,
        "racyCheckOk": true,
        "context": { "client": {
            "clientName": "IOS", "clientVersion": "21.03.2",
            "deviceModel": "iPhone16,2", "deviceMake": "Apple",
            "osName": "iOS", "osVersion": "18.7.2",
            "hl": "en", "gl": "US"
        }, "user": {} }
    });
    let ios_resp = http.execute(
        Request::post("https://www.youtube.com/youtubei/v1/player?prettyPrint=false", serde_json::to_vec(&ios_payload).unwrap())
            .with_header("content-type", "application/json")
            .with_header("user-agent", "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)")
            .with_header("x-youtube-client-name", "5")
            .with_header("x-youtube-client-version", "21.03.2"),
        &token,
    ).await.unwrap();
    let ios_body = ios_resp.text().unwrap();
    let ios_v: serde_json::Value = serde_json::from_str(&ios_body).unwrap();
    let visitor = ios_v.pointer("/responseContext/visitorData").and_then(|s| s.as_str()).map(str::to_string);
    println!("IOS_VISITOR={:?}", visitor.as_ref().map(|v| v.chars().take(24).collect::<String>()));

    // 2) VISIONOS with that visitor.
    let mut client = serde_json::json!({
        "clientName": "VISIONOS", "clientVersion": "1.02",
        "deviceModel": "RealityDevice14,1", "deviceMake": "Apple",
        "osName": "visionOS", "osVersion": "25.6.0.0",
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
    println!("VISIONOS2 PLAYABILITY={play} reason={reason} len={}", body.len());
    if let Some(fmts) = v.pointer("/streamingData/adaptiveFormats").and_then(|a| a.as_array()) {
        let audio: Vec<_> = fmts.iter().filter(|f| f.get("mimeType").and_then(|m| m.as_str()).unwrap_or("").contains("audio")).collect();
        println!("VISIONOS2 AUDIO_FORMATS={}", audio.len());
        if let Some(f) = audio.first() {
            let url = f.get("url").and_then(|u| u.as_str()).unwrap_or("NO").to_string();
            std::fs::write("/tmp/opencode/vision_url2.txt", &url).ok();
            println!("VISION_URL2_START={}", url.chars().take(100).collect::<String>());
        }
    }
}
