//! Integration tests against a local mock HTTP server.
//!
//! The mock server runs ONLY inside these tests. The production engine never
//! runs a server, never listens on a port, and never touches localhost in
//! app code.

use std::net::TcpListener;
use std::sync::atomic::{AtomicUsize, Ordering};
use std::sync::Arc;

use network::{HttpClient, NetworkConfig, Request};

/// A tiny scripted HTTP/1.1 server for tests.
struct MockServer {
    addr: String,
    requests: Arc<AtomicUsize>,
}

impl MockServer {
    fn start(handler: impl Fn(&str) -> (u16, String) + Send + Sync + 'static) -> Self {
        let listener = TcpListener::bind("127.0.0.1:0").expect("bind");
        let addr = listener.local_addr().unwrap().to_string();
        let requests = Arc::new(AtomicUsize::new(0));
        let reqs = requests.clone();
        std::thread::spawn(move || {
            for stream in listener.incoming() {
                let Ok(mut stream) = stream else { continue };
                reqs.fetch_add(1, Ordering::SeqCst);
                let mut buf = [0u8; 4096];
                let n = std::io::Read::read(&mut stream, &mut buf).unwrap_or(0);
                if n == 0 {
                    continue;
                }
                let raw = String::from_utf8_lossy(&buf[..n]).to_string();
                let (status, body) = handler(&raw);
                let reason = match status {
                    200 => "OK",
                    404 => "Not Found",
                    429 => "Too Many Requests",
                    500 => "Internal Server Error",
                    302 => "Found",
                    _ => "X",
                };
                let resp = format!(
                    "HTTP/1.1 {status} {reason}\r\nContent-Type: application/json\r\nContent-Length: {}\r\nConnection: close\r\n\r\n{}",
                    body.len(),
                    body
                );
                let _ = std::io::Write::write_all(&mut stream, resp.as_bytes());
            }
        });
        Self { addr, requests: requests }
    }
}

fn client(cfg: NetworkConfig) -> HttpClient {
    HttpClient::new(cfg, Arc::new(network::Diagnostics::new()))
}

fn cfg() -> NetworkConfig {
    NetworkConfig {
        retry_jitter: false,
        max_attempts: 2,
        max_redirects: 3,
        ..Default::default()
    }
}

fn base(s: &MockServer) -> String {
    format!("http://{}", s.addr)
}

