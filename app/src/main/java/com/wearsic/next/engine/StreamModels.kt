package com.wearsic.next.engine

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Mirrors Rust `stream_resolver::models::StreamDescriptor`. */
@Serializable
data class StreamDescriptor(
    @SerialName("video_id") val videoId: String,
    val url: String,
    @SerialName("mime_type") val mimeType: String,
    val codec: String? = null,
    val bitrate: Long? = null,
    @SerialName("sample_rate") val sampleRate: Long? = null,
    val channels: Long? = null,
    @SerialName("content_length") val contentLength: Long = 0L,
    @SerialName("duration_ms") val durationMs: Long? = null,
    val source: String,
)

@Serializable
data class ResolveEnvelope(
    val ok: Boolean,
    val stream: StreamDescriptor? = null,
    val error: NetworkErrorPublic? = null,
)

@Serializable
data class ResolveRequest(
    @SerialName("video_id") val videoId: String,
    @SerialName("request_id") val requestId: Long? = null,
)
