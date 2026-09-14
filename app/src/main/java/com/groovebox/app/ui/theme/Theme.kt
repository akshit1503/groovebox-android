package com.groovebox.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

@Composable
fun GrooveBoxTheme(content: @Composable () -> Unit) {
    // Built inside the composable (not a top-level val) so it re-reads GbColors's
    // current values — a plain val would freeze on whatever theme was active at
    // first composition and never follow later theme switches.
    val scheme = darkColorScheme(
        primary = GbColors.AccentPurple,
        secondary = GbColors.AccentPink,
        tertiary = GbColors.AccentTeal,
        background = GbColors.Bg0,
        surface = GbColors.PanelTop,
        onBackground = GbColors.Text1,
        onSurface = GbColors.Text1,
        error = GbColors.AccentRed
    )
    MaterialTheme(
        colorScheme = scheme,
        typography = MaterialTheme.typography,
        content = content
    )
}
