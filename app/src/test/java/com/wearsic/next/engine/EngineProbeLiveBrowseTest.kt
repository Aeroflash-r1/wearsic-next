package com.wearsic.next.engine

import java.io.File
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * LIVE network-dependent JNI browse test (album / playlist / artist).
 *
 * Requires internet access and the real YouTube Music service, like the live
 * search probes. Run explicitly with:
 *   ./gradlew :app:testDebugUnitTest --tests "com.wearsic.next.engine.EngineProbeLiveBrowseTest"
 */
class EngineProbeLiveBrowseTest {

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

    private fun browse(browseId: String): BrowseEnvelope {
        val raw = EngineProbe.probeBrowse(
            Json.encodeToString(BrowseRequest.serializer(), BrowseRequest(browseId = browseId)),
        )
        return json.decodeFromString(BrowseEnvelope.serializer(), raw)
    }

    @Test
    fun live_album_browse_returns_title_and_tracks() {
        val env = browse("MPREb_oBucXyijinj") // Parachutes — Coldplay
        assertTrue("expected ok, got: $env", env.ok)
        val page = requireNotNull(env.page) { "no page: ${env.error}" }
        assertTrue("album needs a title", page.title.isNotBlank())
        assertTrue("album needs tracks", page.tracks.isNotEmpty())
        assertTrue("album tracks need video ids", page.tracks.all { it.videoId.isNotBlank() })
        assertTrue(
            "album tracks should carry durations from fixedColumns",
            page.tracks.any { (it.durationMs ?: 0L) > 0L },
        )
        assertTrue("album needs artwork", !page.artworkUrl.isNullOrBlank())
        println("LIVE[ALBUM] ${page.title} / ${page.subtitle} / ${page.tracks.size} tracks")
    }

    @Test
    fun live_playlist_browse_returns_tracks() {
        val env = browse("VLPLd1BPJBAzj_u1Mmwq2d96vuV7x35axpZZ")
        assertTrue("expected ok, got: $env", env.ok)
        val page = requireNotNull(env.page) { "no page: ${env.error}" }
        assertTrue("playlist needs a title", page.title.isNotBlank())
        assertTrue("playlist should have a page of tracks", page.tracks.size >= 10)
        println("LIVE[PLAYLIST] ${page.title} / ${page.tracks.size} tracks")
    }

    @Test
    fun live_artist_browse_returns_top_songs_and_albums() {
        val env = browse("UCIaFw5VBEK8qaW6nRpx_qnw") // Coldplay
        assertTrue("expected ok, got: $env", env.ok)
        val page = requireNotNull(env.page) { "no page: ${env.error}" }
        assertTrue("artist needs a title", page.title.isNotBlank())
        assertTrue("artist needs top songs", page.tracks.isNotEmpty())
        assertTrue("artist page should list albums", page.albums.isNotEmpty())
        println(
            "LIVE[ARTIST] ${page.title} / ${page.tracks.size} songs / ${page.albums.size} albums",
        )
    }

    @Test
    fun live_browse_bad_id_is_error() {
        val env = browse("MPREb_this_does_not_exist_123")
        assertTrue("a bad browse id must not report ok", !env.ok)
        println("LIVE[BROWSE-ERR] ${env.error?.kind}: ${env.error?.message}")
    }
}
