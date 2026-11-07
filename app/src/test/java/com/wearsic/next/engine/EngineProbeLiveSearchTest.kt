package com.wearsic.next.engine

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * LIVE network-dependent JNI search test.
 *
 * Requires internet access and the real YouTube Music service. This test is
 * intentionally kept separate from the always-green suite: it can fail if
 * the network is unavailable or InnerTube's response shape changes.
 *
 * Run explicitly with:
 *   ./gradlew :app:testDebugUnitTest --tests "com.wearsic.next.engine.EngineProbeLiveSearchTest"
 */
class EngineProbeLiveSearchTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val hostSo: File =
            File(System.getProperty("user.dir"), "../rust/target/debug/libwearsic_engine.so")

        @BeforeClass
        @JvmStatic
        fun loadHostEngine() {
            assumeTrue("host Rust .so not built (run: cargo build in rust/)", hostSo.exists())
            System.load(hostSo.absolutePath)
            EngineProbe.probeNetworkShutdown()
            assertTrue("network init failed", EngineProbe.probeNetworkInit())
        }
    }

    private fun search(query: String): SearchEnvelope {
        val raw = EngineProbe.probeSearchMusic(
            Json.encodeToString(SearchRequest.serializer(), SearchRequest(query = query)),
        )
        return json.decodeFromString(SearchEnvelope.serializer(), raw)
    }

    @Test
    fun live_search_returns_tracks() {
        val env = search("coldplay yellow")
        assertTrue("expected ok, got: $env", env.ok)
        assertTrue("expected some results, got ${env.results.size}", env.results.isNotEmpty())
        val tracks = env.results.mapNotNull { it.track }
        assertTrue("expected at least one track", tracks.isNotEmpty())
        val first = tracks.first()
        assertTrue("track needs videoId", first.videoId.isNotBlank())
        assertTrue("track needs title", first.title.isNotBlank())
        println("LIVE[OK] first track: ${first.title} by ${first.artists.map { it.name }} (${first.videoId})")
    }

    @Test
    fun live_search_mixed_types() {
        val env = search("coldplay")
        assertTrue(env.ok)
        val kinds = env.results.mapNotNull { r ->
            when {
                r.track != null -> "track"
                r.artist != null -> "artist"
                r.album != null -> "album"
                r.playlist != null -> "playlist"
                else -> null
            }
        }
        println("LIVE[kinds] ${kinds.groupingBy { it }.eachCount()}")
        // The service should expose typed results; we assert at least one
        // recognizable kind (track) even if the mix varies.
        assertTrue("expected at least one recognized kind: $kinds", kinds.isNotEmpty())
    }

    @Test
    fun live_search_handles_empty_query() {
        val env = search("")
        // Empty query should not crash; may return ok with zero results.
        assertFalse("empty query should not be malformed ok", !env.ok && env.error?.kind == "invalid_url")
    }
}
