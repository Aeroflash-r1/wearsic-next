package com.wearsic.next.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * The app's single MediaSessionService. Owns the one ExoPlayer + MediaSession
 * for the whole process. Only active while playback is actually required;
 * Android tears it down when the session is no longer referenced.
 *
 * The load-control / wake-mode builders are @UnstableApi by Media3's own
 * definition; opting in at the class keeps the annotation off every call.
 */
@OptIn(UnstableApi::class)
class WearsicPlaybackService : MediaSessionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        // Resolve through the engine is handled by PlaybackController; here
        // we only own the Media3 objects.
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        // A single long-lived session; do not recreate per controller.
        val existing = mediaSession
        if (existing != null) return existing
        val p = buildPlayer()
        player = p
        return MediaSession.Builder(this, p).build().also { mediaSession = it }
    }

    private fun buildPlayer(): ExoPlayer {
        // Watch-tuned buffering (see docs/MEDIA3_INTEGRATION.md for rationale):
        // modest buffers to keep RAM low while avoiding frequent spotty rebuffers.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs */ 15_000,
                /* maxBufferMs */ 60_000,
                /* bufferForPlaybackMs */ 1_200,
                /* bufferForPlaybackAfterRebufferMs */ 3_000,
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setTargetBufferBytes(4 * 1024 * 1024) // 4 MiB cap
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val httpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // YouTube CDN 403s default OkHttp UA on signed IOS-client
            // stream URLs; mirror the UA used to resolve the stream.
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", IOS_CLIENT_UA)
                    .header("Accept", "*/*")
                    .build()
                chain.proceed(request)
            }
            .build()
        val dataSourceFactory = DefaultDataSource.Factory(
            this,
            OkHttpDataSource.Factory(httpClient),
        )
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        return ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        // If the user swipes the app away while nothing is playing, stop.
        val p = player
        if (p != null && !p.isPlaying) {
            p.stop()
            p.clearMediaItems()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        // Engine-level cleanup is the controller's job; here we release the
        // Media3 objects.
        mediaSession?.run {
            player?.release()
            release()
        }
        mediaSession = null
        player = null
        super.onDestroy()
    }

    companion object {
        /** Matches the IOS client UA used by stream resolution. */
        private const val IOS_CLIENT_UA =
            "com.google.ios.youtube/21.03.2 (iPhone16,2; U; CPU iOS 18_7_2 like Mac OS X;)"
    }
}
