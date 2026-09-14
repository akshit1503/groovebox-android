package com.groovebox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.groovebox.app.ui.theme.GbColors
import kotlin.math.cos
import kotlin.math.sin

/** The 10 selectable visualizer looks, all driven by the same smoothed FFT data. */
enum class VisualizerMode(val id: String, val displayName: String) {
    BARS("bars", "Bars"),
    MIRROR("mirror", "Mirror Bars"),
    WAVE("wave", "Wave"),
    DOTS("dots", "Dots"),
    RADIAL("radial", "Radial"),
    FILLED_WAVE("filled_wave", "Filled Wave"),
    BLOCKS("blocks", "Blocks"),
    PULSE("pulse", "Pulse"),
    STARBURST("starburst", "Starburst"),
    RIPPLE("ripple", "Ripple");

    companion object {
        fun byId(id: String): VisualizerMode = entries.find { it.id == id } ?: BARS
    }
}

/** Turns a stored hue (0-360, or -1 for "use the app theme colors") into a Color. */
fun visualizerHueToColor(hue: Float): Color? {
    if (hue < 0f) return null
    return Color.hsv(hue.coerceIn(0f, 359.9f), 0.85f, 1f)
}

/**
 * Draws directly from the latest FFT reading — smoothing already happened as cheap
 * arithmetic in PlayerController, once per capture, on the thread that was already
 * computing the FFT. An earlier version re-animated 24 bars via separate Animatable
 * coroutines on every capture (~20-30x/sec => 500+ coroutine launches/sec) trying to
 * get this same smoothness in Compose; that was the actual source of the lag, not
 * the drawing itself — a single Canvas redraw at 20-30fps is trivial by comparison.
 */
@Composable
fun BarVisualizer(
    data: FloatArray,
    modifier: Modifier = Modifier,
    mode: VisualizerMode = VisualizerMode.BARS,
    accentColor: Color? = null
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        if (data.isEmpty()) return@Canvas
        when (mode) {
            VisualizerMode.BARS -> drawBars(data, accentColor)
            VisualizerMode.MIRROR -> drawMirrorBars(data, accentColor)
            VisualizerMode.WAVE -> drawWave(data, accentColor)
            VisualizerMode.DOTS -> drawDots(data, accentColor)
            VisualizerMode.RADIAL -> drawRadial(data, accentColor)
            VisualizerMode.FILLED_WAVE -> drawFilledWave(data, accentColor)
            VisualizerMode.BLOCKS -> drawBlocks(data, accentColor)
            VisualizerMode.PULSE -> drawPulse(data, accentColor)
            VisualizerMode.STARBURST -> drawStarburst(data, accentColor)
            VisualizerMode.RIPPLE -> drawRipple(data, accentColor)
        }
    }
}

private fun accentBrush(height: Float, custom: Color?) = if (custom != null) {
    Brush.verticalGradient(colors = listOf(custom.copy(alpha = 0.85f), Color.White), startY = height, endY = 0f)
} else {
    Brush.verticalGradient(
        colors = listOf(GbColors.AccentPurple.copy(alpha = 0.85f), GbColors.AccentPink.copy(alpha = 0.95f), Color.White),
        startY = height,
        endY = 0f
    )
}

private fun DrawScope.drawBars(values: FloatArray, custom: Color?) {
    val barCount = values.size
    val gap = 5.dp.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val corner = CornerRadius(barWidth / 2, barWidth / 2)
    // ONE gradient, anchored to the full canvas height and reused for every bar this
    // frame, instead of allocating a fresh Brush (plus 2 extra draw calls for glow/cap)
    // per bar. That was 24 Brush allocations + 72 draw calls every single frame at
    // 20-30fps — real, measurable CPU cost recording the display list, independent of
    // how fast the GPU itself renders. Taller bars simply reach further up the same
    // ramp (purple -> pink -> white), which still reads as the intended "peak" look.
    val brush = accentBrush(size.height, custom)

    for (i in 0 until barCount) {
        val magnitude = values[i]
        val barHeight = (size.height * magnitude).coerceAtLeast(4.dp.toPx())
        val x = i * (barWidth + gap)
        val top = size.height - barHeight
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, top),
            size = Size(barWidth, barHeight),
            cornerRadius = corner
        )
    }
}

