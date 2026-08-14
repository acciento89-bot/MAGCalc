import Foundation

struct MAGResult {
    let expansionLiters: Double
    let reserveLiters: Double
    let requiredLiters: Double
    let recommendedLiters: Double
    let expansionPercent: Double
    let staticPressure: Double
    let prechargePressure: Double
    let fillPressure: Double
    let finalPressure: Double
    let acceptanceFactor: Double
    let isValid: Bool
}

struct PressureResult {
    let staticPressure: Double
    let prechargePressure: Double
    let fillPressure: Double
    let recommendedMaxPressure: Double
    let isValid: Bool
}

enum MAGCalculator {
    static let standardVesselSizes: [Double] = [
        8, 12, 18, 25, 35, 50, 80, 100, 140, 200, 250, 300, 400, 500, 600
    ]

    static func heating(
        systemVolume: Double,
        maximumTemperature: Double,
        staticHeight: Double,
        safetyValvePressure: Double
    ) -> MAGResult {
        let volume = max(systemVolume, 0)
        let staticPressure = max(staticHeight, 0) / 10.0
        let precharge = max(0.5, staticPressure + 0.2)
        let fill = precharge + 0.3
        let finalPressure = safetyValvePressure - 0.5

        let expansionRatio = waterExpansionRatio(from: 10, to: maximumTemperature)
        let expansionVolume = volume * expansionRatio
        let reserveVolume = max(3.0, volume * 0.005)

        let acceptance = finalPressure > precharge
            ? (finalPressure - precharge) / (finalPressure + 1.0)
            : 0

        let valid = volume > 0 &&
            maximumTemperature >= 10 &&
            finalPressure > fill &&
            acceptance > 0

        let required = valid
            ? (expansionVolume + reserveVolume) / acceptance
            : 0

        return MAGResult(
            expansionLiters: expansionVolume,
            reserveLiters: reserveVolume,
            requiredLiters: required,
            recommendedLiters: recommendedSize(for: required),
            expansionPercent: expansionRatio * 100,
            staticPressure: staticPressure,
            prechargePressure: precharge,
            fillPressure: fill,
            finalPressure: finalPressure,
            acceptanceFactor: acceptance,
            isValid: valid
        )
    }

    static func pressure(
        staticHeight: Double,
        safetyValvePressure: Double
    ) -> PressureResult {
        let staticPressure = max(staticHeight, 0) / 10.0
        let precharge = max(0.5, staticPressure + 0.2)
        let fill = precharge + 0.3
        let maxPressure = safetyValvePressure - 0.5

        return PressureResult(
            staticPressure: staticPressure,
            prechargePressure: precharge,
            fillPressure: fill,
            recommendedMaxPressure: maxPressure,
            isValid: maxPressure > fill
        )
    }

    static func advanced(
        systemVolume: Double,
        expansionPercent: Double,
        prechargePressure: Double,
        finalPressure: Double,
        reservePercent: Double
    ) -> MAGResult {
        let volume = max(systemVolume, 0)
        let expansionRatio = max(expansionPercent, 0) / 100.0
        let expansionVolume = volume * expansionRatio
        let reserveVolume = volume * max(reservePercent, 0) / 100.0
        let precharge = max(prechargePressure, 0)
        let final = max(finalPressure, 0)

        let acceptance = final > precharge
            ? (final - precharge) / (final + 1.0)
            : 0

        let valid = volume > 0 && expansionRatio > 0 && acceptance > 0
        let required = valid
            ? (expansionVolume + reserveVolume) / acceptance
            : 0

        return MAGResult(
            expansionLiters: expansionVolume,
            reserveLiters: reserveVolume,
            requiredLiters: required,
            recommendedLiters: recommendedSize(for: required),
            expansionPercent: expansionRatio * 100,
            staticPressure: 0,
            prechargePressure: precharge,
            fillPressure: precharge + 0.3,
            finalPressure: final,
            acceptanceFactor: acceptance,
            isValid: valid
        )
    }

    static func recommendedSize(for required: Double) -> Double {
        guard required > 0 else { return 0 }
        if let size = standardVesselSizes.first(where: { $0 >= required }) {
            return size
        }
        return ceil(required / 100.0) * 100.0
    }

    static func waterExpansionRatio(from startTemperature: Double, to endTemperature: Double) -> Double {
        let startDensity = waterDensity(at: startTemperature)
        let endDensity = waterDensity(at: max(endTemperature, startTemperature))
        guard endDensity > 0 else { return 0 }
        return max(0, startDensity / endDensity - 1.0)
    }

    private static func waterDensity(at temperature: Double) -> Double {
        let points: [(Double, Double)] = [
            (0, 999.84),
            (10, 999.70),
            (20, 998.21),
            (30, 995.65),
            (40, 992.22),
            (50, 988.05),
            (60, 983.20),
            (70, 977.76),
            (80, 971.80),
            (90, 965.30),
            (100, 958.35),
            (110, 950.95),
            (120, 943.10)
        ]

        let t = min(max(temperature, points.first!.0), points.last!.0)

        for index in 0..<(points.count - 1) {
            let lower = points[index]
            let upper = points[index + 1]
            if t >= lower.0 && t <= upper.0 {
                let fraction = (t - lower.0) / (upper.0 - lower.0)
                return lower.1 + (upper.1 - lower.1) * fraction
            }
        }

        return points.last!.1
    }
}
