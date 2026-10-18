package com.inouiw.kilowatt.data.repository

import com.inouiw.kilowatt.data.local.PendingReadingStore
import com.inouiw.kilowatt.data.remote.IngestionService
import com.inouiw.kilowatt.domain.MeterReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * The single place that decides what a reading is: saved on the phone first, uploaded when the
 * server is reachable, and removed from the phone once the server has it.
 */
class ReadingRepository(
    private val pendingStore: PendingReadingStore,
    private val remote: IngestionService,
) {
    private val _pending = MutableStateFlow(pendingStore.all())

    /** Readings on this phone that the server has not accepted yet. */
    val pendingReadings: StateFlow<List<MeterReading>> = _pending.asStateFlow()

    /** Saves a confirmed reading on the phone. The caller should then request a sync. */
    fun save(kWh: Double, capturedAtMillis: Long) {
        pendingStore.add(kWh, capturedAtMillis)
        _pending.value = pendingStore.all()
    }

    private val _server = MutableStateFlow<List<MeterReading>?>(null)

    /** The server's readings from the last check. Null until a check succeeds. */
    val serverReadings: StateFlow<List<MeterReading>?> = _server.asStateFlow()

    private val _checking = MutableStateFlow(false)

    /** True while the server is being asked for its readings. */
    val serverChecking: StateFlow<Boolean> = _checking.asStateFlow()

    /** Asks the server for its readings. Phone copies the server already has are removed. */
    suspend fun refreshServer() = withContext(Dispatchers.IO) {
        _checking.value = true
        try {
            val server = remote.fetchReadings()
            if (server != null) {
                pendingStore.removeAll(server.map { it.id }.toSet())
                _pending.value = pendingStore.all()
            }
            _server.value = server
        } finally {
            _checking.value = false
        }
    }

    /** Uploads every reading still on the phone. True when all of them were accepted. */
    suspend fun syncPending(): Boolean = withContext(Dispatchers.IO) {
        for (reading in pendingStore.all()) {
            if (!remote.postReading(reading)) return@withContext false
            pendingStore.removeAll(setOf(reading.id))
        }
        _pending.value = pendingStore.all()
        true
    }
}
