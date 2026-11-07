package com.wearsic.next.engine

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * LIVE network-dependent JNI stream resolution test (real YouTube Music).
 * Separate from the always-green suite; requires internet access.
 */
class EngineProbeLiveResolveTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val hostSo: File =
            File(System.getProperty("user.dir"), "../rust/target/debug/libwearsic_engine.so")

        @BeforeClass
        @JvmStatic
        fun loadHostEngine() {
            assumeTrue("host Rust .so not built", hostSo.exists())
            System.load(hostSo.absolutePath)
            EngineProbe.probeNetworkShutdown()
            assertTrue(EngineProbe.probeNetworkInit())
        }
    }

    private fun resolve(videoId: String): ResolveEnvelope {
        val raw = EngineProbe.probeResolveStream(
            Json.encodeToString(
                ResolveRequest.serializer(),
                ResolveRequest(videoId = videoId),
            ),
        )
        return json.decodeFromString(ResolveEnvelope.serializer(), raw)
    }

    @Test
    fun live_resolve_returns_playable_stream() {
        val env = resolve("H3Kzh6RrnMc") // "Hymn for the Weekend" — Coldplay
        assertTrue("expected ok, got: $env", env.ok)
        val stream = env.stream ?: error("no stream in $env")
        assertTrue("need https stream URL", stream.url.startsWith("https://"))
        assertTrue("need audio mime", stream.mimeType.contains("audio"))
        assertTrue("need non-empty source", stream.source.isNotBlank())
        println("LIVE[RESOLVE] ${stream.videoId} ${stream.mimeType} br=${stream.bitrate} len=${stream.contentLength} via ${stream.source}")
    }

    @Test
    fun live_resolve_bad_id_is_error() {
        val env = resolve("!!!not-a-valid-id!!!")
        assertTrue("expected failure", !env.ok)
        assertTrue("expected invalid_track kind", env.error?.kind == "invalid_track")
    }
}
