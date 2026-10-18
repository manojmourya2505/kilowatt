package com.inouiw.kilowatt.data.repository

import com.inouiw.kilowatt.data.local.SurveyStore
import com.inouiw.kilowatt.domain.BalconySurvey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Balcony surveys saved on the phone. */
class SurveyRepository(private val store: SurveyStore) {

    private val _surveys = MutableStateFlow(store.all())
    val surveys: StateFlow<List<BalconySurvey>> = _surveys.asStateFlow()

    fun save(spanM: Float, areaM2: Float, panels: Int, capturedAtMillis: Long) {
        store.add(spanM, areaM2, panels, capturedAtMillis)
        _surveys.value = store.all()
    }
}
