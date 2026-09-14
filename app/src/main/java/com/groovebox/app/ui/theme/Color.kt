package com.groovebox.app.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** One selectable color theme: the three brand accents plus the two background tones
 *  they drive (ambient background, full-player gradient). Everything else — text,
 *  glass fill/border alphas, status colors (amber/green/red) — stays constant across
 *  themes since it carries functional meaning, not brand identity. */
data class GbTheme(
    val id: String,
    val displayName: String,
    val bg0: Color,
    val panelTop: Color,
    val panelBottom: Color,
    val accentPurple: Color,
    val accentPink: Color,
    val accentTeal: Color
)

object GbThemes {
    val Default = GbTheme("midnight_violet", "Midnight Violet", Color(0xFF08080F), Color(0xFF180C2E), Color(0xFF09070D), Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFF2DD4BF))

    val all = listOf(
        Default,
        GbTheme("ocean_breeze", "Ocean Breeze", Color(0xFF06090F), Color(0xFF0C1830), Color(0xFF07090D), Color(0xFF3B82F6), Color(0xFF06B6D4), Color(0xFF14B8A6)),
        GbTheme("sunset_blaze", "Sunset Blaze", Color(0xFF0F0806), Color(0xFF2E170C), Color(0xFF0D0907), Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFFBBF24)),
        GbTheme("neon_cyber", "Neon Cyber", Color(0xFF05050A), Color(0xFF1A0C2E), Color(0xFF08070D), Color(0xFFE930FF), Color(0xFF00F0FF), Color(0xFF7C3AED)),
        GbTheme("forest_mist", "Forest Mist", Color(0xFF060B08), Color(0xFF0C2E18), Color(0xFF070D09), Color(0xFF22C55E), Color(0xFF84CC16), Color(0xFF10B981)),
        GbTheme("rose_gold", "Rose Gold", Color(0xFF0F0A0C), Color(0xFF2E0C1E), Color(0xFF0D0709), Color(0xFFF472B6), Color(0xFFFB7185), Color(0xFFFCD34D)),
        GbTheme("crimson_night", "Crimson Night", Color(0xFF0F0606), Color(0xFF2E0C0C), Color(0xFF0D0707), Color(0xFFEF4444), Color(0xFFDC2626), Color(0xFFF97316)),
        GbTheme("arctic_frost", "Arctic Frost", Color(0xFF060A0F), Color(0xFF0C222E), Color(0xFF070B0D), Color(0xFF38BDF8), Color(0xFF7DD3FC), Color(0xFF2DD4BF)),
        GbTheme("golden_hour", "Golden Hour", Color(0xFF0F0B06), Color(0xFF2E200C), Color(0xFF0D0A07), Color(0xFFF59E0B), Color(0xFFFB923C), Color(0xFFFDE047)),
        GbTheme("monochrome", "Monochrome", Color(0xFF0A0A0A), Color(0xFF1A1A1A), Color(0xFF0A0A0A), Color(0xFFE5E7EB), Color(0xFF9CA3AF), Color(0xFFD1D5DB))
    )

    fun byId(id: String): GbTheme = all.find { it.id == id } ?: Default
}

// Matches the desktop app's dark purple/pink liquid-glass palette by default; the
// brand-accent and background fields below are `var`s backed by mutableStateOf so
// switching themes (GbColors.applyTheme) reactively updates every composable that
// reads them, without threading a theme object through every screen and component.
object GbColors {
    var Bg0 by mutableStateOf(GbThemes.Default.bg0)
        private set
    var PanelTop by mutableStateOf(GbThemes.Default.panelTop)
        private set
    var PanelBottom by mutableStateOf(GbThemes.Default.panelBottom)
        private set

    var AccentPurple by mutableStateOf(GbThemes.Default.accentPurple)
        private set
    var AccentPink by mutableStateOf(GbThemes.Default.accentPink)
        private set
    var AccentTeal by mutableStateOf(GbThemes.Default.accentTeal)
        private set

    // Status colors: fixed meaning regardless of theme (danger, lossy-format badge, etc).
    val AccentAmber = Color(0xFFFBBF24)
    val AccentGreen = Color(0xFF34D399)
    val AccentRed = Color(0xFFFF1249)

    val Text1 = Color(0xFFF5F3FF)
    val Text2 = Color(0xFFC7C2D9)
    val Text3 = Color(0xFF8B85A3)

    val GlassBorder = Color(0x1FFFFFFF)   // ~12% white
    val GlassFill = Color(0x0DFFFFFF)     // ~5% white
    val GlassFill2 = Color(0x14FFFFFF)    // ~8% white
    val GlassHi = Color(0x26FFFFFF)       // top inner highlight

    // The one real "frosted" surface tint — dark, not white — used only for the
    // few big structural panels that get a live backdrop blur (main content
    // area, bottom bar). Everything else nested inside stays flat (GlassFill/2).
    val GlassBg = Color(18, 16, 30, alpha = 128)     // rgba(18,16,30,.5)
    val GlassBg2 = Color(22, 19, 36, alpha = 158)    // rgba(22,19,36,.62)

    val GlassOpaque = Color(0xFF0D0B16)   // for surfaces that must never show what's behind (dialogs, sheets)

    // The playback-panel color cast (Speed/Sleep card in the full player) — derived
    // from the current theme's primary accent so it re-tints along with everything else.
    val TintPurpleBg: Color get() = AccentPurple.copy(alpha = 0.16f)
    val TintPurpleBorder: Color get() = AccentPurple.copy(alpha = 0.35f)

    fun applyTheme(theme: GbTheme) {
        Bg0 = theme.bg0
        PanelTop = theme.panelTop
        PanelBottom = theme.panelBottom
        AccentPurple = theme.accentPurple
        AccentPink = theme.accentPink
        AccentTeal = theme.accentTeal
    }
}
