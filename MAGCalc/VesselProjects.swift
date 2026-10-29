import Foundation

struct VesselSelection: Codable, Equatable {
    var nominalLiters: Double
    var designation: String
    var source: String
    var isValid: Bool {
        nominalLiters.isFinite && nominalLiters > 0 &&
        !designation.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !source.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
}

/// A numerical capacity comparison under this scenario's water model, not installation approval.
struct VesselCapacityComparison: Equatable {
    let requiredLiters: Double
    let selectedLiters: Double
    var marginLiters: Double { selectedLiters - requiredLiters }
    var shortfallLiters: Double { max(0, -marginLiters) }
}

struct VesselScenario: Identifiable, Codable, Equatable {
    var id = UUID()
    var name: String
    var notes = ""
    var systemVolume = 300.0
    var maximumTemperature = 80.0
    var staticHeight = 8.0
    var safetyValvePressure = 3.0
    var selectedVessel: VesselSelection? = nil

    var result: MAGResult {
        MAGCalculator.heating(systemVolume: systemVolume, maximumTemperature: maximumTemperature,
                              staticHeight: staticHeight, safetyValvePressure: safetyValvePressure)
    }
    var capacityComparison: VesselCapacityComparison? {
        guard isValid, let selectedVessel else { return nil }
        return VesselCapacityComparison(requiredLiters: result.requiredLiters, selectedLiters: selectedVessel.nominalLiters)
    }
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        [systemVolume, maximumTemperature, staticHeight, safetyValvePressure].allSatisfy(\.isFinite) &&
        systemVolume > 0 && (10...120).contains(maximumTemperature) && staticHeight >= 0 &&
        safetyValvePressure > 0 && result.isValid &&
        [result.requiredLiters, result.recommendedLiters, result.fillPressure].allSatisfy(\.isFinite) &&
        (selectedVessel?.isValid ?? true)
    }
}

struct VesselProject: Identifiable, Codable, Equatable {
    var id = UUID()
    var name: String
    var notes = ""
    var createdAt = Date()
    var updatedAt = Date()
    var scenarios: [VesselScenario]
    var baselineID: UUID

    init(name: String, notes: String = "", scenarios: [VesselScenario]) {
        self.name = name; self.notes = notes; self.scenarios = scenarios
        baselineID = scenarios.first?.id ?? UUID()
    }
    var baseline: VesselScenario? { scenarios.first { $0.id == baselineID } }
    var isValid: Bool {
        !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !scenarios.isEmpty &&
        baseline != nil && scenarios.allSatisfy(\.isValid) &&
        Set(scenarios.map(\.id)).count == scenarios.count
    }
    @discardableResult mutating func duplicateScenario(id: UUID) -> VesselScenario? {
        guard var copy = scenarios.first(where: { $0.id == id }) else { return nil }
        copy.id = UUID(); scenarios.append(copy); updatedAt = Date(); return copy
    }
    mutating func removeScenario(id: UUID) {
        guard scenarios.count > 1 else { return }
        scenarios.removeAll { $0.id == id }
        if baseline == nil { baselineID = scenarios[0].id }
        updatedAt = Date()
    }
}

enum VesselArchiveError: Error {
    case invalidData, unsupportedVersion
}

enum VesselProjectArchive {
    private struct Document: Codable { var version: Int; var projects: [VesselProject] }
    static func load(from url: URL) throws -> [VesselProject] {
        guard FileManager.default.fileExists(atPath: url.path) else { return [] }
        let data = try Data(contentsOf: url)
        let decoder = JSONDecoder()
        let projects: [VesselProject]
        if let document = try? decoder.decode(Document.self, from: data) {
            guard (1...2).contains(document.version) else { throw VesselArchiveError.unsupportedVersion }
            projects = document.projects
        } else {
            projects = try decoder.decode([VesselProject].self, from: data)
        }
        try validate(projects)
        return projects
    }
    static func save(_ projects: [VesselProject], to url: URL) throws {
        try validate(projects)
        // Refuse to replace unreadable or newer documents, even after a failed initial load.
        _ = try load(from: url)
        let encoder = JSONEncoder(); encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        let data = try encoder.encode(Document(version: 2, projects: projects))
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try data.write(to: url, options: .atomic)
    }
    private static func validate(_ projects: [VesselProject]) throws {
        guard projects.allSatisfy(\.isValid), Set(projects.map(\.id)).count == projects.count else {
            throw VesselArchiveError.invalidData
        }
    }
}

