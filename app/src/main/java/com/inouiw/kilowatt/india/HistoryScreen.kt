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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class HistoryFilter(val label: String) { ALL("All"), METERS("Meters"), SOLAR("Solar Surveys") }

// Readings come from the server when it can be reached. Readings still waiting to upload come from the phone.
@Composable
fun HistoryScreen(
    localPending: List<QueuedReading>,
    surveys: List<BalconySurveyRecord>,
    onRescan: () -> Unit,
) {
    var filter by remember { mutableStateOf(HistoryFilter.ALL) }
    var checking by remember { mutableStateOf(true) }
    var serverReadings by remember { mutableStateOf<List<ServerReading>?>(null) }

    LaunchedEffect(Unit) {
        checking = true
        serverReadings = withContext(Dispatchers.IO) { fetchServerReadings() }
        checking = false
    }

    val sortedSurveys = surveys.sortedByDescending { it.capturedAtMillis }
    val serverList = serverReadings?.sortedByDescending { it.capturedAtMillis } ?: emptyList()
    // A phone copy is hidden once the server has the same reading
    val serverIds = serverList.map { it.id }.toSet()
    val pendingList = localPending
        .filter { it.id !in serverIds }
        .sortedByDescending { it.capturedAtMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text(text = "Audit & History", style = KwType.H1, color = KwColor.Text)
        Spacer(Modifier.height(4.dp))
        Text(text = "Saved readings and surveys", style = KwType.Caption, color = KwColor.Muted)
        Spacer(Modifier.height(12.dp))

        ServerStatusCard(
            checking = checking,
            reachable = serverReadings != null,
        )
        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(KwRadius.Pill))
                .background(KwColor.Card)
                .border(1.dp, KwColor.Border, RoundedCornerShape(KwRadius.Pill)),
        ) {
            HistoryFilter.entries.forEach { option ->
                val active = filter == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(KwRadius.Pill))
                        .background(if (active) KwColor.Mint else KwColor.Card)
                        .clickable(interactionSource = MutableInteractionSource(), indication = null) { filter = option },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = option.label,
                        style = KwType.Caption.copy(fontWeight = FontWeight.Bold),
                        color = if (active) KwColor.OnMint else KwColor.Muted,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val showMeters = filter != HistoryFilter.SOLAR
            val showSurveys = filter != HistoryFilter.METERS
            val nothing = (showMeters && serverList.isEmpty() && pendingList.isEmpty()) &&
                (showSurveys && sortedSurveys.isEmpty())

            if (nothing) {
                KwCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nothing saved yet. Scan a meter or survey the balcony to start.",
                        style = KwType.Body,
                        color = KwColor.Muted,
                    )
                }
            }
            if (showMeters) {
                serverList.forEach { reading -> ServerReadingRow(reading) }
                pendingList.forEach { reading -> PendingReadingRow(reading) }
            }
            if (showSurveys) sortedSurveys.forEach { survey -> SurveyRow(survey) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ServerStatusCard(checking: Boolean, reachable: Boolean) {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            when {
                checking -> Text(text = "Checking the server…", style = KwType.Body, color = KwColor.Muted)
                reachable -> Text(text = "Showing records from the server", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Mint)
                else -> Text(text = "Server not reachable", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.FaultText)
            }
            if (!checking && !reachable) {
                Text(text = "Showing only readings still saved on this phone.", style = KwType.Caption, color = KwColor.Muted)
            }
        }
    }
}

@Composable
private fun ServerReadingRow(reading: ServerReading) {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "Meter reading", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                    Text(text = formatIst(reading.capturedAtMillis), style = KwType.Caption, color = KwColor.Muted)
                }
                Text(
                    text = String.format(Locale.UK, "%.2f kWh", reading.kWh),
                    style = KwType.Body.copy(fontWeight = FontWeight.Bold),
                    color = KwColor.Text,
                )
            }
            Spacer(Modifier.height(10.dp))
            KwPill("✓ On server", KwColor.Mint, KwColor.MintFill)
        }
    }
}

@Composable
private fun PendingReadingRow(reading: QueuedReading) {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "Meter reading", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                    Text(text = formatIst(reading.capturedAtMillis), style = KwType.Caption, color = KwColor.Muted)
                }
                Text(
                    text = String.format(Locale.UK, "%.2f kWh", reading.kWh),
                    style = KwType.Body.copy(fontWeight = FontWeight.Bold),
                    color = KwColor.Text,
                )
            }
            Spacer(Modifier.height(10.dp))
            KwPill("● Not on server yet", KwColor.Gold, KwColor.GoldFill)
        }
    }
}

@Composable
private fun SurveyRow(survey: BalconySurveyRecord) {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(text = "Balcony survey", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
                    Text(text = formatIst(survey.capturedAtMillis), style = KwType.Caption, color = KwColor.Muted)
                }
                Text(
                    text = String.format(Locale.UK, "%.1f m² · %.1f m", survey.areaM2, survey.spanM),
                    style = KwType.Caption.copy(fontWeight = FontWeight.Bold),
                    color = KwColor.Cyan,
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                KwPill("● Phone only", KwColor.Gold, KwColor.GoldFill)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${survey.panels} ${if (survey.panels == 1) "panel" else "panels"} (estimate)",
                    style = KwType.Caption,
                    color = KwColor.Muted,
                )
            }
        }
    }
}
