package com.inouiw.kilowatt.india

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

// Live camera scanner. The reader fills in a reading when a kWh value is stable across frames.
// The shutter only locks when a reading is already there; otherwise the caller shows a message.
@Composable
fun ScannerScreen(
    locked: Boolean,
    controlsBottom: Dp,
    message: String?,
    onReading: (kWh: Double, raw: String) -> Unit,
    onShutter: () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    val currentOnReading by rememberUpdatedState(onReading)
    val context = LocalContext.current
    val reader = remember(locked) {
        if (locked) null else TfLiteMeterReader(context) { kWh, raw -> currentOnReading(kWh, raw) }
    }

    val transition = rememberInfiniteTransition(label = "scanline")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400), RepeatMode.Reverse),
        label = "sweep",
    )

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val reticleTop = statusTop + 300.dp
    val reticleWidth = 280.dp
    val reticleHeight = 120.dp

    Box(modifier = Modifier.fillMaxSize().background(KwColor.Camera)) {
        LiveCameraPreview(Modifier.fillMaxSize(), analyzer = reader)

        // Dim (72%) everywhere except the reticle, so the meter inside the box stays clear
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = (size.width - reticleWidth.toPx()) / 2f
            val top = reticleTop.toPx()
            val right = left + reticleWidth.toPx()
            val bottom = top + reticleHeight.toPx()
            val dim = Color.Black.copy(alpha = 0.72f)
            drawRect(dim, Offset.Zero, Size(size.width, top))
            drawRect(dim, Offset(0f, bottom), Size(size.width, size.height - bottom))
            drawRect(dim, Offset(0f, top), Size(left, bottom - top))
            drawRect(dim, Offset(right, top), Size(size.width - right, bottom - top))

            val bracket = 28.dp.toPx()
            val stroke = 3.dp.toPx()
            fun corner(x: Float, y: Float, dx: Float, dy: Float) {
                drawLine(KwColor.Cyan, Offset(x, y), Offset(x + dx, y), strokeWidth = stroke)
                drawLine(KwColor.Cyan, Offset(x, y), Offset(x, y + dy), strokeWidth = stroke)
            }
            corner(left, top, bracket, bracket)
            corner(right, top, -bracket, bracket)
            corner(left, bottom, bracket, -bracket)
            corner(right, bottom, -bracket, -bracket)

            if (!locked && !reduceMotion) {
                val y = top + (bottom - top) * sweep
                drawLine(
                    brush = Brush.horizontalGradient(listOf(Color.Transparent, KwColor.Mint, Color.Transparent)),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 2.dp.toPx(),
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(KwRadius.Pill))
                .background(KwColor.GlassFillStrong)
                .border(1.dp, KwColor.Border, RoundedCornerShape(KwRadius.Pill))
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(text = "KiloWatt AI", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = KwColor.Text)
                Text(text = "Smart Meter Reader", style = KwType.Caption, color = KwColor.Muted)
            }
            if (locked) {
                KwPill("✓ READING LOCKED", KwColor.Mint, KwColor.MintFill)
            } else {
                KwPill("● SCANNING...", KwColor.Cyan, KwColor.CyanFill)
            }
        }

        Text(
            text = message ?: "Align utility meter display inside the box or tap to lock",
            style = KwType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp),
            color = if (message != null) KwColor.FaultText else KwColor.Text,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 440.dp, start = 32.dp, end = 32.dp)
                .clip(RoundedCornerShape(KwRadius.Card))
                .background(KwColor.GlassFill)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = controlsBottom)
                .fillMaxWidth()
                .padding(horizontal = 40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(KwColor.GlassFill)
                    .border(1.dp, KwColor.Border, CircleShape)
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = Icons.Filled.Bolt, contentDescription = "Flash", tint = KwColor.Muted, modifier = Modifier.size(22.dp))
            }
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, KwColor.Text, CircleShape)
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) { onShutter() },
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(58.dp).clip(CircleShape).background(KwColor.Text))
            }
            Spacer(Modifier.size(52.dp))
        }
    }
}

// Scanner locked: the badge changes, then the reading sheet opens after 1 s (spec).
@Composable
fun ReadingScreen(
    reading: Double?,
    confirmed: Boolean,
    onConfirm: (Double) -> Unit,
    onScanAnother: () -> Unit,
    onTelemetry: () -> Unit,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1000)
        sheetOpen = true
    }

    Box(modifier = Modifier.fillMaxSize()) {
        ScannerScreen(
            locked = true,
            controlsBottom = 48.dp,
            message = null,
            onReading = { _, _ -> },
            onShutter = {},
        )

        AnimatedVisibility(visible = sheetOpen, enter = fadeIn(), modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
        }
        AnimatedVisibility(
            visible = sheetOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ReadingSheet(
                reading = reading,
                confirmed = confirmed,
                onConfirm = onConfirm,
                onScanAnother = onScanAnother,
                onTelemetry = onTelemetry,
            )
        }
    }
}

// Solar figures need roof details, which are not captured yet.
private const val SAMPLE_PANELS = 6
private const val SAMPLE_KWP = 3.3
private const val SAMPLE_SOLAR_SAVING_INR = 31360L
private const val ROOF_ON_FILE = false

