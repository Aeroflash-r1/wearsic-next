//! Wearsic Next — JNI bridge.
//!
//! Exposes the engine as `libwearsic_engine.so` for Kotlin on Wear OS.
//! Phase 2 interface (all synchronous, all safe):
//!
//! ```text
//! com.wearsic.next.engine.RustEngine
//!   nativeInitialize()    -> Boolean   (true = engine running)
//!   nativeGetVersion()    -> String
//!   nativeGetStatus()     -> String    (compact JSON)
//!   nativeShutdown()                   (idempotent)
//!
//! com.wearsic.next.engine.EngineProbe   (host-JVM test mirror)
//!   probeInit()           -> Boolean
//!   probeVersion()        -> String
//!   probeStatus()         -> String
//!   probeShutdown()
//!   probePanicBarrier()   -> Boolean   (true = panic was contained)
//! ```
//!
//! Phase 3 adds networking:
//!
//! ```text
//! RustEngine:
//!   nativeNetworkInit()                       -> Boolean
//!   nativeNetworkExecute(requestJson)          -> String (response/error JSON)
//!   nativeNetworkCancel(requestId)             -> void
//!   nativeNetworkDiagnostics()                 -> String
//!   nativeNetworkShutdown()                    -> void
//! EngineProbe mirrors: probeNetworkInit/Execute/Cancel/Diagnostics/Shutdown
//! ```
//!
//! Safety / correctness properties implemented here:
//! - Every native entry point wraps its body in `catch_unwind`; a Rust
//!   panic never crosses JNI (returns an error sentinel instead).
//! - The engine lives behind a process-wide `Mutex`; no global mutable
//!   state outside it. No busy loops.
//! - All `unsafe` is confined to this crate's JNI boundary.
//! - Network calls block the *calling* JVM thread (Kotlin workers) — never
//!   the Android main thread — and are cancellable via a token registry.

use std::panic::{catch_unwind, AssertUnwindSafe};
use std::sync::Arc;

use engine_core::Engine;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong, jstring};
use jni::{JNIEnv, NativeMethod, JavaVM};

/// Process-wide engine instance. `Mutex` guards lifecycle transitions.
static ENGINE: std::sync::Mutex<Option<Engine>> = std::sync::Mutex::new(None);

fn with_engine<F: FnOnce(&Engine) -> R, R>(default: R, f: F) -> R {
    match ENGINE.lock() {
        Ok(guard) => match guard.as_ref() {
            Some(engine) => f(engine),
            None => default,
        },
        Err(_) => default, // poisoned lock: report as unavailable
    }
}

/// Runs `f` and maps a panic to a sentinel, never letting it cross JNI.
fn panic_barrier<F, R>(fallback: R, f: F) -> R
where
    F: FnOnce() -> R,
{
    match catch_unwind(AssertUnwindSafe(f)) {
        Ok(value) => value,
        Err(_) => fallback,
    }
}

fn jstring_from(env: &JNIEnv, text: &str) -> jstring {
    match env.new_string(text) {
        Ok(s) => s.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

// ---------------------------------------------------------------------------
// Native entry points (registered by name in JNI_OnLoad)
// ---------------------------------------------------------------------------

fn engine_initialize(env: JNIEnv, _class: JClass) -> jboolean {
    panic_barrier(0, || {
        let mut guard = match ENGINE.lock() {
            Ok(g) => g,
            Err(_) => return 0,
        };
        if guard.is_none() {
            *guard = Some(Engine::new());
        }
        if let Some(engine) = guard.as_ref() {
            engine.init();
            1
        } else {
            0
        }
    })
}

fn engine_get_version(env: JNIEnv, _class: JClass) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let version = with_engine(
            engine_core::ENGINE_VERSION.to_string(),
            |engine| engine.version().to_string(),
        );
        jstring_from(&env, &version)
    })
}

fn engine_get_status(env: JNIEnv, _class: JClass) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let status = with_engine("{\"phase\":\"unavailable\"}".to_string(), |engine| {
            engine.status_json()
        });
        jstring_from(&env, &status)
    })
}

fn engine_shutdown(env: JNIEnv, _class: JClass) {
    panic_barrier((), || {
        if let Ok(mut guard) = ENGINE.lock() {
            if let Some(engine) = guard.as_mut() {
                engine.shutdown();
            }
        }
    });
}

