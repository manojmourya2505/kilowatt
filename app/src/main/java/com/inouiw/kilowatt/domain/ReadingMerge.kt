package com.inouiw.kilowatt.domain

/**
 * What the History and Home screens show: the server's readings, plus phone readings the server
 * does not have yet. A reading is never shown twice.
 */
fun mergeReadings(pending: List<MeterReading>, server: List<MeterReading>?): List<MeterReading> {
    val serverList = server ?: emptyList()
    val serverIds = serverList.map { it.id }.toSet()
    return (serverList + pending.filter { it.id !in serverIds })
        .sortedByDescending { it.capturedAtMillis }
}
