import SwiftUI

struct ProjectsView: View {
    @State private var store = ProjectStore()
    @State private var editing: VesselProject?
    @State private var deleting: VesselProject?

    var body: some View {
        List {
            Section {
                Text("projects.intro").foregroundStyle(.secondary)
                if store.loadFailed {
                    Text("projects.loadError").foregroundStyle(.red)
                    Button("projects.retry") { store.reload() }
                }
            }
            if store.projects.isEmpty && !store.loadFailed {
                ContentUnavailableView("projects.empty", systemImage: "folder", description: Text("projects.emptyHint"))
            }
            ForEach(store.projects) { project in
                NavigationLink {
                    ProjectDetailView(store: store, projectID: project.id)
                } label: {
                    VStack(alignment: .leading, spacing: 5) {
                        Text(project.name).font(.headline)
                        Text(project.updatedAt, format: .dateTime.day().month().year()).font(.caption).foregroundStyle(.secondary)
                        Text(project.notes).lineLimit(2).font(.caption).foregroundStyle(.secondary)
                    }
                }
                .swipeActions {
                    Button("projects.delete", role: .destructive) { deleting = project }
                }
            }
        }
        .navigationTitle("projects.title")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("projects.new", systemImage: "plus") {
                    editing = VesselProject(name: String(localized: "projects.defaultName"),
                                            scenarios: [VesselScenario(name: String(localized: "projects.baseline"))])
                }.disabled(store.loadFailed)
            }
        }
        .sheet(item: $editing) { project in
            ProjectInfoEditor(project: project) { store.save($0) }
        }
        .confirmationDialog("projects.deleteConfirm", isPresented: Binding(get: { deleting != nil }, set: { if !$0 { deleting = nil } }), titleVisibility: .visible) {
            Button("projects.delete", role: .destructive) {
                if let deleting { store.delete(id: deleting.id) }; deleting = nil
            }
        }
        .alert("projects.error", isPresented: Binding(get: { store.errorMessage != nil }, set: { if !$0 { store.errorMessage = nil } })) {
            Button("common.done") { store.errorMessage = nil }
        } message: { Text(store.errorMessage ?? "") }
    }
}

private struct ProjectDetailView: View {
    @Bindable var store: ProjectStore
    let projectID: UUID
    @Environment(\.locale) private var locale
    @State private var editingScenario: VesselScenario?
    @State private var editingProject: VesselProject?
    @State private var deletingScenario: VesselScenario?
    private var project: VesselProject? { store.projects.first { $0.id == projectID } }

    var body: some View {
        Group {
            if let project {
                List {
                    Section {
                        if !project.notes.isEmpty { Text(project.notes) }
                        Button("projects.editInfo", systemImage: "pencil") { editingProject = project }
                        ShareLink(item: VesselReport.text(project: project, german: locale.language.languageCode?.identifier == "de")) {
                            Label("projects.share", systemImage: "square.and.arrow.up")
                        }
                    }
                    Section("projects.scenarios") {
                        ForEach(project.scenarios) { scenario in
                            ScenarioComparison(scenario: scenario, baseline: project.baseline,
                                               isBaseline: scenario.id == project.baselineID)
                            VStack(alignment: .leading, spacing: 12) {
                                Button("projects.editScenario", systemImage: "pencil") { editingScenario = scenario }
                                Button("projects.duplicate", systemImage: "plus.square.on.square") {
                                    var updated = project
                                    if let copy = updated.duplicateScenario(id: scenario.id),
                                       let index = updated.scenarios.firstIndex(where: { $0.id == copy.id }) {
                                        updated.scenarios[index].name += " · " + String(localized: "projects.copy")
                                    }
                                    store.save(updated)
                                }
                                if scenario.id != project.baselineID {
                                    Button("projects.setBaseline", systemImage: "flag") {
                                        var updated = project; updated.baselineID = scenario.id; store.save(updated)
                                    }
                                }
                                if project.scenarios.count > 1 {
                                    Button("projects.deleteScenario", role: .destructive) { deletingScenario = scenario }
                                }
                            }.padding(.vertical, 8)
                        }
                    }
                    Section("projects.record") {
                        Text(VesselReport.text(project: project, german: locale.language.languageCode?.identifier == "de"))
                            .font(.footnote).textSelection(.enabled)
                    }
                }
                .navigationTitle(project.name)
                .navigationBarTitleDisplayMode(.inline)
            } else { ContentUnavailableView("projects.empty", systemImage: "folder") }
        }
        .sheet(item: $editingProject) { project in ProjectInfoEditor(project: project) { store.save($0) } }
        .sheet(item: $editingScenario) { scenario in
            ScenarioEditor(scenario: scenario) { edited in
                guard var updated = project, let index = updated.scenarios.firstIndex(where: { $0.id == edited.id }) else { return false }
                updated.scenarios[index] = edited; return store.save(updated)
            }
        }
        .confirmationDialog("projects.deleteScenarioConfirm", isPresented: Binding(get: { deletingScenario != nil }, set: { if !$0 { deletingScenario = nil } }), titleVisibility: .visible) {
            Button("projects.delete", role: .destructive) {
                if var updated = project, let deletingScenario { updated.removeScenario(id: deletingScenario.id); store.save(updated) }
                deletingScenario = nil
            }
        }
    }
}

