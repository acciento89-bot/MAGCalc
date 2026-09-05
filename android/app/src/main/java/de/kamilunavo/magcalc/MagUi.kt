package de.kamilunavo.magcalc

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val BackgroundTop = Color(0xFF03090E)
private val BackgroundBottom = Color(0xFF051316)
private val Accent = Color(0xFF2ED1B3)
private val Accent2 = Color(0xFF4DEBCC)
private val TextPrimary = Color(0xFFF7FAFA)
private val Muted = Color(0xFFA4ADAE)
private val Line = Color(0xFF223033)
private val Panel = Color(0xFF101D20)
private val CardTop = Color(0xFF162326)
private val CardBottom = Color(0xFF0B181B)
private val Error = Color(0xFFFF7C7C)

private val MagColors = darkColorScheme(
    primary = Accent, onPrimary = Color(0xFF00251E), secondary = Accent2,
    background = BackgroundTop, onBackground = TextPrimary, surface = Panel,
    onSurface = TextPrimary, surfaceVariant = CardTop, onSurfaceVariant = Muted,
    outline = Line, error = Error,
)

private val MagTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 35.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 25.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
)

private enum class Screen { HOME, HEATING, PRESSURE, ADVANCED, SETTINGS, PAYWALL }
private enum class Symbol { VESSEL, GAUGE, DROP, RULER, UP, PERCENT, CHECK, PLUS, WARNING, SPARKLES, SUN, SNOW, INFINITY }

private class Copy(private val german: Boolean) {
    fun text(de: String, en: String) = if (german) de else en
    val done = text("Fertig", "Done")
    val homeTitle = text("Ausdehnungsgefäße schnell berechnen", "Size expansion vessels quickly")
    val homeSubtitle = text("MAG-Größe, Vordruck und Fülldruck direkt auf der Baustelle oder im Kundendienst abschätzen.", "Estimate vessel size, pre-charge and fill pressure directly on site or during service.")
    val heating = text("Heizungs-MAG", "Heating vessel")
    val heatingSubtitle = text("Gefäßgröße für wassergeführte Heizungsanlagen.", "Vessel sizing for water-based heating systems.")
    val pressure = text("Druck-Assistent", "Pressure assistant")
    val pressureSubtitle = text("Statische Höhe in Vordruck und Fülldruck umrechnen.", "Convert static height into pre-charge and fill pressure.")
    val advanced = text("Sondermedien & Solar", "Special fluids & solar")
    val advancedSubtitle = text("Manuelle Ausdehnung für Glykol, Solar und Sonderfälle.", "Manual expansion input for glycol, solar and special cases.")
    val disclaimer = text("Rechenhilfe für Fachkräfte. Herstellerangaben, geltende Normen, Sicherheitsbauteile und Messwerte der realen Anlage haben immer Vorrang.", "Calculation aid for trained professionals. Manufacturer data, applicable standards, safety components and actual system measurements always take precedence.")
}

@Composable
internal fun MAGCalcApp(activity: Activity, billing: BillingManager) {
    val configuration = LocalConfiguration.current
    val copy = remember(configuration.locales) { Copy(configuration.locales[0]?.language == Locale.GERMAN.language) }
    var screenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    var returnScreenName by rememberSaveable { mutableStateOf(Screen.HOME.name) }
    val screen = Screen.valueOf(screenName)
    fun navigate(target: Screen) { screenName = target.name }
    fun openPaywall(from: Screen) { returnScreenName = from.name; navigate(Screen.PAYWALL) }
    fun closeOverlay() { navigate(Screen.valueOf(returnScreenName)) }

    BackHandler(screen != Screen.HOME) {
        when (screen) { Screen.PAYWALL, Screen.SETTINGS -> closeOverlay(); else -> navigate(Screen.HOME) }
    }

    MaterialTheme(colorScheme = MagColors, typography = MagTypography) {
        AppBackground {
            when (screen) {
                Screen.HOME -> HomeScreen(copy, billing.isPro, { navigate(Screen.HEATING) }, { navigate(Screen.PRESSURE) }, { if (billing.isPro) navigate(Screen.ADVANCED) else openPaywall(Screen.HOME) }) { returnScreenName = Screen.HOME.name; navigate(Screen.SETTINGS) }
                Screen.HEATING -> HeatingScreen(copy) { navigate(Screen.HOME) }
                Screen.PRESSURE -> PressureScreen(copy) { navigate(Screen.HOME) }
                Screen.ADVANCED -> if (billing.isPro) AdvancedScreen(copy) { navigate(Screen.HOME) }
                    else PaywallScreen(copy, activity, billing) { navigate(Screen.HOME) }
                Screen.SETTINGS -> SettingsScreen(copy, billing, ::closeOverlay) { openPaywall(Screen.SETTINGS) }
                Screen.PAYWALL -> PaywallScreen(copy, activity, billing, ::closeOverlay)
            }
        }
    }
}

