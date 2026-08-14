import SwiftUI

struct PressureAssistantView: View {
    @State private var staticHeight = 8.0
    @State private var safetyValvePressure = 3.0

    private var result: PressureResult {
        MAGCalculator.pressure(staticHeight: staticHeight, safetyValvePressure: safetyValvePressure)
    }

    var body: some View {
        CalculatorScreen(title: "pressure.title", subtitle: "pressure.subtitle") {
            InputCard {
                NumberInputRow(title: "input.staticHeight", unit: "m", value: $staticHeight)
                NumberInputRow(title: "input.safetyValve", unit: "bar", value: $safetyValvePressure)
            }

            ResultCard(icon: "arrow.down.to.line", title: "result.staticPressure", value: result.staticPressure, unit: "bar")
            ResultCard(icon: "gauge.with.dots.needle.50percent", title: "result.precharge", value: result.prechargePressure, unit: "bar")
            ResultCard(icon: "drop.fill", title: "result.fillPressure", value: result.fillPressure, unit: "bar")
            ResultCard(icon: "exclamationmark.triangle", title: "result.maxOperatingPressure", value: result.recommendedMaxPressure, unit: "bar")

            if !result.isValid {
                FormulaNote(text: "error.pressureWindow")
            }

            FormulaNote(text: "pressure.formulaNote")
        }
    }
}
