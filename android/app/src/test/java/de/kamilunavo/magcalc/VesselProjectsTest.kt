package de.kamilunavo.magcalc

import org.junit.Assert.*
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule
import java.io.File
import org.json.JSONObject

class VesselProjectsTest {
    @get:Rule val folder = TemporaryFolder()
    private fun rejects(action: () -> Unit) {
        var rejected = false
        try { action() } catch (_: Exception) { rejected = true }
        assertTrue("Expected invalid archive to be rejected", rejected)
    }
    @Test fun defaultWaterScenarioMatchesSwiftAndKnownDensityCase() {
        val s = VesselScenario(name = "Existing", notes = "Measured onsite")
        assertTrue(s.isValid)
        assertEquals(8.61288330932295, s.result.expansionLiters, 0.000001)
        assertEquals(35.0, s.result.recommendedLiters, 0.0)
    }
    @Test fun invalidWaterInputsCannotBeSavedAsValid() {
        val s = VesselScenario(name = "Test")
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0, 0.0)) {
            assertFalse(s.copy(systemVolume = value).isValid)
        }
        assertFalse(s.copy(maximumTemperature = 121.0).isValid)
        assertFalse(s.copy(maximumTemperature = 9.0).isValid)
        assertFalse(s.copy(staticHeight = -1.0).isValid)
        assertFalse(s.copy(safetyValvePressure = 1.0).isValid)
        assertFalse(s.copy(name = "  ").isValid)
    }
    @Test fun duplicationAndBaselineDeletionKeepValidProject() {
        val s = VesselScenario(name = "Existing")
        val original = VesselProject(name = "Boiler room", scenarios = listOf(s))
        val duplicated = original.duplicateScenario(s.id)
        assertEquals(2, duplicated.scenarios.size)
        assertNotEquals(s.id, duplicated.scenarios.last().id)
        assertEquals(s.systemVolume, duplicated.scenarios.last().systemVolume, 0.0)
        val removed = duplicated.copy(baselineID = duplicated.scenarios.last().id).removeScenario(duplicated.scenarios.last().id)
        assertEquals(s.id, removed.baselineID)
        assertEquals(removed, removed.removeScenario(s.id))
        assertTrue(removed.isValid)
    }
    @Test fun reportsContainCompleteSourcesUnitsAndBothLanguages() {
        val p = VesselProject(name = "Boiler", notes = "Installer source", scenarios = listOf(VesselScenario(name = "Existing", notes = "Measured onsite")))
        val en = VesselReport.text(p, false)
        assertTrue(en.contains("Installer source") && en.contains("Measured onsite"))
        assertTrue(en.contains("300") && en.contains("10") && en.contains("bar") && en.contains("Acceptance"))
        assertTrue(en.contains("Difference") && en.contains("MAGCalc"))
        assertTrue(VesselReport.text(p, true).contains("Berechnungsprotokoll"))
    }
    @Test fun archiveRoundtripMissingFileAndLegacyArray() {
        val file = File(folder.root, "projects.json")
        assertTrue(VesselProjectArchive.load(file).isEmpty())
        val projects = listOf(VesselProject(name = "Boiler", scenarios = listOf(VesselScenario(name = "Existing"))))
        VesselProjectArchive.save(projects, file)
        assertEquals(projects, VesselProjectArchive.load(file))
        file.writeText(JSONObject(file.readText()).getJSONArray("projects").toString())
        assertEquals(projects, VesselProjectArchive.load(file))
    }
    @Test fun malformedUnknownAndCorruptArchiveCannotBeOverwritten() {
        val file = File(folder.root, "projects.json")
        val p = VesselProject(name = "Boiler", scenarios = listOf(VesselScenario(name = "Existing")))
        for (text in listOf("broken", "{\"version\":99,\"projects\":[]}")) {
            file.writeText(text)
            rejects { VesselProjectArchive.load(file) }
            rejects { VesselProjectArchive.save(listOf(p), file) }
            assertEquals(text, file.readText())
        }
        file.delete()
        rejects { VesselProjectArchive.save(listOf(p.copy(baselineID = "missing")), file) }
        rejects { VesselProjectArchive.save(listOf(p.copy(scenarios = p.scenarios + p.scenarios)), file) }
    }
}
