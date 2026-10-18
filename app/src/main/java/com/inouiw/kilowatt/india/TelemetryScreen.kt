package com.inouiw.kilowatt.india

import androidx.compose.foundation.background
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// Shows the latest saved reading and where it is in the sync flow. Nothing is sample data.
@Composable
fun TelemetryScreen(reading: QueuedReading?, onBackHome: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text(text = "Reading telemetry", style = KwType.H1, color = KwColor.Text)
        Spacer(Modifier.height(4.dp))
        if (reading == null) {
            Text(text = "No reading saved yet.", style = KwType.Caption, color = KwColor.Muted)
        } else {
            Text(
                text = "${String.format(Locale.UK, "%.2f", reading.kWh)} kWh · ${formatIst(reading.capturedAtMillis)}",
                style = KwType.Caption,
                color = KwColor.Muted,
            )
            Spacer(Modifier.height(16.dp))
            KwCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    StatusPill(reading)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (reading.synced) {
                            "Accepted by the server."
                        } else {
                            "Saved on this phone. It will be sent when the server is connected."
                        },
                        style = KwType.Body,
                        color = KwColor.Muted,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            KwCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    PayloadRow("reading", String.format(Locale.UK, "%.2f kWh", reading.kWh))
                    PayloadRow("captured", formatIst(reading.capturedAtMillis))
                    PayloadRow("id", reading.id.take(8), last = true)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBackHome,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(KwRadius.Inner),
            colors = ButtonDefaults.buttonColors(containerColor = KwColor.Mint, contentColor = KwColor.OnMint),
        ) {
            Text(text = "Back to Home", style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold))
        }
    }
}

@Composable
private fun PayloadRow(key: String, value: String, last: Boolean = false) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = key, style = KwType.Body, color = KwColor.Muted)
            Text(text = value, style = KwType.Body.copy(fontWeight = FontWeight.Bold), color = KwColor.Text)
        }
        if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(KwColor.Border))
    }
}
