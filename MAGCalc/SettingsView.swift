import SwiftUI

struct SettingsView: View {
    @Environment(ProStore.self) private var proStore
    @Environment(\.dismiss) private var dismiss
    @State private var showPaywall = false

    var body: some View {
        List {
            Section {
                HStack {
                    Label("settings.proStatus", systemImage: "sparkles")
                    Spacer()
                    Text(proStore.isPro ? String(localized: "settings.proActive") : String(localized: "settings.free"))
                        .foregroundStyle(proStore.isPro ? AppTheme.accent : .secondary)
                }

                if !proStore.isPro {
                    Button("settings.unlockPro") { showPaywall = true }
                }

                Button("pro.restore") {
                    Task { await proStore.restore() }
                }
            }

            Section("settings.links") {
                Link("settings.support", destination: URL(string: "https://kamilunavo.com/support")!)
                Link("settings.privacy", destination: URL(string: "https://kamilunavo.com/magcalc/privacy")!)
            }

            Section("settings.about") {
                LabeledContent("settings.version", value: "1.0.0")
                LabeledContent("settings.developer", value: "Kamilunavo")
            }

            Section {
                Text("settings.disclaimer")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
        .scrollContentBackground(.hidden)
        .background(AppTheme.background.ignoresSafeArea())
        .navigationTitle("settings.title")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("common.done") { dismiss() }
            }
        }
        .sheet(isPresented: $showPaywall) {
            NavigationStack { ProPaywallView() }
        }
    }
}
