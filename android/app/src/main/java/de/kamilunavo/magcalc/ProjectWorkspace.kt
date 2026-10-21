package de.kamilunavo.magcalc

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.UUID

private class ProjectRepository(context: Context) {
    private val file = File(context.filesDir, "vessel-projects.json")
    var projects by mutableStateOf<List<VesselProject>>(emptyList()); private set
    var blocked by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    init { reload() }
    fun reload() {
        try { projects = VesselProjectArchive.load(file); blocked = false; error = null }
        catch (_: Exception) { blocked = true; error = "load" }
    }
    fun save(project: VesselProject): Boolean {
        val updated = project.copy(updatedAt = System.currentTimeMillis())
        val next = if (projects.any { it.id == project.id }) projects.map { if (it.id == project.id) updated else it }
                   else listOf(updated) + projects
        return persist(next)
    }
    fun delete(id: String) = persist(projects.filter { it.id != id })
    private fun persist(next: List<VesselProject>): Boolean {
        if (blocked) { error = "load"; return false }
        return try { VesselProjectArchive.save(next, file); projects = next; true }
               catch (_: Exception) { error = "save"; false }
    }
}

private class ProjectCopy(val german: Boolean) {
    fun t(en: String, de: String) = if (german) de else en
    val title get() = t("Saved projects", "Gespeicherte Projekte")
    val baseline get() = t("Baseline", "Referenz")
    val save get() = t("Save", "Speichern")
    val cancel get() = t("Cancel", "Abbrechen")
    val delete get() = t("Delete", "Löschen")
    val name get() = t("Name", "Name")
    val loadError get() = t("The project archive could not be read. The original file is preserved and saving is blocked. Retry after resolving the file or storage problem.", "Das Projektarchiv konnte nicht gelesen werden. Die Originaldatei bleibt erhalten; Speichern ist gesperrt. Nach Beheben des Datei- oder Speicherproblems erneut versuchen.")
    val saveError get() = t("The project could not be saved. Your previous saved data is unchanged. Check available storage and try again.", "Das Projekt konnte nicht gespeichert werden. Die zuvor gespeicherten Daten bleiben unverändert. Freien Speicher prüfen und erneut versuchen.")
    val required get() = t("Required vessel", "Erforderliches Gefäß")
    val recommended get() = t("Recommended vessel", "Empfohlene Gefäßgröße")
    fun number(value: Double, signed: Boolean = false): String =
        (if (signed && value > 0) "+" else "") + String.format(if (german) Locale.GERMANY else Locale.US, "%.2f", value)
}

