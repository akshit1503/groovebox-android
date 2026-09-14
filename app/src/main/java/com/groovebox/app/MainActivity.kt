package com.groovebox.app

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.groovebox.app.ui.GrooveBoxViewModel
import com.groovebox.app.ui.GrooveBoxRoot
import com.groovebox.app.ui.screens.OnboardingScreen
import com.groovebox.app.ui.theme.GrooveBoxTheme

class MainActivity : ComponentActivity() {
    private val viewModel: GrooveBoxViewModel by viewModels()

    // Read live in Compose (set here in onCreate/onResume) since these reflect
    // system/OS state that can change outside our own callbacks — e.g. the user
    // grants battery exemption from Settings and comes back to the app.
    private var notificationsGranted by mutableStateOf(false)
    private var batteryExempt by mutableStateOf(false)

    private val folderPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.linkFolder(it) }
    }

    private val folderAsPlaylistPicker = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { viewModel.createFolderPlaylist(it) }
    }

    // Tracks which playlist a cover pick was launched for — the photo picker's
    // callback only hands back the picked uri, not any context we passed in.
    private var pendingCoverPlaylistId: String? = null

    // Android's system Photo Picker (no READ_MEDIA_IMAGES / READ_EXTERNAL_STORAGE
    // permission needed at any API level, in keeping with this app's SAF-only,
    // no-broad-storage-permission approach elsewhere).
    private val coverPicker = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val playlistId = pendingCoverPlaylistId
        pendingCoverPlaylistId = null
        if (uri != null && playlistId != null) viewModel.setPlaylistCoverArt(playlistId, uri)
    }

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notificationsGranted = granted
    }

    private val recordAudioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.player.retryEffectsAttach()
    }

    private val bluetoothConnectPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val batteryExemptionRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        notificationsGranted = isNotificationsGranted()
        batteryExempt = isIgnoringBatteryOptimizations()

        // No manual service start here: MediaController.Builder (inside
        // PlayerController.connect(), called from the ViewModel) binds to
        // PlaybackService as a regular bound service. MediaSessionService
        // promotes itself to a foreground service automatically once
        // playback actually starts — calling startForegroundService()
        // ourselves before that would crash the app (Android requires
        // startForeground() within ~5s of a foreground-service start, and
        // there's nothing to play yet at this point).

        // Visualizer (android.media.audiofx.Visualizer) requires RECORD_AUDIO to
        // initialize on API 28+, even though it only reads our own audio session's
        // output buffer, not the microphone.
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            recordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        // Needed only to receive ACTION_ACL_CONNECTED for the Utils "Resume on
        // Bluetooth" toggle on Android 12+; requesting it upfront (rather than only
        // when the toggle is flipped) keeps SettingsScreen from needing its own
        // permission-request plumbing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) {
            bluetoothConnectPermission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }

        setContent {
            GrooveBoxTheme {
                val state by viewModel.uiState.collectAsState()
                when (state.onboardingComplete) {
                    null -> { /* still loading persisted settings — render nothing rather than flash a screen */ }
                    false -> OnboardingScreen(
                        hasFolder = state.linkedFolders.isNotEmpty(),
                        notificationsGranted = notificationsGranted,
                        batteryExempt = batteryExempt,
                        onLinkFolder = { folderPicker.launch(null) },
                        onRequestNotifications = { requestNotifications() },
                        onRequestBatteryExemption = { requestBatteryExemption() },
                        onFinish = { viewModel.completeOnboarding() }
                    )
                    true -> GrooveBoxRoot(
                        viewModel = viewModel,
                        onLinkFolder = { folderPicker.launch(null) },
                        onAddFolderAsPlaylist = { folderAsPlaylistPicker.launch(null) },
                        onPickPlaylistCover = { playlistId ->
                            pendingCoverPlaylistId = playlistId
                            coverPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                        onContactSupport = { contactSupport() },
                        notificationsGranted = notificationsGranted,
                        batteryExempt = batteryExempt,
                        onRequestBatteryExemption = { requestBatteryExemption() },
                        onRequestNotifications = { requestNotifications() }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        notificationsGranted = isNotificationsGranted()
        batteryExempt = isIgnoringBatteryOptimizations()
    }

    private fun isNotificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestBatteryExemption() {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            batteryExemptionRequest.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Battery settings aren't available on this device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:akshitsingh153@gmail.com")).apply {
            putExtra(Intent.EXTRA_SUBJECT, "GrooveBox for Android — Support")
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "No email app found", Toast.LENGTH_SHORT).show()
        }
    }
}