@Composable
private fun AppBackground(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(BackgroundTop, BackgroundBottom), Offset.Zero, Offset(1300f, 2100f)))) { content() }
}

@Composable
private fun ScreenFrame(title: String, onBack: (() -> Unit)? = null, trailingLabel: String? = null, onTrailing: (() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onBack != null) RoundAction("‹", "Back", onBack) else Spacer(Modifier.size(48.dp))
                Text(title, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (trailingLabel != null && onTrailing != null) TextButton(onClick = onTrailing, modifier = Modifier.height(48.dp)) { Text(trailingLabel, fontWeight = FontWeight.Bold) }
                else Spacer(Modifier.size(48.dp))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).imePadding(), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.fillMaxWidth().widthIn(max = 680.dp).padding(horizontal = 18.dp, vertical = 12.dp).padding(bottom = 30.dp), verticalArrangement = Arrangement.spacedBy(22.dp), content = content)
        }
    }
}

@Composable
private fun HomeScreen(copy: Copy, isPro: Boolean, onHeating: () -> Unit, onPressure: () -> Unit, onAdvanced: () -> Unit, onSettings: () -> Unit) {
    ScreenFrame("MAGCalc", trailingLabel = "⚙", onTrailing = onSettings) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                ExpansionVesselIcon(66.dp, true)
                Spacer(Modifier.weight(1f))
                if (isPro) Badge("MAGCalc Pro", Accent)
            }
            Text(copy.homeTitle, style = MaterialTheme.typography.displaySmall)
            Text(copy.homeSubtitle, style = MaterialTheme.typography.bodyLarge, color = Muted)
        }
        HomeCard(Symbol.VESSEL, copy.heating, copy.heatingSubtitle, "FREE", false, onHeating)
        HomeCard(Symbol.GAUGE, copy.pressure, copy.pressureSubtitle, "FREE", false, onPressure)
        HomeCard(Symbol.DROP, copy.advanced, copy.advancedSubtitle, "PRO", !isPro, onAdvanced)
        FormulaNote(copy.disclaimer)
    }
}

@Composable
private fun HomeCard(symbol: Symbol, title: String, subtitle: String, badge: String, locked: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Row(Modifier.fillMaxWidth().clip(shape).background(Brush.linearGradient(listOf(CardTop, CardBottom))).border(1.dp, Line, shape).clickable(role = Role.Button, onClick = onClick).padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (symbol == Symbol.VESSEL) ExpansionVesselIcon(48.dp) else SymbolTile(symbol)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f, fill = false))
                Badge(badge, if (locked) Muted else Accent)
            }
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Muted)
        }
        Text(if (locked) "●" else "›", color = Muted, fontSize = if (locked) 12.sp else 29.sp)
    }
}

