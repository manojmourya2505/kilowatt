package com.inouiw.kilowatt.data.local

import android.content.Context
import com.inouiw.kilowatt.domain.MeterReading
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Confirmed readings waiting for the server. Written to app-private storage first, so nothing is lost
 * when the phone is offline. An entry is removed once the server has accepted it.
 */
class PendingReadingStore(context: Context) {

    private val file = File(context.filesDir, "india_readings.json")

    @Synchronized
    fun add(kWh: Double, capturedAtMillis: Long): String {
        val id = UUID.randomUUID().toString()
        val array = readArray()
        array.put(JSONObject().put("id", id).put("kwh", kWh).put("capturedAt", capturedAtMillis))
        file.writeText(array.toString())
        return id
    }

    @Synchronized
    fun all(): List<MeterReading> {
        val array = readArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            MeterReading(
                id = obj.getString("id"),
                kWh = obj.getDouble("kwh"),
                capturedAtMillis = obj.getLong("capturedAt"),
                onServer = false,
            )
        }
    }

    @Synchronized
    fun removeAll(ids: Set<String>) {
        val array = readArray()
        val kept = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") !in ids) kept.put(obj)
        }
        file.writeText(kept.toString())
    }

    private fun readArray(): JSONArray = try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }
}