// ---------------------------------------------------------------------------
// Host-probe entry points (identical contract, separate class)
// ---------------------------------------------------------------------------

fn probe_init(env: JNIEnv, _class: JClass) -> jboolean {
    engine_initialize(env, _class)
}

fn probe_version(env: JNIEnv, class: JClass) -> jstring {
    engine_get_version(env, class)
}

fn probe_status(env: JNIEnv, class: JClass) -> jstring {
    engine_get_status(env, class)
}

fn probe_shutdown(env: JNIEnv, class: JClass) {
    engine_shutdown(env, class);
}

/// Deliberately panics inside native code to prove the JNI barrier contains
/// it. Returns true when the panic was caught (i.e. the VM survived).
fn probe_panic_barrier(_env: JNIEnv, _class: JClass) -> jboolean {
    match catch_unwind(AssertUnwindSafe(|| {
        // This panic must be contained; if the barrier were missing, the
        // process would abort at the JNI boundary.
        panic!("intentional JNI panic test");
    })) {
        Ok(_) => 0, // unreachable: the closure always panics
        Err(_) => 1, // panic was contained
    }
}

// ---------------------------------------------------------------------------
// Phase 3 — networking engine (JNI surface)
// ---------------------------------------------------------------------------

use network::{CancellationToken, NetworkConfig, NetworkEngine, Request};

/// Process-wide network engine, started lazily by `network_init`.
static NETWORK: std::sync::Mutex<Option<Arc<NetworkEngine>>> = std::sync::Mutex::new(None);

/// Registry of in-flight request ids -> cancellation tokens, so Kotlin can
/// cancel by id.
static IN_FLIGHT: std::sync::LazyLock<std::sync::Mutex<std::collections::HashMap<u64, CancellationToken>>> =
    std::sync::LazyLock::new(|| {
        std::sync::Mutex::new(std::collections::HashMap::new())
    });

static NEXT_REQUEST_ID: std::sync::atomic::AtomicU64 = std::sync::atomic::AtomicU64::new(1);

fn network_engine() -> Option<Arc<NetworkEngine>> {
    NETWORK.lock().ok().and_then(|g| g.clone())
}

fn network_init() -> bool {
    let mut guard = match NETWORK.lock() {
        Ok(g) => g,
        Err(_) => return false,
    };
    if guard.is_none() {
        *guard = Some(Arc::new(NetworkEngine::start(NetworkConfig::default())));
    }
    true
}

fn network_shutdown() {
    let _ = IN_FLIGHT.lock().map(|mut g| {
        for token in g.values() {
            token.cancel();
        }
        g.clear();
    });
}

fn network_cancel(request_id: u64) {
    if let Ok(g) = IN_FLIGHT.lock() {
        if let Some(token) = g.get(&request_id) {
            token.cancel();
        }
    }
}

fn network_diagnostics_json() -> String {
    match network_engine() {
        Some(engine) => engine.diagnostics_json(),
        None => "{\"total_requests\":0}".to_string(),
    }
}

