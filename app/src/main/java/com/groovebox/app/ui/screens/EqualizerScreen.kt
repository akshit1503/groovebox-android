package com.groovebox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.data.EqPresets
import com.groovebox.app.playback.PlayerController
import com.groovebox.app.ui.theme.GbColors
import kotlin.math.roundToInt

/** ±15dB is the standard graphic-EQ range this app's presets (Flat/Bass/Vocal/Treble/
 *  Jazz/Rock in [EqPresets]) already tune within — Manual mode uses the same headroom
 *  so a hand-tuned curve stays comparable to the presets rather than clipping harder. */
private const val MANUAL_EQ_RANGE_DB = 15

@Composable
fun EqualizerScreen(
    eqPreset: String,
    customBands: List<Int>,
    bandInfo: List<PlayerController.EqBandInfo>,
    onSetPreset: (String) -> Unit,
    onSetBand: (Int, Int) -> Unit,
    onBack: () -> Unit
) {
    val freqs = EqPresets.FREQS
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GbColors.Text1)
            }
            Text("Equalizer", color = GbColors.Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Text(
            "Presets",
            color = GbColors.Text3, fontSize = 11.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 8.dp)
        )
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(EqPresets.PRESETS.keys.toList()) { name ->
                FilterChip(selected = eqPreset == name, onClick = { onSetPreset(name) }, label = { Text(name) })
            }
            item {
                FilterChip(selected = eqPreset == "Custom", onClick = { }, label = { Text("Custom") })
            }
        }

        Text(
            if (bandInfo.isEmpty()) "${freqs.size}-band graphic EQ · ±${MANUAL_EQ_RANGE_DB}dB · applies once playback starts"
            else "${bandInfo.size}-band graphic EQ · ±${MANUAL_EQ_RANGE_DB}dB",
            color = GbColors.Text3, fontSize = 11.sp,
            modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 4.dp)
        )

        // The sliders should reflect whatever gain curve is actually driving the audio
        // right now — a named preset's fixed values, or the user's own custom curve —
        // not always the last hand-tuned bands, which would otherwise sit stuck at
        // their old values while a preset chip shows as selected above them.
        val displayedBands = EqPresets.PRESETS[eqPreset] ?: customBands.toIntArray()
        Column(modifier = Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            freqs.indices.forEach { i ->
                val gain = displayedBands.getOrElse(i) { 0 }
                EqBandRow(
                    freqHz = freqs[i],
                    gainDb = gain,
                    rangeDb = MANUAL_EQ_RANGE_DB,
                    onChange = { newGain -> onSetBand(i, newGain) }
                )
            }
            // See GbDimens.BottomBarClearance.
            Spacer(Modifier.height(com.groovebox.app.ui.theme.GbDimens.BottomBarClearance))
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EqBandRow(freqHz: Int, gainDb: Int, rangeDb: Int, onChange: (Int) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (freqHz >= 1000) "${freqHz / 1000}kHz" else "${freqHz}Hz",
            color = GbColors.Text2, fontSize = 12.sp,
            modifier = Modifier.width(52.dp)
        )
        Slider(
            value = gainDb.toFloat(),
            onValueChange = { onChange(it.roundToInt()) },
            valueRange = -rangeDb.toFloat()..rangeDb.toFloat(),
            steps = rangeDb * 2 - 1,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = GbColors.AccentPurple,
                activeTrackColor = GbColors.AccentPurple,
                inactiveTrackColor = GbColors.GlassFill2
            ),
            // Same thin-bar override as the Full Player's sliders — Material3's
            // newer default track is much thicker than this app's original design.
            track = { sliderState ->
                val fraction = ((sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start)).coerceIn(0f, 1f)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(GbColors.GlassFill2)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(GbColors.AccentPurple)
                    )
                }
            },
            thumb = {
                Box(
                    Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(GbColors.AccentPurple)
                )
            }
        )
        Text(
            if (gainDb > 0) "+$gainDb" else "$gainDb",
            color = if (gainDb == 0) GbColors.Text3 else GbColors.AccentPurple,
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(32.dp)
        )
    }
}
