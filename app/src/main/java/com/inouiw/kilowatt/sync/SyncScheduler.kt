package com.inouiw.kilowatt.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Schedules uploads. Work only runs when the phone has a network connection, so readings saved
 * offline are sent automatically once it is back.
 */
object SyncScheduler {
    private const val ONE_OFF_WORK = "kilowatt-readings-sync"
    private const val PERIODIC_WORK = "kilowatt-readings-periodic"

    private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Upload as soon as the phone is online. */
    fun requestNow(context: Context) {
        val request = OneTimeWorkRequest.Builder(ReadingSyncWorker::class.java)
            .setConstraints(connected)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONE_OFF_WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    /** Retry every 15 minutes (the WorkManager minimum) whenever the phone is online. */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReadingSyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(connected)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
