package com.wearsic.next.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

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

        private val json = Json { ignoreUnknownKeys = true }

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

        // --- Phase 3 networking natives ---

        @JvmStatic
        external fun nativeNetworkInit(): Boolean

        @JvmStatic
        external fun nativeNetworkExecute(requestJson: String): String

        @JvmStatic
        external fun nativeNetworkCancel(requestId: Long)

        @JvmStatic
        external fun nativeNetworkDiagnostics(): String

        @JvmStatic
        external fun nativeNetworkShutdown()

        // --- Phase 4 music search natives ---

        /** Executes a search/continuation; returns JSON envelope. */
        @JvmStatic
        external fun nativeSearchMusic(requestJson: String): String

        /** Cancels an in-flight search by request id. */
        @JvmStatic
        external fun nativeSearchCancel(requestId: Long)

        // --- Phase 5 stream resolution natives ---

        /** Resolves a playable audio stream; returns JSON envelope. */
        @JvmStatic
        external fun nativeResolveStream(requestJson: String): String

        /**
         * Fetches an album / playlist / artist page; returns JSON envelope.
         */
        @JvmStatic
        external fun nativeBrowse(requestJson: String): String

        /** Cancels an in-flight browse by request id. */
        @JvmStatic
        external fun nativeBrowseCancel(requestId: Long)

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

    /** Phase 3: starts the networking engine (idempotent). */
    fun networkInit(): Boolean = nativeNetworkInit()

    /**
     * Executes one request (blocking on the calling worker thread; never on
     * the Android main thread). Returns the parsed response or throws
     * [NetworkEngineException] on error/cancellation.
     */
    fun networkExecute(request: NetworkRequest): NetworkResponse {
        val raw = nativeNetworkExecute(json.encodeToString(NetworkRequest.serializer(), request))
        val envelope = json.decodeFromString(NetworkEnvelope.serializer(), raw)
        if (!envelope.ok) {
            val err = envelope.error
                ?: NetworkErrorPublic("internal", 0, "empty error envelope")
            throw NetworkEngineException(err)
        }
        return NetworkResponse(
            status = envelope.status ?: 0,
            headers = envelope.headers.orEmpty(),
            body = envelope.body.orEmpty(),
        )
    }

    fun networkCancel(requestId: Long) = nativeNetworkCancel(requestId)

    fun networkDiagnostics(): String = nativeNetworkDiagnostics()

    fun networkShutdown() = nativeNetworkShutdown()

    // --- Phase 4: music search ---

    /**
     * Searches for music (blocking on the calling worker thread). Returns
     * the first page of typed results.
     *
     * @param query search text
     * @param continuation optional continuation token for pagination
     * @param requestId used for cancellation (0 = no cancellation tracking)
     */
    fun searchMusic(
        query: String,
        continuation: String? = null,
        requestId: Long = 0L,
    ): SearchEnvelope {
        val request = SearchRequest(
            query = query,
            continuation = continuation,
            requestId = if (requestId != 0L) requestId else null,
        )
        val raw = nativeSearchMusic(json.encodeToString(SearchRequest.serializer(), request))
        return runCatching {
            json.decodeFromString(SearchEnvelope.serializer(), raw)
        }.getOrElse {
            SearchEnvelope(ok = false, error = NetworkErrorPublic("invalid_response", 0, "bad envelope"))
        }
    }

    fun searchCancel(requestId: Long) = nativeSearchCancel(requestId)

    // --- Phase 6: browse pages (album / playlist / artist) ---

    /**
     * Fetches one browse page. Pass [continuation] to load the next page of a
     * long album/playlist. Blocking on the calling worker thread.
     */
    fun browse(
        browseId: String,
        continuation: String? = null,
        requestId: Long = 0L,
    ): BrowsePageDto {
        val request = BrowseRequest(
            browseId = browseId,
            continuation = continuation,
            requestId = requestId.takeIf { it != 0L },
        )
        val raw = nativeBrowse(json.encodeToString(BrowseRequest.serializer(), request))
        val envelope = runCatching {
            json.decodeFromString(BrowseEnvelope.serializer(), raw)
        }.getOrElse {
            BrowseEnvelope(ok = false, error = NetworkErrorPublic("invalid_response", 0, "bad envelope"))
        }
        val page = envelope.page
        if (!envelope.ok || page == null) {
            throw NetworkEngineException(
                envelope.error ?: NetworkErrorPublic("internal", 0, "browse failed")
            )
        }
        return page
    }

    fun browseCancel(requestId: Long) = nativeBrowseCancel(requestId)

    // --- Phase 5: stream resolution ---

    /**
     * Resolves a playable audio stream for a track video id (blocking on
     * the calling worker thread). Returns the envelope; on failure, throws
     * [NetworkEngineException] with a structured error.
     */
    fun resolveStream(videoId: String, requestId: Long = 0L): StreamDescriptor {
        val raw = nativeResolveStream(
            json.encodeToString(
                ResolveRequest.serializer(),
                ResolveRequest(videoId = videoId, requestId = requestId.takeIf { it != 0L }),
            ),
        )
        val envelope = runCatching {
            json.decodeFromString(ResolveEnvelope.serializer(), raw)
        }.getOrElse {
            ResolveEnvelope(ok = false, error = NetworkErrorPublic("invalid_response", 0, "bad envelope"))
        }
        if (!envelope.ok || envelope.stream == null) {
            throw NetworkEngineException(envelope.error ?: NetworkErrorPublic("internal", 0, "resolve failed"))
        }
        return envelope.stream
    }
}

@kotlinx.serialization.Serializable
data class BrowseRequest(
    @SerialName("browse_id") val browseId: String,
    val continuation: String? = null,
    @SerialName("request_id") val requestId: Long? = null,
)

@Serializable
data class BrowseEnvelope(
    val ok: Boolean,
    val page: BrowsePageDto? = null,
    val error: NetworkErrorPublic? = null,
)

@kotlinx.serialization.Serializable
data class SearchRequest(
    val query: String,
    val continuation: String? = null,
    @SerialName("request_id") val requestId: Long? = null,
)

/** Structured error surfaced when the native engine cannot start. */
class EngineException(message: String) : Exception(message)

/** Raised when a native network request fails (including cancellation). */
class NetworkEngineException(val error: NetworkErrorPublic) : Exception(error.message)

@Serializable
data class NetworkRequest(
    @SerialName("method") val method: String = "get",
    @SerialName("url") val url: String,
    @SerialName("headers") val headers: Map<String, String> = emptyMap(),
    @SerialName("body") val body: String? = null,
    @SerialName("timeout_ms") val timeoutMs: Long? = null,
    @SerialName("max_attempts") val maxAttempts: Int? = null,
    @SerialName("idempotent") val idempotent: Boolean = true,
)

@Serializable
data class NetworkResponse(
    val status: Int,
    val headers: Map<String, String>,
    val body: String,
)

@Serializable
private data class NetworkEnvelope(
    val ok: Boolean,
    val status: Int? = null,
    val headers: Map<String, String>? = null,
    val body: String? = null,
    val error: NetworkErrorPublic? = null,
)
