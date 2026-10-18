package com.inouiw.kilowatt.india

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

// Grid Ingestion API. Leave BASE_URL blank until the real endpoint is known: readings then stay on the phone.
object IngestionApi {
    // Your Mac on the same Wi-Fi. Change this if the Mac gets a new address.
    const val BASE_URL = "http://192.168.29.213:8000"
    const val READINGS_PATH = "/api/v1/readings"
}

private const val UNIQUE_WORK = "kilowatt-readings-sync"
private const val PERIODIC_WORK = "kilowatt-readings-periodic"
private const val TAG = "ReadingSync"

// Runs every 15 minutes whenever the phone is online, so pending readings upload without a button press.
fun schedulePeriodicReadingSync(context: Context) {
    val request = PeriodicWorkRequestBuilder<ReadingSyncWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
}

// Queues an upload that runs as soon as the phone has a network connection.
fun enqueueReadingSync(context: Context) {
    val request = OneTimeWorkRequest.Builder(ReadingSyncWorker::class.java)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(context).enqueueUniqueWork(UNIQUE_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
}

// Uploads every pending reading. A reading is marked synced only after HTTP 201 Created.
class ReadingSyncWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        if (IngestionApi.BASE_URL.isBlank()) {
            Log.i(TAG, "Ingestion endpoint not set; readings stay on the device")
            return Result.success()
        }
        val queue = ReadingQueue(applicationContext)
        queue.purgeSynced()
        for (reading in queue.pending()) {
            val code = post(reading) ?: return Result.retry()
            if (code == HTTP_CREATED) {
                queue.remove(reading.id)
            } else {
                Log.w(TAG, "Server answered $code; will retry")
                return Result.retry()
            }
        }
        return Result.success()
    }

    private fun post(reading: QueuedReading): Int? = try {
        val connection = URL(IngestionApi.BASE_URL + IngestionApi.READINGS_PATH).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
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
        code
    } catch (e: Exception) {
        Log.w(TAG, "Upload failed, will retry", e)
        null
    }

    private fun utcIso(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date(millis))

    companion object {
        const val HTTP_CREATED = 201
    }
}
