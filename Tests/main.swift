import Foundation

func check(_ condition: @autoclosure () throws -> Bool, _ name: String) rethrows {
    guard try condition() else { fatalError("FAIL: \(name)") }
    print("PASS: \(name)")
}
func rejects(_ name: String, _ action: () throws -> Void) {
    do { try action(); fatalError("FAIL: \(name)") } catch { print("PASS: \(name)") }
}
check(VesselNumericInput.parse("") == nil, "empty edited input cannot reuse previous value")
check(VesselNumericInput.parse("invalid") == nil, "nonnumeric edited input rejected")
check(VesselNumericInput.parse("NaN") == nil, "nonfinite edited input rejected")
check(VesselNumericInput.parse(" 1,25 ") == 1.25, "German decimal entry")
check(VesselNumericInput.parse("1.25") == 1.25, "English decimal entry")
let scenario = VesselScenario(name: "Existing", notes: "Measured onsite")
check(scenario.isValid, "default water scenario valid")
check(abs(scenario.result.expansionLiters - 8.6128833093229) < 0.000001, "known 300L 10-80C density case")
check(scenario.result.recommendedLiters == 35, "known next standard vessel")
for value in [Double.nan, .infinity, -1, 0] {
    var invalid = scenario; invalid.systemVolume = value
    check(!invalid.isValid, "reject nonfinite/nonpositive volume \(value)")
}
var invalid = scenario; invalid.maximumTemperature = 121
check(!invalid.isValid, "reject unsupported density extrapolation")
invalid = scenario; invalid.staticHeight = -1
check(!invalid.isValid, "reject negative height")
invalid = scenario; invalid.safetyValvePressure = 1
check(!invalid.isValid, "reject impossible pressure window")
var project = VesselProject(name: "Boiler room", notes: "Installer source", scenarios: [scenario])
let duplicate = project.duplicateScenario(id: scenario.id)!
check(duplicate.id != scenario.id && duplicate.systemVolume == scenario.systemVolume, "duplicate uses fresh identity")
check(project.scenarios.count == 2, "duplicate appends")
project.baselineID = duplicate.id
project.removeScenario(id: duplicate.id)
check(project.baselineID == scenario.id, "deleting baseline repairs selection")
project.removeScenario(id: scenario.id)
check(project.scenarios.count == 1, "last scenario protected")
let report = VesselReport.text(project: project, german: false)
check(report.contains("Measured onsite") && report.contains("Installer source") && report.contains("300") && report.contains("10"), "report includes provenance and inputs")
check(report.contains("bar") && report.contains("Acceptance") && report.contains("MAGCalc"), "report includes complete outputs and engine")
check(VesselReport.text(project: project, german: true).contains("Berechnungsprotokoll"), "German report")
let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString)
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
defer { try? FileManager.default.removeItem(at: folder) }
let url = folder.appendingPathComponent("projects.json")
try check((try VesselProjectArchive.load(from: url)).isEmpty, "missing file starts empty")
try VesselProjectArchive.save([project], to: url)
try check((try VesselProjectArchive.load(from: url)) == [project], "archive roundtrip")
let legacy = try JSONEncoder().encode([project]); try legacy.write(to: url)
try check((try VesselProjectArchive.load(from: url)) == [project], "legacy bare array migration")
let corrupt = Data("broken".utf8); try corrupt.write(to: url)
rejects("corruption visible") { _ = try VesselProjectArchive.load(from: url) }
rejects("corruption protected from overwrite") { try VesselProjectArchive.save([project], to: url) }
try check((try Data(contentsOf: url)) == corrupt, "corrupt bytes preserved")
let future = Data("{\"version\":99,\"projects\":[]}".utf8); try future.write(to: url)
rejects("unknown schema visible") { _ = try VesselProjectArchive.load(from: url) }
rejects("future schema protected") { try VesselProjectArchive.save([project], to: url) }
try FileManager.default.removeItem(at: url)
var malformed = project; malformed.baselineID = UUID()
rejects("missing baseline rejected") { try VesselProjectArchive.save([malformed], to: url) }
malformed = project; malformed.scenarios.append(scenario)
rejects("duplicate scenario IDs rejected") { try VesselProjectArchive.save([malformed], to: url) }
print("ALL MAGCALC PROJECT TESTS PASSED")