/// Executes one request JSON on the network engine. Blocks the calling JVM
/// thread (Kotlin worker) until completion, cancellation, or the request
/// timeout. Returns a response/error JSON string.
fn network_execute(request_json: &str) -> String {
    let Some(engine) = network_engine() else {
        return serde_json::json!({"ok": false, "error": {
            "kind": "internal", "status": 0, "message": "network engine not initialised"
        }}).to_string();
    };

    let request: Request = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::json!({"ok": false, "error": {
                "kind": "invalid_url", "status": 0, "message": format!("bad request JSON: {e}")
            }}).to_string();
        }
    };

    let request_id = NEXT_REQUEST_ID.fetch_add(1, std::sync::atomic::Ordering::Relaxed);
    let token = CancellationToken::new();
    let token_for_task = token.clone();
    {
        let mut g = match IN_FLIGHT.lock() {
            Ok(g) => g,
            Err(_) => return "{\"ok\":false,\"error\":{\"kind\":\"internal\",\"status\":0,\"message\":\"registry lock poisoned\"}}".to_string(),
        };
        if g.len() >= 64 {
            return "{\"ok\":false,\"error\":{\"kind\":\"internal\",\"status\":0,\"message\":\"too many concurrent requests\"}}".to_string();
        }
        g.insert(request_id, token.clone());
    }

    let engine_task = engine.clone();
    let task = engine.spawn(async move {
        engine_task.execute(request, &token_for_task).await
    });

    // Wait with a generous outer cap (the request itself has its own timeout,
    // plus cancellation). Blocking here is on a worker thread, never main.
    // NOTE: the `timeout` future must be constructed INSIDE block_on — its
    // inner timer requires a Tokio reactor context at creation.
    let result = engine.block_on(async {
        tokio::time::timeout(std::time::Duration::from_secs(60), task).await
    });

    let _ = IN_FLIGHT.lock().map(|mut g| g.remove(&request_id));

    let outcome = match result {
        Err(_) => Err(network::NetworkError::new(
            network::NetworkErrorKind::Timeout,
            "engine task timed out",
        )),
        Ok(join) => match join {
            Err(_) => Err(network::NetworkError::new(
                network::NetworkErrorKind::Internal,
                "engine task panicked",
            )),
            Ok(inner) => inner,
        },
    };

    match outcome {
        Ok(response) => serde_json::json!({
            "ok": true,
            "status": response.status,
            "headers": response.headers,
            "body": String::from_utf8_lossy(&response.body).to_string(),
        })
        .to_string(),
        Err(err) => serde_json::json!({
            "ok": false,
            "error": {
                // Use serde's snake_case renaming, not Debug formatting.
                "kind": serde_json::to_value(err.kind).unwrap_or_else(|_| "internal".into()),
                "status": err.status,
                "message": err.message,
            },
        })
        .to_string(),
    }
}

fn network_init_jni(_env: &JNIEnv, _class: JClass) -> jboolean {
    panic_barrier(false, network_init) as jboolean
}

fn network_shutdown_jni(_env: &JNIEnv, _class: JClass) {
    panic_barrier((), network_shutdown);
}

fn network_cancel_jni(_env: &JNIEnv, _class: JClass, request_id: jlong) {
    panic_barrier((), || network_cancel(request_id as u64));
}

fn network_diagnostics_jni(env: JNIEnv, _class: JClass) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        jstring_from(&env, &network_diagnostics_json())
    })
}

fn network_execute_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let body: String = env
            .get_string(&request_json)
            .map(|s| s.into())
            .unwrap_or_default();
        jstring_from(&env, &network_execute(&body))
    })
}

fn probe_network_init(_env: &JNIEnv, _class: JClass) -> jboolean {
    panic_barrier(false, network_init) as jboolean
}

fn probe_network_shutdown(_env: &JNIEnv, _class: JClass) {
    panic_barrier((), network_shutdown);
}

fn probe_network_cancel(_env: &JNIEnv, _class: JClass, request_id: jlong) {
    panic_barrier((), || network_cancel(request_id as u64));
}

fn probe_network_diagnostics(env: JNIEnv, _class: JClass) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        jstring_from(&env, &network_diagnostics_json())
    })
}

fn probe_network_execute(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let body: String = env
            .get_string(&request_json)
            .map(|s| s.into())
            .unwrap_or_default();
        jstring_from(&env, &network_execute(&body))
    })
}

// ---------------------------------------------------------------------------
// Phase 4 — music discovery (search) JNI surface
// ---------------------------------------------------------------------------

/// Global YTM service, started lazily with the network engine.
static YTM: std::sync::Mutex<Option<ytmusic::YtMusicService>> = std::sync::Mutex::new(None);

fn ytm_service() -> Option<ytmusic::YtMusicService> {
    let net = network_engine()?;
    let mut guard = YTM.lock().ok()?;
    if guard.is_none() {
        *guard = Some(ytmusic::YtMusicService::new(net.client.clone()));
    }
    guard.clone()
}

/// Search request envelope from Kotlin.
#[derive(serde::Deserialize)]
#[serde(rename_all = "snake_case")]
struct SearchRequestJson {
    query: String,
    #[serde(default)]
    filter: Option<String>,
    #[serde(default)]
    continuation: Option<String>,
    #[serde(default)]
    request_id: Option<u64>,
}

