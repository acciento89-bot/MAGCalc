package de.kamilunavo.magcalc

import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant
import java.util.Locale
import java.util.UUID

data class VesselScenario(
    val name: String,
    val notes: String = "",
    val id: String = UUID.randomUUID().toString(),
    val systemVolume: Double = 300.0,
    val maximumTemperature: Double = 80.0,
    val staticHeight: Double = 8.0,
    val safetyValvePressure: Double = 3.0,
) {
    val result get() = MAGCalculator.heating(systemVolume, maximumTemperature, staticHeight, safetyValvePressure)
    val isValid get() = name.isNotBlank() && id.isNotBlank() &&
        listOf(systemVolume, maximumTemperature, staticHeight, safetyValvePressure).all { it.isFinite() } &&
        systemVolume > 0 && maximumTemperature in 10.0..120.0 && staticHeight >= 0 && safetyValvePressure > 0 &&
        result.isValid && listOf(result.requiredLiters, result.recommendedLiters, result.fillPressure).all { it.isFinite() }
}

data class VesselProject(
    val name: String,
    val notes: String = "",
    val scenarios: List<VesselScenario>,
    val id: String = UUID.randomUUID().toString(),
    val baselineID: String = scenarios.firstOrNull()?.id ?: "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val baseline get() = scenarios.firstOrNull { it.id == baselineID }
    val isValid get() = name.isNotBlank() && id.isNotBlank() && scenarios.isNotEmpty() && baseline != null &&
        scenarios.all { it.isValid } && scenarios.map { it.id }.distinct().size == scenarios.size
    fun duplicateScenario(id: String): VesselProject {
        val source = scenarios.firstOrNull { it.id == id } ?: return this
        return copy(scenarios = scenarios + source.copy(id = UUID.randomUUID().toString()), updatedAt = System.currentTimeMillis())
    }
    fun removeScenario(id: String): VesselProject {
        if (scenarios.size <= 1 || scenarios.none { it.id == id }) return this
        val remaining = scenarios.filter { it.id != id }
        return copy(scenarios = remaining, baselineID = if (remaining.any { it.id == baselineID }) baselineID else remaining.first().id,
                    updatedAt = System.currentTimeMillis())
    }
}

