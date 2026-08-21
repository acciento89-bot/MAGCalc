package de.kamilunavo.magcalc

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val billing = remember { BillingManager(applicationContext) }
            MAGCalcRoot(this, billing)
        }
    }
}

private enum class Language { DE, EN }
private enum class TabMode { HEATING, PRESSURE, ADVANCED }

@Composable
private fun MAGCalcRoot(activity: Activity, billing: BillingManager) {
    var language by remember { mutableStateOf(Language.DE) }
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = TabMode.entries

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF4DD0E1),
            secondary = Color(0xFF66BB6A),
            background = Color(0xFF10151A),
            surface = Color(0xFF182027),
        )
    ) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header(language) { language = it }
                ScrollableTabRow(selectedTabIndex = selectedTab) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(tabTitle(tab, language)) },
                        )
                    }
                }

                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (tabs[selectedTab]) {
                        TabMode.HEATING -> HeatingCalculator(language)
                        TabMode.PRESSURE -> PressureCalculator(language)
                        TabMode.ADVANCED -> if (billing.isPro) AdvancedCalculator(language) else ProGate(language, activity, billing)
                    }

                    if (billing.isPro) {
                        Text(
                            if (language == Language.DE) "MAGCalc Pro aktiv" else "MAGCalc Pro active",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold,
                        )
                    } else {
                        OutlinedButton(onClick = billing::restorePurchases, modifier = Modifier.fillMaxWidth()) {
                            Text(if (language == Language.DE) "Käufe wiederherstellen" else "Restore purchases")
                        }
                    }
                    billing.statusMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    Text(
                        if (language == Language.DE)
                            "Rechenhilfe für Fachkräfte. Normen, Herstellerangaben, Sicherheitsbauteile und reale Messwerte haben Vorrang."
                        else
                            "Calculation aid for trained professionals. Standards, manufacturer data, safety components and actual measurements take precedence.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(language: Language, onLanguage: (Language) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        Text("MAGCalc", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
        Text(
            if (language == Language.DE) "MAG-Rechner für SHK-Profis" else "Expansion vessel calculator",
            color = Color.LightGray,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            if (language == Language.DE) Button(onClick = { onLanguage(Language.DE) }) { Text("DE") }
            else OutlinedButton(onClick = { onLanguage(Language.DE) }) { Text("DE") }
            if (language == Language.EN) Button(onClick = { onLanguage(Language.EN) }) { Text("EN") }
            else OutlinedButton(onClick = { onLanguage(Language.EN) }) { Text("EN") }
        }
    }
}

private fun tabTitle(tab: TabMode, language: Language): String = when (tab) {
    TabMode.HEATING -> if (language == Language.DE) "Heizung" else "Heating"
    TabMode.PRESSURE -> if (language == Language.DE) "Druck" else "Pressure"
    TabMode.ADVANCED -> if (language == Language.DE) "Erweitert" else "Advanced"
}

