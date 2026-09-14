package com.groovebox.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.ui.components.AmbientBackground
import com.groovebox.app.ui.theme.GbColors

private data class Step(
    val title: String,
    val description: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val actionLabel: String?,
    val onAction: (() -> Unit)?,
    val done: Boolean
)

@Composable
fun OnboardingScreen(
    hasFolder: Boolean,
    notificationsGranted: Boolean,
    batteryExempt: Boolean,
    onLinkFolder: () -> Unit,
    onRequestNotifications: () -> Unit,
    onRequestBatteryExemption: () -> Unit,
    onFinish: () -> Unit
) {
    var stepIndex by remember { mutableStateOf(0) }

    val steps = listOf(
        Step(
            title = "Welcome to GrooveBox",
            description = "Your music, played locally — no accounts, no ads, no cloud. Let's get everything set up.",
            icon = Icons.Filled.MusicNote,
            actionLabel = null,
            onAction = null,
            done = true
        ),
        Step(
            title = "Link your music folder",
            description = "GrooveBox plays files directly from a folder you choose — nothing is copied or uploaded.",
            icon = Icons.Filled.CreateNewFolder,
            actionLabel = if (hasFolder) null else "Choose folder",
            onAction = onLinkFolder,
            done = hasFolder
        ),
        Step(
            title = "Notifications",
            description = "Lets GrooveBox show playback controls on your lock screen and notification shade.",
            icon = Icons.Filled.Notifications,
            actionLabel = if (notificationsGranted) null else "Enable notifications",
            onAction = onRequestNotifications,
            done = notificationsGranted
        ),
        Step(
            title = "Uninterrupted playback",
            description = "Some phones aggressively kill background apps. Exempting GrooveBox from battery optimization keeps your music playing when the app isn't on screen.",
            icon = Icons.Filled.BatteryChargingFull,
            actionLabel = if (batteryExempt) null else "Disable battery optimization",
            onAction = onRequestBatteryExemption,
            done = batteryExempt
        ),
        Step(
            title = "All set!",
            description = "You're ready to enjoy your music.",
            icon = Icons.Filled.Check,
            actionLabel = null,
            onAction = null,
            done = true
        )
    )
    val isLast = stepIndex == steps.lastIndex

    Box(modifier = Modifier.fillMaxSize()) {
        AmbientBackground(modifier = Modifier.fillMaxSize())

        Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
            Spacer(Modifier.height(48.dp))

            AnimatedContent(targetState = stepIndex, label = "onboarding-step") { idx ->
                val s = steps[idx]
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(GbColors.AccentPurple.copy(alpha = 0.35f), GbColors.AccentPink.copy(alpha = 0.25f)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(s.icon, contentDescription = null, tint = GbColors.Text1, modifier = Modifier.size(44.dp))
                    }
                    Spacer(Modifier.height(28.dp))
                    Text(
                        s.title,
                        color = GbColors.Text1,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        s.description,
                        color = GbColors.Text2,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
                    Spacer(Modifier.height(28.dp))
                    if (s.actionLabel != null && s.onAction != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(GbColors.AccentPurple)
                                .clickable(onClick = s.onAction)
                                .padding(horizontal = 28.dp, vertical = 14.dp)
                        ) {
                            Text(s.actionLabel, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    } else if (s.done && idx in 1..3) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = GbColors.AccentTeal, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Done", color = GbColors.AccentTeal, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (stepIndex in 1..3) {
                    TextButton(onClick = { stepIndex++ }) {
                        Text("Skip", color = GbColors.Text3)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Spacer(Modifier.weight(1f))
                Text("Step ${stepIndex + 1} of ${steps.size}", color = GbColors.Text3, fontSize = 12.sp)
                Spacer(Modifier.width(16.dp))
                IconButton(
                    onClick = { if (isLast) onFinish() else stepIndex++ },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(GbColors.AccentPurple)
                ) {
                    Icon(
                        if (isLast) Icons.Filled.Check else Icons.Filled.ArrowForward,
                        contentDescription = if (isLast) "Finish" else "Next",
                        tint = Color.White
                    )
                }
            }
        }
    }
}
