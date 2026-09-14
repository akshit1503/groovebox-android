package com.groovebox.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.groovebox.app.ui.theme.GbColors
import com.groovebox.app.ui.theme.GbDimens
import com.groovebox.app.ui.theme.GbTheme
import com.groovebox.app.ui.theme.GbThemes

@Composable
fun ThemesScreen(themeId: String, onSetTheme: (String) -> Unit, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GbColors.Text1)
            }
            Text("Themes", color = GbColors.Text1, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            GbThemes.all.chunked(2).forEach { rowThemes ->
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowThemes.forEach { theme ->
                        ThemeSwatch(
                            theme = theme,
                            selected = themeId == theme.id,
                            onClick = { onSetTheme(theme.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowThemes.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            // See GbDimens.BottomBarClearance — 90dp still left content tucked
            // behind the mini player/nav bar; this is the actual measured clearance.
            Spacer(Modifier.height(GbDimens.BottomBarClearance))
        }
    }
}

@Composable
private fun ThemeSwatch(theme: GbTheme, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    // Fixed 1dp border and no extra selected-only content (the old check mark row)
    // — either of those changes a tile's own measured size, which made the selected
    // tile visibly grow relative to its neighbors. Only the fill/border color change.
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) theme.accentPurple.copy(alpha = 0.14f) else GbColors.GlassFill)
            .border(1.dp, if (selected) theme.accentPurple else GbColors.GlassBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
            Dot(theme.accentPurple)
            Dot(theme.accentPink)
            Dot(theme.accentTeal)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            theme.displayName,
            color = GbColors.Text1,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2
        )
    }
}

@Composable
private fun Dot(color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.5.dp, GbColors.Bg0, CircleShape)
    )
}
