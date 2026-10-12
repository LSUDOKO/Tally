package app.tally.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.ui.theme.TallyBrushes
import app.tally.ui.theme.TallyColors
import app.tally.ui.theme.TallyFonts
import kotlinx.coroutines.delay

// ---------------------------------------------------------------------------------- surfaces

/**
 * The three lighting set-ups every route sits on.
 * Dawn: brand light pouring from the top (home). Night: near-black with cobalt light at the top and
 * both edges (the focused moments: scan, checkout, rules). Dusk: Night turned down, for screens that
 * are mostly reading (receipts, rewards, settings).
 */
enum class Backdrop { Dawn, Night, Dusk }

/** Film grain, tiled. It keeps the large gradients from banding and gives the glass something to sit on. */
private val grain: ImageBitmap by lazy {
    val side = 128
    val rnd = java.util.Random(7)
    val px = IntArray(side * side) {
        val alpha = rnd.nextInt(22)
        val v = if (rnd.nextBoolean()) 255 else 0
        (alpha shl 24) or (v shl 16) or (v shl 8) or v
    }
    android.graphics.Bitmap.createBitmap(px, side, side, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
}

/** The lit cobalt canvas. Changing [variant] cross-fades the light rather than cutting it. */
@Composable
fun BrandBackground(variant: Backdrop = Backdrop.Night, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    // The light drifts slowly. It is read only in the draw phase, so nothing recomposes.
    val drift = rememberInfiniteTransition(label = "backdrop").animateFloat(
        0f, 1f, infiniteRepeatable(tween(11_000, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "drift",
    )
    val dawn by animateFloatAsState(if (variant == Backdrop.Dawn) 1f else 0f, tween(700), label = "dawn")
    val night by animateFloatAsState(if (variant == Backdrop.Dusk) 0.55f else 1f, tween(700), label = "night")
    val grainBrush = remember { ShaderBrush(ImageShader(grain, TileMode.Repeated, TileMode.Repeated)) }
    Box(
        modifier.fillMaxSize().background(TallyColors.Ink).drawBehind {
            val d = drift.value
            if (dawn < 1f) night(d, night * (1f - dawn))
            if (dawn > 0f) dawn(d, dawn)
            drawRect(grainBrush)
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides TallyColors.Text) { content() }
    }
}

private fun DrawScope.dawn(d: Float, alpha: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.verticalGradient(
            0f to Color(0xFF2F7BFF),
            0.15f to Color(0xFF1C5FEB),
            0.34f to Color(0xFF0E39A8),
            0.52f to Color(0xFF061A55),
            0.70f to TallyColors.Ink,
            1f to TallyColors.Ink,
        ),
        alpha = alpha,
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0x8CA8CCFF), Color(0x00A8CCFF)), center = Offset(w * (0.60f + 0.24f * d), h * (0.03f + 0.05f * d)), radius = w * 0.85f),
        alpha = alpha,
    )
    // Shade pooling from the left, so the headline sits on darker ground.
    drawRect(
        Brush.radialGradient(listOf(Color(0x99020611), Color(0x00020611)), center = Offset(w * (0.05f - 0.10f * d), h * 0.46f), radius = w * 0.95f),
        alpha = alpha,
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0x6B0A3DB8), Color(0x000A3DB8)), center = Offset(w * 0.5f, h * 1.12f), radius = w * 0.95f),
        alpha = alpha,
    )
}

