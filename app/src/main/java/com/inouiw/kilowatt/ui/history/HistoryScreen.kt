package com.inouiw.kilowatt.ui.history

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.inouiw.kilowatt.domain.BalconySurvey
import com.inouiw.kilowatt.domain.MeterReading
import com.inouiw.kilowatt.ui.components.KwCard
import com.inouiw.kilowatt.ui.components.ReadingStatusPill
import com.inouiw.kilowatt.ui.components.formatIst
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwRadius
import com.inouiw.kilowatt.ui.theme.KwType
import java.util.Locale

/** Saved readings and surveys. Readings come from the server when it can be reached. */
@Composable
fun HistoryScreen(
    state: HistoryUiState,
    onFilter: (HistoryFilter) -> Unit,
    onRescan: () -> Unit,
) {
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

        ServerStatusCard(reachable = state.serverReachable, checking = state.checkingServer)
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
                val active = state.filter == option
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clip(RoundedCornerShape(KwRadius.Pill))
                        .background(if (active) KwColor.Mint else KwColor.Card)
                        .clickable(interactionSource = MutableInteractionSource(), indication = null) { onFilter(option) },
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
        val showMeters = state.filter != HistoryFilter.SOLAR
        val showSurveys = state.filter != HistoryFilter.METERS
        val nothing = (showMeters && state.readings.isEmpty()) && (showSurveys && state.surveys.isEmpty())

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (nothing) {
                KwCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Nothing saved yet. Scan a meter or survey the balcony to start.",
                        style = KwType.Body,
                        color = KwColor.Muted,
                    )
                }
            }
            if (showMeters) state.readings.forEach { reading -> ReadingRow(reading) }
            if (showSurveys) state.surveys.forEach { survey -> SurveyRow(survey) }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ServerStatusCard(reachable: Boolean?, checking: Boolean) {
    KwCard(modifier = Modifier.fillMaxWidth()) {
        Column {
            when {
                checking -> Text(text = "Checking the server…", style = KwType.Body, color = KwColor.Muted)
                reachable == true -> Text(
                    text = "Showing records from the server",
                    style = KwType.Body.copy(fontWeight = FontWeight.Bold),
                    color = KwColor.Mint,
                )
                else -> {
                    Text(text = "Server not reachable", style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.FaultText)
                    Text(text = "Showing only readings still saved on this phone.", style = KwType.Caption, color = KwColor.Muted)
                }
            }
        }
    }
}

@Composable
private fun ReadingRow(reading: MeterReading) {
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
            ReadingStatusPill(reading)
        }
    }
}

@Composable
private fun SurveyRow(survey: BalconySurvey) {
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
                com.inouiw.kilowatt.ui.components.KwPill("● Phone only", KwColor.Gold, KwColor.GoldFill)
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
