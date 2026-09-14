package com.groovebox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.LinkedFolderEntity
import com.groovebox.app.ui.components.VisualizerMode
import com.groovebox.app.ui.theme.GbColors
import com.groovebox.app.ui.theme.GbThemes

private val speedOptions = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

@Composable
fun SettingsScreen(
    linkedFolders: List<LinkedFolderEntity>,
    autoplayOnLaunch: Boolean,
    playbackRate: Float,
    themeId: String,
    visualizerMode: String,
    visualizerEnabled: Boolean,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    resumeOnHeadset: Boolean,
    resumeOnBluetooth: Boolean,
    pauseResumeOnVolume: Boolean,
    onOpenSource: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenVisualizer: () -> Unit,
    onSetAutoplayOnLaunch: (Boolean) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onRequestNotifications: () -> Unit,
    onSetResumeOnHeadset: (Boolean) -> Unit,
    onSetResumeOnBluetooth: (Boolean) -> Unit,
    onSetPauseResumeOnVolume: (Boolean) -> Unit,
    onContactSupport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", color = GbColors.Text1, fontSize = 20.sp, fontWeight = FontWeight.Bold)

        SectionHeader("Library")
        NavRow(
            icon = Icons.Filled.Folder,
            title = "Source",
            subtitle = if (linkedFolders.isEmpty()) "No folders linked" else "${linkedFolders.size} folder${if (linkedFolders.size != 1) "s" else ""} linked",
            onClick = onOpenSource
        )

        SectionHeader("Playback")
        SettingsRow(title = "Resume last played track", subtitle = "Auto-play on launch") {
            Switch(
                checked = autoplayOnLaunch,
                onCheckedChange = onSetAutoplayOnLaunch,
                colors = SwitchDefaults.colors(checkedThumbColor = GbColors.AccentPurple, checkedTrackColor = GbColors.AccentPurple.copy(alpha = 0.4f))
            )
        }
        Text("Playback speed", color = GbColors.Text2, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp, bottom = 6.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(speedOptions) { s ->
                val label = if (s == s.toInt().toFloat()) "${s.toInt()}×" else "${s}×"
                FilterChip(
                    selected = kotlin.math.abs(playbackRate - s) < 0.001f,
                    onClick = { onSetPlaybackSpeed(s) },
                    label = { Text(label) }
                )
            }
        }

        SectionHeader("Sound")
        NavRow(icon = Icons.Filled.Equalizer, title = "Equalizer", subtitle = "Presets or manual 10-band EQ", onClick = onOpenEqualizer)
        NavRow(
            icon = Icons.Filled.GraphicEq,
            title = "Visualization",
            subtitle = if (!visualizerEnabled) "Off" else VisualizerMode.byId(visualizerMode).displayName,
            onClick = onOpenVisualizer
        )

        SectionHeader("Appearance")
        NavRow(
            icon = null,
            title = "Themes",
            subtitle = GbThemes.byId(themeId).displayName,
            onClick = onOpenThemes,
            trailing = {
                val theme = GbThemes.byId(themeId)
                Row(horizontalArrangement = Arrangement.spacedBy((-6).dp)) {
                    ThemeDot(theme.accentPurple); ThemeDot(theme.accentPink); ThemeDot(theme.accentTeal)
                }
            }
        )

        SectionHeader("Utils")
        if (!batteryExempt) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GbColors.AccentAmber.copy(alpha = 0.12f))
                    .clickable(onClick = onRequestBatteryExemption)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GbColors.AccentAmber, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text("Not excluded from battery optimization", color = GbColors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("Background playback may be killed by the system. Tap to fix.", color = GbColors.Text3, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (!notificationsGranted) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(GbColors.AccentAmber.copy(alpha = 0.12f))
                    .clickable(onClick = onRequestNotifications)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = GbColors.AccentAmber, modifier = Modifier.size(20.dp))
                Column(modifier = Modifier.padding(start = 10.dp)) {
                    Text("Notifications disabled", color = GbColors.Text1, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("No playback controls in the notification shade. Tap to enable.", color = GbColors.Text3, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        SettingsRow(title = "Resume on wired headset", subtitle = "Start playing again when headphones are plugged in") {
            Switch(
                checked = resumeOnHeadset,
                onCheckedChange = onSetResumeOnHeadset,
                colors = SwitchDefaults.colors(checkedThumbColor = GbColors.AccentPurple, checkedTrackColor = GbColors.AccentPurple.copy(alpha = 0.4f))
            )
        }
        SettingsRow(title = "Resume on Bluetooth", subtitle = "Start playing again when a Bluetooth device connects") {
            Switch(
                checked = resumeOnBluetooth,
                onCheckedChange = onSetResumeOnBluetooth,
                colors = SwitchDefaults.colors(checkedThumbColor = GbColors.AccentPurple, checkedTrackColor = GbColors.AccentPurple.copy(alpha = 0.4f))
            )
        }
        SettingsRow(title = "Pause/resume on volume", subtitle = "Pause at volume 0, resume when raised again") {
            Switch(
                checked = pauseResumeOnVolume,
                onCheckedChange = onSetPauseResumeOnVolume,
                colors = SwitchDefaults.colors(checkedThumbColor = GbColors.AccentPurple, checkedTrackColor = GbColors.AccentPurple.copy(alpha = 0.4f))
            )
        }

        SectionHeader("Support")
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onContactSupport),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Email, contentDescription = null, tint = GbColors.AccentPurple)
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Contact support", color = GbColors.Text1)
                Text("Email the developer for help or feedback", color = GbColors.Text3, fontSize = 11.sp)
            }
        }

        SectionHeader("About")
        Text("GrooveBox for Android — v1.0", color = GbColors.Text2)
        Text("Local playback, Media3/ExoPlayer, no ads, no accounts.", color = GbColors.Text3, fontSize = 11.sp)
        // See GbDimens.BottomBarClearance — 90dp still left the last rows tucked
        // behind the mini player/nav bar; this is the actual measured clearance.
        Spacer(Modifier.height(com.groovebox.app.ui.theme.GbDimens.BottomBarClearance))
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = GbColors.Text1)
            Text(subtitle, color = GbColors.Text3, fontSize = 11.sp)
        }
        trailing()
    }
}

@Composable
private fun NavRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = GbColors.AccentPurple)
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, color = GbColors.Text1)
                Text(subtitle, color = GbColors.Text3, fontSize = 11.sp)
            }
        }
        if (trailing != null) { trailing(); Spacer(Modifier.width(6.dp)) }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = GbColors.Text3)
    }
}

@Composable
private fun ThemeDot(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text.uppercase(),
        color = GbColors.Text3,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)
    )
}
