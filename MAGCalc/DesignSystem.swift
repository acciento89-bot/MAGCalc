import SwiftUI
import UIKit

enum AppTheme {
    static let background = LinearGradient(
        colors: [
            Color(red: 0.012, green: 0.035, blue: 0.055),
            Color(red: 0.018, green: 0.075, blue: 0.085)
        ],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let accent = Color(red: 0.18, green: 0.82, blue: 0.70)
    static let accent2 = Color(red: 0.30, green: 0.92, blue: 0.80)
    static let muted = Color.white.opacity(0.64)
    static let line = Color.white.opacity(0.09)
    static let panel = Color.white.opacity(0.055)

    static let accentGradient = LinearGradient(
        colors: [accent, accent2],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )

    static let cardGradient = LinearGradient(
        colors: [Color.white.opacity(0.075), Color.white.opacity(0.035)],
        startPoint: .topLeading,
        endPoint: .bottomTrailing
    )
}

struct CalculatorScreen<Content: View>: View {
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(title)
                            .font(.system(size: 30, weight: .bold, design: .rounded))
                        Text(subtitle)
                            .foregroundStyle(AppTheme.muted)
                    }
                    content()
                }
                .padding(18)
                .padding(.bottom, 30)
            }
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .keyboard) {
                Spacer()
                Button("common.done") {
                    dismissKeyboard()
                }
                .fontWeight(.semibold)
            }
        }
    }

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(
            #selector(UIResponder.resignFirstResponder),
            to: nil,
            from: nil,
            for: nil
        )
    }
}

struct InputCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 16) {
            content()
        }
        .padding(18)
        .background(AppTheme.cardGradient, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(AppTheme.line, lineWidth: 1)
        }
    }
}

struct NumberInputRow: View {
    let title: LocalizedStringKey
    let unit: String
    @Binding var value: Double

    var body: some View {
        HStack(spacing: 14) {
            Text(title)
                .foregroundStyle(.white)
            Spacer()
            TextField("", value: $value, format: .number.precision(.fractionLength(0...3)))
                .keyboardType(.decimalPad)
                .multilineTextAlignment(.trailing)
                .font(.system(.body, design: .rounded).weight(.semibold))
                .frame(width: 92)
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .background(Color.black.opacity(0.2), in: RoundedRectangle(cornerRadius: 10))
            Text(unit)
                .font(.subheadline.weight(.semibold))
                .foregroundStyle(AppTheme.muted)
                .frame(minWidth: 48, alignment: .leading)
        }
    }
}

struct ResultCard: View {
    let icon: String
    let title: LocalizedStringKey
    let value: Double
    let unit: String
    var digits: ClosedRange<Int> = 1...2

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(AppTheme.accent)
                .frame(width: 36, height: 36)
                .background(AppTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
                Text(value, format: .number.precision(.fractionLength(digits)))
                    .font(.system(size: 24, weight: .bold, design: .rounded))
            }
            Spacer()
            Text(unit)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(AppTheme.muted)
        }
        .padding(16)
        .background(AppTheme.panel, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct TextResultCard: View {
    let icon: String
    let title: LocalizedStringKey
    let value: String
    let unit: String

    var body: some View {
        HStack(spacing: 14) {
            Image(systemName: icon)
                .font(.system(size: 20, weight: .semibold))
                .foregroundStyle(AppTheme.accent)
                .frame(width: 36, height: 36)
                .background(AppTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 11))

            VStack(alignment: .leading, spacing: 3) {
                Text(title)
                    .font(.caption)
                    .foregroundStyle(AppTheme.muted)
                Text(value)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
            }
            Spacer()
            Text(unit)
                .font(.subheadline.weight(.bold))
                .foregroundStyle(AppTheme.muted)
        }
        .padding(16)
        .background(AppTheme.panel, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

struct FormulaNote: View {
    let text: LocalizedStringKey

    var body: some View {
        HStack(alignment: .top, spacing: 9) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(AppTheme.accent)
            Text(text)
                .font(.footnote)
                .foregroundStyle(AppTheme.muted)
                .lineSpacing(3)
        }
        .padding(15)
        .background(AppTheme.panel, in: RoundedRectangle(cornerRadius: 16))
    }
}
