package de.kamilunavo.magcalc

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object MAGCalculator {
    val standardVesselSizes = listOf(8.0, 12.0, 18.0, 25.0, 35.0, 50.0, 80.0, 100.0, 140.0, 200.0, 250.0, 300.0, 400.0, 500.0, 600.0)

    data class MAGResult(
        val expansionLiters: Double,
        val reserveLiters: Double,
        val requiredLiters: Double,
        val recommendedLiters: Double,
        val expansionPercent: Double,
        val staticPressure: Double,
        val prechargePressure: Double,
        val fillPressure: Double,
        val finalPressure: Double,
        val acceptanceFactor: Double,
        val isValid: Boolean,
    )

    data class PressureResult(
        val staticPressure: Double,
        val prechargePressure: Double,
        val fillPressure: Double,
        val recommendedMaxPressure: Double,
        val isValid: Boolean,
    )

    fun heating(
        systemVolume: Double,
        maximumTemperature: Double,
        staticHeight: Double,
        safetyValvePressure: Double,
    ): MAGResult {
        val volume = max(systemVolume, 0.0)
        val staticPressure = max(staticHeight, 0.0) / 10.0
        val precharge = max(0.5, staticPressure + 0.2)
        val fill = precharge + 0.3
        val finalPressure = safetyValvePressure - 0.5
        val expansionRatio = waterExpansionRatio(10.0, maximumTemperature)
        val expansionVolume = volume * expansionRatio
        val reserveVolume = max(3.0, volume * 0.005)
        val acceptance = if (finalPressure > precharge) (finalPressure - precharge) / (finalPressure + 1.0) else 0.0
        val valid = volume > 0 && maximumTemperature >= 10 && finalPressure > fill && acceptance > 0
        val required = if (valid) (expansionVolume + reserveVolume) / acceptance else 0.0

        return MAGResult(
            expansionVolume,
            reserveVolume,
            required,
            recommendedSize(required),
            expansionRatio * 100.0,
            staticPressure,
            precharge,
            fill,
            finalPressure,
            acceptance,
            valid,
        )
    }

    fun pressure(staticHeight: Double, safetyValvePressure: Double): PressureResult {
        val staticPressure = max(staticHeight, 0.0) / 10.0
        val precharge = max(0.5, staticPressure + 0.2)
        val fill = precharge + 0.3
        val maxPressure = safetyValvePressure - 0.5
        return PressureResult(staticPressure, precharge, fill, maxPressure, maxPressure > fill)
    }

    fun advanced(
        systemVolume: Double,
        expansionPercent: Double,
        prechargePressure: Double,
        finalPressure: Double,
        reservePercent: Double,
    ): MAGResult {
        val volume = max(systemVolume, 0.0)
        val expansionRatio = max(expansionPercent, 0.0) / 100.0
        val expansionVolume = volume * expansionRatio
        val reserveVolume = volume * max(reservePercent, 0.0) / 100.0
        val precharge = max(prechargePressure, 0.0)
        val final = max(finalPressure, 0.0)
        val acceptance = if (final > precharge) (final - precharge) / (final + 1.0) else 0.0
        val valid = volume > 0 && expansionRatio > 0 && acceptance > 0
        val required = if (valid) (expansionVolume + reserveVolume) / acceptance else 0.0

        return MAGResult(
            expansionVolume,
            reserveVolume,
            required,
            recommendedSize(required),
            expansionRatio * 100.0,
            0.0,
            precharge,
            precharge + 0.3,
            final,
            acceptance,
            valid,
        )
    }

    fun recommendedSize(required: Double): Double {
        if (required <= 0) return 0.0
        return standardVesselSizes.firstOrNull { it >= required } ?: (ceil(required / 100.0) * 100.0)
    }

    fun waterExpansionRatio(startTemperature: Double, endTemperature: Double): Double {
        val startDensity = waterDensity(startTemperature)
        val endDensity = waterDensity(max(endTemperature, startTemperature))
        if (endDensity <= 0) return 0.0
        return max(0.0, startDensity / endDensity - 1.0)
    }

    private fun waterDensity(temperature: Double): Double {
        val points = listOf(
            0.0 to 999.84, 10.0 to 999.70, 20.0 to 998.21, 30.0 to 995.65,
            40.0 to 992.22, 50.0 to 988.05, 60.0 to 983.20, 70.0 to 977.76,
            80.0 to 971.80, 90.0 to 965.30, 100.0 to 958.35, 110.0 to 950.95, 120.0 to 943.10,
        )
        val t = min(max(temperature, points.first().first), points.last().first)
        for (index in 0 until points.lastIndex) {
            val lower = points[index]
            val upper = points[index + 1]
            if (t >= lower.first && t <= upper.first) {
                val fraction = (t - lower.first) / (upper.first - lower.first)
                return lower.second + (upper.second - lower.second) * fraction
            }
        }
        return points.last().second
    }
}
