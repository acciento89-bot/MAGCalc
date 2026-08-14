import SwiftUI

struct AdvancedMAGView: View {
    @Environment(ProStore.self) private var proStore

    @State private var systemVolume = 300.0
    @State private var expansionPercent = 4.0
    @State private var prechargePressure = 1.0
    @State private var finalPressure = 2.5
    @State private var reservePercent = 0.5

    private var result: MAGResult {
        MAGCalculator.advanced(
            systemVolume: systemVolume,
            expansionPercent: expansionPercent,
            prechargePressure: prechargePressure,
            finalPressure: finalPressure,
            reservePercent: reservePercent
        )
    }

    var body: some View {
        if proStore.isPro {
            CalculatorScreen(title: "advanced.title", subtitle: "advanced.subtitle") {
                InputCard {
                    NumberInputRow(title: "input.systemVolume", unit: "l", value: $systemVolume)
                    NumberInputRow(title: "input.expansionPercent", unit: "%", value: $expansionPercent)
                    NumberInputRow(title: "input.precharge", unit: "bar", value: $prechargePressure)
                    NumberInputRow(title: "input.finalPressure", unit: "bar", value: $finalPressure)
                    NumberInputRow(title: "input.reservePercent", unit: "%", value: $reservePercent)
                }

                if result.isValid {
                    TextResultCard(
                        icon: "checkmark.seal.fill",
                        title: "result.recommendedVessel",
                        value: result.recommendedLiters.formatted(.number.precision(.fractionLength(0))),
                        unit: "l"
                    )
                    ResultCard(icon: "ruler", title: "result.requiredVessel", value: result.requiredLiters, unit: "l")
                    ResultCard(icon: "arrow.up.right.circle", title: "result.expansionVolume", value: result.expansionLiters, unit: "l")
                    ResultCard(icon: "plus.circle", title: "result.reserveVolume", value: result.reserveLiters, unit: "l")
                } else {
                    FormulaNote(text: "advanced.invalid")
                }

                FormulaNote(text: "advanced.formulaNote")
            }
        } else {
            ProPaywallView()
        }
    }
}
