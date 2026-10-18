package com.inouiw.kilowatt.di

import android.content.Context
import com.inouiw.kilowatt.BuildConfig
import com.inouiw.kilowatt.data.local.PendingReadingStore
import com.inouiw.kilowatt.data.local.SurveyStore
import com.inouiw.kilowatt.data.remote.IngestionService
import com.inouiw.kilowatt.data.repository.ReadingRepository
import com.inouiw.kilowatt.data.repository.SurveyRepository

/** Creates the app's dependencies once. Screens get what they need from here through their ViewModels. */
class AppContainer(context: Context) {
    val readingRepository = ReadingRepository(
        pendingStore = PendingReadingStore(context),
        remote = IngestionService(BuildConfig.INGESTION_BASE_URL),
    )
    val surveyRepository = SurveyRepository(SurveyStore(context))
}
