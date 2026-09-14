package com.groovebox.app.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.groovebox.app.MainActivity

/**
 * Hosts the ExoPlayer instance and exposes it via MediaSession, which is
 * what gives GrooveBox lock-screen controls, the media notification, and
 * Bluetooth/headset button support — the Android equivalent of the desktop
 * app's Windows SMTC integration.
 *
 * Hi-Res audio: setEnableAudioFloatOutput(true) stops ExoPlayer from
 * downmixing/truncating high-bit-depth FLAC/WAV sources to 16-bit before
 * they reach AudioTrack. What actually gets heard beyond that point is
 * decided by whichever output route is active (phone DAC, Bluetooth, or a
 * USB-C external DAC) — Android negotiates that automatically, so the only
 * thing the app controls is not being the bottleneck itself.
 */
class PlaybackService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    lateinit var player: ExoPlayer
        private set

    companion object {
        // MediaController (what the UI talks to) is a cross-process proxy
        // and doesn't expose audioSessionId — Equalizer/Visualizer need the
        // real ExoPlayer instance, which only exists here. Safe as a plain
        // static reference since the service always runs in the app's main
        // process (no android:process override in the manifest).
        @Volatile var instance: PlaybackService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableAudioFloatOutput(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus = */ true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onDestroy() {
        instance = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || (!p.playWhenReady || p.mediaItemCount == 0)) {
            stopSelf()
        }
    }
}
