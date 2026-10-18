package com.inouiw.kilowatt.india

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

// A reading as the server has it. The server is the record that counts for "saved".
data class ServerReading(val id: String, val kWh: Double, val capturedAtMillis: Long)

// Reads the saved readings from the server. Returns null when the server cannot be reached.
fun fetchServerReadings(): List<ServerReading>? = try {
    val connection = URL(IngestionApi.BASE_URL + IngestionApi.READINGS_PATH).openConnection() as HttpURLConnection
    connection.connectTimeout = 10_000
    connection.readTimeout = 10_000
    val code = connection.responseCode
    if (code != HttpURLConnection.HTTP_OK) {
        connection.disconnect()
        null
    } else {
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()
        val array = JSONArray(text)
        (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ServerReading(
                id = obj.getString("id"),
                kWh = obj.getDouble("kwh"),
                capturedAtMillis = parseUtc(obj.getString("captured_at")),
            )
        }
    }
} catch (e: Exception) {
    null
}

// The server sends times like "2026-10-18T08:42:15+00:00"; the first 19 characters are the UTC time
private fun parseUtc(value: String): Long =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .parse(value.take(19))!!
        .time
