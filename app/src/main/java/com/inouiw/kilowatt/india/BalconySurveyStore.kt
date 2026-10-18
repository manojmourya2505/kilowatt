package com.inouiw.kilowatt.india

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// A balcony survey saved from a steady ARCore measurement. Stored on the phone like readings.
data class BalconySurveyRecord(
    val id: String,
    val spanM: Float,
    val areaM2: Float,
    val panels: Int,
    val capturedAtMillis: Long,
    val synced: Boolean,
)

class BalconySurveyStore(context: Context) {

    private val file = File(context.filesDir, "india_surveys.json")

    @Synchronized
    fun add(spanM: Float, areaM2: Float, panels: Int, capturedAtMillis: Long) {
        val array = readArray()
        array.put(
            JSONObject()
                .put("id", UUID.randomUUID().toString())
                .put("spanM", spanM.toDouble())
                .put("areaM2", areaM2.toDouble())
                .put("panels", panels)
                .put("capturedAt", capturedAtMillis)
                .put("synced", false),
        )
        file.writeText(array.toString())
    }

    @Synchronized
    fun all(): List<BalconySurveyRecord> {
        val array = readArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BalconySurveyRecord(
                id = obj.getString("id"),
                spanM = obj.getDouble("spanM").toFloat(),
                areaM2 = obj.getDouble("areaM2").toFloat(),
                panels = obj.getInt("panels"),
                capturedAtMillis = obj.getLong("capturedAt"),
                synced = obj.getBoolean("synced"),
            )
        }
    }

    private fun readArray(): JSONArray = try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }
}
