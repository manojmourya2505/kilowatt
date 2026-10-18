package com.inouiw.kilowatt.ui.balcony

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.google.ar.core.Session
import com.inouiw.kilowatt.ar.ArBalconyRenderer
import com.inouiw.kilowatt.ar.ArSessionFactory
import com.inouiw.kilowatt.data.repository.SurveyRepository

/** Owns the ARCore session for the balcony screen and saves finished surveys. */
class BalconyViewModel(application: Application, private val surveys: SurveyRepository) : AndroidViewModel(application) {

    private val session: Session? = ArSessionFactory.create(application)

    /** Null when ARCore is not available on this phone. */
    val renderer: ArBalconyRenderer? = session?.let { ArBalconyRenderer(it) }

    fun resume() {
        try {
            session?.resume()
        } catch (e: Exception) {
            Log.e(TAG, "ARCore resume failed", e)
        }
    }

    fun pause() {
        session?.pause()
    }

    fun save(spanM: Float, areaM2: Float, panels: Int) {
        surveys.save(spanM, areaM2, panels, System.currentTimeMillis())
    }

    override fun onCleared() {
        session?.close()
    }

    private companion object {
        const val TAG = "BalconyViewModel"
    }
}
