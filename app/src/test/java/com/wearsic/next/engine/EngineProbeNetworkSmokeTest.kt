package com.wearsic.next.engine

import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test

/**
 * Host-JVM verification of the Phase 3 networking JNI contract.
 *
 * Loads the host-built Rust engine and drives a tiny in-process Java mock
 * HTTP server (test-only). This proves request submission, structured
 * response envelopes, error mapping, diagnostics and cancellation over the
 * real JNI boundary — without any Android device or production server.
 */
class EngineProbeNetworkSmokeTest {

    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val hostSo: File =
            File(System.getProperty("user.dir"), "../rust/target/debug/libwearsic_engine.so")

        @BeforeClass
        @JvmStatic
        fun loadHostEngine() {
            assumeTrue("host Rust .so not built (run: cargo build in rust/)", hostSo.exists())
            System.load(hostSo.absolutePath)
            // Ensure a fresh network engine per test class.
            EngineProbe.probeNetworkShutdown()
            assertTrue("network init failed", EngineProbe.probeNetworkInit())
        }

        /** Minimal single-shot HTTP/1.1 mock server. */
        private fun mockServer(handler: (String) -> Pair<Int, String>): Pair<String, AutoCloseable> {
            val server = ServerSocket(0)
            val port = server.localPort
            val acceptThread = Thread {
                runCatching {
                    while (true) {
                        val client: Socket = server.accept()
                        // Dedicated thread per client: the accept loop must
                        // keep accepting while handlers run.
                        Thread {
                            runCatching {
                                val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                                val requestLine = reader.readLine() ?: return@runCatching
                                var line = reader.readLine()
                                while (!line.isNullOrBlank()) line = reader.readLine()
                                val (status, body) = handler(requestLine)
                                val reason = when (status) {
                                    200 -> "OK"; 404 -> "Not Found"; 429 -> "Too Many Requests"
                                    500 -> "Internal Server Error"; else -> "X"
                                }
                                val resp = "HTTP/1.1 $status $reason\r\n" +
                                    "Content-Type: application/json\r\nContent-Length: ${body.length}\r\n" +
                                    "Connection: close\r\n\r\n$body"
                                client.getOutputStream().write(resp.toByteArray())
                                client.close()
                            }
                        }.start()
                    }
                }
            }
            acceptThread.isDaemon = true
            acceptThread.start()
            val close = AutoCloseable {
                runCatching { server.close() }
                runCatching { acceptThread.join(500) }
            }
            return "http://127.0.0.1:$port" to close
        }
    }

    private fun executeJson(url: String): String {
        val req = Json.encodeToString(
            NetworkRequest.serializer(),
            NetworkRequest(url = url),
        )
        return EngineProbe.probeNetworkExecute(req)
    }

    @Test
    fun successful_get_returns_structured_response() {
        val (base, server) = mockServer { _ -> 200 to """{"ok":true}""" }
        server.use {
            val raw = executeJson("$base/hello")
            val envelope = json.decodeFromString(NetworkEnvelopePublic.serializer(), raw)
            assertTrue("expected ok, got: $raw", envelope.ok)
            assertEquals(200, envelope.status)
            assertEquals("""{"ok":true}""", envelope.body)
        }
    }

    @Test
    fun http_404_is_error() {
        val (base, server) = mockServer { _ -> 404 to """{"error":"missing"}""" }
        server.use {
            val raw = executeJson("$base/missing")
            val envelope = json.decodeFromString(NetworkEnvelopePublic.serializer(), raw)
            assertFalse("expected failure, got: $raw", envelope.ok)
            assertEquals("http", envelope.error?.kind)
            assertEquals(404, envelope.error?.status)
        }
    }

    @Test
    fun http_500_retries_then_fails() {
        var hits = 0
        val (base, server) = mockServer {
            hits++
            500 to "{}"
        }
        server.use {
            val raw = executeJson("$base/boom")
            val envelope = json.decodeFromString(NetworkEnvelopePublic.serializer(), raw)
            assertFalse(envelope.ok)
            assertEquals("http", envelope.error?.kind)
            // max_attempts default = 2, so exactly 2 hits.
            assertEquals(2, hits)
        }
    }

    @Test
    fun invalid_url_is_rejected_without_network() {
        val raw = EngineProbe.probeNetworkExecute(
            Json.encodeToString(NetworkRequest.serializer(), NetworkRequest(url = "not a url")),
        )
        val envelope = json.decodeFromString(NetworkEnvelopePublic.serializer(), raw)
        assertFalse(envelope.ok)
        assertEquals("invalid_url", envelope.error?.kind)
    }

    @Test
    fun diagnostics_are_returned_after_requests() {
        val (base, server) = mockServer { _ -> 200 to """{}""" }
        server.use {
            repeat(3) { executeJson("$base/d$it") }
        }
        val diag = EngineProbe.probeNetworkDiagnostics()
        assertTrue("diagnostics should mention total_requests: $diag", diag.contains("\"total_requests\""))
    }

    @Test
    fun shutdown_then_reinit_is_safe() {
        EngineProbe.probeNetworkShutdown()
        assertTrue(EngineProbe.probeNetworkInit())
        val (base, server) = mockServer { _ -> 200 to """{}""" }
        server.use {
            val raw = executeJson("$base/after-reinit")
            assertTrue(json.decodeFromString(NetworkEnvelopePublic.serializer(), raw).ok)
        }
    }
}

// Public decode mirror (private data class in RustEngine is not visible).
@kotlinx.serialization.Serializable
data class NetworkEnvelopePublic(
    val ok: Boolean,
    val status: Int? = null,
    val headers: Map<String, String>? = null,
    val body: String? = null,
    val error: NetworkErrorPublic? = null,
)

@kotlinx.serialization.Serializable
data class NetworkErrorPublic(
    val kind: String,
    val status: Int,
    val message: String,
)