/** Bars grow from a center line both up and down, mirrored — a classic media-player look. */
private fun DrawScope.drawMirrorBars(values: FloatArray, custom: Color?) {
    val barCount = values.size
    val gap = 5.dp.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val corner = CornerRadius(barWidth / 2, barWidth / 2)
    val brush = accentBrush(size.height, custom)
    val centerY = size.height / 2f

    for (i in 0 until barCount) {
        val magnitude = values[i]
        val halfHeight = (centerY * magnitude).coerceAtLeast(2.dp.toPx())
        val x = i * (barWidth + gap)
        drawRoundRect(
            brush = brush,
            topLeft = Offset(x, centerY - halfHeight),
            size = Size(barWidth, halfHeight * 2f),
            cornerRadius = corner
        )
    }
}

/** A smooth line connecting each band's magnitude, like an oscilloscope trace. */
private fun DrawScope.drawWave(values: FloatArray, custom: Color?) {
    val path = wavePath(values)
    drawPath(
        path = path,
        brush = accentBrush(size.height, custom),
        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
    )
}

/** Same trace as Wave, but the area beneath it is filled — reads as a "spectrum" shape. */
private fun DrawScope.drawFilledWave(values: FloatArray, custom: Color?) {
    val path = wavePath(values)
    val filled = androidx.compose.ui.graphics.Path().apply {
        addPath(path)
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    val fillColor = custom ?: GbColors.AccentPurple
    drawPath(
        path = filled,
        brush = Brush.verticalGradient(listOf(fillColor.copy(alpha = 0.55f), fillColor.copy(alpha = 0.05f)), startY = 0f, endY = size.height)
    )
    drawPath(path = path, brush = accentBrush(size.height, custom), style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round))
}

private fun DrawScope.wavePath(values: FloatArray): androidx.compose.ui.graphics.Path {
    val n = values.size
    val path = androidx.compose.ui.graphics.Path()
    if (n < 2) return path
    val stepX = size.width / (n - 1)
    fun yAt(i: Int) = size.height - (size.height * values[i]).coerceAtLeast(4.dp.toPx())
    path.moveTo(0f, yAt(0))
    for (i in 1 until n) {
        val prevX = (i - 1) * stepX
        val curX = i * stepX
        val midX = (prevX + curX) / 2f
        path.cubicTo(midX, yAt(i - 1), midX, yAt(i), curX, yAt(i))
    }
    return path
}

/** One glowing dot per band, floating at its magnitude height — a minimal, airy look. */
private fun DrawScope.drawDots(values: FloatArray, custom: Color?) {
    val barCount = values.size
    val gap = size.width / barCount
    val radius = (gap * 0.32f).coerceAtMost(10.dp.toPx())
    val core = custom ?: GbColors.AccentPink
    val outer = custom?.copy(alpha = 0.6f) ?: GbColors.AccentPurple.copy(alpha = 0.6f)
    for (i in 0 until barCount) {
        val magnitude = values[i]
        val cx = gap * i + gap / 2f
        val cy = size.height - (size.height * magnitude).coerceAtLeast(6.dp.toPx())
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White, core.copy(alpha = 0.9f), outer), center = Offset(cx, cy), radius = radius * 2.2f),
            radius = radius,
            center = Offset(cx, cy)
        )
    }
}

/** Bars radiating outward from a center point, like a circular spectrum analyzer. */
private fun DrawScope.drawRadial(values: FloatArray, custom: Color?) {
    val n = values.size
    val center = Offset(size.width / 2f, size.height / 2f)
    val innerRadius = (size.minDimension / 2f) * 0.32f
    val maxBarLen = (size.minDimension / 2f) * 0.62f
    val barWidth = (2f * Math.PI.toFloat() * innerRadius / n) * 0.6f
    val brush = accentBrush(size.height, custom)

    for (i in 0 until n) {
        val magnitude = values[i]
        val angle = (i.toFloat() / n) * 2f * Math.PI.toFloat() - (Math.PI.toFloat() / 2f)
        val len = (maxBarLen * magnitude).coerceAtLeast(3.dp.toPx())
        val startX = center.x + cos(angle) * innerRadius
        val startY = center.y + sin(angle) * innerRadius
        val endX = center.x + cos(angle) * (innerRadius + len)
        val endY = center.y + sin(angle) * (innerRadius + len)
        drawLine(
            brush = brush,
            start = Offset(startX, startY),
            end = Offset(endX, endY),
            strokeWidth = barWidth.coerceAtLeast(2.dp.toPx()),
            cap = StrokeCap.Round
        )
    }
}

