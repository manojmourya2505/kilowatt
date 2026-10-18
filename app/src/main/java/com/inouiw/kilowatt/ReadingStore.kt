package com.inouiw.kilowatt

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves accepted meter readings to a JSON file in app-private storage, so they survive restarts. */
class ReadingStore(context: Context) {

    private val file = File(context.filesDir, "readings.json")

    @Synchronized
    fun all(): List<MeterReading> {
        val array = readArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            MeterReading(obj.getDouble("kWh"), obj.getLong("timeMillis"))
        }
    }

    fun latest(): MeterReading? = all().maxByOrNull { it.timeMillis }

    @Synchronized
    fun add(reading: MeterReading) {
        val array = readArray()
        array.put(JSONObject().put("kWh", reading.kWh).put("timeMillis", reading.timeMillis))
        file.writeText(array.toString())
    }

    // A missing or unreadable file counts as no history, so the app never crashes on bad storage
    private fun readArray(): JSONArray =
        try {
            if (file.exists()) JSONArray(file.readText()) else JSONArray()
        } catch (e: Exception) {
            JSONArray()
        }
}
