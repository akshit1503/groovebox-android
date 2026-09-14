package com.groovebox.app.ui.theme

import androidx.compose.ui.unit.dp

/**
 * The mini player + nav bar float over content instead of reserving space in the
 * Scaffold (see GrooveBoxRoot's dropped bottom padding), so every scrollable screen
 * needs a trailing spacer at least this tall or its last rows end up unreachable
 * underneath them. Measured on-device (Galaxy A32, 420dpi): mini player ~53dp +
 * nav bar ~88dp (incl. gesture-nav inset) ≈ 141dp; this adds margin for devices
 * with taller system bars.
 */
object GbDimens {
    val BottomBarClearance = 150.dp
}