@Composable
private fun HeatingScreen(copy: Copy, onBack: () -> Unit) {
    var volume by rememberSaveable { mutableStateOf("300") }; var temperature by rememberSaveable { mutableStateOf("80") }
    var height by rememberSaveable { mutableStateOf("8") }; var valve by rememberSaveable { mutableStateOf("3") }
    val result = MAGCalculator.heating(volume.number(), temperature.number(), height.number(), valve.number())
    CalculatorFrame(copy.heating, copy.text("Schnelle Dimensionierung für Wasser als Anlagenmedium.", "Quick sizing for water as the system fluid."), onBack) {
        InputCard {
            NumberInputRow(copy.text("Anlagenvolumen", "System volume"), "l", volume) { volume = it }
            NumberInputRow(copy.text("Max. Temperatur", "Max. temperature"), "°C", temperature) { temperature = it }
            NumberInputRow(copy.text("Statische Höhe", "Static height"), "m", height) { height = it }
            NumberInputRow(copy.text("Sicherheitsventil", "Safety valve"), "bar", valve) { valve = it }
        }
        if (result.isValid) {
            ResultCard(Symbol.CHECK, copy.text("Nächste Gefäßgröße", "Next vessel size"), fmt(result.recommendedLiters, 0), "l", true)
            ResultCard(Symbol.RULER, copy.text("Erforderliches Volumen", "Required volume"), fmt(result.requiredLiters), "l")
            ResultCard(Symbol.UP, copy.text("Wasserausdehnung", "Water expansion"), fmt(result.expansionLiters), "l")
            ResultCard(Symbol.PERCENT, copy.text("Ausdehnung", "Expansion"), fmt(result.expansionPercent), "%")
            ResultCard(Symbol.GAUGE, copy.text("Empf. Vordruck", "Suggested pre-charge"), fmt(result.prechargePressure), "bar")
            ResultCard(Symbol.DROP, copy.text("Empf. Fülldruck", "Suggested fill pressure"), fmt(result.fillPressure), "bar")
        } else FormulaNote(copy.text("Die eingegebenen Druckwerte lassen kein sinnvolles Arbeitsfenster zu. Statische Höhe, Sicherheitsventil und Druckvorgaben prüfen.", "The entered pressure values do not provide a usable operating window. Check static height, safety valve and pressure settings."), true)
        FormulaNote(copy.text("Die Wasserausdehnung wird aus einer Dichtetabelle ab 10 °C angenähert. Vordruck, Fülldruck und Druckreserve sind praxisorientierte Rechenwerte und müssen an die reale Anlage angepasst werden.", "Water expansion is approximated from a density table starting at 10 °C. Pre-charge, fill pressure and pressure margins are practical calculation values and must be adapted to the real system."))
    }
}

@Composable
private fun PressureScreen(copy: Copy, onBack: () -> Unit) {
    var height by rememberSaveable { mutableStateOf("8") }; var valve by rememberSaveable { mutableStateOf("3") }
    val result = MAGCalculator.pressure(height.number(), valve.number())
    CalculatorFrame(copy.pressure, copy.text("Statische Höhe und Sicherheitsventil als schnelle Druckkontrolle.", "Quick pressure check from static height and safety valve setting."), onBack) {
        InputCard { NumberInputRow(copy.text("Statische Höhe", "Static height"), "m", height) { height = it }; NumberInputRow(copy.text("Sicherheitsventil", "Safety valve"), "bar", valve) { valve = it } }
        ResultCard(Symbol.RULER, copy.text("Statischer Druck", "Static pressure"), fmt(result.staticPressure), "bar")
        ResultCard(Symbol.GAUGE, copy.text("Empf. Vordruck", "Suggested pre-charge"), fmt(result.prechargePressure), "bar")
        ResultCard(Symbol.DROP, copy.text("Empf. Fülldruck", "Suggested fill pressure"), fmt(result.fillPressure), "bar")
        ResultCard(Symbol.WARNING, copy.text("Empf. max. Druck", "Suggested max pressure"), fmt(result.recommendedMaxPressure), "bar")
        if (!result.isValid) FormulaNote(copy.text("Die eingegebenen Werte ergeben kein sinnvolles Arbeitsfenster. Bitte Eingaben prüfen.", "The entered values do not provide a usable operating window. Please check the inputs."), true)
        FormulaNote(copy.text("Faustwert: 10 m statische Höhe entsprechen etwa 1 bar. Der vorgeschlagene Vordruck liegt 0,2 bar über dem statischen Druck, der Fülldruck nochmals 0,3 bar darüber.", "Rule of thumb: 10 m static height is approximately 1 bar. Suggested pre-charge is 0.2 bar above static pressure, with fill pressure another 0.3 bar higher."))
    }
}

