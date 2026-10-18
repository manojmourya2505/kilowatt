package com.inouiw.kilowatt.india

import android.provider.Settings
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.inouiw.kilowatt.R
import kotlinx.coroutines.delay

enum class IndiaScreen { LAUNCH, HOME, SCANNER, READING, TELEMETRY, BALCONY, HISTORY }

// True when the OS reduce-motion setting is on. Scanline, pulse and glow stop when it is.
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun KiloWattIndiaApp() {
    KiloWattIndiaTheme {
        val context = LocalContext.current
        val reduceMotion = remember {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }
        CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
            var screen by remember { mutableStateOf(IndiaScreen.LAUNCH) }
            var kWh by remember { mutableStateOf<Double?>(null) }
            var kWhConfirmed by remember { mutableStateOf(false) }
            val queue = remember { ReadingQueue(context) }
            // Upload anything waiting on the phone, now and whenever the phone is online
            LaunchedEffect(Unit) {
                schedulePeriodicReadingSync(context)
                enqueueReadingSync(context)
            }
            val surveyStore = remember { BalconySurveyStore(context) }
            var surveys by remember { mutableStateOf(surveyStore.all()) }
            var readings by remember { mutableStateOf(queue.all()) }
            var scanMessage by remember { mutableStateOf<String?>(null) }
            Box(Modifier.fillMaxSize().background(KwColor.Bg)) {
                when (screen) {
                    IndiaScreen.LAUNCH -> LaunchScreen(onReady = { screen = IndiaScreen.HOME })
                    IndiaScreen.HOME -> HomeScreen(
                        readings = readings,
                        onScan = { screen = IndiaScreen.SCANNER },
                        onBalcony = { screen = IndiaScreen.BALCONY },
                        onLatestReading = { screen = IndiaScreen.HISTORY },
                    )
                    IndiaScreen.SCANNER -> ScannerScreen(
                        locked = false,
                        controlsBottom = 100.dp,
                        message = scanMessage,
                        onReading = { value, _ ->
                            kWh = value
                            kWhConfirmed = false
                            scanMessage = null
                            screen = IndiaScreen.READING
                        },
                        onShutter = {
                            scanMessage = "Couldn't read the display. Move closer and avoid glare."
                        },
                    )
                    IndiaScreen.READING -> ReadingScreen(
                        reading = kWh,
                        confirmed = kWhConfirmed,
                        onConfirm = { value ->
                            kWh = value
                            kWhConfirmed = true
                            // Save on the phone first, then upload when the network allows
                            queue.add(value, System.currentTimeMillis())
                            readings = queue.all()
                            enqueueReadingSync(context)
                        },
                        onScanAnother = {
                            kWh = null
                            kWhConfirmed = false
                            screen = IndiaScreen.SCANNER
                        },
                        onTelemetry = { screen = IndiaScreen.TELEMETRY },
                    )
                    IndiaScreen.TELEMETRY -> TelemetryScreen(
                        reading = readings.maxByOrNull { it.capturedAtMillis },
                        onBackHome = { screen = IndiaScreen.HOME },
                    )
                    IndiaScreen.BALCONY -> BalconyScreen(
                        onSaved = { spanM, areaM2, panels ->
                            // Save on the phone first, then show it in History
                            surveyStore.add(spanM, areaM2, panels, System.currentTimeMillis())
                            surveys = surveyStore.all()
                            screen = IndiaScreen.HISTORY
                        },
                    )
                    IndiaScreen.HISTORY -> HistoryScreen(
                        localPending = readings.filter { !it.synced },
                        surveys = surveys,
                        onRescan = { screen = IndiaScreen.SCANNER },
                    )
                }

                if (screen.showsTabBar()) {
                    KwTabBar(
                        selected = screen,
                        onSelect = { screen = it },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

private fun IndiaScreen.showsTabBar(): Boolean =
    this == IndiaScreen.HOME || this == IndiaScreen.SCANNER || this == IndiaScreen.BALCONY || this == IndiaScreen.HISTORY

// Bottom bar: exactly four tabs, no Settings (out of scope per the spec).
@Composable
fun KwTabBar(selected: IndiaScreen, onSelect: (IndiaScreen) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple(IndiaScreen.HOME, "Home", Icons.Filled.Home),
        Triple(IndiaScreen.BALCONY, "Balcony AR", Icons.Filled.WbSunny),
        Triple(IndiaScreen.SCANNER, "Scanner", Icons.Filled.CameraAlt),
        Triple(IndiaScreen.HISTORY, "History", Icons.Filled.History),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp)
            .background(KwColor.Surface2)
            .border(1.dp, KwColor.Border)
            .navigationBarsPadding(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (screen, label, icon) ->
            val active = selected == screen
            val tint = if (active) KwColor.Mint else KwColor.Muted
            Column(
                modifier = Modifier
                    .size(width = 84.dp, height = 56.dp)
                    .clickable(interactionSource = MutableInteractionSource(), indication = null) { onSelect(screen) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(22.dp))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = label,
                    style = KwType.Caption.copy(fontWeight = if (active) FontWeight.Bold else FontWeight.Medium),
                    color = tint,
                )
            }
        }
    }
}

@Composable
fun LaunchScreen(onReady: () -> Unit) {
    val reduceMotion = LocalReduceMotion.current
    // The Neural Vision Core is not wired yet, so this waits 2.4 s, as the spec's auto-advance does.
    LaunchedEffect(Unit) {
        delay(2400)
        onReady()
    }

    val transition = rememberInfiniteTransition(label = "launch")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(tween(3000), RepeatMode.Reverse),
        label = "glow",
    )
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "pulse",
    )
    val glowAlpha = if (reduceMotion) 0.6f else glow
    val pulseAlpha = if (reduceMotion) 1f else pulse

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(interactionSource = MutableInteractionSource(), indication = null) { onReady() },
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(KwColor.Mint.copy(alpha = 0.22f * glowAlpha), Color.Transparent),
                        ),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .clip(RoundedCornerShape(40.dp))
                        .background(KwColor.Surface2)
                        .border(1.dp, KwColor.Border, RoundedCornerShape(40.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_kilowatt_mark),
                        contentDescription = "KiloWatt logo",
                        modifier = Modifier.size(104.dp),
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                text = buildAnnotatedString {
                    append("Kilo")
                    withStyle(SpanStyle(color = KwColor.Mint)) { append("Watt") }
                },
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1).sp),
                color = KwColor.Text,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Smart Clean Energy Scanner",
                style = TextStyle(fontSize = 14.sp),
                color = KwColor.Muted,
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
                .clip(RoundedCornerShape(50))
                .background(KwColor.Card)
                .border(1.dp, KwColor.Border, RoundedCornerShape(50))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KwDot(color = KwColor.Mint.copy(alpha = pulseAlpha), size = 8.dp)
            Spacer(Modifier.width(8.dp))
            Text(text = "Neural Vision Core Ready", style = KwType.Caption, color = KwColor.Text)
        }
    }
}
