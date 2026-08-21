package de.kamilunavo.magcalc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MAGCalculatorTest {
    private fun assertClose(expected: Double, actual: Double, tolerance: Double = 0.0001) {
        assertEquals(expected, actual, tolerance)
    }

    @Test
    fun pressureAssistantMatchesIosFormula() {
        val result = MAGCalculator.pressure(staticHeight = 8.0, safetyValvePressure = 3.0)
        assertClose(0.8, result.staticPressure)
        assertClose(1.0, result.prechargePressure)
        assertClose(1.3, result.fillPressure)
        assertClose(2.5, result.recommendedMaxPressure)
        assertTrue(result.isValid)
    }

    @Test
    fun recommendationUsesNextStandardVesselSize() {
        assertClose(25.0, MAGCalculator.recommendedSize(19.0))
        assertClose(600.0, MAGCalculator.recommendedSize(599.9))
        assertClose(700.0, MAGCalculator.recommendedSize(601.0))
    }

    @Test
    fun waterExpansionIncreasesWithTemperature() {
        val at60 = MAGCalculator.waterExpansionRatio(10.0, 60.0)
        val at90 = MAGCalculator.waterExpansionRatio(10.0, 90.0)
        assertTrue(at60 > 0)
        assertTrue(at90 > at60)
    }

    @Test
    fun heatingResultIsValidAndRecommendationCoversRequirement() {
        val result = MAGCalculator.heating(
            systemVolume = 500.0,
            maximumTemperature = 80.0,
            staticHeight = 8.0,
            safetyValvePressure = 3.0,
        )
        assertTrue(result.isValid)
        assertTrue(result.requiredLiters > 0)
        assertTrue(result.recommendedLiters >= result.requiredLiters)
    }

    @Test
    fun advancedModeMatchesAcceptanceFormula() {
        val result = MAGCalculator.advanced(
            systemVolume = 200.0,
            expansionPercent = 5.0,
            prechargePressure = 1.0,
            finalPressure = 2.5,
            reservePercent = 1.0,
        )
        assertClose(10.0, result.expansionLiters)
        assertClose(2.0, result.reserveLiters)
        assertClose((2.5 - 1.0) / 3.5, result.acceptanceFactor)
        assertTrue(result.isValid)
    }
}
