import SwiftUI

struct ContentView: View {
    @Environment(ProStore.self) private var proStore
    @State private var showSettings = false
    @State private var showPaywall = false

    var body: some View {
        NavigationStack {
            ZStack {
                AppTheme.background.ignoresSafeArea()

                ScrollView {
                    VStack(alignment: .leading, spacing: 20) {
                        header

                        NavigationLink {
                            HeatingMAGView()
                        } label: {
                            HomeCard(
                                icon: "cylinder.fill",
                                title: "home.heating.title",
                                subtitle: "home.heating.subtitle",
                                badge: "home.free.badge",
                                locked: false
                            )
                        }
                        .buttonStyle(.plain)

                        NavigationLink {
                            PressureAssistantView()
                        } label: {
                            HomeCard(
                                icon: "gauge.with.dots.needle.67percent",
                                title: "home.pressure.title",
                                subtitle: "home.pressure.subtitle",
                                badge: "home.free.badge",
                                locked: false
                            )
                        }
                        .buttonStyle(.plain)

                        if proStore.isPro {
                            NavigationLink {
                                AdvancedMAGView()
                            } label: {
                                HomeCard(
                                    icon: "drop.triangle.fill",
                                    title: "home.advanced.title",
                                    subtitle: "home.advanced.subtitle",
                                    badge: "home.pro.badge",
                                    locked: false
                                )
                            }
                            .buttonStyle(.plain)
                        } else {
                            Button {
                                showPaywall = true
                            } label: {
                                HomeCard(
                                    icon: "drop.triangle.fill",
                                    title: "home.advanced.title",
                                    subtitle: "home.advanced.subtitle",
                                    badge: "home.pro.badge",
                                    locked: true
                                )
                            }
                            .buttonStyle(.plain)
                        }

                        FormulaNote(text: "home.disclaimer")
                    }
                    .padding(18)
                    .padding(.bottom, 30)
                }
            }
            .navigationTitle("MAGCalc")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showSettings = true
                    } label: {
                        Image(systemName: "gearshape.fill")
                    }
                }
            }
            .sheet(isPresented: $showSettings) {
                NavigationStack {
                    SettingsView()
                }
            }
            .sheet(isPresented: $showPaywall) {
                NavigationStack {
                    ProPaywallView()
                }
            }
        }
        .tint(AppTheme.accent)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                ZStack {
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .fill(AppTheme.accentGradient)
                        .frame(width: 62, height: 62)

                    Image(systemName: "cylinder.fill")
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(Color.black.opacity(0.78))
                }

                Spacer()

                if proStore.isPro {
                    Text("MAGCalc Pro")
                        .font(.caption.bold())
                        .padding(.horizontal, 11)
                        .padding(.vertical, 7)
                        .background(AppTheme.accent.opacity(0.14), in: Capsule())
                        .foregroundStyle(AppTheme.accent)
                }
            }

            Text("home.title")
                .font(.system(size: 34, weight: .bold, design: .rounded))

            Text("home.subtitle")
                .foregroundStyle(AppTheme.muted)
                .lineSpacing(3)
        }
        .padding(.bottom, 4)
    }
}

private struct HomeCard: View {
    let icon: String
    let title: LocalizedStringKey
    let subtitle: LocalizedStringKey
    let badge: LocalizedStringKey
    let locked: Bool

    var body: some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(AppTheme.accent)
                .frame(width: 48, height: 48)
                .background(AppTheme.accent.opacity(0.12), in: RoundedRectangle(cornerRadius: 15))

            VStack(alignment: .leading, spacing: 5) {
                HStack(spacing: 8) {
                    Text(title)
                        .font(.headline)
                    Text(badge)
                        .font(.caption2.bold())
                        .foregroundStyle(locked ? AppTheme.muted : AppTheme.accent)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Color.white.opacity(0.06), in: Capsule())
                }
                Text(subtitle)
                    .font(.subheadline)
                    .foregroundStyle(AppTheme.muted)
                    .multilineTextAlignment(.leading)
            }

            Spacer()

            Image(systemName: locked ? "lock.fill" : "chevron.right")
                .foregroundStyle(AppTheme.muted)
        }
        .padding(18)
        .background(AppTheme.cardGradient, in: RoundedRectangle(cornerRadius: 22, style: .continuous))
        .overlay {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .stroke(AppTheme.line, lineWidth: 1)
        }
    }
}