/** Each bar rendered as discrete lit segments (LED-style), like a classic hardware EQ display. */
private fun DrawScope.drawBlocks(values: FloatArray, custom: Color?) {
    val barCount = values.size
    val gap = 5.dp.toPx()
    val barWidth = (size.width - gap * (barCount - 1)) / barCount
    val segments = 10
    val segGap = 3.dp.toPx()
    val segHeight = (size.height - segGap * (segments - 1)) / segments
    val brush = accentBrush(size.height, custom)

    for (i in 0 until barCount) {
        val magnitude = values[i]
        val litSegments = (magnitude * segments).toInt().coerceIn(1, segments)
        val x = i * (barWidth + gap)
        for (s in 0 until litSegments) {
            val top = size.height - (s + 1) * (segHeight + segGap) + segGap
            drawRoundRect(
                brush = brush,
                topLeft = Offset(x, top),
                size = Size(barWidth, segHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

/** A single circle breathing with the average magnitude — minimal, ambient. */
private fun DrawScope.drawPulse(values: FloatArray, custom: Color?) {
    val avg = values.average().toFloat()
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.minDimension / 2f
    val radius = (maxRadius * 0.35f) + maxRadius * 0.55f * avg
    val core = custom ?: GbColors.AccentPink
    val outer = custom?.copy(alpha = 0.05f) ?: GbColors.AccentPurple.copy(alpha = 0.05f)
    drawCircle(
        brush = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), core.copy(alpha = 0.5f), outer), center = center, radius = radius),
        radius = radius,
        center = center
    )
}

/** Thin lines radiating from the center, denser than Radial, tipped with a small dot. */
private fun DrawScope.drawStarburst(values: FloatArray, custom: Color?) {
    val n = values.size
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxLen = size.minDimension / 2f * 0.9f
    val tipColor = custom ?: GbColors.AccentPink
    val lineColor = (custom ?: GbColors.AccentPurple).copy(alpha = 0.7f)

    for (i in 0 until n) {
        val magnitude = values[i]
        val angle = (i.toFloat() / n) * 2f * Math.PI.toFloat()
        val len = (maxLen * magnitude).coerceAtLeast(4.dp.toPx())
        val endX = center.x + cos(angle) * len
        val endY = center.y + sin(angle) * len
        drawLine(color = lineColor, start = center, end = Offset(endX, endY), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
        drawCircle(color = tipColor, radius = 2.5.dp.toPx(), center = Offset(endX, endY))
    }
}

/** Concentric rings whose radii track a few averaged frequency buckets — a sonar-like pulse. */
private fun DrawScope.drawRipple(values: FloatArray, custom: Color?) {
    val center = Offset(size.width / 2f, size.height / 2f)
    val maxRadius = size.minDimension / 2f * 0.92f
    val rings = 4
    val bucketSize = values.size / rings
    val ringColor = custom ?: GbColors.AccentTeal
    for (r in 0 until rings) {
        val bucket = values.copyOfRange(r * bucketSize, ((r + 1) * bucketSize).coerceAtMost(values.size))
        val magnitude = if (bucket.isEmpty()) 0f else bucket.average().toFloat()
        val radius = (maxRadius * (r + 1) / rings) * (0.5f + 0.5f * magnitude)
        drawCircle(
            color = ringColor.copy(alpha = (0.15f + 0.5f * magnitude).coerceIn(0f, 0.8f)),
            radius = radius.coerceAtLeast(4.dp.toPx()),
            center = center,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}
