package com.inouiw.kilowatt.ui

import android.app.Application
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
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.inouiw.kilowatt.KiloWattApplication
import com.inouiw.kilowatt.sync.SyncScheduler
import com.inouiw.kilowatt.ui.balcony.BalconyScreen
import com.inouiw.kilowatt.ui.balcony.BalconyViewModel
import com.inouiw.kilowatt.ui.components.LocalReduceMotion
import com.inouiw.kilowatt.ui.history.HistoryScreen
import com.inouiw.kilowatt.ui.history.HistoryViewModel
import com.inouiw.kilowatt.ui.home.HomeScreen
import com.inouiw.kilowatt.ui.home.HomeViewModel
import com.inouiw.kilowatt.ui.launch.LaunchScreen
import com.inouiw.kilowatt.ui.navigation.AppNavigationViewModel
import com.inouiw.kilowatt.ui.navigation.AppScreen
import com.inouiw.kilowatt.ui.scanner.ReadingScreen
import com.inouiw.kilowatt.ui.scanner.ScannerScreen
import com.inouiw.kilowatt.ui.scanner.ScannerViewModel
import com.inouiw.kilowatt.ui.telemetry.TelemetryScreen
import com.inouiw.kilowatt.ui.theme.KwColor
import com.inouiw.kilowatt.ui.theme.KwType
import android.provider.Settings

/**
 * Root of the app. It only wires ViewModels to screens and decides which screen shows.
 * Screens get state from their ViewModel and send user actions back to it.
 */
@Composable
fun KiloWattApp() {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val container = (app as KiloWattApplication).container

    val navigation: AppNavigationViewModel = viewModel()
    val home: HomeViewModel = viewModel(factory = viewModelFactory {
        initializer { HomeViewModel(container.readingRepository) }
    })
    val scanner: ScannerViewModel = viewModel(factory = viewModelFactory {
        initializer { ScannerViewModel(app, container.readingRepository) }
    })
    val balcony: BalconyViewModel = viewModel(factory = viewModelFactory {
        initializer { BalconyViewModel(app, container.surveyRepository) }
    })
    val history: HistoryViewModel = viewModel(factory = viewModelFactory {
        initializer { HistoryViewModel(container.readingRepository, container.surveyRepository) }
    })

    val screen by navigation.screen.collectAsState()
    val homeState by home.uiState.collectAsState()
    val historyState by history.uiState.collectAsState()
    val suggested by scanner.suggested.collectAsState()
    val confirmed by scanner.confirmed.collectAsState()
    val scanMessage by scanner.message.collectAsState()

    // Upload anything waiting on the phone, now and whenever the phone is online
    LaunchedEffect(Unit) {
        SyncScheduler.schedulePeriodic(context)
        SyncScheduler.requestNow(context)
    }
    // A reading was suggested while the scanner is open: show it for confirmation
    LaunchedEffect(suggested) {
        if (suggested != null && screen == AppScreen.SCANNER) navigation.navigate(AppScreen.READING)
    }

    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }

    CompositionLocalProvider(LocalReduceMotion provides reduceMotion) {
        Box(modifier = Modifier.fillMaxSize().background(KwColor.Bg)) {
            when (screen) {
                AppScreen.LAUNCH -> LaunchScreen(onReady = {
                    home.refreshServer()
                    navigation.navigate(AppScreen.HOME)
                })
                AppScreen.HOME -> HomeScreen(
                    state = homeState,
                    onScan = { navigation.navigate(AppScreen.SCANNER) },
                    onBalcony = { navigation.navigate(AppScreen.BALCONY) },
                    onLatestReading = { navigation.navigate(AppScreen.HISTORY) },
                )
                AppScreen.SCANNER -> ScannerScreen(
                    analyzer = scanner.analyzer,
                    locked = false,
                    controlsBottom = 100.dp,
                    message = scanMessage,
                    onShutter = scanner::onShutter,
                )
                AppScreen.READING -> ReadingScreen(
                    reading = suggested,
                    confirmed = confirmed,
                    onConfirm = scanner::confirm,
                    onScanAnother = {
                        scanner.startNewScan()
                        navigation.navigate(AppScreen.SCANNER)
                    },
                    onTelemetry = { navigation.navigate(AppScreen.TELEMETRY) },
                )
                AppScreen.TELEMETRY -> TelemetryScreen(
                    reading = homeState.latest,
                    onBackHome = { navigation.navigate(AppScreen.HOME) },
                )
                AppScreen.BALCONY -> BalconyScreen(
                    renderer = balcony.renderer,
                    onResume = balcony::resume,
                    onPause = balcony::pause,
                    onSaved = { spanM, areaM2, panels ->
                        balcony.save(spanM, areaM2, panels)
                        navigation.navigate(AppScreen.HISTORY)
                    },
                )
                AppScreen.HISTORY -> HistoryScreen(
                    state = historyState,
                    onFilter = history::selectFilter,
                    onRescan = {
                        scanner.startNewScan()
                        navigation.navigate(AppScreen.SCANNER)
                    },
                )
            }

            if (screen.showsTabBar()) {
                TabBar(
                    selected = screen,
                    onSelect = { target ->
                        if (target == AppScreen.SCANNER) scanner.startNewScan()
                        navigation.navigate(target)
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

private fun AppScreen.showsTabBar(): Boolean =
    this == AppScreen.HOME || this == AppScreen.SCANNER || this == AppScreen.BALCONY || this == AppScreen.HISTORY

// Exactly four tabs. Settings is out of scope for this version.
@Composable
private fun TabBar(selected: AppScreen, onSelect: (AppScreen) -> Unit, modifier: Modifier = Modifier) {
    val tabs = listOf(
        Triple(AppScreen.HOME, "Home", Icons.Filled.Home),
        Triple(AppScreen.BALCONY, "Balcony AR", Icons.Filled.WbSunny),
        Triple(AppScreen.SCANNER, "Scanner", Icons.Filled.CameraAlt),
        Triple(AppScreen.HISTORY, "History", Icons.Filled.History),
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
