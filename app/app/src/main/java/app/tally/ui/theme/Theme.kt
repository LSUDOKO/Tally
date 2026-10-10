package app.tally.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import app.tally.R

/**
 * Tally brand: cobalt blue from the logo and mascot, on a deep night-blue canvas with frosted
 * glass surfaces. Semantic colours keep one meaning everywhere: green = paid, red = blocked,
 * amber = needs you.
 */
object TallyColors {
    // Brand blues (asset sheet palette: #0066FF, #3382F6, #EBF0FF).
    val Brand = Color(0xFF0066FF)
    val BrandBright = Color(0xFF3382F6)
    val BrandDeep = Color(0xFF0043C7)
    val BrandMist = Color(0xFFEBF0FF)

    // Canvas and glass.
    val Ink = Color(0xFF050B1E)
    val Night = Color(0xFF0A1638)
    val Surface = Color(0xFF101D40)
    val SurfaceHigh = Color(0xFF192D57)
    val Glass = Color(0x16FFFFFF)
    val GlassHigh = Color(0x28FFFFFF)
    val GlassStroke = Color(0x33FFFFFF)

    // Text.
    val Text = Color(0xFFF4F7FF)
    val Muted = Color(0xFFB4C3E2)
    val Faint = Color(0xFF8496BD)

    // Semantics.
    val Paid = Color(0xFF2ED47A)
    val Blocked = Color(0xFFFF5A5F)
    val NeedsYou = Color(0xFFFFB547)
    val Accent = Color(0xFF8DB8FF)
}

object TallyBrushes {
    /** Light arrives from the upper left throughout the app. */
    val PrimaryButton = Brush.verticalGradient(listOf(Color(0xFF4F98FF), TallyColors.Brand, Color(0xFF0751DA)))
    val HeroPanel = Brush.verticalGradient(listOf(Color(0xFF165FE8), Color(0xFF1145B7), Color(0xFF101F52)))
    val HeroLayerBack = Brush.horizontalGradient(listOf(Color(0xFF427FFF), Color(0xFF1E55CC)))
    val HeroLayerFront = Brush.horizontalGradient(listOf(Color(0xFF285FCF), Color(0xFF1747AE)))

    /** Frosted glass: a faint top highlight fading into the panel. */
    val GlassPanel = Brush.verticalGradient(listOf(Color(0x26FFFFFF), Color(0x0BFFFFFF)))

    /** The 1 dp rim on glass panels, lit from above. */
    val GlassRim = Brush.verticalGradient(listOf(Color(0x55FFFFFF), Color(0x10FFFFFF)))

    fun tinted(c: Color) = Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.75f)))
}

object TallyShadows {
    val Card = 12.dp
    val Action = 18.dp
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

object TallyFonts {
    val Display = FontFamily(
        variable(R.font.playfair_display, 500),
        variable(R.font.playfair_display, 600),
        variable(R.font.playfair_display, 700),
    )
    val Sans = FontFamily(
        variable(R.font.plus_jakarta_sans, 400),
        variable(R.font.plus_jakarta_sans, 500),
        variable(R.font.plus_jakarta_sans, 600),
        variable(R.font.plus_jakarta_sans, 700),
        variable(R.font.plus_jakarta_sans, 800),
    )
    val Mono = FontFamily(
        variable(R.font.jetbrains_mono, 400),
        variable(R.font.jetbrains_mono, 500),
    )
}

private val type = Typography(
    displayLarge = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-0.8).sp),
    displayMedium = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.6).sp),
    displaySmall = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp, letterSpacing = (-0.4).sp),
    headlineMedium = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.6).sp),
    headlineSmall = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    bodyLarge = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.2.sp),
)

private val scheme = darkColorScheme(
    primary = TallyColors.Brand,
    onPrimary = Color.White,
    primaryContainer = TallyColors.SurfaceHigh,
    onPrimaryContainer = TallyColors.Text,
    secondary = TallyColors.Accent,
    background = TallyColors.Ink,
    onBackground = TallyColors.Text,
    surface = TallyColors.Surface,
    onSurface = TallyColors.Text,
    surfaceVariant = TallyColors.SurfaceHigh,
    onSurfaceVariant = TallyColors.Muted,
    surfaceContainerLow = TallyColors.Surface,
    surfaceContainer = TallyColors.Surface,
    surfaceContainerHigh = TallyColors.SurfaceHigh,
    outline = TallyColors.GlassStroke,
    outlineVariant = TallyColors.GlassStroke,
    error = TallyColors.Blocked,
    tertiary = TallyColors.NeedsYou,
)

@Composable
fun TallyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
