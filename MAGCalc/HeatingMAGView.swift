import SwiftUI

struct HeatingMAGView: View {
    @State private var systemVolume = 300.0
    @State private var maximumTemperature = 80.0
    @State private var staticHeight = 8.0
    @State private var safetyValvePressure = 3.0

    private var result: MAGResult {
        MAGCalculator.heating(
            systemVolume: systemVolume,
            maximumTemperature: maximumTemperature,
            staticHeight: staticHeight,
            safetyValvePressure: safetyValvePressure
        )
    }

    var body: some View {
        CalculatorScreen(
            title: "heating.title",
            subtitle: "heating.subtitle"
        ) {
            InputCard {
                NumberInputRow(title: "input.systemVolume", unit: "l", value: $systemVolume)
                NumberInputRow(title: "input.maximumTemperature", unit: "°C", value: $maximumTemperature)
                NumberInputRow(title: "input.staticHeight", unit: "m", value: $staticHeight)
                NumberInputRow(title: "input.safetyValve", unit: "bar", value: $safetyValvePressure)
            }

            if result.isValid {
                TextResultCard(
                    icon: "checkmark.seal.fill",
                    title: "result.recommendedVessel",
                    value: result.recommendedLiters.formatted(.number.precision(.fractionLength(0))),
                    unit: "l"
                )

                ResultCard(icon: "ruler", title: "result.requiredVessel", value: result.requiredLiters, unit: "l")
                ResultCard(icon: "arrow.up.right.circle", title: "result.waterExpansion", value: result.expansionLiters, unit: "l")
                ResultCard(icon: "percent", title: "result.expansionPercent", value: result.expansionPercent, unit: "%")
                ResultCard(icon: "gauge.with.dots.needle.50percent", title: "result.precharge", value: result.prechargePressure, unit: "bar")
                ResultCard(icon: "drop.fill", title: "result.fillPressure", value: result.fillPressure, unit: "bar")
            } else {
                FormulaNote(text: "error.pressureWindow")
            }

            FormulaNote(text: "heating.formulaNote")
        }
    }
}