@Composable
private fun AdvancedScreen(copy: Copy, onBack: () -> Unit) {
    var volume by rememberSaveable { mutableStateOf("300") }; var expansion by rememberSaveable { mutableStateOf("4") }
    var precharge by rememberSaveable { mutableStateOf("1") }; var finalPressure by rememberSaveable { mutableStateOf("2,5") }; var reserve by rememberSaveable { mutableStateOf("0,5") }
    val result = MAGCalculator.advanced(volume.number(), expansion.number(), precharge.number(), finalPressure.number(), reserve.number())
    CalculatorFrame(copy.advanced, copy.text("MAG-Berechnung mit frei vorgegebener Volumenausdehnung.", "Vessel sizing with a freely specified volumetric expansion."), onBack) {
        InputCard {
            NumberInputRow(copy.text("Anlagenvolumen", "System volume"), "l", volume) { volume = it }
            NumberInputRow(copy.text("Volumenausdehnung", "Volume expansion"), "%", expansion) { expansion = it }
            NumberInputRow(copy.text("Vordruck p₀", "Pre-charge p₀"), "bar", precharge) { precharge = it }
            NumberInputRow(copy.text("Enddruck", "Final pressure"), "bar", finalPressure) { finalPressure = it }
            NumberInputRow(copy.text("Wasserreserve", "Water reserve"), "%", reserve) { reserve = it }
        }
        if (result.isValid) {
            ResultCard(Symbol.CHECK, copy.text("Nächste Gefäßgröße", "Next vessel size"), fmt(result.recommendedLiters, 0), "l", true)
            ResultCard(Symbol.RULER, copy.text("Erforderliches Volumen", "Required volume"), fmt(result.requiredLiters), "l")
            ResultCard(Symbol.UP, copy.text("Ausdehnungsvolumen", "Expansion volume"), fmt(result.expansionLiters), "l")
            ResultCard(Symbol.PLUS, copy.text("Reservevolumen", "Reserve volume"), fmt(result.reserveLiters), "l")
        } else FormulaNote(copy.text("Bitte prüfen: Anlagenvolumen und Ausdehnung müssen größer als 0 sein und der Enddruck muss über dem Vordruck liegen.", "Please check the inputs: system volume and expansion must be above 0 and final pressure must exceed pre-charge."), true)
        FormulaNote(copy.text("Für Glykol, Solarflüssigkeiten und Sondermedien die Volumenausdehnung aus den Herstellerdaten übernehmen. Bei Solaranlagen müssen zusätzlich Kollektor-, Dampf- und Stagnationsbedingungen separat berücksichtigt werden.", "For glycol, solar fluids and special media, enter volumetric expansion from manufacturer data. Solar systems also require separate consideration of collector, vapour and stagnation conditions."))
    }
}

@Composable
private fun CalculatorFrame(title: String, subtitle: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ScreenFrame("MAGCalc", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium)
            Text(subtitle, color = Muted, style = MaterialTheme.typography.bodyLarge)
        }
        content()
    }
}

@Composable
private fun InputCard(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = CardBottom), border = BorderStroke(1.dp, Line)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

@Composable
private fun NumberInputRow(label: String, unit: String, value: String, onValueChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = value,
            onValueChange = { next -> if (next.all { it.isDigit() || it == ',' || it == '.' || it == '-' }) onValueChange(next) },
            modifier = Modifier.width(96.dp), singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }), shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Accent, unfocusedBorderColor = Color.Transparent, focusedContainerColor = Color(0xFF071113), unfocusedContainerColor = Color(0xFF071113), cursorColor = Accent),
        )
        Text(unit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = Muted, modifier = Modifier.width(45.dp))
    }
}

@Composable
private fun ResultCard(symbol: Symbol, title: String, value: String, unit: String, prominent: Boolean = false) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().background(if (prominent) Color(0xFF102925) else Panel, shape)
            .then(if (prominent) Modifier.border(1.dp, Accent.copy(alpha = .35f), shape) else Modifier).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SymbolTile(symbol, 36.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = Muted)
            Text(value, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
        }
        Text(unit, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Muted)
    }
}

