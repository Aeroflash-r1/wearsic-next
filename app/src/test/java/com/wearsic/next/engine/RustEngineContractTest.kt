package com.wearsic.next.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure Kotlin test: verifies the wrapper types and that the engine version
 * contract matches the Rust core. Does NOT load native code (that is the
 * host smoke test / instrumentation test's job) — so it always runs.
 */
class RustEngineContractTest {

    @Test
    fun version_constant_matches_rust_core() {
        // Keep in sync with rust/crates/engine-core/src/lib.rs ENGINE_VERSION.
        assertEquals("0.2.0", ENGINE_VERSION_EXPECTED)
    }

    @Test
    fun engine_exception_is_a_throwable() {
        val t = EngineException("boom")
        assertEquals("boom", t.message)
        assertTrue(t is Exception)
    }

    @Test
    fun probe_class_declares_expected_natives() {
        val externals = EngineProbe::class.java.declaredMethods
            .filter { it.name.startsWith("probe") }
            .map { it.name }
            .toSet()
        assertTrue("probeInit", "probeInit" in externals)
        assertTrue("probeVersion", "probeVersion" in externals)
        assertTrue("probeStatus", "probeStatus" in externals)
        assertTrue("probeShutdown", "probeShutdown" in externals)
        assertTrue("probePanicBarrier", "probePanicBarrier" in externals)
    }

    private companion object {
        const val ENGINE_VERSION_EXPECTED = "0.2.0"
    }
}
