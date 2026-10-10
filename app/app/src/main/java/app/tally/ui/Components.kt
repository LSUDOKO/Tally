package app.tally.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.ui.theme.TallyBrushes
import app.tally.ui.theme.TallyColors
import app.tally.ui.theme.TallyFonts

// ---------------------------------------------------------------------------------- surfaces

/** The app canvas: night blue with a cobalt glow rising from the top, like the reference's red bloom. */
@Composable
fun BrandBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(modifier.fillMaxSize().background(TallyColors.Ink)) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(TallyColors.Night, TallyColors.Ink), endY = size.height * 0.9f))
            drawCircle(
                Brush.radialGradient(listOf(TallyColors.Brand.copy(alpha = 0.55f), TallyColors.Brand.copy(alpha = 0.12f), Color.Transparent), center = Offset(size.width * 0.5f, -size.width * 0.1f), radius = size.width * 1.05f),
                radius = size.width * 1.05f,
                center = Offset(size.width * 0.5f, -size.width * 0.1f),
            )
            drawCircle(
                Brush.radialGradient(listOf(TallyColors.BrandBright.copy(alpha = 0.16f), Color.Transparent), center = Offset(size.width * 1.0f, size.height * 0.62f), radius = size.width * 0.8f),
                radius = size.width * 0.8f,
                center = Offset(size.width * 1.0f, size.height * 0.62f),
            )
        }
        content()
    }
}

/** Frosted glass: translucent fill, lit 1 dp rim. */
fun Modifier.glass(shape: Shape = RoundedCornerShape(24.dp), tint: Color? = null): Modifier =
    this.clip(shape)
        .background(if (tint != null) Brush.verticalGradient(listOf(tint.copy(alpha = 0.22f), tint.copy(alpha = 0.08f))) else TallyBrushes.GlassPanel)
        .border(1.dp, if (tint != null) Brush.verticalGradient(listOf(tint.copy(alpha = 0.6f), tint.copy(alpha = 0.15f))) else TallyBrushes.GlassRim, shape)

/** Shrinks a little while pressed, springing back on release. */
@Composable
fun Modifier.pressable(onClick: () -> Unit, enabled: Boolean = true, role: Role = Role.Button): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium), label = "press")
    return this.graphicsLayer { scaleX = scale; scaleY = scale }.clickable(source, indication = null, enabled = enabled, role = role, onClick = onClick)
}

// ---------------------------------------------------------------------------------- chrome

@Composable
fun TopBar(title: String, onBack: (() -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            GlassIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack)
            Spacer(Modifier.width(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, color = TallyColors.Text, modifier = Modifier.weight(1f))
        actions()
    }
}

@Composable
fun GlassIconButton(icon: ImageVector, description: String, onClick: () -> Unit, tint: Color = TallyColors.Text, size: Dp = 44.dp) {
    Box(Modifier.size(size).glass(CircleShape).pressable(onClick), contentAlignment = Alignment.Center) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

/** A rounded tile holding a tinted icon, like the asset sheet's verification icons. */
@Composable
fun IconTile(icon: ImageVector, tint: Color = TallyColors.Accent, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.32f))
            .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.32f), tint.copy(alpha = 0.12f))))
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.52f)) }
}

/** Two-line serif headline, the second line dimmed: "Create Music / From Imagination". */
@Composable
fun HeroTitle(first: String, second: String, modifier: Modifier = Modifier, align: TextAlign = TextAlign.Center) {
    Column(modifier, horizontalAlignment = if (align == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start) {
        Text(first, style = MaterialTheme.typography.displayMedium, color = TallyColors.Text, textAlign = align)
        Text(second, style = MaterialTheme.typography.displayMedium, color = TallyColors.Muted.copy(alpha = 0.75f), textAlign = align)
    }
}

// ---------------------------------------------------------------------------------- content

@Composable
fun Card(modifier: Modifier = Modifier, accent: Color? = null, content: @Composable () -> Unit) {
    Box(modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), accent).padding(18.dp)) { content() }
}

@Composable
fun Label(text: String, color: Color = TallyColors.Muted) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color)

@Composable
fun Big(text: String, color: Color = TallyColors.Text, size: Int = 34) =
    Text(text, fontFamily = TallyFonts.Sans, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, lineHeight = (size * 1.15).sp, letterSpacing = (-0.5).sp, color = color)

@Composable
fun Body(text: String, color: Color = TallyColors.Text) = Text(text, style = MaterialTheme.typography.bodyMedium, color = color)

@Composable
fun Mono(text: String, color: Color = TallyColors.Muted) = Text(text, fontFamily = TallyFonts.Mono, fontSize = 12.sp, lineHeight = 17.sp, color = color)

@Composable
fun Chip(text: String, color: Color = TallyColors.Accent, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
fun Dot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

/** The main call to action: a lit cobalt pill with a soft glow beneath. */
@Composable
fun Primary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, color: Color = TallyColors.Brand, icon: ImageVector? = null) {
    val fill = if (color == TallyColors.Brand) TallyBrushes.PrimaryButton else TallyBrushes.tinted(color)
    val content = if (color == TallyColors.NeedsYou) TallyColors.Ink else Color.White
    Box(
        modifier.fillMaxWidth().height(56.dp)
            .alpha(if (enabled) 1f else 0.45f)
            .shadow(if (enabled) 18.dp else 0.dp, RoundedCornerShape(50), ambientColor = color, spotColor = color)
            .clip(RoundedCornerShape(50))
            .background(fill)
            .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.45f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(50))
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
            Text(text, color = content, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
        }
    }
}

/** Secondary action: a frosted glass pill. */
@Composable
fun Secondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color = TallyColors.Text) {
    Box(
        modifier.fillMaxWidth().height(50.dp).alpha(if (enabled) 1f else 0.4f).glass(RoundedCornerShape(50)).pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun Gap(h: Int = 12) = Spacer(Modifier.height(h.dp))

@Composable
fun Stat(label: String, value: String, color: Color = TallyColors.Text, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) IconTile(icon, color, 36.dp)
        Label(label)
        Text(value, fontFamily = TallyFonts.Sans, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, color = color)
    }
}

fun short(a: String?) = a?.let { if (it.length > 10) it.take(4) + "…" + it.takeLast(4) else it } ?: "—"

// ---------------------------------------------------------------------------------- inputs

/** A glass text field with a cobalt focus ring. */
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
) {
    androidx.compose.material3.OutlinedTextField(
        value, onValueChange, modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(20.dp),
        leadingIcon = leading?.let { { Icon(it, contentDescription = null, tint = TallyColors.Muted) } },
        trailingIcon = trailing,
        visualTransformation = visualTransformation,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedContainerColor = TallyColors.Glass,
            unfocusedContainerColor = TallyColors.Glass,
            focusedBorderColor = TallyColors.BrandBright,
            unfocusedBorderColor = TallyColors.GlassStroke,
            focusedLabelColor = TallyColors.Accent,
            unfocusedLabelColor = TallyColors.Muted,
            cursorColor = TallyColors.BrandBright,
            focusedTextColor = TallyColors.Text,
            unfocusedTextColor = TallyColors.Text,
        ),
    )
}

/** Brand switch: cobalt track when on. */
@Composable
fun TallySwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) =
    androidx.compose.material3.Switch(
        checked, onChange, enabled = enabled,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = TallyColors.Brand,
            checkedBorderColor = TallyColors.BrandBright,
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