private struct ScenarioComparison: View {
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    let scenario: VesselScenario
    let baseline: VesselScenario?
    let isBaseline: Bool
    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(scenario.name).font(.title3.bold())
            if isBaseline { Label("projects.baseline", systemImage: "flag.fill").font(.caption).foregroundStyle(AppTheme.accent) }
            if !scenario.notes.isEmpty { Text(scenario.notes).font(.footnote).foregroundStyle(.secondary) }
            metric("input.systemVolume", scenario.systemVolume, "l")
            metric("input.maximumTemperature", scenario.maximumTemperature, "°C")
            metric("input.staticHeight", scenario.staticHeight, "m")
            metric("input.safetyValve", scenario.safetyValvePressure, "bar")
            metric("result.requiredVessel", scenario.result.requiredLiters, "l")
            metric("result.recommendedVessel", scenario.result.recommendedLiters, "l")
            if let baseline {
                if dynamicTypeSize.isAccessibilitySize {
                    Text("projects.baseline").font(.headline)
                    metric("result.requiredVessel", baseline.result.requiredLiters, "l")
                    metric("result.recommendedVessel", baseline.result.recommendedLiters, "l")
                } else {
                    Grid(alignment: .leading, horizontalSpacing: 14, verticalSpacing: 8) {
                        GridRow { Text("projects.comparison"); Text("projects.baseline"); Text("projects.scenario") }
                            .font(.caption).foregroundStyle(.secondary)
                        GridRow { Text("result.requiredVessel"); Text(baseline.result.requiredLiters, format: .number.precision(.fractionLength(2))); Text(scenario.result.requiredLiters, format: .number.precision(.fractionLength(2))) }
                        GridRow { Text("result.recommendedVessel"); Text(baseline.result.recommendedLiters, format: .number); Text(scenario.result.recommendedLiters, format: .number) }
                    }.font(.footnote)
                }
                metric("projects.requiredDifference", scenario.result.requiredLiters - baseline.result.requiredLiters, "l", signed: true)
                metric("projects.standardDifference", scenario.result.recommendedLiters - baseline.result.recommendedLiters, "l", signed: true)
            }
        }.padding(.vertical, 10)
    }
    private func metric(_ title: LocalizedStringKey, _ value: Double, _ unit: String, signed: Bool = false) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title).font(.caption).foregroundStyle(.secondary)
            Text((signed && value > 0 ? "+" : "") + value.formatted(.number.precision(.fractionLength(0...2))) + " " + unit)
                .font(.body.monospacedDigit())
        }
    }
}

private struct ProjectInfoEditor: View {
    @Environment(\.dismiss) private var dismiss
    @State var project: VesselProject
    var save: (VesselProject) -> Bool
    @State private var failed = false
    var body: some View {
        NavigationStack {
            Form {
                Section("projects.installation") { TextField("projects.name", text: $project.name) }
                Section("projects.notes") { TextField("projects.notesHint", text: $project.notes, axis: .vertical).lineLimit(3...8) }
            }
            .navigationTitle("projects.editInfo")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("projects.cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("projects.save") { if save(project) { dismiss() } else { failed = true } }.disabled(!project.isValid)
                }
            }
            .alert("projects.error", isPresented: $failed) { Button("common.done") {} } message: { Text("projects.saveError") }
        }
    }
}

private struct ScenarioEditor: View {
    @Environment(\.dismiss) private var dismiss
    let scenario: VesselScenario
    var save: (VesselScenario) -> Bool
    @State private var name: String
    @State private var notes: String
    @State private var volume: String
    @State private var temperature: String
    @State private var height: String
    @State private var valve: String
    @State private var failed = false

    init(scenario: VesselScenario, save: @escaping (VesselScenario) -> Bool) {
        self.scenario = scenario; self.save = save
        _name = State(initialValue: scenario.name); _notes = State(initialValue: scenario.notes)
        _volume = State(initialValue: String(scenario.systemVolume))
        _temperature = State(initialValue: String(scenario.maximumTemperature))
        _height = State(initialValue: String(scenario.staticHeight))
        _valve = State(initialValue: String(scenario.safetyValvePressure))
    }
    private var edited: VesselScenario {
        var updated = scenario
        updated.name = name.trimmingCharacters(in: .whitespacesAndNewlines); updated.notes = notes
        updated.systemVolume = VesselNumericInput.parse(volume) ?? .nan
        updated.maximumTemperature = VesselNumericInput.parse(temperature) ?? .nan
        updated.staticHeight = VesselNumericInput.parse(height) ?? .nan
        updated.safetyValvePressure = VesselNumericInput.parse(valve) ?? .nan
        return updated
    }
    var body: some View {
        NavigationStack {
            Form {
                Section("projects.scenario") { TextField("projects.name", text: $name) }
                Section("projects.waterInputs") {
                    number("input.systemVolume", "l", $volume)
                    number("input.maximumTemperature", "°C", $temperature)
                    number("input.staticHeight", "m", $height)
                    number("input.safetyValve", "bar", $valve)
                }
                Section("projects.sourceNotes") { TextField("projects.sourceHint", text: $notes, axis: .vertical).lineLimit(3...8) }
                Section { Text("projects.waterLimits").font(.footnote)
                    if !edited.isValid { Text("projects.invalid").foregroundStyle(.red) }
                }
                if edited.isValid {
                    Section("projects.preview") {
                        Text(edited.result.recommendedLiters.formatted(.number) + " l").font(.title2.bold())
                        Text("result.recommendedVessel").font(.caption)
                    }
                }
            }
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("projects.editScenario")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("projects.cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("projects.save") { if save(edited) { dismiss() } else { failed = true } }.disabled(!edited.isValid)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer(); Button("common.done") { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
                }
            }
            .alert("projects.error", isPresented: $failed) { Button("common.done") {} } message: { Text("projects.saveError") }
        }
    }
    private func number(_ title: LocalizedStringKey, _ unit: String, _ binding: Binding<String>) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
            HStack {
                TextField(title, text: binding)
                    .keyboardType(.decimalPad).accessibilityLabel(title)
                Text(unit).foregroundStyle(.secondary)
            }
        }.padding(.vertical, 4)
    }
}
