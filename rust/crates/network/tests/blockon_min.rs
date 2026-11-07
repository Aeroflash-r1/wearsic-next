use network::{NetworkConfig, NetworkEngine};

#[test]
fn block_on_sleep_works() {
    let engine = NetworkEngine::start(NetworkConfig::default());
    engine.block_on(async {
        tokio::time::sleep(std::time::Duration::from_millis(1)).await;
    });
    eprintln!("SLEEP_OK");
}

#[test]
fn block_on_handle_sleep_works() {
    let engine = NetworkEngine::start(NetworkConfig::default());
    let handle = engine.runtime_handle();
    handle.block_on(async {
        tokio::time::sleep(std::time::Duration::from_millis(1)).await;
    });
    eprintln!("HANDLE_SLEEP_OK");
}
