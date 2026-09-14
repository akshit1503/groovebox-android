package com.groovebox.app.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.groovebox.app.ui.theme.GbColors

/** Shared animation phase for the ambient blobs, provided once at the root so
 *  [FrostSurface] can redraw them in sync with the real background. */
val LocalAmbientPhase = compositionLocalOf { 0f }

internal data class BlobSpec(val color: Color, val size: Dp, val x: (Float) -> Dp, val y: (Float) -> Dp)

// A function, not a fixed list: GbColors.AccentPurple/Pink/Teal change when the theme
// changes, so this needs to read their CURRENT values on every call rather than
// freezing them at first access.
internal fun blobSpecs() = listOf(
    BlobSpec(GbColors.AccentPurple, 340.dp, { t -> (-60).dp + (40.dp * t) }, { t -> (-80).dp + (30.dp * t) }),
    BlobSpec(GbColors.AccentPink, 300.dp, { t -> 180.dp - (50.dp * t) }, { t -> 260.dp - (20.dp * t) }),
    BlobSpec(GbColors.AccentTeal, 260.dp, { t -> 40.dp + (30.dp * t) }, { t -> 520.dp + (25.dp * t) })
)

/**
 * Flat frosted-glass surface for ordinary UI: chips, rows, thumbnails, dropdowns,
 * feature cards. Matches the desktop's `--glass-fill` tint + border — no blur here.
 * The desktop only ever gives ONE real backdrop-filter boundary per major panel
 * (see [FrostSurface]); everything nested inside that uses flat translucency
 * instead of its own blur, "so blur cost doesn't stack layer on layer."
 *
 * The specular sheen (a soft diagonal light streak near the top edge, brighter
 * top border than bottom) is what actually reads as "glass" rather than "tinted
 * plastic" — real glass/liquid-glass surfaces catch light unevenly, they don't
 * just sit at one flat translucency. Cheap to draw (one gradient, one border
 * path) so it's on by default; surfaces that shouldn't catch light (e.g. a
 * flush thumbnail sitting flat against a list row) can opt out.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    tint: Color = GbColors.GlassFill,
    borderColor: Color = GbColors.GlassBorder,
    opaque: Boolean = false,
    showSheen: Boolean = true,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.clip(shape)) {
        Box(modifier = Modifier.matchParentSize().background(if (opaque) GbColors.GlassOpaque else tint))
        if (showSheen) {
            // Brush-based borders (needed for the two-tone sheen edge) can't use the
            // cheap hardware outline stroke non-rectangular shapes normally get — they
            // rasterize a path in software instead. Only pay for that on the handful
            // of large, single-instance panels that actually show `showSheen`; every
            // repeated small surface (list thumbnails etc.) uses a flat solid border
            // below instead, which stays on the cheap path.
            Box(
                modifier = Modifier.matchParentSize().background(
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.16f), Color.Transparent, Color.Transparent)
                    )
                )
            )
            Box(
                modifier = Modifier.matchParentSize().border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(borderColor.copy(alpha = (borderColor.alpha + 0.25f).coerceAtMost(1f)), borderColor)
                    ),
                    shape = shape
                )
            )
        } else {
            Box(modifier = Modifier.matchParentSize().border(1.dp, borderColor, shape))
        }
        content()
    }
}

/**
 * The desktop's `.glass-frost`: a dark frosted tint for the big structural surfaces
 * (main content area, bottom bar). Used to redraw a live, per-frame-animated blurred
 * duplicate of the ambient blobs here too, but that meant two full-screen RenderEffect
 * blurs recomputing every frame of the (always-running) ambient animation, sitting
 * behind a scrolling list — real, measurable jank on a mid-range phone. The AmbientBackground
 * beneath is already softly blurred once; this just needs a flat dark tint on top of
 * that, not a second live blur, to read as "frosted glass."
 */
@Composable
fun FrostSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(0.dp),
    tint: Color = GbColors.GlassBg,
    content: @Composable () -> Unit = {}
) {
    Box(modifier = modifier.clip(shape).background(tint)) {
        content()
    }
}

/**
 * A [androidx.compose.material3.DropdownMenu] styled to match the rest of the app —
 * Material3's default renders an opaque `colorScheme.surface` fill (our theme's
 * PanelTop, a solid saturated color), which reads as a plain colored box, not glass.
 * This swaps that for a translucent dark fill with a soft border instead.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    // This Material3 version's DropdownMenu has no shape/containerColor/border params
    // (those were added in a later Material3 release than this project's Compose BOM
    // provides), so the glass look is applied by overriding the ambient MaterialTheme
    // colorScheme/shapes that DropdownMenu's internal Surface reads from, plus a border
    // drawn on the menu's own modifier.
    val glassShape = RoundedCornerShape(14.dp)
    androidx.compose.material3.MaterialTheme(
        colorScheme = androidx.compose.material3.MaterialTheme.colorScheme.copy(surface = GbColors.GlassOpaque.copy(alpha = 0.95f)),
        shapes = androidx.compose.material3.MaterialTheme.shapes.copy(extraSmall = glassShape)
    ) {
        androidx.compose.material3.DropdownMenu(
            expanded = expanded,
            onDismissRequest = onDismissRequest,
            modifier = modifier.border(1.dp, GbColors.GlassBorder, glassShape),
            content = content
        )
    }
}

/** Animated ambient gradient blobs behind the main content, matching the desktop background. */
@Composable
fun AmbientBackground(modifier: Modifier = Modifier) {
    val phase = LocalAmbientPhase.current
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    Box(
        modifier = modifier
            .background(GbColors.Bg0)
            .drawBehind {
                blobSpecs().forEach { spec ->
                    val sizePx = spec.size.toPx()
                    val radius = sizePx / 2f
                    val center = Offset(spec.x(phase).toPx() + radius, spec.y(phase).toPx() + radius)
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(spec.color.copy(alpha = 0.35f), Color.Transparent),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )
                }
            }
            // 120dp was a genuinely expensive full-screen blur to recompute — even with
            // the phase animation now throttled (see GrooveBoxRoot), a smaller radius
            // still costs less per recompute. The blobs already fade to transparent via
            // their own radial gradient, so they read just as soft at half the radius.
            .then(if (canBlur) Modifier.blur(60.dp) else Modifier.blur(30.dp))
    )
}
