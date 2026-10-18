package com.inouiw.kilowatt.india

// Domestic energy charges for Telangana (LT-I(A), up to 1 kW load).
// Source: TGERC retail supply tariff order FY 2025-26 (tgsouthernpower.org). Only the first two slabs
// were confirmed from that document. Replace this with the full slab table from the customer's bill.
object TariffSchedule {
    const val SOURCE_LABEL = "TGERC order FY 2025-26 · first 100 units only"

    // (upper limit of the slab in units, rate in ₹ per unit)
    private val confirmedSlabs = listOf(50 to 1.95, 100 to 3.10)

    // Highest monthly usage this schedule can price
    val pricedUpToUnits: Int = confirmedSlabs.last().first

    // Energy charge for a month's usage, or null when the usage is beyond the confirmed slabs
    fun energyCharge(unitsPerMonth: Double): Double? {
        if (unitsPerMonth <= 0.0) return 0.0
        if (unitsPerMonth > pricedUpToUnits) return null
        var remaining = unitsPerMonth
        var previousLimit = 0
        var charge = 0.0
        for ((limit, rate) in confirmedSlabs) {
            val slabUnits = minOf(remaining, (limit - previousLimit).toDouble())
            charge += slabUnits * rate
            remaining -= slabUnits
            previousLimit = limit
            if (remaining <= 0.0) break
        }
        return charge
    }
}