private fun DrawScope.night(d: Float, strength: Float) {
    val w = size.width
    val h = size.height
    drawRect(
        Brush.radialGradient(
            0f to Color(0xFF2E76FF).copy(alpha = 0.95f * strength),
            0.45f to Color(0xFF0B3EBA).copy(alpha = 0.62f * strength),
            1f to Color(0x000B3EBA),
            center = Offset(w * (0.5f + 0.14f * (d - 0.5f)), -h * 0.07f),
            radius = w * 0.95f,
        ),
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0xFF1F5FE8).copy(alpha = 0.52f * strength), Color(0x001F5FE8)), center = Offset(-w * 0.22f, h * (0.40f + 0.07f * d)), radius = w * 0.75f),
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0xFF1F5FE8).copy(alpha = 0.52f * strength), Color(0x001F5FE8)), center = Offset(w * 1.22f, h * (0.52f - 0.07f * d)), radius = w * 0.75f),
    )
    drawRect(
        Brush.radialGradient(listOf(Color(0xFF08237A).copy(alpha = 0.34f * strength), Color(0x0008237A)), center = Offset(w * 0.5f, h * 1.15f), radius = w * 0.9f),
    )
}

/** A short, staggerable route reveal using only opacity and translation. */
@Composable
fun Reveal(delayMillis: Long = 0, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(delayMillis)
        progress.animateTo(1f, tween(520, easing = androidx.compose.animation.core.FastOutSlowInEasing))
    }
    Box(modifier.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * 16.dp.toPx()
    }, content = content)
}

/** Smoked glass: a translucent fill with a hairline that catches light along the top edge. A [tint] colours both. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(28.dp), tint: Color? = null): Modifier =
    if (tint == null) this.background(TallyColors.Glass, shape).border(1.dp, TallyBrushes.GlassRim, shape).clip(shape)
    else this.background(TallyColors.Glass, shape).background(tint.copy(alpha = 0.13f), shape)
        .border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.08f))), shape).clip(shape)

@Composable
private fun pressScale(source: InteractionSource): Float {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.55f, stiffness = 700f), label = "press")
    return scale
}

/** Shrinks a little while pressed, springing back on release. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

// ---------------------------------------------------------------------------------- brand

/** The Tally mark: four strokes and the fifth across, white on the brand blue. The logo, not the mascot. */
@Composable
fun TallyMark(size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(size * 0.28f)
    androidx.compose.foundation.Canvas(
        modifier.size(size).background(Brush.verticalGradient(listOf(Color(0xFF2E78FF), TallyColors.Brand, Color(0xFF0F4BD8))), shape)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x80E8F1FF), Color(0x00E8F1FF))), shape),
    ) {
        val u = this.size.minDimension / 108f
        val w = 7.5f * u
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        for (x in listOf(37f, 48f, 59f, 70f)) drawLine(Color.White, Offset(x * u, 34f * u), Offset(x * u, 72f * u), w, cap)
        drawLine(Color.White, Offset(29f * u, 69f * u), Offset(78f * u, 38f * u), w, cap)
    }
}

/** Icons drawn for Tally where the stock set reads wrong. */
object TallyIcons {
    /** A viewfinder: four corner brackets and the scan line. */
    val Scan: ImageVector by lazy {
        val stroke = androidx.compose.ui.graphics.SolidColor(Color.Black)
        androidx.compose.ui.graphics.vector.ImageVector.Builder("tally.scan", 24.dp, 24.dp, 24f, 24f).apply {
            fun corner(block: androidx.compose.ui.graphics.vector.PathBuilder.() -> Unit) =
                path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = androidx.compose.ui.graphics.StrokeCap.Round, strokeLineJoin = androidx.compose.ui.graphics.StrokeJoin.Round, pathBuilder = block)
            corner { moveTo(3.5f, 8.5f); verticalLineTo(6f); arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, -2.5f); horizontalLineTo(8.5f) }
            corner { moveTo(15.5f, 3.5f); horizontalLineTo(18f); arcToRelative(2.5f, 2.5f, 0f, false, true, 2.5f, 2.5f); verticalLineTo(8.5f) }
            corner { moveTo(20.5f, 15.5f); verticalLineTo(18f); arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, 2.5f); horizontalLineTo(15.5f) }
            corner { moveTo(8.5f, 20.5f); horizontalLineTo(6f); arcToRelative(2.5f, 2.5f, 0f, false, true, -2.5f, -2.5f); verticalLineTo(15.5f) }
            corner { moveTo(7.5f, 12f); horizontalLineTo(16.5f) }
        }.build()
    }
}

