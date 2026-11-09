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

        // --- Phase 3 networking probes ---

        @JvmStatic
        external fun probeNetworkInit(): Boolean

        @JvmStatic
        external fun probeNetworkExecute(requestJson: String): String

        @JvmStatic
        external fun probeNetworkCancel(requestId: Long)

        @JvmStatic
        external fun probeNetworkDiagnostics(): String

        @JvmStatic
        external fun probeNetworkShutdown()

        // --- Phase 4 search probes ---

        @JvmStatic
        external fun probeSearchMusic(requestJson: String): String

        @JvmStatic
        external fun probeSearchCancel(requestId: Long)

        // --- Phase 5 resolution probes ---

        @JvmStatic
        external fun probeResolveStream(requestJson: String): String

        // --- Phase 6 browse probes ---

        @JvmStatic
        external fun probeBrowse(requestJson: String): String

        @JvmStatic
        external fun probeBrowseCancel(requestId: Long)

        // --- Phase 7 suggest probes ---

        @JvmStatic
        external fun probeSuggest(requestJson: String): String
    }
}
