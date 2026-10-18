package com.inouiw.kilowatt.data.local

import android.content.Context
import com.inouiw.kilowatt.domain.BalconySurvey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Balcony surveys saved on the phone. */
class SurveyStore(context: Context) {

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
                .put("capturedAt", capturedAtMillis),
        )
        file.writeText(array.toString())
    }

    @Synchronized
    fun all(): List<BalconySurvey> {
        val array = readArray()
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            BalconySurvey(
                id = obj.getString("id"),
                spanM = obj.getDouble("spanM").toFloat(),
                areaM2 = obj.getDouble("areaM2").toFloat(),
                panels = obj.getInt("panels"),
                capturedAtMillis = obj.getLong("capturedAt"),
            )
        }
    }

    private fun readArray(): JSONArray = try {
        if (file.exists()) JSONArray(file.readText()) else JSONArray()
    } catch (e: Exception) {
        JSONArray()
    }
}