var selectedJSON = try JSONSerialization.jsonObject(with: JSONEncoder().encode(scenario)) as! [String: Any]
selectedJSON["selectedVessel"] = ["nominalLiters": 20.0, "designation": "Maker M20", "source": "Nameplate A"]
let selectedScenario = try JSONDecoder().decode(VesselScenario.self, from: JSONSerialization.data(withJSONObject: selectedJSON))
let selectedRoundtrip = try JSONSerialization.jsonObject(with: JSONEncoder().encode(selectedScenario)) as! [String: Any]
check(selectedRoundtrip["selectedVessel"] != nil, "documented selected vessel survives decoding and encoding")

check(selectedScenario.isValid, "documented positive selection accepted")
check(abs(selectedScenario.capacityComparison!.shortfallLiters - 7.096727721753434) < 1e-8, "20l selection compared with independently known water requirement")
check(VesselCapacityComparison(requiredLiters: 25, selectedLiters: 25).shortfallLiters == 0, "equal capacity has no shortfall")
check(VesselCapacityComparison(requiredLiters: 25, selectedLiters: 35).marginLiters == 10, "larger capacity has positive numerical margin")
for capacity in [0.0, -1, Double.nan, Double.infinity] {
    var bad = selectedScenario; bad.selectedVessel!.nominalLiters = capacity
    check(!bad.isValid && bad.capacityComparison == nil, "invalid documented capacity rejected")
}
var noSource = selectedScenario; noSource.selectedVessel!.source = " "
check(!noSource.isValid, "selection requires a traceable source")
noSource = selectedScenario; noSource.selectedVessel!.designation = ""
check(!noSource.isValid, "selection requires a designation")
var selectedProject = VesselProject(name: "Vessel comparison", scenarios: [selectedScenario])
let copiedSelection = selectedProject.duplicateScenario(id: selectedScenario.id)!
selectedProject.scenarios[1].selectedVessel!.nominalLiters = 50
check(selectedProject.scenarios[0].selectedVessel!.nominalLiters == 20 && copiedSelection.id != selectedScenario.id, "duplicate selection edits do not mutate baseline scenario")
let selectedReport = VesselReport.text(project: selectedProject, german: false)
check(selectedReport.contains("Maker M20") && selectedReport.contains("Nameplate A") && selectedReport.contains("Shortfall against this calculated value") && selectedReport.contains("Not a safety approval"), "report documents source and numerical scope without approval")
check(VesselReport.text(project: selectedProject, german: true).contains("Keine Sicherheitsfreigabe"), "German comparison scope")
try VesselProjectArchive.save([selectedProject], to: url)
try check(VesselProjectArchive.load(from: url) == [selectedProject], "selection archive cold reload")
var damaged = try JSONSerialization.jsonObject(with: JSONEncoder().encode([selectedProject])) as! [[String: Any]]
var damagedScenarios = damaged[0]["scenarios"] as! [[String: Any]]
var damagedSelection = damagedScenarios[0]["selectedVessel"] as! [String: Any]; damagedSelection["nominalLiters"] = 0
damagedScenarios[0]["selectedVessel"] = damagedSelection; damaged[0]["scenarios"] = damagedScenarios
let damagedBytes = try JSONSerialization.data(withJSONObject: damaged); try damagedBytes.write(to: url)
rejects("invalid selected-capacity archive rejected") { _ = try VesselProjectArchive.load(from: url) }
rejects("invalid selection archive protected") { try VesselProjectArchive.save([selectedProject], to: url) }
try check(Data(contentsOf: url) == damagedBytes, "invalid selection bytes preserved")
let oldProjectBytes = try JSONEncoder().encode([project]); try oldProjectBytes.write(to: url)
try check(VesselProjectArchive.load(from: url)[0].scenarios[0].selectedVessel == nil, "legacy scenarios receive no fabricated selection")
try JSONSerialization.data(withJSONObject: ["version": 1, "projects": try JSONSerialization.jsonObject(with: oldProjectBytes)]).write(to: url)
try check(VesselProjectArchive.load(from: url) == [project], "old v1 envelope remains readable")
print("PASS MAG selected vessel: capacity boundaries, documented provenance, isolated scenario edits, archive migration and scope")
