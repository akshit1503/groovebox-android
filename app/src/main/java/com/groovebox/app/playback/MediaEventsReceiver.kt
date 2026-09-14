package com.groovebox.app.playback

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager

/**
 * Plain volatile flags mirroring the Settings toggles, kept in sync by the ViewModel's
 * settings collectors. [MediaEventsReceiver] runs outside any coroutine scope (it can
 * fire before the ViewModel or even the UI exists), so it needs a value it can read
 * synchronously rather than a suspend/Flow-based settings read.
 */
object MediaEventsPrefs {
    @Volatile var resumeOnHeadset: Boolean = false
    @Volatile var resumeOnBluetooth: Boolean = false
    @Volatile var pauseResumeOnVolume: Boolean = false

    // Only auto-resume audio we ourselves auto-paused (volume dropped to 0) — never
    // resume just because the volume key was raised while the user paused on purpose.
    @Volatile var autoPausedByVolume: Boolean = false
}

// Not exposed as public constants in the SDK (they're @hide / SystemApi on AudioManager),
// but their string values are stable and documented in AOSP — this is the standard way
// apps observe stream volume changes without the ContentObserver/Settings.System route.
private const val VOLUME_CHANGED_ACTION = "android.media.VOLUME_CHANGED_ACTION"
private const val EXTRA_VOLUME_STREAM_TYPE = "android.media.EXTRA_VOLUME_STREAM_TYPE"

/**
 * Registered once, for the app's whole process lifetime, in GrooveBoxApp.onCreate.
 * Dynamic (context.registerReceiver) rather than manifest-declared, since implicit
 * broadcasts like ACTION_HEADSET_PLUG can't be manifest-registered on API 26+ anyway —
 * this only fires while the app process is alive, same limitation Poweramp and most
 * other local players live with unless a foreground service is already running.
 */
class MediaEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = PlaybackService.instance?.player ?: return
        when (intent.action) {
            Intent.ACTION_HEADSET_PLUG -> {
                val plugged = intent.getIntExtra("state", 0) == 1
                if (plugged && MediaEventsPrefs.resumeOnHeadset && player.mediaItemCount > 0 && !player.isPlaying) {
                    player.play()
                }
            }
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (MediaEventsPrefs.resumeOnBluetooth && player.mediaItemCount > 0 && !player.isPlaying) {
                    player.play()
                }
            }
            VOLUME_CHANGED_ACTION -> {
                if (!MediaEventsPrefs.pauseResumeOnVolume) return
                val streamType = intent.getIntExtra(EXTRA_VOLUME_STREAM_TYPE, -1)
                if (streamType != AudioManager.STREAM_MUSIC) return
                val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val level = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                if (level == 0 && player.isPlaying) {
                    MediaEventsPrefs.autoPausedByVolume = true
                    player.pause()
                } else if (level > 0 && MediaEventsPrefs.autoPausedByVolume) {
                    MediaEventsPrefs.autoPausedByVolume = false
                    player.play()
                }
            }
        }
    }

    companion object {
        fun register(context: Context) {
            val receiver = MediaEventsReceiver()

            // ACTION_HEADSET_PLUG and VOLUME_CHANGED_ACTION are broadcast by system_server,
            // so an unexported receiver gets them fine.
            val systemFilter = android.content.IntentFilter().apply {
                addAction(Intent.ACTION_HEADSET_PLUG)
                addAction(VOLUME_CHANGED_ACTION)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                context, receiver, systemFilter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )

            // ACTION_ACL_CONNECTED is broadcast by the separate com.android.bluetooth
            // process, not system_server — confirmed via logcat that Android's exported
            // check treats that as "another app" regardless of this being a protected
            // system broadcast, so RECEIVER_NOT_EXPORTED silently drops it ("Exported
            // Denial: ... not specifying RECEIVER_EXPORTED"). Safe to export: only
            // privileged holders of BLUETOOTH_CONNECT can actually send this protected
            // action, so a third-party app can't spoof it.
            val bluetoothFilter = android.content.IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED)
            androidx.core.content.ContextCompat.registerReceiver(
                context, receiver, bluetoothFilter, androidx.core.content.ContextCompat.RECEIVER_EXPORTED
            )
        }
    }
}