enum VesselReport {
    static func text(project: VesselProject, german: Bool) -> String {
        func l(_ en: String, _ de: String) -> String { german ? de : en }
        func n(_ value: Double) -> String {
            String(format: "%.3f", locale: Locale(identifier: german ? "de_DE" : "en_US_POSIX"), value)
        }
        var lines = ["MAGCalc — " + l("Calculation record", "Berechnungsprotokoll"), project.name,
                     "ID: \(project.id.uuidString)",
                     l("Updated: ", "Aktualisiert: ") + ISO8601DateFormatter().string(from: project.updatedAt),
                     l("Installation notes: ", "Anlagennotizen: ") + project.notes,
                     l("Baseline: ", "Referenz: ") + (project.baseline?.name ?? "—")]
        for scenario in project.scenarios {
            let r = scenario.result
            lines += ["", scenario.name + (scenario.id == project.baselineID ? l(" (baseline)", " (Referenz)") : ""),
                      "ID: \(scenario.id.uuidString)", l("Input sources: ", "Eingabequellen: ") + scenario.notes,
                      l("System volume", "Anlagenvolumen") + ": \(n(scenario.systemVolume)) l",
                      l("Water temperatures", "Wassertemperaturen") + ": 10 °C → \(n(scenario.maximumTemperature)) °C",
                      l("Static height", "Statische Höhe") + ": \(n(scenario.staticHeight)) m",
                      l("Safety valve", "Sicherheitsventil") + ": \(n(scenario.safetyValvePressure)) bar",
                      l("Expansion", "Ausdehnung") + ": \(n(r.expansionPercent)) % / \(n(r.expansionLiters)) l",
                      l("Reserve", "Reserve") + ": \(n(r.reserveLiters)) l",
                      l("Required vessel", "Erforderliches Gefäß") + ": \(n(r.requiredLiters)) l",
                      l("Recommended vessel size", "Empfohlene Gefäßgröße") + ": \(n(r.recommendedLiters)) l",
                      l("Static pressure", "Statischer Druck") + ": \(n(r.staticPressure)) bar",
                      l("Pre-charge / fill / final pressure", "Vordruck / Füll- / Enddruck") + ": \(n(r.prechargePressure)) / \(n(r.fillPressure)) / \(n(r.finalPressure)) bar",
                      l("Acceptance factor", "Aufnahmefaktor") + ": \(n(r.acceptanceFactor))"]
            if let selected = scenario.selectedVessel, let comparison = scenario.capacityComparison {
                lines += [l("Documented selected vessel", "Dokumentiertes ausgewähltes Gefäß") + ": " + selected.designation,
                          l("Capacity source", "Kapazitätsquelle") + ": " + selected.source,
                          l("Selected nominal capacity", "Ausgewählter Nenninhalt") + ": \(n(selected.nominalLiters)) l",
                          l("Selected minus this calculated requirement", "Ausgewählter Nenninhalt minus berechneter Bedarf") + ": \(n(comparison.marginLiters)) l",
                          l("Shortfall against this calculated value", "Fehlvolumen gegenüber diesem berechneten Wert") + ": \(n(comparison.shortfallLiters)) l",
                          l("Capacity comparison for this scenario only. Not a safety approval; verify the actual vessel and manufacturer operating limits separately.", "Kapazitätsvergleich nur für dieses Szenario. Keine Sicherheitsfreigabe; tatsächliches Gefäß und Hersteller-Betriebsgrenzen gesondert prüfen.")]
            }
            if let baseline = project.baseline {
                lines.append(l("Difference from baseline (required / recommended)", "Differenz zur Referenz (erforderlich / empfohlen)") + ": \(n(r.requiredLiters - baseline.result.requiredLiters)) / \(n(r.recommendedLiters - baseline.result.recommendedLiters)) l")
            }
        }
        lines += ["", l("Vessel selection: first adequate size from 8, 12, 18, 25, 35, 50, 80, 100, 140, 200, 250, 300, 400, 500, 600 l; larger requirements round upward to 100 l. Verify actual manufacturer availability.", "Gefäßauswahl: erste ausreichende Größe aus 8, 12, 18, 25, 35, 50, 80, 100, 140, 200, 250, 300, 400, 500, 600 l; größere Anforderungen auf 100 l aufrunden. Tatsächliche Herstellerverfügbarkeit prüfen."), l("Engine: MAGCalc heating water model, revision 1. Reference water 10 °C; tabulated density interpolation 10–120 °C. Gauge pressures in bar; absolute pressure = gauge + 1 bar.", "Rechenmodell: MAGCalc Heizungswasser, Revision 1. Wasserreferenz 10 °C; tabellierte Dichteinterpolation 10–120 °C. Überdruck in bar; Absolutdruck = Überdruck + 1 bar."),
                  "Ve = V × (ρ10/ρT − 1); Vr = max(3 l, V × 0.005); p0 = max(0.5, H/10 + 0.2); pf = p0 + 0.3; pe = pSV − 0.5; A = (pe − p0)/(pe + 1); Vn = (Ve + Vr)/A.",
                  l("Sizing aid for water only. Verify manufacturer limits, system conditions and applicable requirements with a qualified professional. No manufacturer approval or compliance certificate.", "Auslegungshilfe nur für Wasser. Herstellergrenzen, Anlagenbedingungen und geltende Anforderungen fachlich prüfen. Keine Herstellerfreigabe oder Konformitätsbescheinigung.")]
        return lines.joined(separator: "\n")
    }
}

// Visible editor text must be independently valid; a cleared field cannot reuse an old value.
enum VesselNumericInput {
    static func parse(_ text: String) -> Double? {
        let normalized = text.trimmingCharacters(in: .whitespacesAndNewlines).replacingOccurrences(of: ",", with: ".")
        guard let value = Double(normalized), value.isFinite else { return nil }
        return value
    }
}
