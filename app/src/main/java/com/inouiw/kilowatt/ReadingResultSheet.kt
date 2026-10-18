package com.inouiw.kilowatt

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ElectricMeter
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.inouiw.kilowatt.ui.theme.KilowattTheme
import java.util.Locale

/** The three layouts the result sheet can show. */
enum class ReadingResultState { FAULT, FIRST_READING, SECOND_READING }

fun MeterUiState.resultState(): ReadingResultState = when {
    hasFault -> ReadingResultState.FAULT
    hasConsumptionEstimate -> ReadingResultState.SECOND_READING
    else -> ReadingResultState.FIRST_READING
}

@Composable
fun ReadingResultSheet(uiState: MeterUiState, onResetScan: () -> Unit) {
    val state = uiState.resultState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 12.dp,
        shadowElevation = 16.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Box(
                modifier = Modifier
                    .width(40.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f))
                    .align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = when (state) {
                    ReadingResultState.FAULT -> "Meter fault"
                    ReadingResultState.FIRST_READING -> "First reading"
                    ReadingResultState.SECOND_READING -> "Usage ready"
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            ReadingValueCard(uiState)

            Spacer(modifier = Modifier.height(12.dp))

            when (state) {
                ReadingResultState.FAULT -> FaultCard(uiState.faultMessage)
                ReadingResultState.FIRST_READING -> FirstReadingCard()
                ReadingResultState.SECOND_READING -> {
                    BillCard(uiState)
                    Spacer(modifier = Modifier.height(12.dp))
                    SolarCard(uiState)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onResetScan,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(imageVector = Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Scan Another Meter", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReadingValueCard(uiState: MeterUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.ElectricMeter,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Meter Reading",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = uiState.detectedKWh?.let { String.format(Locale.US, "%,.2f kWh", it) } ?: "Reading unclear",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            if (uiState.isReadingVerified && uiState.detectedKWh != null) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Verified",
                    tint = Color(0xFF00C853),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
private fun FirstReadingCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Baseline saved",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Scan this meter again at least a day later. We'll compare the two readings to estimate your usage, bill and solar potential.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BillCard(uiState: MeterUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Est. monthly bill",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = String.format(Locale.US, "%s%.2f", Tariff.CURRENCY, uiState.estimatedMonthlyBill),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "@ ${Tariff.CURRENCY}${Tariff.RATE_PER_KWH} / kWh, based on usage since your last reading",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SolarCard(uiState: MeterUiState) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.WbSunny,
                contentDescription = "Solar potential",
                tint = Color(0xFFF57F17),
                modifier = Modifier.size(26.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = "Rooftop solar: ${uiState.solarPanelCount} panels",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Text(
                    text = String.format(Locale.US, "Save ~%s%.0f / year (estimate)", Tariff.CURRENCY, uiState.solarAnnualSavings),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun FaultCard(faultMessage: String?) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = "Fault",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = faultMessage ?: "Meter error code flagged.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Bill and solar estimates are hidden until the meter has been checked.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.9f)
                )
            }
        }
    }
}

@Preview(showBackground = true, name = "1. First reading")
@Composable
private fun PreviewFirstReading() {
    KilowattTheme {
        ReadingResultSheet(
            uiState = MeterUiState(isLocked = true, isReadingVerified = true, detectedKWh = 35788.0),
            onResetScan = {}
        )
    }
}

@Preview(showBackground = true, name = "2. Second reading")
@Composable
private fun PreviewSecondReading() {
    KilowattTheme {
        ReadingResultSheet(
            uiState = MeterUiState(
                isLocked = true,
                isReadingVerified = true,
                detectedKWh = 35788.0,
                hasConsumptionEstimate = true,
                estimatedMonthlyBill = 52.40,
                solarPanelCount = 6,
                solarAnnualSavings = 1260.0
            ),
            onResetScan = {}
        )
    }
}

@Preview(showBackground = true, name = "3. Meter fault")
@Composable
private fun PreviewFault() {
    KilowattTheme {
        ReadingResultSheet(
            uiState = MeterUiState(
                isLocked = true,
                isReadingVerified = true,
                hasFault = true,
                faultMessage = "Meter Anomaly Detected: ERR flag active"
            ),
            onResetScan = {}
        )
    }
}
