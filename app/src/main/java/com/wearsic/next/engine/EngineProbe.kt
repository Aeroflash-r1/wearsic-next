package com.wearsic.next.engine

/**
 * Host-JVM probe mirror of [RustEngine]'s native methods.
 *
 * The Rust `JNI_OnLoad` registers the same method table for this class, so a
 * plain JVM unit test can load a host-built `libwearsic_engine.so`
 * (rust/target/debug/libwearsic_engine.so) and exercise the full JNI
 * contract without an Android device.
 */
class EngineProbe {

    companion object {
        @JvmStatic
        external fun probeInit(): Boolean

        @JvmStatic
        external fun probeVersion(): String

        @JvmStatic
        external fun probeStatus(): String

        @JvmStatic
        external fun probeShutdown()

        /**
         * Asks the engine to panic inside a native call; returns true when
         * the panic was contained by the JNI barrier (never crashes the VM).
         */
        @JvmStatic
        external fun probePanicBarrier(): Boolean
    }
}
