import SwiftUI

struct ProPaywallView: View {
    @Environment(ProStore.self) private var proStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ZStack {
            AppTheme.background.ignoresSafeArea()

            ScrollView {
                VStack(spacing: 22) {
                    Image(systemName: "cylinder.fill")
                        .font(.system(size: 48, weight: .bold))
                        .foregroundStyle(AppTheme.accent)
                        .frame(width: 92, height: 92)
                        .background(AppTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 28))

                    VStack(spacing: 8) {
                        Text("MAGCalc Pro")
                            .font(.system(size: 34, weight: .bold, design: .rounded))
                        Text("pro.subtitle")
                            .foregroundStyle(AppTheme.muted)
                            .multilineTextAlignment(.center)
                    }

                    VStack(spacing: 12) {
                        ProFeature(icon: "drop.triangle.fill", text: "pro.feature.custom")
                        ProFeature(icon: "sun.max.fill", text: "pro.feature.solar")
                        ProFeature(icon: "snowflake", text: "pro.feature.glycol")
                        ProFeature(icon: "infinity", text: "pro.feature.once")
                    }

                    Button {
                        Task {
                            await proStore.purchase()
                            if proStore.isPro { dismiss() }
                        }
                    } label: {
                        HStack {
                            if proStore.isLoading {
                                ProgressView().tint(.black)
                            } else {
                                Text(proStore.product?.displayPrice ?? String(localized: "pro.buy"))
                                    .fontWeight(.bold)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .foregroundStyle(.black)
                        .background(AppTheme.accentGradient, in: RoundedRectangle(cornerRadius: 17))
                    }
                    .disabled(proStore.isLoading)

                    Button("pro.restore") {
                        Task {
                            await proStore.restore()
                            if proStore.isPro { dismiss() }
                        }
                    }
                    .foregroundStyle(AppTheme.accent)

                    if let error = proStore.errorMessage {
                        Text(error)
                            .font(.footnote)
                            .foregroundStyle(.red.opacity(0.9))
                            .multilineTextAlignment(.center)
                    }

                    Text("pro.note")
                        .font(.footnote)
                        .foregroundStyle(AppTheme.muted)
                        .multilineTextAlignment(.center)
                }
                .padding(22)
            }
        }
        .navigationTitle("MAGCalc Pro")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct ProFeature: View {
    let icon: String
    let text: LocalizedStringKey

    var body: some View {
        HStack(spacing: 13) {
            Image(systemName: icon)
                .foregroundStyle(AppTheme.accent)
                .frame(width: 30)
            Text(text)
                .frame(maxWidth: .infinity, alignment: .leading)
            Image(systemName: "checkmark.circle.fill")
                .foregroundStyle(AppTheme.accent)
        }
        .padding(15)
        .background(AppTheme.panel, in: RoundedRectangle(cornerRadius: 16))
    }
}
