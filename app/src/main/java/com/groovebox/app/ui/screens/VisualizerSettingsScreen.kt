package com.groovebox.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.ui.components.BarVisualizer
import com.groovebox.app.ui.components.GlassPanel
import com.groovebox.app.ui.components.VisualizerMode
import com.groovebox.app.ui.components.visualizerHueToColor
import com.groovebox.app.ui.theme.GbColors
import com.groovebox.app.ui.theme.GbDimens
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun VisualizerSettingsScreen(
    enabled: Boolean,
    mode: String,
    colorHue: Float,
    onSetEnabled: (Boolean) -> Unit,
    onSetMode: (String) -> Unit,
    onSetColorHue: (Float) -> Unit,
    onBack: () -> Unit
) {
    // Tracked locally (not straight from `colorHue`) so both the slider thumb and this
    // live preview move at full frame rate while dragging — `colorHue` only catches up
    // once per DataStore write + Flow re-emission, which lags behind a fast drag.
    var visualHue by remember { mutableStateOf(if (colorHue < 0f) 0f else colorHue) }
    var draggingHue by remember { mutableStateOf(false) }
    LaunchedEffect(colorHue) { if (!draggingHue) visualHue = if (colorHue < 0f) 0f else colorHue }
    val customColor = if (draggingHue) visualizerHueToColor(visualHue) else visualizerHueToColor(colorHue)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GbColors.Text1)
            }
            Text("Visualization", color = GbColors.Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Show visualizer", color = GbColors.Text1)
                    Text("Displayed over the album art while playing", color = GbColors.Text3, fontSize = 11.sp)
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onSetEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = GbColors.AccentPurple, checkedTrackColor = GbColors.AccentPurple.copy(alpha = 0.4f))
                )
            }

            if (enabled) {
                // Live preview with fake, always-animating data — real FFT data only
                // exists while a track is actually playing, but the user should be able
                // to compare looks/colors from this settings screen regardless.
                GlassPanel(
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).padding(vertical = 12.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    val demoData = rememberDemoVisualizerData()
                    BarVisualizer(
                        data = demoData,
                        modifier = Modifier.padding(16.dp),
                        mode = VisualizerMode.byId(mode),
                        accentColor = customColor
                    )
                }

                Text(
                    "STYLE", color = GbColors.Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth().height(52.dp * ((VisualizerMode.entries.size + 1) / 2)),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(VisualizerMode.entries) { m ->
                        val selected = m.id == mode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) GbColors.AccentPurple.copy(alpha = 0.18f) else GbColors.GlassFill)
                                .clickable { onSetMode(m.id) }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(m.displayName, color = GbColors.Text1, fontSize = 13.sp)
                            if (selected) Icon(Icons.Filled.Check, contentDescription = null, tint = GbColors.AccentPurple, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Text(
                    "COLOR", color = GbColors.Text3, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(
                            Modifier.size(20.dp).clip(CircleShape).background(customColor ?: GbColors.AccentPurple)
                        )
                        Text(
                            if (customColor == null) "  Theme colors" else "  Custom",
                            color = GbColors.Text2, fontSize = 13.sp
                        )
                    }
                    if (customColor != null) {
                        Text(
                            "Reset to theme",
                            color = GbColors.AccentPurple, fontSize = 12.sp,
                            modifier = Modifier.clickable { onSetColorHue(-1f) }
                        )
                    }
                }
                HueSlider(
                    hue = visualHue,
                    onHueChange = { v -> draggingHue = true; visualHue = v; onSetColorHue(v) },
                    onHueChangeFinished = { draggingHue = false }
                )
                // See GbDimens.BottomBarClearance — this screen's color picker/slider
                // was the exact content getting tucked behind the mini player/nav bar.
                Spacer(Modifier.height(GbDimens.BottomBarClearance))
            }
        }
    }
}

/** A rainbow-gradient track with a transparent-track Slider riding on top for the thumb.
 *  Fully controlled — the caller owns the live-drag vs. persisted value distinction so
 *  the same live value can also drive the preview above it. */
@Composable
private fun HueSlider(hue: Float, onHueChange: (Float) -> Unit, onHueChangeFinished: () -> Unit) {
    val rainbow = remember {
        Brush.horizontalGradient((0..360 step 60).map { Color.hsv(it.toFloat() % 360, 0.85f, 1f) })
    }
    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(rainbow)
        )
        Slider(
            value = hue,
            onValueChange = onHueChange,
            onValueChangeFinished = onHueChangeFinished,
            valueRange = 0f..359f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.Transparent,
                inactiveTrackColor = Color.Transparent
            )
        )
    }
}

/** Fake, endlessly-animating band data so the preview always has something to show,
 *  independent of whether a track is actually playing right now. */
@Composable
private fun rememberDemoVisualizerData(): FloatArray {
    val transition = rememberInfiniteTransition(label = "vizDemo")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "vizPhase"
    )
    return FloatArray(24) { i ->
        val base = abs(sin(phase + i * 0.35f))
        (0.15f + 0.75f * base).coerceIn(0.05f, 0.95f)
    }
}
