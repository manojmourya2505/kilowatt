package com.inouiw.kilowatt.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TariffScheduleTest {

    @Test
    fun noUsageCostsNothing() {
        assertEquals(0.0, TariffSchedule.energyCharge(0.0)!!, 0.001)
    }

    @Test
    fun firstFiftyUnitsUseTheFirstSlab() {
        assertEquals(50 * 1.95, TariffSchedule.energyCharge(50.0)!!, 0.001)
    }

    @Test
    fun usageAcrossSlabsIsSplitCorrectly() {
        // 50 units at 1.95 plus 25 units at 3.10
        assertEquals(50 * 1.95 + 25 * 3.10, TariffSchedule.energyCharge(75.0)!!, 0.001)
    }

    @Test
    fun usageBeyondTheConfirmedSlabsIsNotPriced() {
        assertNull(TariffSchedule.energyCharge(120.0))
    }
}

class EstimatesTest {

    @Test
    fun panelsUseSixtyPercentOfTheSurface() {
        // 10.9 m² x 60% = 6.54 m² usable, / 2.6 m² per panel = 2 panels
        assertEquals(2, Estimates.panelsFor(10.9f))
    }

    @Test
    fun noAreaMeansNoPanels() {
        assertEquals(0, Estimates.panelsFor(0f))
    }

    @Test
    fun generationIsPanelsTimesRatingTimesYield() {
        // 2 x 0.55 kW x 1300 kWh/kWp
        assertEquals(1430.0, Estimates.yearlyGenerationKwh(2), 0.001)
    }

    @Test
    fun monthlyUsageScalesTwoReadingsToAMonth() {
        val day = 86_400_000L
        val older = MeterReading("a", 100.0, 0L, onServer = true)
        val newer = MeterReading("b", 130.0, 30 * day, onServer = true)
        // 30 kWh over 30 days, scaled to 30.44 days
        assertEquals(30.44, Estimates.monthlyUsageKwh(older, newer)!!, 0.001)
    }

    @Test
    fun readingsLessThanADayApartGiveNoUsage() {
        val older = MeterReading("a", 100.0, 0L, onServer = true)
        val newer = MeterReading("b", 110.0, 3_600_000L, onServer = true)
        assertNull(Estimates.monthlyUsageKwh(older, newer))
    }

    @Test
    fun meterGoingDownGivesNoUsage() {
        val day = 86_400_000L
        val older = MeterReading("a", 200.0, 0L, onServer = true)
        val newer = MeterReading("b", 150.0, 2 * day, onServer = true)
        assertNull(Estimates.monthlyUsageKwh(older, newer))
    }
}

class ReadingMergeTest {

    @Test
    fun aReadingTheServerHasIsShownOnce() {
        val onPhone = MeterReading("same", 15794.8, 1_000L, onServer = false)
        val onServer = MeterReading("same", 15794.8, 1_000L, onServer = true)
        val merged = mergeReadings(listOf(onPhone), listOf(onServer))
        assertEquals(1, merged.size)
        assertTrue(merged.single().onServer)
    }

    @Test
    fun phoneOnlyReadingsAreKeptWhenTheServerIsUnreachable() {
        val onPhone = MeterReading("p", 1432.9, 1_000L, onServer = false)
        assertEquals(listOf(onPhone), mergeReadings(listOf(onPhone), null))
    }

    @Test
    fun newestReadingComesFirst() {
        val older = MeterReading("o", 1.0, 1_000L, onServer = true)
        val newer = MeterReading("n", 2.0, 2_000L, onServer = true)
        assertEquals(listOf("n", "o"), mergeReadings(emptyList(), listOf(older, newer)).map { it.id })
    }
}

class GeometryTest {

    @Test
    fun squareAreaIsCorrect() {
        val square = listOf(0f to 0f, 2f to 0f, 2f to 2f, 0f to 2f)
        assertEquals(4f, Geometry.polygonArea(square), 0.001f)
    }

    @Test
    fun steadyValuesAreRecognised() {
        assertTrue(Geometry.isSteady(listOf(5.0f, 5.1f, 5.2f)))
    }

    @Test
    fun driftingValuesAreNotSteady() {
        assertTrue(!Geometry.isSteady(listOf(5.0f, 7.0f, 9.0f)))
    }
}
