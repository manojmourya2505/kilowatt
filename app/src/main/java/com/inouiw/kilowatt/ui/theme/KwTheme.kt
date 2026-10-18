package com.inouiw.kilowatt.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Design tokens from the KiloWatt India handoff (tokens.json). Change colours here only. */
object KwColor {
    val Bg = Color(0xFF0A0C10)
    val Card = Color(0xFF141820)
    val Surface2 = Color(0xFF10141B)
    val Border = Color(0xFF232936)
    val Text = Color(0xFFF5F7FA)
    val Muted = Color(0xFF8E95A3)
    val Mint = Color(0xFF00F08B)
    val Cyan = Color(0xFF00E5FF)
    val Gold = Color(0xFFFFB800)
    val Fault = Color(0xFFFF4D5E)
    val FaultText = Color(0xFFFF8A95)
    val Camera = Color(0xFF12161D)
    val OnMint = Color(0xFF0A0C10)
    val GlassFill = Color(0x9E10141B)
    val GlassFillStrong = Color(0xDB10141B)
    val FaultFill = Color(0x1AFF4D5E)
    val FaultBorder = Color(0x73FF4D5E)
    val MintFill = Color(0x2400F08B)
    val CyanFill = Color(0x1F00E5FF)
    val GoldFill = Color(0x29FFB800)
}

object KwRadius {
    val Inner = 14.dp
    val Card = 20.dp
    val Sheet = 28.dp
    val Pill = 999.dp
}

object KwType {
    val Display = TextStyle(fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp)
    val H1 = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
    val H2 = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
    val Body = TextStyle(fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val Caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val Overline = TextStyle(fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
}

@Composable
fun KiloWattTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = KwColor.Bg,
            surface = KwColor.Card,
            onBackground = KwColor.Text,
            onSurface = KwColor.Text,
            primary = KwColor.Mint,
            onPrimary = KwColor.OnMint,
            secondary = KwColor.Cyan,
            error = KwColor.Fault,
        ),
        content = content,
    )
}
