package app.tally.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Semantic colours used across the app: green = paid, red = blocked, amber = needs you. */
object TallyColors {
    val Ink = Color(0xFF0B0F0E)
    val Surface = Color(0xFF141A18)
    val SurfaceHigh = Color(0xFF1C2421)
    val Text = Color(0xFFE9F1EC)
    val Muted = Color(0xFF8FA39A)
    val Paid = Color(0xFF3DDC97)
    val Blocked = Color(0xFFFF5D5D)
    val NeedsYou = Color(0xFFFFB547)
    val Accent = Color(0xFF7CF0C0)
}

private val scheme = darkColorScheme(
    primary = TallyColors.Accent,
    onPrimary = TallyColors.Ink,
    background = TallyColors.Ink,
    onBackground = TallyColors.Text,
    surface = TallyColors.Surface,
    onSurface = TallyColors.Text,
    surfaceVariant = TallyColors.SurfaceHigh,
    onSurfaceVariant = TallyColors.Muted,
    error = TallyColors.Blocked,
    tertiary = TallyColors.NeedsYou,
)

@Composable
fun TallyTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = Typography(), content = content)
}
