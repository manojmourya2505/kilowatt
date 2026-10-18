package com.inouiw.kilowatt.data.remote

import com.inouiw.kilowatt.domain.MeterReading
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** HTTP client for the FastAPI ingestion service (backend/). */
class IngestionService(private val baseUrl: String) {

    /** Uploads one reading. True when the server answers 201 Created. */
    fun postReading(reading: MeterReading): Boolean = try {
        val connection = open(READINGS_PATH, "POST")
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/json")
        val body = JSONObject()
            .put("id", reading.id)
            .put("kwh", reading.kWh)
            .put("captured_at", utcIso(reading.capturedAtMillis))
            .toString()
        connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        connection.disconnect()
        code == HTTP_CREATED
    } catch (e: Exception) {
        false
    }

    /** All readings the server holds, or null when the server cannot be reached. */
    fun fetchReadings(): List<MeterReading>? = try {
        val connection = open(READINGS_PATH, "GET")
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            null
        } else {
            val text = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()
            val array = JSONArray(text)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                MeterReading(
                    id = obj.getString("id"),
                    kWh = obj.getDouble("kwh"),
                    capturedAtMillis = parseUtc(obj.getString("captured_at")),
                    onServer = true,
                )
            }
        }
    } catch (e: Exception) {
        null
    }

    private fun open(path: String, method: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
        }

    companion object {
        const val READINGS_PATH = "/api/v1/readings"
        const val HTTP_CREATED = 201
        private const val TIMEOUT_MS = 15_000

        private fun utcIso(millis: Long): String =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .format(Date(millis))

        // The server sends "2026-10-18T08:42:15+00:00"; the first 19 characters are the UTC time
        private fun parseUtc(value: String): Long =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .apply { timeZone = TimeZone.getTimeZone("UTC") }
                .parse(value.take(19))!!
                .time
    }
}
