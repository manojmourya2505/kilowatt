package com.inouiw.kilowatt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inouiw.kilowatt.ui.theme.KilowattTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class AppScreen { HOME, SCAN }

private val dateFormat = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.UK)

@Composable
fun HomeDashboard(readings: List<MeterReading>, onScanClick: () -> Unit) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "KiloWatt",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        item { LatestReadingCard(readings.firstOrNull()) }
        item { UsageCard(readings) }
        item {
            Text(
                text = "History",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
        if (readings.isEmpty()) {
            item {
                Text(
                    text = "No readings yet. Scan your meter to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(readings) { reading -> HistoryRow(reading) }
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onScanClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Filled.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Scan meter", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun LatestReadingCard(latest: MeterReading?) {
    DashboardCard(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
        Text(
            text = "Latest reading",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (latest == null) "No reading yet" else String.format(Locale.UK, "%,.2f kWh", latest.kWh),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
        if (latest != null) {
            Text(
                text = dateFormat.format(Date(latest.timeMillis)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
            )
        }
    }
}

// Shows usage only when there are two readings to compare. Otherwise it says what's missing.
@Composable
private fun UsageCard(readings: List<MeterReading>) {
    val latest = readings.getOrNull(0)
    val previous = readings.getOrNull(1)
    val monthlyKwh = if (latest != null && previous != null) {
        estimateMonthlyKwh(previous, latest.kWh, latest.timeMillis)
    } else null

    DashboardCard(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)) {
        Text(
            text = "Usage estimate",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        if (monthlyKwh == null) {
            Text(
                text = if (readings.size < 2) {
                    "Needs two readings at least a day apart."
                } else {
                    "Last two readings can't give an estimate (the meter went down, or they're less than a day apart)."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val panels = (monthlyKwh / Tariff.KWH_PER_PANEL_PER_MONTH).toInt().coerceIn(4, 24)
            Text(
                text = String.format(Locale.UK, "About %.0f kWh a month", monthlyKwh),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = String.format(Locale.UK, "Est. bill %s%.2f a month", Tariff.CURRENCY, monthlyKwh * Tariff.RATE_PER_KWH),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Rooftop solar: $panels panels (estimate)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistoryRow(reading: MeterReading) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateFormat.format(Date(reading.timeMillis)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = String.format(Locale.UK, "%,.2f kWh", reading.kWh),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DashboardCard(containerColor: Color, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            content()
        }
    }
}

@Preview(showBackground = true, name = "Dashboard - history")
@Composable
private fun PreviewDashboard() {
    KilowattTheme {
        HomeDashboard(
            readings = listOf(
                MeterReading(35788.0, System.currentTimeMillis()),
                MeterReading(35700.0, System.currentTimeMillis() - 2 * 86_400_000L),
            ),
            onScanClick = {}
        )
    }
}

@Preview(showBackground = true, name = "Dashboard - empty")
@Composable
private fun PreviewDashboardEmpty() {
    KilowattTheme {
        HomeDashboard(readings = emptyList(), onScanClick = {})
    }
}