fn search_execute(request_json: &str) -> String {
    let Some(service) = ytm_service() else {
        return serde_json::json!({"ok": false, "error": {
            "kind": "internal", "status": 0, "message": "network engine not initialised"
        }}).to_string();
    };

    let req: SearchRequestJson = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::json!({"ok": false, "error": {
                "kind": "invalid_response", "status": 0, "message": format!("bad request JSON: {e}")
            }}).to_string();
        }
    };

    let token = CancellationToken::new();
    let request_id = req.request_id.unwrap_or(0);
    if request_id != 0 {
        if let Ok(mut g) = IN_FLIGHT.lock() {
            g.insert(request_id, token.clone());
        }
    }

    let engine = network_engine().unwrap();
    let service_task = service.clone();
    let token_task = token.clone();
    let task = engine.spawn(async move {
        if let Some(cont) = req.continuation.clone() {
            service_task.load_more(&cont, ytmusic::SearchFilter::Songs, &token_task).await
        } else {
            service_task.search(&req.query, ytmusic::SearchFilter::Songs, &token_task).await
        }
    });

    let result = engine.block_on(async {
        tokio::time::timeout(std::time::Duration::from_secs(60), task).await
    });

    if request_id != 0 {
        let _ = IN_FLIGHT.lock().map(|mut g| g.remove(&request_id));
    }

    let outcome = match result {
        Err(_) => Err(network::NetworkError::new(
            network::NetworkErrorKind::Timeout,
            "search timed out",
        )),
        Ok(join) => match join {
            Err(_) => Err(network::NetworkError::new(
                network::NetworkErrorKind::Internal,
                "search task panicked",
            )),
            Ok(inner) => inner.map_err(|e| {
                network::NetworkError::new(network::NetworkErrorKind::Internal, e)
            }),
        },
    };

    match outcome {
        Ok(page) => serde_json::json!({
            "ok": true,
            "results": page.results,
            "continuation": page.continuation,
        })
        .to_string(),
        Err(err) => serde_json::json!({
            "ok": false,
            "error": {
                "kind": serde_json::to_value(err.kind).unwrap_or_else(|_| "internal".into()),
                "status": err.status,
                "message": err.message,
            },
        })
        .to_string(),
    }
}

fn search_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let body: String = env
            .get_string(&request_json)
            .map(|s| s.into())
            .unwrap_or_default();
        jstring_from(&env, &search_execute(&body))
    })
}

fn probe_search_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    search_jni(env, _class, request_json)
}

fn search_cancel_jni(_env: &JNIEnv, _class: JClass, request_id: jlong) {
    panic_barrier((), || network_cancel(request_id as u64));
}

fn probe_search_cancel_jni(_env: &JNIEnv, _class: JClass, request_id: jlong) {
    panic_barrier((), || network_cancel(request_id as u64));
}

// ---------------------------------------------------------------------------
// Phase 6 — browse pages (album / playlist / artist) JNI surface
// ---------------------------------------------------------------------------

#[derive(serde::Deserialize)]
#[serde(rename_all = "snake_case")]
struct BrowseRequestJson {
    browse_id: String,
    #[serde(default)]
    continuation: Option<String>,
    #[serde(default)]
    request_id: Option<u64>,
}

fn browse_execute(request_json: &str) -> String {
    let Some(service) = ytm_service() else {
        return serde_json::json!({"ok": false, "error": {
            "kind": "internal", "status": 0, "message": "network engine not initialised"
        }}).to_string();
    };

    let req: BrowseRequestJson = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::json!({"ok": false, "error": {
                "kind": "invalid_response", "status": 0, "message": format!("bad request JSON: {e}")
            }}).to_string();
        }
    };

    let token = CancellationToken::new();
    let request_id = req.request_id.unwrap_or(0);
    if request_id != 0 {
        if let Ok(mut g) = IN_FLIGHT.lock() {
            g.insert(request_id, token.clone());
        }
    }

    let engine = network_engine().unwrap();
    let service_task = service.clone();
    let token_task = token.clone();
    let browse_id = req.browse_id.clone();
    let continuation = req.continuation.clone();
    let task = engine.spawn(async move {
        if let Some(cont) = continuation {
            service_task.browse_more(&cont, &token_task).await
        } else {
            service_task.browse(&browse_id, &token_task).await
        }
    });

    let result = engine.block_on(async {
        tokio::time::timeout(std::time::Duration::from_secs(60), task).await
    });

    if request_id != 0 {
        let _ = IN_FLIGHT.lock().map(|mut g| g.remove(&request_id));
    }

    match result {
        Ok(Ok(Ok(page))) => serde_json::json!({"ok": true, "page": page}).to_string(),
        Ok(Ok(Err(message))) => serde_json::json!({
            "ok": false,
            "error": { "kind": "internal", "status": 0, "message": message },
        })
        .to_string(),
        Ok(Err(_)) => serde_json::json!({
            "ok": false,
            "error": { "kind": "internal", "status": 0, "message": "browse task panicked" },
        })
        .to_string(),
        Err(_) => serde_json::json!({
            "ok": false,
            "error": { "kind": "timeout", "status": 0, "message": "browse timed out" },
        })
        .to_string(),
    }
}

