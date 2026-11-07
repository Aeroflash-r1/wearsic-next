package com.wearsic.next.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Host-JVM verification of the JNI contract.
 *
 * Loads the HOST-built Rust engine (rust/target/debug/libwearsic_engine.so)
 * through [EngineProbe] — the same native methods the app's RustEngine uses.
 * This proves the JNI ABI, marshalling, lifecycle and panic barrier run on a
 * real JVM. It does NOT prove on-device loading (that requires a watch /
 * instrumentation test).
 */
class EngineProbeHostSmokeTest {

    companion object {
        private val hostSo: File =
            File(System.getProperty("user.dir"), "../rust/target/debug/libwearsic_engine.so")

        @BeforeClass
        @JvmStatic
        fun loadHostEngine() {
            assumeTrue("host Rust .so not built (run: cargo build in rust/)", hostSo.exists())
            System.load(hostSo.absolutePath)
        }
    }

    @Test
    fun initialize_returns_true() {
        assertTrue(EngineProbe.probeInit())
    }

    @Test
    fun version_matches_engine_core() {
        assertEquals("0.2.0", EngineProbe.probeVersion())
    }

    @Test
    fun status_is_compact_json_and_running() {
        assertTrue(EngineProbe.probeInit())
        val status = EngineProbe.probeStatus()
        assertTrue("status should contain phase, got: $status", status.contains("\"phase\":\"running\""))
        assertTrue(status.contains("\"version\":\"0.2.0\""))
        assertTrue(status.contains("\"uptime_s\""))
        assertTrue(status.contains("\"ops\""))
    }

    @Test
    fun init_is_idempotent() {
        assertTrue(EngineProbe.probeInit())
        assertTrue(EngineProbe.probeInit())
        val status = EngineProbe.probeStatus()
        assertTrue(status.contains("\"phase\":\"running\""))
    }

    @Test
    fun shutdown_then_reinit_works() {
        assertTrue(EngineProbe.probeInit())
        EngineProbe.probeShutdown()
        // idempotent shutdown
        EngineProbe.probeShutdown()
        assertTrue(EngineProbe.probeInit())
        assertTrue(EngineProbe.probeStatus().contains("\"phase\":\"running\""))
    }

    @Test
    fun panic_never_crosses_jni() {
        // If the barrier failed, this would crash the test JVM.
        assertTrue(EngineProbe.probePanicBarrier())
        // Engine still usable after a contained panic.
        assertTrue(EngineProbe.probeInit())
        assertEquals("0.2.0", EngineProbe.probeVersion())
    }

    @Test
    fun multiple_sequences_are_stable() {
        repeat(5) {
            assertTrue(EngineProbe.probeInit())
            val v = EngineProbe.probeVersion()
            assertEquals("0.2.0", v)
            EngineProbe.probeShutdown()
        }
        // After all shutdowns, status reports shutdown (not crash).
        assertFalse(EngineProbe.probeStatus().contains("\"phase\":\"running\""))
    }
}
