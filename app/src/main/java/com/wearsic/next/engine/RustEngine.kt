package com.wearsic.next.engine

/**
 * Typed JNI bridge to the embedded Rust engine.
 *
 * The Rust side registers these native methods in `JNI_OnLoad` (jni-bridge
 * crate). Loading is explicit via [load] so the app controls when the
 * library is loaded (and so host-JVM tests can load a host-built .so
 * instead).
 */
class RustEngine private constructor() {

    companion object {
        @Volatile
        private var loaded = false

        /** Loads `libwearsic_engine.so` (idempotent). */
        @JvmStatic
        fun load() {
            if (!loaded) {
                System.loadLibrary("wearsic_engine")
                loaded = true
            }
        }

        // --- Native methods (registered by Rust JNI_OnLoad) ---

        /** Creates/returns the engine singleton. True on success. */
        @JvmStatic
        external fun nativeInitialize(): Boolean

        /** Engine version string (e.g. "0.2.0"). */
        @JvmStatic
        external fun nativeGetVersion(): String

        /** Engine status as a compact JSON string. */
        @JvmStatic
        external fun nativeGetStatus(): String

        /** Shuts the engine down. Safe to call repeatedly. */
        @JvmStatic
        external fun nativeShutdown()

        /**
         * Loads + initializes the engine. Throws [EngineException] if the
         * native library is missing or initialization fails.
         */
        @JvmStatic
        fun create(): RustEngine {
            load()
            if (!nativeInitialize()) {
                throw EngineException("Rust engine failed to initialize")
            }
            return RustEngine()
        }
    }

    val version: String get() = nativeGetVersion()
    val status: String get() = nativeGetStatus()
    fun shutdown() = nativeShutdown()
}

/** Structured error surfaced when the native engine cannot start. */
class EngineException(message: String) : Exception(message)
