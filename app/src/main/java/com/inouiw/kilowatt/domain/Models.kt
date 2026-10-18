package com.inouiw.kilowatt.domain

/** A confirmed meter reading. [onServer] is true once the ingestion server has accepted it. */
data class MeterReading(
    val id: String,
    val kWh: Double,
    val capturedAtMillis: Long,
    val onServer: Boolean,
)

/** A balcony survey from a steady ARCore measurement. Surveys are not uploaded yet. */
data class BalconySurvey(
    val id: String,
    val spanM: Float,
    val areaM2: Float,
    val panels: Int,
    val capturedAtMillis: Long,
)
