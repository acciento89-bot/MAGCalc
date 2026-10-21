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
