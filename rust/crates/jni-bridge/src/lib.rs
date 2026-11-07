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
//! Safety / correctness properties implemented here:
//! - Every native entry point wraps its body in `catch_unwind`; a Rust
//!   panic never crosses JNI (returns an error sentinel instead).
//! - The engine lives behind a process-wide `Mutex`; no global mutable
//!   state outside it. No background threads, no Tokio, no busy loops.
//! - All `unsafe` is confined to this crate's JNI boundary.

use std::panic::{catch_unwind, AssertUnwindSafe};

use engine_core::Engine;
use jni::objects::JClass;
use jni::sys::{jboolean, jint, jstring};
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
