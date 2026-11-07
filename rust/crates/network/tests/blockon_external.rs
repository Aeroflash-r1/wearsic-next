use network::{CancellationToken, NetworkConfig, NetworkEngine, Request};

#[test]
fn block_on_timeout_from_external_thread_works() {
    let engine = NetworkEngine::start(NetworkConfig::default());
    let engine2 = engine.clone();
    let task = engine.spawn(async move {
        engine2
            .execute(Request::get("http://127.0.0.1:1/nope"), &CancellationToken::new())
            .await
    });
    // Simulate the JNI thread: construct the timeout INSIDE block_on.
    let result = engine.block_on(async {
        tokio::time::timeout(std::time::Duration::from_secs(5), task).await
    });
    assert!(result.is_ok());
}
