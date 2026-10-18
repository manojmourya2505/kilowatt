package com.inouiw.kilowatt.india

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// A confirmed meter reading. It stays on the phone until the server has accepted it (HTTP 201).
data class QueuedReading(
    val id: String,
    val kWh: Double,
    val capturedAtMillis: Long,
    val synced: Boolean,
)

// Offline-first store: readings are written to app-private storage before any upload is tried.
class ReadingQueue(context: Context) {

    private val file = File(context.filesDir, "india_readings.json")

    @Synchronized
    fun add(kWh: Double, capturedAtMillis: Long): QueuedReading {
        val reading = QueuedReading(UUID.randomUUID().toString(), kWh, capturedAtMillis, synced = false)
        val array = readArray()
        array.put(
            JSONObject()
                .put("id", reading.id)
                .put("kwh", reading.kWh)
                .put("capturedAt", reading.capturedAtMillis)
                .put("synced", false),
        )
        write(array)
        return reading
    }

    @Synchronized
    fun pending(): List<QueuedReading> = all().filter { !it.synced }

    // Entries marked synced from an earlier build are removed: the server already has them
    @Synchronized
    fun purgeSynced() {
        val array = readArray()
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (!obj.getBoolean("synced")) kept.put(obj)
        }
        write(kept)
    }

    // Once the server has accepted a reading, the phone copy is deleted
    @Synchronized
    fun remove(id: String) {
        val array = readArray()
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != id) kept.put(obj)
        }
        write(kept)
    }

    @Synchronized
    fun all(): List<QueuedReading> {
        val array = readArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            QueuedReading(
                id = obj.getString("id"),
                kWh = obj.getDouble("kwh"),
                capturedAtMillis = obj.getLong("capturedAt"),
                synced = obj.getBoolean("synced"),
            )
        }
    }

    private fun write(array: JSONArray) {
        file.writeText(array.toString())
    }

    private fun readArray(): JSONArray = try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }
}