fn browse_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let body: String = env
            .get_string(&request_json)
            .map(|s| s.into())
            .unwrap_or_default();
        jstring_from(&env, &browse_execute(&body))
    })
}

fn probe_browse_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    browse_jni(env, _class, request_json)
}

fn browse_cancel_jni(_env: &JNIEnv, _class: JClass, request_id: jlong) {
    panic_barrier((), || network_cancel(request_id as u64));
}

// ---------------------------------------------------------------------------
// Phase 5 — stream resolution JNI surface
// ---------------------------------------------------------------------------

static RESOLVER: std::sync::Mutex<Option<stream_resolver::StreamResolver>> =
    std::sync::Mutex::new(None);

fn stream_resolver() -> Option<stream_resolver::StreamResolver> {
    let net = network_engine()?;
    let mut guard = RESOLVER.lock().ok()?;
    if guard.is_none() {
        *guard = Some(stream_resolver::StreamResolver::new(net.client.clone()));
    }
    guard.clone()
}

#[derive(serde::Deserialize)]
#[serde(rename_all = "snake_case")]
struct ResolveRequestJson {
    video_id: String,
    #[serde(default)]
    request_id: Option<u64>,
}

fn resolve_execute(request_json: &str) -> String {
    let Some(resolver) = stream_resolver() else {
        return serde_json::json!({"ok": false, "error": {
            "kind": "internal", "status": 0, "message": "network engine not initialised"
        }}).to_string();
    };

    let req: ResolveRequestJson = match serde_json::from_str(request_json) {
        Ok(r) => r,
        Err(e) => {
            return serde_json::json!({"ok": false, "error": {
                "kind": "invalid_track", "status": 0, "message": format!("bad request JSON: {e}")
            }}).to_string();
        }
    };

    let token = CancellationToken::new();
    let request_id = req.request_id.unwrap_or(0);
    if request_id != 0 {
        if let Ok(mut g) = IN_FLIGHT.lock() {
            g.insert(request_id, token.clone());
        }
    }

    let engine = network_engine().unwrap();
    let resolver_task = resolver.clone();
    let token_task = token.clone();
    let video_id = req.video_id.clone();
    let task = engine.spawn(async move {
        resolver_task.resolve(&video_id, &token_task).await
    });

    let result = engine.block_on(async {
        tokio::time::timeout(std::time::Duration::from_secs(60), task).await
    });

    if request_id != 0 {
        let _ = IN_FLIGHT.lock().map(|mut g| g.remove(&request_id));
    }

    match result {
        Ok(Ok(Ok(desc))) => serde_json::json!({
            "ok": true,
            "stream": desc,
        })
        .to_string(),
        Ok(Ok(Err(err))) => serde_json::json!({
            "ok": false,
            "error": {
                "kind": serde_json::to_value(err.kind).unwrap_or_else(|_| "internal".into()),
                "status": 0,
                "message": err.message,
            },
        })
        .to_string(),
        Ok(Err(_)) => serde_json::json!({
            "ok": false,
            "error": { "kind": "internal", "status": 0, "message": "resolution task panicked" },
        })
        .to_string(),
        Err(_) => serde_json::json!({
            "ok": false,
            "error": { "kind": "timeout", "status": 0, "message": "resolution timed out" },
        })
        .to_string(),
    }
}

fn resolve_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    panic_barrier(std::ptr::null_mut(), || {
        let body: String = env
            .get_string(&request_json)
            .map(|s| s.into())
            .unwrap_or_default();
        jstring_from(&env, &resolve_execute(&body))
    })
}

fn probe_resolve_jni(mut env: JNIEnv, _class: JClass, request_json: JString) -> jstring {
    resolve_jni(env, _class, request_json)
}

// ---------------------------------------------------------------------------
// JNI_OnLoad — registers the native method tables
// ---------------------------------------------------------------------------

