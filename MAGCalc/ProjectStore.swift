import Foundation
import Observation

@MainActor @Observable
final class ProjectStore {
    private(set) var projects: [VesselProject] = []
    private(set) var loadFailed = false
    var errorMessage: String?
    private let url: URL

    init() {
        url = URL.applicationSupportDirectory.appendingPathComponent("MAGCalc", isDirectory: true)
            .appendingPathComponent("vessel-projects.json")
        reload()
    }
    func reload() {
        do {
            projects = try VesselProjectArchive.load(from: url)
            loadFailed = false; errorMessage = nil
        } catch {
            loadFailed = true
            errorMessage = String(localized: "projects.loadError")
        }
    }
    @discardableResult func save(_ project: VesselProject) -> Bool {
        var next = projects
        var updated = project; updated.updatedAt = Date()
        if let index = next.firstIndex(where: { $0.id == project.id }) { next[index] = updated }
        else { next.insert(updated, at: 0) }
        return persist(next)
    }
    @discardableResult func delete(id: UUID) -> Bool {
        persist(projects.filter { $0.id != id })
    }
    private func persist(_ next: [VesselProject]) -> Bool {
        guard !loadFailed else { errorMessage = String(localized: "projects.loadError"); return false }
        do { try VesselProjectArchive.save(next, to: url); projects = next; return true }
        catch { errorMessage = String(localized: "projects.saveError"); return false }
    }
}