@Composable
private fun FormulaNote(text: String, error: Boolean = false) {
    Row(Modifier.fillMaxWidth().background(if (error) Error.copy(alpha = .07f) else Panel, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        Text(if (error) "!" else "i", color = if (error) Error else Accent, fontWeight = FontWeight.Black)
        Text(text, style = MaterialTheme.typography.bodySmall, color = if (error) Error else Muted, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun PaywallScreen(copy: Copy, activity: Activity, billing: BillingManager, onClose: () -> Unit) {
    if (billing.isPro) { LaunchedEffect(Unit) { onClose() }; return }
    ScreenFrame("MAGCalc Pro", onClose) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExpansionVesselIcon(92.dp, true); Spacer(Modifier.height(4.dp))
            Text("MAGCalc Pro", style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
            Text(copy.text("Erweiterte MAG-Berechnung dauerhaft freischalten.", "Permanently unlock advanced expansion-vessel sizing."), color = Muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            FeatureRow(Symbol.DROP, copy.text("Freie Volumenausdehnung für Sondermedien", "Custom volumetric expansion for special fluids"))
            FeatureRow(Symbol.SUN, copy.text("Auslegungshilfe für Solar-Sonderfälle", "Sizing aid for solar special cases"))
            FeatureRow(Symbol.SNOW, copy.text("Für Glykol mit Herstellerwerten nutzbar", "Usable for glycol with manufacturer data"))
            FeatureRow(Symbol.INFINITY, copy.text("Einmal kaufen, dauerhaft freigeschaltet", "One-time purchase, permanently unlocked"))
        }
        Button(onClick = { billing.launchPurchase(activity) }, enabled = billing.billingReady, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(17.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF00251E), disabledContainerColor = Accent.copy(alpha = .35f))) {
            Text(billing.productPrice ?: copy.text("Pro freischalten", "Unlock Pro"), fontWeight = FontWeight.Bold)
        }
        TextButton(onClick = billing::restorePurchases, modifier = Modifier.align(Alignment.CenterHorizontally)) { Text(copy.text("Käufe wiederherstellen", "Restore purchases"), fontWeight = FontWeight.SemiBold) }
        billing.statusMessage?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
        Text(copy.text("Einmaliger In-App-Kauf. Kein Abo.", "One-time In-App Purchase. No subscription."), color = Muted, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun FeatureRow(symbol: Symbol, text: String) {
    Row(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(16.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp)) {
        MiniSymbol(symbol, Modifier.size(30.dp)); Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge); Text("✓", color = Accent, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SettingsScreen(copy: Copy, billing: BillingManager, onDone: () -> Unit, onUnlock: () -> Unit) {
    val context = LocalContext.current
    ScreenFrame(copy.text("Einstellungen", "Settings"), trailingLabel = copy.done, onTrailing = onDone) {
        SettingsSection {
            SettingsRow(copy.text("Pro-Status", "Pro status"), if (billing.isPro) copy.text("Aktiv", "Active") else "Free", billing.isPro)
            if (!billing.isPro) SettingsButton(copy.text("MAGCalc Pro freischalten", "Unlock MAGCalc Pro"), onUnlock)
            SettingsButton(copy.text("Käufe wiederherstellen", "Restore purchases"), billing::restorePurchases)
        }
        SectionLabel(copy.text("Links", "Links"))
        SettingsSection {
            SettingsButton(copy.text("Support", "Support")) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kamilunavo.com/support"))) }
            SettingsButton(copy.text("Datenschutz", "Privacy")) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://kamilunavo.com/magcalc/privacy"))) }
        }
        SectionLabel(copy.text("Über MAGCalc", "About MAGCalc"))
        SettingsSection { SettingsRow(copy.text("Version", "Version"), "1.0.2"); SettingsRow(copy.text("Entwickler", "Developer"), "Kamilunavo") }
        FormulaNote(copy.text("MAGCalc ersetzt keine fachgerechte Planung, Herstellerunterlagen oder die Prüfung nach den für die Anlage geltenden Regeln.", "MAGCalc does not replace professional design, manufacturer documentation or checks required by the rules applicable to the system."))
        billing.statusMessage?.let { Text(it, color = Error, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SettingsSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(18.dp)).border(1.dp, Line, RoundedCornerShape(18.dp)).padding(horizontal = 16.dp), content = content)
}

@Composable
private fun SettingsRow(label: String, value: String, highlighted: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Text(value, color = if (highlighted) Accent else Muted, fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun SettingsButton(label: String, onClick: () -> Unit) {
    Text(label, color = Accent, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick).padding(vertical = 16.dp))
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), style = MaterialTheme.typography.bodySmall, color = Muted, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp))
}

@Composable
private fun Badge(label: String, color: Color) {
    Surface(color = Color.White.copy(alpha = .06f), shape = CircleShape) { Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) }
}

@Composable
private fun RoundAction(label: String, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = description }, contentAlignment = Alignment.Center) { Text(label, color = Accent, fontSize = 34.sp, fontWeight = FontWeight.Light) }
}

@Composable
private fun ExpansionVesselIcon(iconSize: Dp, showsBadge: Boolean = false) {
    Box(Modifier.size(iconSize).background(Accent.copy(alpha = .12f), RoundedCornerShape(iconSize * .24f)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width; val h = size.height
            drawRoundRect(Color.White.copy(alpha = .72f), Offset(w * .43f, h * .17f), Size(w * .14f, h * .11f), CornerRadius(w * .07f))
            drawRoundRect(Brush.linearGradient(listOf(Color(0xFFE0332E), Color(0xFF9E1414))), Offset(w * .26f, h * .25f), Size(w * .48f, h * .57f), CornerRadius(w * .11f))
            drawLine(Color.White.copy(alpha = .72f), Offset(w * .31f, h * .54f), Offset(w * .69f, h * .54f), (w * .025f).coerceAtLeast(1.5f))
            drawRoundRect(Color.White.copy(alpha = .22f), Offset(w * .34f, h * .34f), Size(w * .055f, h * .27f), CornerRadius(w * .03f))
            drawLine(Color.White.copy(alpha = .62f), Offset(w * .38f, h * .80f), Offset(w * .36f, h * .89f), w * .065f, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = .62f), Offset(w * .62f, h * .80f), Offset(w * .64f, h * .89f), w * .065f, StrokeCap.Round)
        }
        if (showsBadge) Box(Modifier.align(Alignment.TopEnd).size(iconSize * .30f).background(Brush.linearGradient(listOf(Accent, Accent2)), CircleShape), contentAlignment = Alignment.Center) {
            MiniSymbol(Symbol.GAUGE, Modifier.fillMaxSize().padding(iconSize * .06f), Color(0xFF00342B))
        }
    }
}

@Composable
private fun SymbolTile(symbol: Symbol, tileSize: Dp = 48.dp) {
    Box(Modifier.size(tileSize).background(Accent.copy(alpha = .12f), RoundedCornerShape(tileSize * .30f)), contentAlignment = Alignment.Center) { MiniSymbol(symbol, Modifier.size(tileSize * .54f)) }
}

@Composable
private fun MiniSymbol(symbol: Symbol, modifier: Modifier, color: Color = Accent) {
    Canvas(modifier) {
        val w = size.width; val h = size.height; val stroke = (w * .09f).coerceAtLeast(2f)
        when (symbol) {
            Symbol.GAUGE -> { drawArc(color, 205f, 130f, false, style = Stroke(stroke, cap = StrokeCap.Round)); drawLine(color, center, Offset(w * .72f, h * .34f), stroke, StrokeCap.Round); drawCircle(color, w * .08f, center) }
            Symbol.DROP -> { val p = Path().apply { moveTo(w * .5f, h * .08f); cubicTo(w * .25f, h * .38f, w * .18f, h * .58f, w * .5f, h * .92f); cubicTo(w * .82f, h * .58f, w * .75f, h * .38f, w * .5f, h * .08f); close() }; drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round)) }
            Symbol.CHECK -> { drawCircle(color, w * .42f, style = Stroke(stroke)); drawLine(color, Offset(w * .28f, h * .52f), Offset(w * .44f, h * .68f), stroke, StrokeCap.Round); drawLine(color, Offset(w * .44f, h * .68f), Offset(w * .75f, h * .34f), stroke, StrokeCap.Round) }
            Symbol.RULER -> { drawLine(color, Offset(w * .15f, h * .78f), Offset(w * .82f, h * .2f), stroke, StrokeCap.Round); drawLine(color, Offset(w * .33f, h * .62f), Offset(w * .42f, h * .72f), stroke * .65f); drawLine(color, Offset(w * .52f, h * .45f), Offset(w * .62f, h * .56f), stroke * .65f) }
            Symbol.UP -> { drawCircle(color, w * .4f, style = Stroke(stroke)); drawLine(color, Offset(w * .3f, h * .65f), Offset(w * .7f, h * .25f), stroke, StrokeCap.Round); drawLine(color, Offset(w * .52f, h * .25f), Offset(w * .7f, h * .25f), stroke, StrokeCap.Round); drawLine(color, Offset(w * .7f, h * .25f), Offset(w * .7f, h * .43f), stroke, StrokeCap.Round) }
            Symbol.PLUS -> { drawCircle(color, w * .4f, style = Stroke(stroke)); drawLine(color, Offset(w * .5f, h * .28f), Offset(w * .5f, h * .72f), stroke, StrokeCap.Round); drawLine(color, Offset(w * .28f, h * .5f), Offset(w * .72f, h * .5f), stroke, StrokeCap.Round) }
            Symbol.PERCENT -> { drawCircle(color, w * .12f, Offset(w * .29f, h * .29f), style = Stroke(stroke * .7f)); drawCircle(color, w * .12f, Offset(w * .71f, h * .71f), style = Stroke(stroke * .7f)); drawLine(color, Offset(w * .25f, h * .78f), Offset(w * .75f, h * .22f), stroke, StrokeCap.Round) }
            Symbol.WARNING -> { val p = Path().apply { moveTo(w * .5f, h * .1f); lineTo(w * .92f, h * .86f); lineTo(w * .08f, h * .86f); close() }; drawPath(p, color, style = Stroke(stroke)); drawLine(color, Offset(w * .5f, h * .35f), Offset(w * .5f, h * .61f), stroke, StrokeCap.Round); drawCircle(color, stroke * .55f, Offset(w * .5f, h * .74f)) }
            Symbol.SUN -> { drawCircle(color, w * .22f, center, style = Stroke(stroke)); repeat(8) { i -> val a = Math.toRadians(i * 45.0); drawLine(color, Offset(center.x + cos(a).toFloat() * w * .32f, center.y + sin(a).toFloat() * h * .32f), Offset(center.x + cos(a).toFloat() * w * .44f, center.y + sin(a).toFloat() * h * .44f), stroke * .7f, StrokeCap.Round) } }
            Symbol.SNOW -> repeat(3) { i -> val a = Math.toRadians(i * 60.0); val dx = cos(a).toFloat() * w * .4f; val dy = sin(a).toFloat() * h * .4f; drawLine(color, Offset(center.x - dx, center.y - dy), Offset(center.x + dx, center.y + dy), stroke * .65f, StrokeCap.Round) }
            Symbol.INFINITY -> { val p = Path().apply { moveTo(w * .5f, h * .5f); cubicTo(w * .25f, h * .15f, w * .02f, h * .25f, w * .12f, h * .55f); cubicTo(w * .24f, h * .83f, w * .42f, h * .66f, w * .5f, h * .5f); cubicTo(w * .58f, h * .34f, w * .76f, h * .17f, w * .88f, h * .45f); cubicTo(w * .98f, h * .75f, w * .75f, h * .85f, w * .5f, h * .5f) }; drawPath(p, color, style = Stroke(stroke, cap = StrokeCap.Round)) }
            Symbol.SPARKLES, Symbol.VESSEL -> { drawCircle(color, w * .28f, center, style = Stroke(stroke)); drawLine(color, Offset(w * .5f, h * .05f), Offset(w * .5f, h * .95f), stroke * .7f, StrokeCap.Round); drawLine(color, Offset(w * .05f, h * .5f), Offset(w * .95f, h * .5f), stroke * .7f, StrokeCap.Round) }
        }
    }
}

private fun String.number(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0
private fun fmt(value: Double, digits: Int = 2): String = String.format(Locale.getDefault(), "%.${digits}f", value)
