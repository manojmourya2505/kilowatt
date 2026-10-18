package com.inouiw.kilowatt.ui.scanner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.inouiw.kilowatt.data.repository.ReadingRepository
import com.inouiw.kilowatt.ml.TfLiteMeterReader
import com.inouiw.kilowatt.sync.SyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

const val MISREAD_MESSAGE = "Couldn't read the display. Move closer and avoid glare."

/**
 * Scanner state: the reader's suggestion, whether the user has confirmed it, and messages.
 * A reading is saved only through [confirm], which the user triggers.
 */
class ScannerViewModel(application: Application, private val readings: ReadingRepository) : AndroidViewModel(application) {

    private val reader = TfLiteMeterReader(application) { kWh, _ -> onSuggested(kWh) }

    /** The camera feeds frames to this analyzer. */
    val analyzer = reader

    private val _suggested = MutableStateFlow<Double?>(null)
    val suggested: StateFlow<Double?> = _suggested.asStateFlow()

    private val _confirmed = MutableStateFlow(false)
    val confirmed: StateFlow<Boolean> = _confirmed.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private fun onSuggested(kWh: Double) {
        _suggested.value = kWh
        _confirmed.value = false
        _message.value = null
    }

    /** The shutter was pressed. It can only lock on a reading that is already there. */
    fun onShutter() {
        if (_suggested.value == null) _message.value = MISREAD_MESSAGE
    }

    /** The user confirmed the value on screen (either the suggestion or an edited number). */
    fun confirm(kWh: Double) {
        readings.save(kWh, System.currentTimeMillis())
        SyncScheduler.requestNow(getApplication())
        _suggested.value = kWh
        _confirmed.value = true
        viewModelScope.launch { readings.refreshServer() }
    }

    fun startNewScan() {
        _suggested.value = null
        _confirmed.value = false
        _message.value = null
        reader.resetForNewScan()
    }

    override fun onCleared() {
        reader.close()
    }
}
