package com.inouiw.kilowatt.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.inouiw.kilowatt.domain.MeterReading
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwRadius
import com.inouiw.kilowatt.ui.theme.KwType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** True when the OS reduce-motion setting is on. Scanline, pulse and glow stop when it is. */
val LocalReduceMotion = compositionLocalOf { false }

private val istDateFormat = SimpleDateFormat("d MMM yyyy, HH:mm 'IST'", Locale.UK).apply {
    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
}

/** Times are shown in IST, per the spec. */
fun formatIst(millis: Long): String = istDateFormat.format(Date(millis))

// Indian grouping above ₹99,999 (₹1,20,500); standard commas below (₹2,198).
fun formatInr(amount: Long): String {
    val sign = if (amount < 0) "-" else ""
    val digits = kotlin.math.abs(amount).toString()
    val grouped = if (kotlin.math.abs(amount) < 100_000) {
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
        Text(text = text, style = KwType.Caption.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold), color = textColor)
    }
}

@Composable
fun KwVerifiedPill() = KwPill("✓ Verified", KwColor.Mint, KwColor.MintFill)

/** Shows whether a reading is on the server or still only on this phone. */
@Composable
fun ReadingStatusPill(reading: MeterReading) {
    if (reading.onServer) {
        KwPill("✓ On server", KwColor.Mint, KwColor.MintFill)
    } else {
        KwPill("● Not on server yet", KwColor.Gold, KwColor.GoldFill)
    }
}

@Composable
fun KwOverline(text: String, modifier: Modifier = Modifier) {
    Text(text = text.uppercase(), style = KwType.Overline, color = KwColor.Muted, modifier = modifier)
}

@Composable
fun KwDot(color: Color, size: Dp = 8.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color))
}
