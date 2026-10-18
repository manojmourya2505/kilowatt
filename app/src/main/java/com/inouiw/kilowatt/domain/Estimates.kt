package com.inouiw.kilowatt.domain

/**
 * Balcony and roof estimates. Each constant is an assumption, not a measurement:
 * confirm the panel size against the product datasheet and the yield against local data.
 */
object Estimates {
    const val PANEL_AREA_M2 = 2.6f        // about 2.3 m x 1.1 m for a 550 W unit
    const val PANEL_KW = 0.55f            // 550 W units
    const val USABLE_FRACTION = 0.6f      // 40% of the surface kept free for access and clearance
    const val YIELD_KWH_PER_KWP = 1300f   // approximate yearly yield

    private const val MILLIS_PER_DAY = 86_400_000.0
    private const val DAYS_PER_MONTH = 30.44

    fun panelsFor(areaM2: Float): Int =
        ((areaM2 * USABLE_FRACTION) / PANEL_AREA_M2).toInt().coerceAtLeast(0)

    fun yearlyGenerationKwh(panels: Int): Double =
        (panels * PANEL_KW * YIELD_KWH_PER_KWP).toDouble()

    /** Monthly usage between two readings taken at least a day apart. Null when they can't give a rate. */
    fun monthlyUsageKwh(older: MeterReading, newer: MeterReading): Double? {
        val used = newer.kWh - older.kWh
        val days = (newer.capturedAtMillis - older.capturedAtMillis) / MILLIS_PER_DAY
        if (used <= 0.0 || days < 1.0) return null
        return used / days * DAYS_PER_MONTH
    }
}
