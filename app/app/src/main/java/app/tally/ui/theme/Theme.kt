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
    val Ink = Color(0xFF040A1E)
    val Night = Color(0xFF071233)
    val Surface = Color(0xFF0D1A3F)
    val SurfaceHigh = Color(0xFF16275A)
    val Glass = Color(0x14FFFFFF)
    val GlassHigh = Color(0x22FFFFFF)
    val GlassStroke = Color(0x29FFFFFF)

    // Text.
    val Text = Color(0xFFF4F7FF)
    val Muted = Color(0xFFA3B1D6)
    val Faint = Color(0xFF6E7DA6)

    // Semantics.
    val Paid = Color(0xFF2ED47A)
    val Blocked = Color(0xFFFF5A5F)
    val NeedsYou = Color(0xFFFFB547)
    val Accent = Color(0xFF6FA6FF)
}

object TallyBrushes {
    /** Primary actions: a lit cobalt pill, brighter at the top like the reference glass buttons. */
    val PrimaryButton = Brush.verticalGradient(listOf(Color(0xFF4D93FF), TallyColors.Brand, TallyColors.BrandDeep))

    /** Frosted glass: a faint top highlight fading into the panel. */
    val GlassPanel = Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0FFFFFFF)))

    /** The 1 dp rim on glass panels, lit from above. */
    val GlassRim = Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x0FFFFFFF)))

    fun tinted(c: Color) = Brush.verticalGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.75f)))
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
    displayLarge = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 40.sp, lineHeight = 46.sp),
    displayMedium = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = TallyFonts.Display, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 28.sp),
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
