package com.inouiw.kilowatt.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.inouiw.kilowatt.KiloWattApplication

/** Uploads pending readings. Retries when the server is unreachable, so nothing is lost. */
class ReadingSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KiloWattApplication).container
        return if (container.readingRepository.syncPending()) Result.success() else Result.retry()
    }
}