// ---------------------------------------------------------------------------------- chrome

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = TallyColors.Text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = TallyColors.Text, size: Dp = 44.dp) {
    Box(Modifier.size(size).glass(CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(if (size >= 44.dp) 20.dp else size * 0.46f))
    }
}

/** The glowing round button: the screen's hot spot (scan, in the tab bar). */
@Composable
fun OrbButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 60.dp, enabled: Boolean = true) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Box(
        modifier
            .size(size)
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.45f }
            .drawBehind {
                val r = this.size.minDimension
                drawCircle(Brush.radialGradient(listOf(Color(0x802E76FF), Color(0x002E76FF)), radius = r * 1.05f), radius = r * 1.05f)
                drawCircle(
                    Brush.radialGradient(
                        0f to TallyColors.BrandCore,
                        0.4f to TallyColors.BrandBright,
                        0.8f to TallyColors.Brand,
                        1f to Color(0xFF1450D6),
                        center = Offset(r * 0.36f, r * 0.28f),
                        radius = r * 0.85f,
                    ),
                )
            }
            .border(1.dp, Color(0x66E8F1FF), CircleShape)
            .clip(CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = description, tint = TallyColors.OnBrand, modifier = Modifier.size(size * 0.42f)) }
}

/** A small glass tile holding a tinted icon. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = TallyColors.Accent, size: Dp = 44.dp) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        Modifier.size(size).background(tint.copy(alpha = 0.14f), shape).border(1.dp, Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), tint.copy(alpha = 0.06f))), shape),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f)) }
}

/** A two-line statement in light type, the second line dimmed: "Every payment, / on the record." */
@Composable
fun HeroTitle(first: String, second: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayMedium) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(first, style = style, color = TallyColors.Text, textAlign = align)
        Text(second, style = style, color = TallyColors.Muted, textAlign = align)
    }
}

// ---------------------------------------------------------------------------------- content

@Composable
fun Card(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), accent).padding(18.dp)) { content() }
}

@Composable
fun Label(text: String, color: Color = TallyColors.Muted) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)

/** Large figures: light weight, tight tracking, the way the headlines are set. */
@Composable
fun Big(text: String, color: Color = TallyColors.Text, size: Int = 34) =
    Text(text, fontFamily = TallyFonts.Sans, fontWeight = if (size >= 28) FontWeight.Light else FontWeight.Normal, fontSize = size.sp, lineHeight = (size * 1.12).sp, letterSpacing = (-size * 0.025).sp, color = color)

@Composable
fun Body(text: String, color: Color = TallyColors.Text) = Text(text, style = MaterialTheme.typography.bodyMedium, color = color)

@Composable
fun Mono(text: String, color: Color = TallyColors.Muted) = Text(text, fontFamily = TallyFonts.Mono, fontSize = 12.sp, lineHeight = 17.sp, color = color)

@Composable
fun Chip(text: String, color: Color = TallyColors.Accent, icon: ImageVector? = null) {
    Row(
        Modifier.background(color.copy(alpha = 0.13f), CircleShape).border(1.dp, color.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

/** Button labels step down in size before they truncate, so long labels survive large text and narrow phones. */
private val FitLabel = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp, stepSize = 0.5.sp)

/** The one primary action on a screen: a lit cobalt pill with dark ink, its light pooling on the ground beneath. */
@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = TallyColors.Brand, icon: ImageVector? = null) {
    val fill = if (color == TallyColors.Brand) TallyBrushes.PrimaryButton else Brush.verticalGradient(listOf(lerp(color, Color.White, 0.25f), color, lerp(color, Color.Black, 0.1f)))
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .drawBehind {
                if (!enabled) return@drawBehind
                // A circle squashed into an ellipse, so the pool fades out on every side.
                val pool = Offset(size.width / 2f, size.height * 0.92f)
                val reach = size.width * 0.52f
                scale(scaleX = 1f, scaleY = (size.height * 1.15f) / reach, pivot = pool) {
                    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.42f), color.copy(alpha = 0f)), center = pool, radius = reach), radius = reach, center = pool)
                }
            }
            .defaultMinSize(minHeight = 56.dp)
            .background(fill, CircleShape)
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x99E8F1FF), Color(0x00E8F1FF))), CircleShape)
            .clip(CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TallyColors.OnBrand, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = TallyColors.OnBrand, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FitLabel)
    }
}

