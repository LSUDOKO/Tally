package app.tally.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.R

/**
 * Cobalt: a near-black navy ground lit by the brand blue, the light the mascot's visor gives off.
 * Smoked-glass panels, one lit pill per screen, light type. Semantic colours keep one meaning
 * everywhere: green = paid, red = blocked, amber = needs you.
 */
object TallyColors {
    // Brand light, from the logo (#0066FF) up to the hot core.
    val Brand = Color(0xFF1F6BFF)
    val BrandBright = Color(0xFF5C9DFF)
    val BrandCore = Color(0xFFBFDBFF)
    val BrandDeep = Color(0xFF0A3DB8)
    val BrandLava = Color(0xFF08237A)
    val BrandMist = Color(0xFFEBF0FF)

    // Ground.
    val Ink = Color(0xFF03060F)
    val Night = Color(0xFF060B1A)
    val Surface = Color(0xFF0B1430)
    val SurfaceHigh = Color(0xFF13214A)

    /** Smoked glass: reads over both the blue and the black parts of the backdrop. */
    val Glass = Color(0x8F040A1C)
    val GlassStrong = Color(0xC703071A)
    val GlassHigh = Color(0x26EEF4FF)
    val GlassStroke = Color(0x38C2DCFF)
    val GlassStrokeTop = Color(0x38C2DCFF)
    val GlassStrokeBottom = Color(0x0FC2DCFF)

    // Text.
    val Text = Color(0xFFEEF4FF)
    val Muted = Color(0xFFB3C2DE)
    val Faint = Color(0xFF7F90B5)
    /** Text and icons on lit blue surfaces. */
    val OnBrand = Color(0xFF00102E)

    // Semantics.
    val Paid = Color(0xFF33D17A)
    val Blocked = Color(0xFFFF6B6B)
    val NeedsYou = Color(0xFFFFB547)
    val Accent = BrandBright
}

object TallyBrushes {
    /** The one primary action: a lit pill, brightest at the top. */
    val PrimaryButton = Brush.verticalGradient(listOf(TallyColors.BrandBright, TallyColors.Brand, Color(0xFF1659F0)))
    val HeroPanel = Brush.verticalGradient(listOf(Color(0xFF2E78FF), TallyColors.Brand, Color(0xFF0F3FB4)))
    val HeroLayerBack = Brush.horizontalGradient(listOf(Color(0x664D8DFF), Color(0x662454D6)))
    val HeroLayerFront = Brush.horizontalGradient(listOf(Color(0x995C9DFF), Color(0x992A62E6)))

    /** Smoked glass fill and the hairline that catches light along the top edge. */
    val GlassPanel = Brush.verticalGradient(listOf(TallyColors.Glass, TallyColors.Glass))
    val GlassRim = Brush.verticalGradient(listOf(TallyColors.GlassStrokeTop, TallyColors.GlassStrokeBottom))

    fun tinted(c: Color) = Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.8f)))
}

object TallyShadows {
    val Card = 0.dp
    val Action = 18.dp
}

private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Inter Tight, one variable file: light for the large statements, medium for labels. */
object TallyFonts {
    val Grotesk = FontFamily(
        variable(R.font.inter_tight, 300),
        variable(R.font.inter_tight, 400),
        variable(R.font.inter_tight, 500),
        variable(R.font.inter_tight, 600),
        variable(R.font.inter_tight, 700),
    )
    val Display = Grotesk
    val Sans = Grotesk
    val Mono = FontFamily(variable(R.font.jetbrains_mono, 400), variable(R.font.jetbrains_mono, 500))
}

private fun style(weight: Int, size: Int, line: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = TallyFonts.Grotesk,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.sp,
)

private val type = Typography(
    displayLarge = style(300, 46, 50, -1.2),
    displayMedium = style(300, 40, 44, -1.0),
    displaySmall = style(300, 32, 37, -0.7),
    headlineLarge = style(300, 30, 35, -0.6),
    headlineMedium = style(300, 26, 31, -0.5),
    headlineSmall = style(500, 22, 27, -0.2),
    titleLarge = style(500, 19, 24, -0.1),
    titleMedium = style(500, 16, 21),
    titleSmall = style(500, 14, 19),
    bodyLarge = style(400, 16, 23),
    bodyMedium = style(400, 14, 20),
    bodySmall = style(400, 12, 17),
    labelLarge = style(600, 15, 20),
    labelMedium = style(500, 13, 17),
    labelSmall = style(500, 11, 15, 0.1),
)

private val shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

private val scheme = darkColorScheme(
    primary = TallyColors.Brand,
    onPrimary = TallyColors.OnBrand,
    primaryContainer = Color(0xFF0E2A6E),
    onPrimaryContainer = TallyColors.Text,
    secondary = TallyColors.BrandBright,
    onSecondary = TallyColors.OnBrand,
    tertiary = TallyColors.NeedsYou,
    background = TallyColors.Ink,
    onBackground = TallyColors.Text,
    surface = TallyColors.Night,
    onSurface = TallyColors.Text,
    surfaceVariant = TallyColors.SurfaceHigh,
    onSurfaceVariant = TallyColors.Muted,
    surfaceContainerLowest = Color(0xFF040812),
    surfaceContainerLow = Color(0xFF070E22),
    surfaceContainer = TallyColors.Surface,
    surfaceContainerHigh = TallyColors.SurfaceHigh,
    surfaceContainerHighest = Color(0xFF1A2A58),
    outline = Color(0xFF42557F),
    outlineVariant = Color(0xFF1E2C50),
    error = TallyColors.Blocked,
    onError = TallyColors.OnBrand,
    scrim = Color(0xCC000000),
)

@Composable
fun TallyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = type, shapes = shapes, content = content)
}
