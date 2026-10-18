package com.inouiw.kilowatt.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inouiw.kilowatt.data.repository.ReadingRepository
import com.inouiw.kilowatt.data.repository.SurveyRepository
import com.inouiw.kilowatt.domain.BalconySurvey
import com.inouiw.kilowatt.domain.MeterReading
import com.inouiw.kilowatt.domain.mergeReadings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HistoryFilter(val label: String) { ALL("All"), METERS("Meters"), SOLAR("Solar Surveys") }

data class HistoryUiState(
    val filter: HistoryFilter = HistoryFilter.ALL,
    val readings: List<MeterReading> = emptyList(),
    val surveys: List<BalconySurvey> = emptyList(),
    val serverReachable: Boolean? = null,
    val checkingServer: Boolean = false,
)

class HistoryViewModel(
    private val readings: ReadingRepository,
    surveys: SurveyRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(HistoryFilter.ALL)

    val uiState: StateFlow<HistoryUiState> = combine(
        filter,
        readings.pendingReadings,
        readings.serverReadings,
        readings.serverChecking,
        surveys.surveys,
    ) { selected, pending, server, checking, surveyList ->
        HistoryUiState(
            filter = selected,
            readings = mergeReadings(pending, server),
            surveys = surveyList.sortedByDescending { it.capturedAtMillis },
            serverReachable = if (server != null) true else if (checking) null else false,
            checkingServer = checking,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, HistoryUiState())

    init {
        refreshServer()
    }

    fun selectFilter(option: HistoryFilter) {
        filter.value = option
    }

    fun refreshServer() {
        viewModelScope.launch { readings.refreshServer() }
    }
}