@Composable
internal fun VesselProjectsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val copy = ProjectCopy(LocalConfiguration.current.locales[0].language == "de")
    val repository = remember(context.applicationContext) { ProjectRepository(context.applicationContext) }
    var route by rememberSaveable { mutableStateOf("list") }
    var selectedID by rememberSaveable { mutableStateOf<String?>(null) }
    var scenarioID by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingID by rememberSaveable { mutableStateOf<String?>(null) }
    var deletingScenarioID by rememberSaveable { mutableStateOf<String?>(null) }
    val selected = repository.projects.firstOrNull { it.id == selectedID }
    val scenario = selected?.scenarios?.firstOrNull { it.id == scenarioID }
    fun back() { route = when (route) { "info", "scenario" -> "detail"; "detail", "new" -> "list"; else -> { onBack(); "list" } } }
    BackHandler { back() }
    val title = when (route) {
        "new", "info" -> copy.t("Installation details", "Anlagendetails")
        "scenario" -> copy.t("Edit scenario", "Szenario bearbeiten")
        "detail" -> selected?.name ?: copy.title
        else -> copy.title
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())) {
            Column(Modifier.fillMaxWidth().widthIn(max = 720.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                TextButton(onClick = ::back) { Text("‹ " + copy.t("Back", "Zurück")) }
                Text(title, style = MaterialTheme.typography.headlineMedium)
                when {
                    route == "new" || (route == "info" && selected != null) -> key(route, selectedID) {
                        ProjectInfoForm(if (route == "new") null else selected, copy, {
                            if (repository.save(it)) { selectedID = it.id; route = "detail" }
                        }) { back() }
                    }
                    route == "scenario" && scenario != null && selected != null -> key(scenario.id) {
                        ScenarioForm(scenario, copy, { edited ->
                            val updated = selected.copy(scenarios = selected.scenarios.map { if (it.id == edited.id) edited else it })
                            if (repository.save(updated)) route = "detail"
                        }) { back() }
                    }
                    route == "detail" && selected != null -> {
                        if (selected.notes.isNotBlank()) Text(selected.notes)
                        TextButton(onClick = { route = "info" }) { Text(copy.t("Installation details", "Anlagendetails")) }
                        TextButton(onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, VesselReport.text(selected, copy.german))
                                context.startActivity(Intent.createChooser(intent, copy.t("Share calculation record", "Berechnungsprotokoll teilen")))
                            } catch (_: Exception) { repository.error = "share" }
                        }) { Text(copy.t("Share calculation record", "Berechnungsprotokoll teilen")) }
                        Text(copy.t("Water-sizing scenarios", "Wasser-Auslegungsszenarien"), style = MaterialTheme.typography.titleLarge)
                        selected.scenarios.forEach { s ->
                            key(s.id) {
                                ScenarioCard(s, selected.baseline, copy)
                                TextButton(onClick = { scenarioID = s.id; route = "scenario" }) { Text(copy.t("Edit scenario", "Szenario bearbeiten")) }
                                TextButton(onClick = {
                                    val duplicated = selected.duplicateScenario(s.id)
                                    val last = duplicated.scenarios.last()
                                    repository.save(duplicated.copy(scenarios = duplicated.scenarios.map {
                                        if (it.id == last.id) it.copy(name = it.name + " · " + copy.t("Copy", "Kopie")) else it
                                    }))
                                }) { Text(copy.t("Duplicate as alternative", "Als Alternative duplizieren")) }
                                if (s.id != selected.baselineID) {
                                    TextButton(onClick = { repository.save(selected.copy(baselineID = s.id)) }) { Text(copy.t("Use as baseline", "Als Referenz verwenden")) }
                                }
                                if (selected.scenarios.size > 1) {
                                    TextButton(onClick = { deletingScenarioID = s.id }) { Text(copy.t("Delete scenario", "Szenario löschen"), color = MaterialTheme.colorScheme.error) }
                                }
                                HorizontalDivider()
                            }
                        }
                        Text(copy.t("Complete calculation record", "Vollständiges Berechnungsprotokoll"), style = MaterialTheme.typography.titleLarge)
                        SelectionContainer { Text(VesselReport.text(selected, copy.german), style = MaterialTheme.typography.bodySmall) }
                    }
                    else -> {
                        Text(copy.t("Keep named installation worksheets on this device. Compare water-sizing scenarios against a baseline; Pro manual-media calculations remain in the advanced calculator.", "Benannte Anlagenprojekte auf diesem Gerät speichern. Wasser-Szenarien mit einer Referenz vergleichen; manuelle Medienberechnungen mit Pro bleiben im erweiterten Rechner."))
                        if (repository.blocked) {
                            Text(copy.loadError, color = MaterialTheme.colorScheme.error)
                            Button(onClick = { repository.reload() }) { Text(copy.t("Retry loading", "Erneut laden")) }
                        }
                        Button(onClick = { selectedID = null; route = "new" }, enabled = !repository.blocked) { Text(copy.t("New project", "Neues Projekt")) }
                        if (repository.projects.isEmpty() && !repository.blocked) {
                            Text(copy.t("No saved projects. Create an installation, then duplicate its baseline to compare alternatives.", "Keine gespeicherten Projekte. Eine Anlage erstellen und ihre Referenz duplizieren, um Alternativen zu vergleichen."))
                        }
                        repository.projects.forEach { project ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(project.name, style = MaterialTheme.typography.titleLarge)
                                    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(if (copy.german) Locale.GERMANY else Locale.US).withZone(ZoneId.systemDefault())
                                    Text(formatter.format(Instant.ofEpochMilli(project.updatedAt)), style = MaterialTheme.typography.bodySmall)
                                    if (project.notes.isNotBlank()) Text(project.notes)
                                    TextButton(onClick = { selectedID = project.id; route = "detail" }) { Text(copy.t("Open project", "Projekt öffnen")) }
                                    TextButton(onClick = { deletingID = project.id }) { Text(copy.delete, color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (deletingID != null || deletingScenarioID != null) {
        val deletingProject = deletingID != null
        AlertDialog(onDismissRequest = { deletingID = null; deletingScenarioID = null },
            title = { Text(copy.delete) },
            text = { Text(if (deletingProject) copy.t("Delete this installation and all its scenarios?", "Diese Anlage mit allen Szenarien löschen?") else copy.t("Delete this scenario? A deleted baseline is replaced by the first remaining scenario.", "Dieses Szenario löschen? Eine gelöschte Referenz wird durch das erste verbleibende Szenario ersetzt.")) },
            confirmButton = { TextButton(onClick = {
                if (deletingProject) deletingID?.let { repository.delete(it) }
                else if (selected != null) deletingScenarioID?.let { repository.save(selected.removeScenario(it)) }
                deletingID = null; deletingScenarioID = null
            }) { Text(copy.delete) } },
            dismissButton = { TextButton(onClick = { deletingID = null; deletingScenarioID = null }) { Text(copy.cancel) } })
    }
    repository.error?.let { error ->
        AlertDialog(onDismissRequest = { repository.error = null }, title = { Text(copy.t("Project storage", "Projektspeicher")) },
            text = { Text(when (error) { "load" -> copy.loadError; "share" -> copy.t("No sharing app is available. Select and copy the calculation record instead.", "Keine App zum Teilen verfügbar. Das Berechnungsprotokoll stattdessen markieren und kopieren."); else -> copy.saveError }) },
            confirmButton = { TextButton(onClick = { repository.error = null }) { Text(copy.t("Done", "Fertig")) } })
    }
}

@Composable
private fun ColumnScope.ProjectInfoForm(project: VesselProject?, copy: ProjectCopy, onSave: (VesselProject) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(project?.name ?: copy.t("New installation", "Neue Anlage")) }
    var notes by rememberSaveable { mutableStateOf(project?.notes ?: "") }
    val id = rememberSaveable { project?.id ?: UUID.randomUUID().toString() }
    val initialScenarioID = rememberSaveable { UUID.randomUUID().toString() }
    val createdAt = rememberSaveable { System.currentTimeMillis() }
    OutlinedTextField(name, { name = it }, label = { Text(copy.name) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(notes, { notes = it }, label = { Text(copy.t("Installation notes", "Anlagennotizen")) },
        placeholder = { Text(copy.t("Location, equipment and manufacturer limits to verify", "Standort, Komponenten und zu prüfende Herstellergrenzen")) },
        modifier = Modifier.fillMaxWidth(), minLines = 3)
    Button(onClick = {
        onSave(project?.copy(name = name.trim(), notes = notes) ?: VesselProject(id = id, name = name.trim(), notes = notes, createdAt = createdAt,
            scenarios = listOf(VesselScenario(name = copy.baseline, id = initialScenarioID))))
    }, enabled = name.isNotBlank()) { Text(copy.save) }
    TextButton(onClick = onCancel) { Text(copy.cancel) }
}

@Composable
private fun ColumnScope.ScenarioForm(scenario: VesselScenario, copy: ProjectCopy, onSave: (VesselScenario) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(scenario.name) }
    var notes by rememberSaveable { mutableStateOf(scenario.notes) }
    var volume by rememberSaveable { mutableStateOf(scenario.systemVolume.toString()) }
    var temperature by rememberSaveable { mutableStateOf(scenario.maximumTemperature.toString()) }
    var height by rememberSaveable { mutableStateOf(scenario.staticHeight.toString()) }
    var valve by rememberSaveable { mutableStateOf(scenario.safetyValvePressure.toString()) }
    fun value(text: String) = text.trim().replace(',', '.').toDoubleOrNull() ?: Double.NaN
    val edited = scenario.copy(name = name.trim(), notes = notes, systemVolume = value(volume), maximumTemperature = value(temperature),
        staticHeight = value(height), safetyValvePressure = value(valve))
    OutlinedTextField(name, { name = it }, label = { Text(copy.name) }, modifier = Modifier.fillMaxWidth())
    ProjectNumber(copy.t("System volume", "Anlagenvolumen"), "l", volume) { volume = it }
    ProjectNumber(copy.t("Maximum temperature", "Maximaltemperatur"), "°C", temperature) { temperature = it }
    ProjectNumber(copy.t("Static height", "Statische Höhe"), "m", height) { height = it }
    ProjectNumber(copy.t("Safety valve pressure", "Sicherheitsventildruck"), "bar", valve) { valve = it }
    OutlinedTextField(notes, { notes = it }, label = { Text(copy.t("Input sources and assumptions", "Eingabequellen und Annahmen")) },
        placeholder = { Text(copy.t("Measurement date, volume source, manufacturer data and assumptions", "Messdatum, Volumenquelle, Herstellerdaten und Annahmen")) },
        modifier = Modifier.fillMaxWidth(), minLines = 3)
    Text(copy.t("Water only, reference temperature 10 °C; maximum temperature 10–120 °C. Verify manufacturer limits and applicable requirements. This is a sizing aid, not an approval certificate.",
        "Nur Wasser, Referenztemperatur 10 °C; Maximaltemperatur 10–120 °C. Herstellergrenzen und geltende Anforderungen prüfen. Auslegungshilfe, keine Freigabebescheinigung."), style = MaterialTheme.typography.bodySmall)
    if (!edited.isValid) Text(copy.t("Enter a name, positive system volume, 10–120 °C, nonnegative height and a safety pressure that leaves a valid pressure window.",
        "Name, positives Anlagenvolumen, 10–120 °C, nichtnegative Höhe und einen Sicherheitsdruck mit gültigem Druckfenster eingeben."), color = MaterialTheme.colorScheme.error)
    else { Text(copy.recommended); Text(copy.number(edited.result.recommendedLiters) + " l", style = MaterialTheme.typography.titleLarge) }
    Button(onClick = { onSave(edited) }, enabled = edited.isValid) { Text(copy.save) }
    TextButton(onClick = onCancel) { Text(copy.cancel) }
}

@Composable
private fun ProjectNumber(label: String, unit: String, value: String, onChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    OutlinedTextField(value, onChange, label = { Text(label) }, suffix = { Text(unit) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }))
}

@Composable
private fun ScenarioCard(scenario: VesselScenario, baseline: VesselScenario?, copy: ProjectCopy) {
    val configuration = LocalConfiguration.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(scenario.name, style = MaterialTheme.typography.titleLarge)
            if (scenario.id == baseline?.id) Text(copy.baseline, color = MaterialTheme.colorScheme.primary)
            if (scenario.notes.isNotBlank()) Text(scenario.notes, style = MaterialTheme.typography.bodySmall)
            Text(copy.t("System volume", "Anlagenvolumen") + ": ${copy.number(scenario.systemVolume)} l")
            Text(copy.t("Maximum temperature", "Maximaltemperatur") + ": ${copy.number(scenario.maximumTemperature)} °C")
            Text(copy.t("Static height", "Statische Höhe") + ": ${copy.number(scenario.staticHeight)} m")
            Text(copy.t("Safety valve pressure", "Sicherheitsventildruck") + ": ${copy.number(scenario.safetyValvePressure)} bar")
            Text(copy.required + ": ${copy.number(scenario.result.requiredLiters)} l")
            Text(copy.recommended + ": ${copy.number(scenario.result.recommendedLiters)} l")
            if (baseline != null) {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 360.dp || configuration.fontScale > 1.4f) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ComparisonValues(copy.baseline, baseline, copy)
                            ComparisonValues(copy.t("Scenario", "Szenario"), scenario, copy)
                        }
                    } else Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f)) { ComparisonValues(copy.baseline, baseline, copy) }
                        Column(Modifier.weight(1f)) { ComparisonValues(copy.t("Scenario", "Szenario"), scenario, copy) }
                    }
                }
                Text(copy.t("Required volume difference from baseline", "Differenz zur Referenz: erforderliches Volumen") + ": ${copy.number(scenario.result.requiredLiters - baseline.result.requiredLiters, true)} l")
                Text(copy.t("Recommended size difference from baseline", "Differenz zur Referenz: empfohlene Gefäßgröße") + ": ${copy.number(scenario.result.recommendedLiters - baseline.result.recommendedLiters, true)} l")
            }
        }
    }
}

@Composable
private fun ComparisonValues(title: String, scenario: VesselScenario, copy: ProjectCopy) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(copy.required + ": ${copy.number(scenario.result.requiredLiters)} l")
        Text(copy.recommended + ": ${copy.number(scenario.result.recommendedLiters)} l")
    }
}