#[tokio::test]
async fn get_success() {
    let s = MockServer::start(|_| (200, r#"{"ok":true}"#.into()));
    let c = client(cfg());
    let r = c.execute(Request::get(format!("{}/hello", base(&s))), &network::CancellationToken::new()).await.unwrap();
    assert_eq!(r.status, 200);
    assert_eq!(r.text().unwrap(), r#"{"ok":true}"#);
    let v: serde_json::Value = r.json().unwrap();
    assert_eq!(v["ok"], true);
}

#[tokio::test]
async fn post_echo() {
    let s = MockServer::start(|raw| {
        if raw.starts_with("POST") {
            (200, r#"{"posted":true}"#.into())
        } else {
            (405, "{}".into())
        }
    });
    let c = client(cfg());
    let r = c
        .execute(Request::post(format!("{}/api", base(&s)), b"{}".to_vec()), &network::CancellationToken::new())
        .await
        .unwrap();
    assert_eq!(r.status, 200);
    assert_eq!(r.text().unwrap(), r#"{"posted":true}"#);
}

#[tokio::test]
async fn http_404_is_permanent() {
    let s = MockServer::start(|_| (404, "{}".into()));
    let c = client(cfg());
    let err = c.execute(Request::get(format!("{}/missing", base(&s))), &network::CancellationToken::new()).await.unwrap_err();
    assert_eq!(err.kind, network::NetworkErrorKind::Http);
    assert_eq!(err.status, 404);
    assert!(err.is_permanent());
}

#[tokio::test]
async fn http_429_retries_then_succeeds() {
    let hits = Arc::new(AtomicUsize::new(0));
    let hits2 = hits.clone();
    let s = MockServer::start(move |_| {
        if hits2.fetch_add(1, Ordering::SeqCst) == 0 {
            (429, "{}".into())
        } else {
            (200, r#"{"ok":true}"#.into())
        }
    });
    let c = client(cfg());
    let r = c.execute(Request::get(format!("{}/rate", base(&s))), &network::CancellationToken::new()).await.unwrap();
    assert_eq!(r.status, 200);
    assert_eq!(hits.load(Ordering::SeqCst), 2);
}

#[tokio::test]
async fn http_500_retries_then_fails_after_limit() {
    let s = MockServer::start(|_| (500, "{}".into()));
    let c = client(NetworkConfig { max_attempts: 3, retry_jitter: false, ..cfg() });
    let err = c.execute(Request::get(format!("{}/boom", base(&s))), &network::CancellationToken::new()).await.unwrap_err();
    assert_eq!(err.kind, network::NetworkErrorKind::Http);
    assert_eq!(err.status, 500);
    assert_eq!(s.requests.load(Ordering::SeqCst), 3);
}

#[tokio::test]
async fn malformed_json_is_invalid_response() {
    let s = MockServer::start(|_| (200, "not json".into()));
    let c = client(cfg());
    let r = c.execute(Request::get(format!("{}/x", base(&s))), &network::CancellationToken::new()).await.unwrap();
    assert!(r.json::<serde_json::Value>().is_err());
}

#[tokio::test]
async fn empty_response_is_ok() {
    let s = MockServer::start(|_| (204, String::new()));
    let c = client(cfg());
    let r = c.execute(Request::get(format!("{}/empty", base(&s))), &network::CancellationToken::new()).await.unwrap();
    assert_eq!(r.status, 204);
    assert!(r.body.is_empty());
}

#[tokio::test]
async fn oversized_response_is_rejected() {
    let big = "x".repeat(64 * 1024);
    let s = MockServer::start(move |_| (200, big.clone()));
    let c = client(NetworkConfig { max_response_bytes: 16 * 1024, ..cfg() });
    let err = c.execute(Request::get(format!("{}/big", base(&s))), &network::CancellationToken::new()).await.unwrap_err();
    assert_eq!(err.kind, network::NetworkErrorKind::BodyTooLarge);
}

#[tokio::test]
async fn same_origin_redirect_followed() {
    // Custom handler emitting a Location header (the simple MockServer cannot).
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap().to_string();
    let addr_for_thread = addr.clone();
    std::thread::spawn(move || {
        for stream in listener.incoming() {
            let Ok(mut stream) = stream else { continue };
            let mut buf = [0u8; 4096];
            let n = std::io::Read::read(&mut stream, &mut buf).unwrap_or(0);
            let raw = String::from_utf8_lossy(&buf[..n]).to_string();
            let (status, headers, body) = if raw.contains("GET /a ") {
                (
                    302,
                    format!("Location: http://{addr_for_thread}/b\r\n"),
                    String::new(),
                )
            } else {
                (200, String::new(), r#"{"ok":true}"#.into())
            };
            let resp = format!(
                "HTTP/1.1 {status} {}\r\n{headers}Content-Length: {}\r\nConnection: close\r\n\r\n{}",
                if status == 302 { "Found" } else { "OK" },
                body.len(),
                body
            );
            let _ = std::io::Write::write_all(&mut stream, resp.as_bytes());
        }
    });
    let c = client(cfg());
    let r = c
        .execute(Request::get(format!("http://{addr}/a")), &network::CancellationToken::new())
        .await
        .unwrap();
    assert_eq!(r.status, 200);
    assert_eq!(r.text().unwrap(), r#"{"ok":true}"#);
}

#[tokio::test]
async fn cross_origin_redirect_refused() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap().to_string();
    std::thread::spawn(move || {
        for stream in listener.incoming() {
            let Ok(mut stream) = stream else { continue };
            let mut buf = [0u8; 4096];
            let _ = std::io::Read::read(&mut stream, &mut buf);
            let resp = "HTTP/1.1 302 Found\r\nLocation: https://evil.example/\r\nContent-Length: 0\r\nConnection: close\r\n\r\n";
            let _ = std::io::Write::write_all(&mut stream, resp.as_bytes());
        }
    });
    let c = client(cfg());
    let err = c
        .execute(Request::get(format!("http://{addr}/go")), &network::CancellationToken::new())
        .await
        .unwrap_err();
    assert!(matches!(err.kind, network::NetworkErrorKind::InvalidResponse));
}

#[tokio::test]
async fn cancellation_stops_request() {
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap().to_string();
    std::thread::spawn(move || {
        for stream in listener.incoming() {
            let Ok(mut stream) = stream else { continue };
            let mut buf = [0u8; 4096];
            let _ = std::io::Read::read(&mut stream, &mut buf);
            // Never respond: hold the connection open.
            std::thread::sleep(std::time::Duration::from_millis(5000));
        }
    });
    let c = client(cfg());
    let token = network::CancellationToken::new();
    let t2 = token.clone();
    let handle = tokio::spawn(async move {
        let _ = c.execute(Request::get(format!("http://{addr}/slow")), &t2).await;
    });
    tokio::task::yield_now().await;
    token.cancel();
    handle.await.unwrap();
}

#[tokio::test]
async fn concurrent_requests_all_succeed() {
    let s = MockServer::start(|_| (200, r#"{"ok":true}"#.into()));
    let c = client(cfg());
    let mut handles = Vec::new();
    for i in 0..8 {
        let c = c.clone();
        let url = format!("{}/c{i}", base(&s));
        handles.push(tokio::spawn(async move {
            c.execute(Request::get(url), &network::CancellationToken::new()).await.unwrap()
        }));
    }
    for h in handles {
        assert_eq!(h.await.unwrap().status, 200);
    }
    assert_eq!(s.requests.load(Ordering::SeqCst), 8);
}

#[tokio::test]
async fn duplicate_requests_are_coalesced() {
    let hits = Arc::new(AtomicUsize::new(0));
    let hits2 = hits.clone();
    let s = MockServer::start(move |_| {
        hits2.fetch_add(1, Ordering::SeqCst);
        (200, r#"{"ok":true}"#.into())
    });
    let c = client(cfg());
    let url = format!("{}/same", base(&s));
    let c1 = c.clone();
    let c2 = c.clone();
    let url1 = url.clone();
    let url2 = url.clone();
    let h1 = tokio::spawn(async move {
        c1.execute(Request::get(url1), &network::CancellationToken::new()).await.unwrap()
    });
    let h2 = tokio::spawn(async move {
        c2.execute(Request::get(url2), &network::CancellationToken::new()).await.unwrap()
    });
    h1.await.unwrap();
    h2.await.unwrap();
    assert_eq!(s.requests.load(Ordering::SeqCst), 1);
}

#[tokio::test]
async fn connection_failure_is_reported() {
    // Connect to an unused port.
    let listener = TcpListener::bind("127.0.0.1:0").unwrap();
    let addr = listener.local_addr().unwrap().to_string();
    drop(listener);
    let c = client(cfg());
    let err = c
        .execute(Request::get(format!("http://{addr}/")), &network::CancellationToken::new())
        .await
        .unwrap_err();
    assert!(matches!(err.kind, network::NetworkErrorKind::Connect | network::NetworkErrorKind::Timeout));
}
