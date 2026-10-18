package com.inouiw.kilowatt

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Tariff and solar assumptions. Change them here, not in the UI. */
object Tariff {
    const val CURRENCY = "£"
    const val RATE_PER_KWH = 0.245
    const val DAYS_PER_MONTH = 30.44
    // Usage needs at least this long between two readings to extrapolate a monthly figure
    const val MIN_DAYS_BETWEEN_READINGS = 1.0
    const val KWH_PER_PANEL_PER_MONTH = 35.0
    // Unverified assumption, not from the spec
    const val SAVINGS_PER_PANEL_PER_YEAR = 210.0
}

private const val MILLIS_PER_DAY = 24 * 60 * 60 * 1000.0

/** Extrapolates usage between two readings to one month. Returns null if the readings can't give a rate. */
fun estimateMonthlyKwh(previous: MeterReading?, kWh: Double, nowMillis: Long): Double? {
    if (previous == null) return null
    val usedKWh = kWh - previous.kWh
    val elapsedDays = (nowMillis - previous.timeMillis) / MILLIS_PER_DAY
    // A lower reading means a misread or a meter change, so no estimate
    if (usedKWh <= 0.0 || elapsedDays < Tariff.MIN_DAYS_BETWEEN_READINGS) return null
    return usedKWh / elapsedDays * Tariff.DAYS_PER_MONTH
}


data class MeterReading(val kWh: Double, val timeMillis: Long)

data class MeterUiState(
    val hasCameraPermission: Boolean = false,
    val isScanning: Boolean = true,
    val isLocked: Boolean = false,
    val detectedKWh: Double? = null,
    val rawReadingText: String = "",
    val monthlyCostEstimate: Double = 0.0,
    val instantTariffRate: Double = Tariff.RATE_PER_KWH,
    val hasConsumptionEstimate: Boolean = false,
    val estimatedMonthlyBill: Double = 0.0,
    val solarPanelCount: Int = 0,
    val solarAnnualSavings: Double = 0.0,
    val hasFault: Boolean = false,
    val faultMessage: String? = null,
    val isReadingVerified: Boolean = false
)

class MeterViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(MeterUiState())
    val uiState: StateFlow<MeterUiState> = _uiState.asStateFlow()

    private val _readings = MutableStateFlow<List<MeterReading>>(emptyList())
    val readings: StateFlow<List<MeterReading>> = _readings.asStateFlow()

    private val readingStore = ReadingStore(application)

    // Last accepted reading. Loaded from storage so usage survives app restarts.
    @Volatile
    private var previousReading: MeterReading? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val stored = readingStore.all().sortedByDescending { it.timeMillis }
            _readings.value = stored
            previousReading = stored.firstOrNull()
        }
    }

    fun updateCameraPermission(granted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = granted) }
    }

    fun onMeterReadingDetected(kWh: Double, rawText: String, hasFault: Boolean, faultKeyword: String?) {
        // Ignore any new incoming frames if reading is already locked
        if (_uiState.value.isLocked) return
        if (kWh <= 0.0 && !hasFault) return

        val nowMillis = System.currentTimeMillis()
        val monthlyKwh = if (kWh > 0) estimateMonthlyKwh(previousReading, kWh, nowMillis) else null
        if (kWh > 0) {
            val reading = MeterReading(kWh, nowMillis)
            previousReading = reading
            _readings.update { listOf(reading) + it }
            viewModelScope.launch(Dispatchers.IO) {
                readingStore.add(reading)
            }
        }

        val monthlyCost = (monthlyKwh ?: 0.0) * Tariff.RATE_PER_KWH
        val panelsNeeded = monthlyKwh?.let { (it / Tariff.KWH_PER_PANEL_PER_MONTH).toInt().coerceIn(4, 24) } ?: 0
        val annualSavings = panelsNeeded * Tariff.SAVINGS_PER_PANEL_PER_YEAR

        _uiState.update { currentState ->
            currentState.copy(
                isLocked = true,
                isScanning = false,
                isReadingVerified = true,
                detectedKWh = if (kWh > 0) kWh else currentState.detectedKWh,
                rawReadingText = if (rawText.isNotEmpty()) rawText else currentState.rawReadingText,
                instantTariffRate = Tariff.RATE_PER_KWH,
                hasConsumptionEstimate = monthlyKwh != null,
                monthlyCostEstimate = monthlyCost,
                estimatedMonthlyBill = monthlyCost,
                solarPanelCount = panelsNeeded,
                solarAnnualSavings = annualSavings,
                hasFault = hasFault,
                faultMessage = if (hasFault) "Meter Anomaly Detected: ${faultKeyword ?: "FAULT"} flag active" else null
            )
        }
    }

    fun resetScan() {
        _uiState.update {
            it.copy(
                isScanning = true,
                isLocked = false,
                isReadingVerified = false,
                detectedKWh = null,
                rawReadingText = "",
                hasFault = false,
                faultMessage = null
            )
        }
    }
}
