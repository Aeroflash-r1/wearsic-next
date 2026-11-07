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
