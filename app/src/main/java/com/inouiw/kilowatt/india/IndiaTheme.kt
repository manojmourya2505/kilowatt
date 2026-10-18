package com.inouiw.kilowatt.india

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

// Design tokens from KiloWatt-India-Handoff/tokens.json. Change colours here only.
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
    val CameraFace = Color(0xFF1A1E25)
    val ReticleGhost = Color(0xFF2A2F38)
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
fun KiloWattIndiaTheme(content: @Composable () -> Unit) {
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

// Indian grouping above ₹99,999 (₹1,20,500); standard commas below (₹2,198).
fun formatInr(amount: Long): String {
    val sign = if (amount < 0) "-" else ""
    val digits = abs(amount).toString()
    val grouped = if (abs(amount) < 100_000) {
        digits.reversed().chunked(3).joinToString(",").reversed()
    } else {
        val last3 = digits.takeLast(3)
        val rest = digits.dropLast(3)
        rest.reversed().chunked(2).joinToString(",").reversed() + "," + last3
    }
    return "₹$sign$grouped"
}

@Composable
fun KwCard(
    modifier: Modifier = Modifier,
    color: Color = KwColor.Card,
    borderColor: Color = KwColor.Border,
    padding: Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KwRadius.Card))
            .background(color)
            .border(1.dp, borderColor, RoundedCornerShape(KwRadius.Card))
            .padding(padding),
    ) {
        content()
    }
}

// A state is shown by text and colour together, per the spec.
@Composable
fun KwPill(text: String, textColor: Color, fill: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(KwRadius.Pill))
            .background(fill)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = KwType.Caption.copy(fontWeight = FontWeight.Bold), color = textColor)
    }
}

@Composable
fun KwVerifiedPill() = KwPill("✓ Verified", KwColor.Mint, KwColor.MintFill)

@Composable
fun KwOverline(text: String, modifier: Modifier = Modifier) {
    Text(text = text.uppercase(), style = KwType.Overline, color = KwColor.Muted, modifier = modifier)
}

@Composable
fun KwSampleLabel(modifier: Modifier = Modifier) {
    Text(
        text = "Sample data",
        style = KwType.Caption,
        color = KwColor.Muted,
        textAlign = TextAlign.End,
        modifier = modifier,
    )
}

@Composable
fun KwStatusRow(content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) { content() }
}

@Composable
fun KwDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(50)).background(color))
}

@Composable
fun KwHSpacer(width: Dp) = Spacer(Modifier.width(width))

private val istDateFormat = java.text.SimpleDateFormat("d MMM yyyy, HH:mm 'IST'", java.util.Locale.UK).apply {
    timeZone = java.util.TimeZone.getTimeZone("Asia/Kolkata")
}

// Times are shown in IST, per the spec
fun formatIst(millis: Long): String = istDateFormat.format(java.util.Date(millis))
