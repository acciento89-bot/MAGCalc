package de.kamilunavo.magcalc

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight

/** Original licensed Istok Web faces, packaged offline with their OFL. */
@Composable
internal fun productTypography(base: Typography = Typography()): Typography {
    val assets = LocalContext.current.assets
    val family = remember(assets) {
        FontFamily(
            Font(assetManager = assets, path = "fonts/IstokWeb-Regular.ttf", weight = FontWeight.Normal),
            Font(assetManager = assets, path = "fonts/IstokWeb-Bold.ttf", weight = FontWeight.Bold),
        )
    }
    return remember(base, family) {
        base.copy(
            displayLarge = base.displayLarge.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            displayMedium = base.displayMedium.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            displaySmall = base.displaySmall.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            headlineLarge = base.headlineLarge.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            headlineMedium = base.headlineMedium.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            headlineSmall = base.headlineSmall.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            titleLarge = base.titleLarge.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            titleMedium = base.titleMedium.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            titleSmall = base.titleSmall.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            bodyLarge = base.bodyLarge.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            bodyMedium = base.bodyMedium.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            bodySmall = base.bodySmall.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            labelLarge = base.labelLarge.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            labelMedium = base.labelMedium.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
            labelSmall = base.labelSmall.copy(fontFamily = family, fontSynthesis = FontSynthesis.None),
        )
    }
}
