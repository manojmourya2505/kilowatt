package com.inouiw.kilowatt.india

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Everything on this screen comes from saved scans. Nothing is sample data.
@Composable
fun HomeScreen(
    readings: List<QueuedReading>,
    onScan: () -> Unit,
    onBalcony: () -> Unit,
    onLatestReading: () -> Unit,
) {
    val latest = readings.maxByOrNull { it.capturedAtMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(text = "KiloWatt", style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold), color = KwColor.Text)
        Text(text = "Readings are saved on this phone.", style = KwType.Caption, color = KwColor.Muted)
        Spacer(Modifier.height(24.dp))

        TariffCard()
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionCard(
                modifier = Modifier.weight(1f),
                tileColor = KwColor.CyanFill,
                iconTint = KwColor.Cyan,
                icon = Icons.Filled.CameraAlt,
                title = "Scan Utility Meter",
                caption = "Read the kWh display and save it",
                onClick = onScan,
            )
            ActionCard(
                modifier = Modifier.weight(1f),
                tileColor = KwColor.GoldFill,
                iconTint = KwColor.Gold,
                icon = Icons.Filled.WbSunny,
                title = "Scan Balcony / Roof",
                caption = "Measure the balcony surface",
                onClick = onBalcony,
            )
        }
        Spacer(Modifier.height(16.dp))
        LatestReadingCard(latest = latest, onClick = onLatestReading)
        Spacer(Modifier.height(16.dp))
        UsageCard(readings = readings)
    }
}

@Composable
private fun TariffCard() {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            KwOverline("Current tariff")
            Spacer(Modifier.height(6.dp))
            Text(text = "₹1.95 · ₹3.10 per unit", style = KwType.H1, color = KwColor.Text)
            Text(text = TariffSchedule.SOURCE_LABEL + ". Confirm with your bill.", style = KwType.Caption, color = KwColor.Muted)
        }
    }
}

@Composable
private fun ActionCard(
    modifier: Modifier,
    tileColor: Color,
    iconTint: Color,
    icon: ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit,
) {
    KwCard(
        modifier = modifier.clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick),
    ) {
        Column {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(KwRadius.Inner))
                    .background(tileColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(text = title, style = KwType.H2.copy(fontSize = 17.sp, lineHeight = 22.sp), color = KwColor.Text)
            Spacer(Modifier.height(6.dp))
            Text(text = caption, style = KwType.Caption.copy(fontSize = 13.sp, lineHeight = 18.sp), color = KwColor.Muted)
        }
    }
}

@Composable
private fun LatestReadingCard(latest: QueuedReading?, onClick: () -> Unit) {
    KwCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = MutableInteractionSource(), indication = null, onClick = onClick),
    ) {
        Column {
            KwOverline("Latest reading")
            Spacer(Modifier.height(8.dp))
            if (latest == null) {
                Text(text = "No reading yet", style = KwType.H1, color = KwColor.Text)
                Text(text = "Scan a meter to save your first reading.", style = KwType.Caption, color = KwColor.Muted)
            } else {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = String.format(Locale.UK, "%.2f", latest.kWh),
                        style = TextStyle(fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
                        color = KwColor.Text,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(text = "kWh", style = TextStyle(fontSize = 16.sp), color = KwColor.Muted)
                }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(latest)
                    Spacer(Modifier.width(8.dp))
                    Text(text = formatIst(latest.capturedAtMillis), style = KwType.Caption, color = KwColor.Muted)
                }
            }
        }
    }
}

// Monthly usage is only known once two readings exist; the bill needs a tariff, which is not set yet.
@Composable
private fun UsageCard(readings: List<QueuedReading>) {
    val sorted = readings.sortedByDescending { it.capturedAtMillis }
    val newest = sorted.getOrNull(0)
    val older = sorted.getOrNull(1)
    val usedKwh = if (newest != null && older != null) newest.kWh - older.kWh else null
    val days = if (newest != null && older != null) {
        (newest.capturedAtMillis - older.capturedAtMillis) / 86_400_000.0
    } else null

    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            KwOverline("Usage")
            Spacer(Modifier.height(6.dp))
            if (usedKwh == null || days == null || usedKwh <= 0.0 || days < 1.0) {
                Text(
                    text = "Needs two readings at least a day apart, with the meter going up.",
                    style = KwType.Body,
                    color = KwColor.Muted,
                )
            } else {
                val monthly = usedKwh / days * 30.44
                Text(
                    text = String.format(Locale.UK, "About %.0f kWh a month", monthly),
                    style = KwType.H2,
                    color = KwColor.Text,
                )
                val bill = TariffSchedule.energyCharge(monthly)
                Text(
                    text = if (bill == null) {
                        "Bill needs the full tariff above ${TariffSchedule.pricedUpToUnits} units."
                    } else {
                        String.format(Locale.UK, "Energy charge estimate ₹%.0f a month", bill)
                    },
                    style = KwType.Caption,
                    color = KwColor.Muted,
                )
            }
        }
    }
}

@Composable
fun StatusPill(reading: QueuedReading) {
    if (reading.synced) {
        KwPill("✓ Synced", KwColor.Mint, KwColor.MintFill)
    } else {
        KwPill("● Saved on phone", KwColor.Gold, KwColor.GoldFill)
    }
}