#[no_mangle]
pub extern "system" fn JNI_OnLoad(vm: JavaVM, _reserved: *mut std::ffi::c_void) -> jint {
    let mut env = match vm.get_env() {
        Ok(env) => env,
        Err(_) => return 0,
    };

    fn m(name: &str, signature: &str, fn_ptr: *mut std::ffi::c_void) -> NativeMethod {
        NativeMethod {
            name: name.into(),
            sig: signature.into(),
            fn_ptr,
        }
    }

    let rust_engine_methods = [
        m("nativeInitialize", "()Z", engine_initialize as *mut std::ffi::c_void),
        m(
            "nativeGetVersion",
            "()Ljava/lang/String;",
            engine_get_version as *mut std::ffi::c_void,
        ),
        m(
            "nativeGetStatus",
            "()Ljava/lang/String;",
            engine_get_status as *mut std::ffi::c_void,
        ),
        m("nativeShutdown", "()V", engine_shutdown as *mut std::ffi::c_void),
        m("nativeNetworkInit", "()Z", network_init_jni as *mut std::ffi::c_void),
        m(
            "nativeNetworkExecute",
            "(Ljava/lang/String;)Ljava/lang/String;",
            network_execute_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeNetworkCancel",
            "(J)V",
            network_cancel_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeNetworkDiagnostics",
            "()Ljava/lang/String;",
            network_diagnostics_jni as *mut std::ffi::c_void,
        ),
        m("nativeNetworkShutdown", "()V", network_shutdown_jni as *mut std::ffi::c_void),
        m(
            "nativeSearchMusic",
            "(Ljava/lang/String;)Ljava/lang/String;",
            search_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeSearchCancel",
            "(J)V",
            search_cancel_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeResolveStream",
            "(Ljava/lang/String;)Ljava/lang/String;",
            resolve_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeBrowse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            browse_jni as *mut std::ffi::c_void,
        ),
        m(
            "nativeBrowseCancel",
            "(J)V",
            browse_cancel_jni as *mut std::ffi::c_void,
        ),
    ];

    let engine_probe_methods = [
        m("probeInit", "()Z", probe_init as *mut std::ffi::c_void),
        m(
            "probeVersion",
            "()Ljava/lang/String;",
            probe_version as *mut std::ffi::c_void,
        ),
        m(
            "probeStatus",
            "()Ljava/lang/String;",
            probe_status as *mut std::ffi::c_void,
        ),
        m("probeShutdown", "()V", probe_shutdown as *mut std::ffi::c_void),
        m(
            "probePanicBarrier",
            "()Z",
            probe_panic_barrier as *mut std::ffi::c_void,
        ),
        m("probeNetworkInit", "()Z", probe_network_init as *mut std::ffi::c_void),
        m(
            "probeNetworkExecute",
            "(Ljava/lang/String;)Ljava/lang/String;",
            probe_network_execute as *mut std::ffi::c_void,
        ),
        m(
            "probeNetworkCancel",
            "(J)V",
            probe_network_cancel as *mut std::ffi::c_void,
        ),
        m(
            "probeNetworkDiagnostics",
            "()Ljava/lang/String;",
            probe_network_diagnostics as *mut std::ffi::c_void,
        ),
        m("probeNetworkShutdown", "()V", probe_network_shutdown as *mut std::ffi::c_void),
        m(
            "probeSearchMusic",
            "(Ljava/lang/String;)Ljava/lang/String;",
            probe_search_jni as *mut std::ffi::c_void,
        ),
        m(
            "probeSearchCancel",
            "(J)V",
            probe_search_cancel_jni as *mut std::ffi::c_void,
        ),
        m(
            "probeResolveStream",
            "(Ljava/lang/String;)Ljava/lang/String;",
            probe_resolve_jni as *mut std::ffi::c_void,
        ),
        m(
            "probeBrowse",
            "(Ljava/lang/String;)Ljava/lang/String;",
            probe_browse_jni as *mut std::ffi::c_void,
        ),
        m(
            "probeBrowseCancel",
            "(J)V",
            browse_cancel_jni as *mut std::ffi::c_void,
        ),
    ];

    let ok_engine = env
        .register_native_methods("com/wearsic/next/engine/RustEngine", &rust_engine_methods)
        .is_ok();
    let ok_probe = env
        .register_native_methods("com/wearsic/next/engine/EngineProbe", &engine_probe_methods)
        .is_ok();

    if ok_engine && ok_probe {
        jni::sys::JNI_VERSION_1_6
    } else {
        0
    }
}
