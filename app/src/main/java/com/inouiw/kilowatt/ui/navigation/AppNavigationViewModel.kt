package com.inouiw.kilowatt.ui.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AppScreen { LAUNCH, HOME, SCANNER, READING, TELEMETRY, BALCONY, HISTORY }

/** Which screen is showing. Screens ask for a change here; they never switch screens themselves. */
class AppNavigationViewModel : ViewModel() {

    private val _screen = MutableStateFlow(AppScreen.LAUNCH)
    val screen: StateFlow<AppScreen> = _screen.asStateFlow()

    fun navigate(target: AppScreen) {
        _screen.value = target
    }
}
