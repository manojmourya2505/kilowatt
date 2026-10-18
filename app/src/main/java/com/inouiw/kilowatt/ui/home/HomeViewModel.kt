package com.inouiw.kilowatt.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inouiw.kilowatt.data.repository.ReadingRepository
import com.inouiw.kilowatt.domain.Estimates
import com.inouiw.kilowatt.domain.MeterReading
import com.inouiw.kilowatt.domain.mergeReadings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeUiState(
    val readings: List<MeterReading> = emptyList(),
    val latest: MeterReading? = null,
    val monthlyUsageKwh: Double? = null,
    val checkingServer: Boolean = false,
)

class HomeViewModel(private val readings: ReadingRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        readings.pendingReadings,
        readings.serverReadings,
        readings.serverChecking,
    ) { pending, server, checking ->
        val all = mergeReadings(pending, server)
        val latest = all.firstOrNull()
        val usage = if (all.size >= 2) Estimates.monthlyUsageKwh(all[1], all[0]) else null
        HomeUiState(readings = all, latest = latest, monthlyUsageKwh = usage, checkingServer = checking)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HomeUiState())

    fun refreshServer() {
        viewModelScope.launch { readings.refreshServer() }
    }
}