private val istFormat = SimpleDateFormat("d MMM yyyy, HH:mm:ss 'IST'", Locale.UK).apply {
    timeZone = TimeZone.getTimeZone("Asia/Kolkata")
}

// A reading is only shown as verified after the user confirms it against the meter.
@Composable
private fun ReadingSheet(
    reading: Double?,
    confirmed: Boolean,
    onConfirm: (Double) -> Unit,
    onScanAnother: () -> Unit,
    onTelemetry: () -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    var editText by remember(reading) { mutableStateOf(reading?.let { "%.2f".format(it) } ?: "") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(688.dp)
            .clip(RoundedCornerShape(topStart = KwRadius.Sheet, topEnd = KwRadius.Sheet))
            .background(KwColor.Surface2)
            .border(1.dp, KwColor.Border, RoundedCornerShape(topStart = KwRadius.Sheet, topEnd = KwRadius.Sheet))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(KwColor.Border),
        )
        Spacer(Modifier.height(16.dp))

        KwCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    KwOverline(if (confirmed) "Meter reading" else "Check against your meter")
                    if (confirmed && reading != null) KwVerifiedPill()
                }
                Spacer(Modifier.height(8.dp))
                if (reading != null) {
                    if (editing) {
                        OutlinedTextField(
                            value = editText,
                            onValueChange = { editText = it },
                            label = { Text(text = "kWh") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = "%.2f".format(reading), style = KwType.Display, color = KwColor.Text)
                            Spacer(Modifier.width(6.dp))
                            Text(text = "kWh", style = TextStyle(fontSize = 18.sp), color = KwColor.Muted)
                        }
                    }
                    Text(
                        text = if (confirmed) {
                            "Confirmed ${istFormat.format(Date())}"
                        } else {
                            "Is this the kWh reading on your meter?"
                        },
                        style = KwType.Caption,
                        color = KwColor.Muted,
                    )
                } else {
                    Text(text = "Reading unclear", style = KwType.H1, color = KwColor.FaultText)
                    Text(text = "Show the kWh screen, move closer, and avoid glare.", style = KwType.Caption, color = KwColor.Muted)
                }
            }
        }

        if (reading != null && !confirmed) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // While editing, only Save applies: "Yes" would save the original suggestion, not the typed value
                if (!editing) {
                    Button(
                        onClick = { onConfirm(reading) },
                        modifier = Modifier.weight(1f).height(52.dp),
                        shape = RoundedCornerShape(KwRadius.Inner),
                        colors = ButtonDefaults.buttonColors(containerColor = KwColor.Mint, contentColor = KwColor.OnMint),
                    ) {
                        Text(text = "Yes, correct", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold))
                    }
                }
                OutlinedButton(
                    onClick = {
                        if (editing) {
                            editText.toDoubleOrNull()?.takeIf { it > 0.0 }?.let(onConfirm)
                            editing = false
                        } else {
                            editing = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(KwRadius.Inner),
                    border = BorderStroke(1.dp, KwColor.Border),
                ) {
                    Text(text = if (editing) "Save" else "Edit", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = KwColor.Text)
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        KwCard(modifier = Modifier.fillMaxWidth()) {
            Column {
                Text(
                    text = "₹1.95 · ₹3.10 per unit (first 100 units)",
                    style = KwType.Body.copy(fontWeight = FontWeight.Bold),
                    color = KwColor.Cyan,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = when {
                        reading == null -> "No reading to quote yet."
                        !confirmed -> "Confirm the reading to see the quote."
                        else -> TariffSchedule.SOURCE_LABEL + ". Usage needs two readings for a bill."
                    },
                    style = KwType.Body,
                    color = KwColor.Muted,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (ROOF_ON_FILE) {
            KwCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(KwRadius.Inner))
                                .background(KwColor.GoldFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(imageVector = Icons.Filled.WbSunny, contentDescription = null, tint = KwColor.Gold, modifier = Modifier.size(20.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(text = "Rooftop Solar Recommendation", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SolarStat(
                            modifier = Modifier.weight(1f),
                            value = "$SAMPLE_PANELS Panels",
                            caption = "%.1f kWp recommended".format(SAMPLE_KWP),
                        )
                        SolarStat(
                            modifier = Modifier.weight(1f),
                            value = "~${formatInr(SAMPLE_SOLAR_SAVING_INR)}",
                            caption = "Est. saving / year",
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        } else {
            Text(text = "Add roof details for a solar estimate", style = KwType.Body, color = KwColor.Muted)
        }

        Spacer(Modifier.weight(1f))

        Button(
            onClick = onScanAnother,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(KwRadius.Inner),
            colors = ButtonDefaults.buttonColors(containerColor = KwColor.Mint, contentColor = KwColor.OnMint),
        ) {
            Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text = "Scan Another Meter", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onTelemetry,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(KwRadius.Inner),
            border = BorderStroke(1.dp, KwColor.Border),
        ) {
            Text(text = "View telemetry", style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = KwColor.Text)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SolarStat(modifier: Modifier, value: String, caption: String) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(KwRadius.Inner))
            .background(KwColor.Surface2)
            .padding(12.dp),
    ) {
        Text(text = value, style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = KwColor.Gold)
        Text(text = caption, style = KwType.Caption, color = KwColor.Muted)
    }
}