object VesselProjectArchive {
    fun load(file: File): List<VesselProject> {
        if (!file.exists()) return emptyList()
        val parser = JSONTokener(file.readText(Charsets.UTF_8))
        val root = parser.nextValue()
        require(parser.nextClean() == '\u0000') { "Trailing archive data" }
        val array = when (root) {
            is JSONArray -> root
            is JSONObject -> { require(root.getInt("version") == 1) { "Unsupported archive version" }; root.getJSONArray("projects") }
            else -> error("Invalid project archive")
        }
        val projects = (0 until array.length()).map { decodeProject(array.getJSONObject(it)) }
        validate(projects)
        return projects
    }
    fun save(projects: List<VesselProject>, file: File) {
        validate(projects)
        load(file) // Never replace unknown or unreadable existing bytes.
        val data = JSONObject().put("version", 1).put("projects", JSONArray(projects.map(::encodeProject))).toString(2)
            .toByteArray(Charsets.UTF_8)
        val parent = file.absoluteFile.parentFile ?: error("Missing parent directory")
        require(parent.isDirectory || parent.mkdirs()) { "Storage directory unavailable" }
        val temporary = File.createTempFile("vessel-projects-", ".tmp", parent)
        try {
            FileOutputStream(temporary).use { it.write(data); it.fd.sync() }
            // Same-directory atomic replacement. Failure leaves the previous file intact.
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { temporary.delete() }
    }
    private fun validate(projects: List<VesselProject>) {
        require(projects.all { it.isValid } && projects.map { it.id }.distinct().size == projects.size) { "Invalid project identities or inputs" }
    }
    private fun encodeScenario(s: VesselScenario) = JSONObject().put("id", s.id).put("name", s.name).put("notes", s.notes)
        .put("systemVolume", s.systemVolume).put("maximumTemperature", s.maximumTemperature)
        .put("staticHeight", s.staticHeight).put("safetyValvePressure", s.safetyValvePressure)
    private fun encodeProject(p: VesselProject) = JSONObject().put("id", p.id).put("name", p.name).put("notes", p.notes)
        .put("baselineID", p.baselineID).put("createdAt", p.createdAt).put("updatedAt", p.updatedAt)
        .put("scenarios", JSONArray(p.scenarios.map(::encodeScenario)))
    private fun decodeScenario(j: JSONObject) = VesselScenario(id = j.getString("id"), name = j.getString("name"), notes = j.getString("notes"),
        systemVolume = j.getDouble("systemVolume"), maximumTemperature = j.getDouble("maximumTemperature"),
        staticHeight = j.getDouble("staticHeight"), safetyValvePressure = j.getDouble("safetyValvePressure"))
    private fun decodeProject(j: JSONObject): VesselProject {
        val array = j.getJSONArray("scenarios")
        return VesselProject(id = j.getString("id"), name = j.getString("name"), notes = j.getString("notes"),
            baselineID = j.getString("baselineID"), createdAt = j.getLong("createdAt"), updatedAt = j.getLong("updatedAt"),
            scenarios = (0 until array.length()).map { decodeScenario(array.getJSONObject(it)) })
    }
}

object VesselReport {
    fun text(project: VesselProject, german: Boolean): String {
        fun l(en: String, de: String) = if (german) de else en
        fun n(value: Double) = String.format(if (german) Locale.GERMANY else Locale.US, "%.3f", value)
        val lines = mutableListOf("MAGCalc — " + l("Calculation record", "Berechnungsprotokoll"), project.name,
            "ID: ${project.id}", l("Updated: ", "Aktualisiert: ") + Instant.ofEpochMilli(project.updatedAt),
            l("Installation notes: ", "Anlagennotizen: ") + project.notes,
            l("Baseline: ", "Referenz: ") + (project.baseline?.name ?: "—"))
        project.scenarios.forEach { s ->
            val r = s.result
            lines += listOf("", s.name + if (s.id == project.baselineID) l(" (baseline)", " (Referenz)") else "",
                "ID: ${s.id}", l("Input sources: ", "Eingabequellen: ") + s.notes,
                l("System volume", "Anlagenvolumen") + ": ${n(s.systemVolume)} l",
                l("Water temperatures", "Wassertemperaturen") + ": 10 °C → ${n(s.maximumTemperature)} °C",
                l("Static height", "Statische Höhe") + ": ${n(s.staticHeight)} m",
                l("Safety valve", "Sicherheitsventil") + ": ${n(s.safetyValvePressure)} bar",
                l("Expansion", "Ausdehnung") + ": ${n(r.expansionPercent)} % / ${n(r.expansionLiters)} l",
                "Reserve: ${n(r.reserveLiters)} l",
                l("Required vessel", "Erforderliches Gefäß") + ": ${n(r.requiredLiters)} l",
                l("Recommended vessel size", "Empfohlene Gefäßgröße") + ": ${n(r.recommendedLiters)} l",
                l("Static pressure", "Statischer Druck") + ": ${n(r.staticPressure)} bar",
                l("Pre-charge / fill / final pressure", "Vordruck / Füll- / Enddruck") + ": ${n(r.prechargePressure)} / ${n(r.fillPressure)} / ${n(r.finalPressure)} bar",
                l("Acceptance factor", "Aufnahmefaktor") + ": ${n(r.acceptanceFactor)}")
            project.baseline?.let { baseline ->
                lines += l("Difference from baseline (required / recommended)", "Differenz zur Referenz (erforderlich / empfohlen)") +
                    ": ${n(r.requiredLiters - baseline.result.requiredLiters)} / ${n(r.recommendedLiters - baseline.result.recommendedLiters)} l"
            }
        }
        lines += listOf("", l("Vessel selection: first adequate size from 8, 12, 18, 25, 35, 50, 80, 100, 140, 200, 250, 300, 400, 500, 600 l; larger requirements round upward to 100 l. Verify actual manufacturer availability.",
            "Gefäßauswahl: erste ausreichende Größe aus 8, 12, 18, 25, 35, 50, 80, 100, 140, 200, 250, 300, 400, 500, 600 l; größere Anforderungen auf 100 l aufrunden. Tatsächliche Herstellerverfügbarkeit prüfen."),
            l("Engine: MAGCalc heating water model, revision 1. Reference water 10 °C; tabulated density interpolation 10–120 °C. Gauge pressures in bar; absolute pressure = gauge + 1 bar.",
                "Rechenmodell: MAGCalc Heizungswasser, Revision 1. Wasserreferenz 10 °C; tabellierte Dichteinterpolation 10–120 °C. Überdruck in bar; Absolutdruck = Überdruck + 1 bar."),
            "Ve = V × (ρ10/ρT − 1); Vr = max(3 l, V × 0.005); p0 = max(0.5, H/10 + 0.2); pf = p0 + 0.3; pe = pSV − 0.5; A = (pe − p0)/(pe + 1); Vn = (Ve + Vr)/A.",
            l("Sizing aid for water only. Verify manufacturer limits, system conditions and applicable requirements with a qualified professional. No manufacturer approval or compliance certificate.",
                "Auslegungshilfe nur für Wasser. Herstellergrenzen, Anlagenbedingungen und geltende Anforderungen fachlich prüfen. Keine Herstellerfreigabe oder Konformitätsbescheinigung."))
        return lines.joinToString("\n")
    }
}