/** Secondary action: a smoked glass pill. */
@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = TallyColors.Text) {
    val source = remember { MutableInteractionSource() }
    val scale = pressScale(source)
    Row(
        modifier.fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = if (enabled) 1f else 0.38f }
            .defaultMinSize(minHeight = 54.dp)
            .glass(CircleShape)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, autoSize = FitLabel)
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun Stat(label: String, value: String, color: Color = TallyColors.Text, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) IconTile(icon, color, 36.dp)
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = TallyColors.Muted, maxLines = 1, softWrap = false)
        Text(value, fontFamily = TallyFonts.Sans, fontWeight = FontWeight.Light, fontSize = 26.sp, letterSpacing = (-0.6).sp, color = color, maxLines = 1)
    }
}

fun short(a: String?) = a?.let { if (it.length > 10) it.take(4) + "…" + it.takeLast(4) else it } ?: "—"

// ---------------------------------------------------------------------------------- inputs

/** A glass text field: smoked fill, hairline rim, no underline. */
@Composable
fun GlassField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    minLines: Int = 1,
    leading: ImageVector? = null,
    trailing: @Composable (() -> Unit)? = null,
    visualTransformation: androidx.compose.ui.text.input.VisualTransformation = androidx.compose.ui.text.input.VisualTransformation.None,
    keyboardType: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text,
) {
    val shape = RoundedCornerShape(20.dp)
    androidx.compose.material3.TextField(
        value, onValueChange, modifier.fillMaxWidth().border(1.dp, TallyBrushes.GlassRim, shape),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = shape,
        leadingIcon = leading?.let { { Icon(it, contentDescription = null) } },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
        colors = androidx.compose.material3.TextFieldDefaults.colors(
            focusedContainerColor = TallyColors.GlassStrong,
            unfocusedContainerColor = TallyColors.Glass,
            disabledContainerColor = TallyColors.Glass,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = TallyColors.BrandBright,
            focusedLabelColor = TallyColors.BrandBright,
            unfocusedLabelColor = TallyColors.Faint,
            focusedTextColor = TallyColors.Text,
            unfocusedTextColor = TallyColors.Text,
            focusedLeadingIconColor = TallyColors.BrandBright,
            unfocusedLeadingIconColor = TallyColors.Faint,
            focusedTrailingIconColor = TallyColors.Text,
            unfocusedTrailingIconColor = TallyColors.Faint,
        ),
    )
}

/** Brand switch: cobalt track when on. */
@Composable
fun TallySwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    androidx.compose.material3.Switch(
        checked, onChange, enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = TallyColors.BrandMist,
            checkedTrackColor = TallyColors.Brand,
            checkedBorderColor = Color.Transparent,
            uncheckedThumbColor = TallyColors.Muted,
            uncheckedTrackColor = TallyColors.Glass,
            uncheckedBorderColor = TallyColors.GlassStroke,
        ),
    )

/** A setting: icon tile, title and explanation, and a switch. */
@Composable
fun ToggleRow(icon: ImageVector, title: String, detail: String, checked: Boolean, tint: Color = TallyColors.Accent, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint, 40.dp)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                Text(detail, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
            }
            TallySwitch(checked, onChange, enabled)
        }
    }
}