@Composable
private fun HeatingCalculator(language: Language) {
    var volume by remember { mutableStateOf("500") }
    var maxTemp by remember { mutableStateOf("80") }
    var staticHeight by remember { mutableStateOf("8") }
    var safetyPressure by remember { mutableStateOf("3") }
    val result = MAGCalculator.heating(volume.number(), maxTemp.number(), staticHeight.number(), safetyPressure.number())

    CalculatorCard(if (language == Language.DE) "Heizungs-MAG" else "Heating expansion vessel") {
        NumberField(if (language == Language.DE) "Anlagenvolumen l" else "System volume l", volume) { volume = it }
        NumberField(if (language == Language.DE) "Max. Temperatur °C" else "Max temperature °C", maxTemp) { maxTemp = it }
        NumberField(if (language == Language.DE) "Statische Höhe m" else "Static height m", staticHeight) { staticHeight = it }
        NumberField(if (language == Language.DE) "Sicherheitsventil bar" else "Safety valve bar", safetyPressure) { safetyPressure = it }

        if (result.isValid) {
            ResultLine(if (language == Language.DE) "Wasserausdehnung" else "Water expansion", "${fmt(result.expansionPercent)} %")
            ResultLine(if (language == Language.DE) "Ausdehnungsvolumen" else "Expansion volume", "${fmt(result.expansionLiters)} l")
            ResultLine(if (language == Language.DE) "Reserve" else "Reserve", "${fmt(result.reserveLiters)} l")
            ResultLine(if (language == Language.DE) "Erforderlich" else "Required", "${fmt(result.requiredLiters)} l")
            ResultLine(if (language == Language.DE) "Empfohlen" else "Recommended", "${fmt(result.recommendedLiters, 0)} l")
            ResultLine(if (language == Language.DE) "Vordruck" else "Pre-charge", "${fmt(result.prechargePressure)} bar")
            ResultLine(if (language == Language.DE) "Fülldruck" else "Fill pressure", "${fmt(result.fillPressure)} bar")
        } else {
            Text(if (language == Language.DE) "Eingaben prüfen: Enddruck muss über dem Fülldruck liegen." else "Check inputs: final pressure must be above fill pressure.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun PressureCalculator(language: Language) {
    var staticHeight by remember { mutableStateOf("8") }
    var safetyPressure by remember { mutableStateOf("3") }
    val result = MAGCalculator.pressure(staticHeight.number(), safetyPressure.number())

    CalculatorCard(if (language == Language.DE) "Druck-Assistent" else "Pressure assistant") {
        NumberField(if (language == Language.DE) "Statische Höhe m" else "Static height m", staticHeight) { staticHeight = it }
        NumberField(if (language == Language.DE) "Sicherheitsventil bar" else "Safety valve bar", safetyPressure) { safetyPressure = it }
        ResultLine(if (language == Language.DE) "Statischer Druck" else "Static pressure", "${fmt(result.staticPressure)} bar")
        ResultLine(if (language == Language.DE) "Vordruck" else "Pre-charge", "${fmt(result.prechargePressure)} bar")
        ResultLine(if (language == Language.DE) "Fülldruck" else "Fill pressure", "${fmt(result.fillPressure)} bar")
        ResultLine(if (language == Language.DE) "Empf. max. Enddruck" else "Recommended max pressure", "${fmt(result.recommendedMaxPressure)} bar")
        if (!result.isValid) {
            Text(if (language == Language.DE) "Druckverhältnisse sind nicht plausibel." else "Pressure conditions are not plausible.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun AdvancedCalculator(language: Language) {
    var volume by remember { mutableStateOf("500") }
    var expansion by remember { mutableStateOf("8") }
    var precharge by remember { mutableStateOf("1") }
    var finalPressure by remember { mutableStateOf("2,5") }
    var reserve by remember { mutableStateOf("1") }
    val result = MAGCalculator.advanced(volume.number(), expansion.number(), precharge.number(), finalPressure.number(), reserve.number())

    CalculatorCard(if (language == Language.DE) "Manuelle Ausdehnung" else "Manual expansion") {
        Text(
            if (language == Language.DE) "Für Glykol, Solarflüssigkeiten und Sondermedien: Ausdehnung aus Herstellerdaten eingeben."
            else "For glycol, solar fluids and special media: enter expansion from manufacturer data.",
            color = Color.LightGray,
        )
        NumberField(if (language == Language.DE) "Anlagenvolumen l" else "System volume l", volume) { volume = it }
        NumberField(if (language == Language.DE) "Ausdehnung %" else "Expansion %", expansion) { expansion = it }
        NumberField(if (language == Language.DE) "Vordruck bar" else "Pre-charge bar", precharge) { precharge = it }
        NumberField(if (language == Language.DE) "Enddruck bar" else "Final pressure bar", finalPressure) { finalPressure = it }
        NumberField(if (language == Language.DE) "Reserve %" else "Reserve %", reserve) { reserve = it }
        if (result.isValid) {
            ResultLine(if (language == Language.DE) "Ausdehnungsvolumen" else "Expansion volume", "${fmt(result.expansionLiters)} l")
            ResultLine(if (language == Language.DE) "Reserve" else "Reserve", "${fmt(result.reserveLiters)} l")
            ResultLine(if (language == Language.DE) "Erforderlich" else "Required", "${fmt(result.requiredLiters)} l")
            ResultLine(if (language == Language.DE) "Empfohlen" else "Recommended", "${fmt(result.recommendedLiters, 0)} l")
        } else {
            Text(if (language == Language.DE) "Eingaben prüfen." else "Check inputs.", color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun ProGate(language: Language, activity: Activity, billing: BillingManager) {
    CalculatorCard("MAGCalc Pro") {
        Text(
            if (language == Language.DE) "Manuelle Ausdehnungsberechnung für Glykol, Solarflüssigkeiten und Sondermedien dauerhaft freischalten. Einmaliger Kauf, kein Abo."
            else "Permanently unlock manual expansion sizing for glycol, solar fluids and special media. One-time purchase, no subscription."
        )
        Button(onClick = { billing.launchPurchase(activity) }, enabled = billing.billingReady, modifier = Modifier.fillMaxWidth()) {
            val price = billing.productPrice?.let { " · $it" } ?: ""
            Text(if (language == Language.DE) "Pro freischalten$price" else "Unlock Pro$price")
        }
        OutlinedButton(onClick = billing::restorePurchases, modifier = Modifier.fillMaxWidth()) {
            Text(if (language == Language.DE) "Käufe wiederherstellen" else "Restore purchases")
        }
    }
}

@Composable
private fun CalculatorCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { next -> if (next.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) onValueChange(next) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color.LightGray)
        Text(value, fontWeight = FontWeight.Bold)
    }
}

private fun String.number(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun fmt(value: Double, digits: Int = 2): String = String.format(Locale.GERMANY, "%.${digits}f", value)
