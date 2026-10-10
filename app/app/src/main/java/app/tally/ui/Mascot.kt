package app.tally.ui

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import app.tally.core.Level
import app.tally.R
import app.tally.ui.theme.TallyColors

/** The mascot's expressions, each tied to a moment in the app. */
enum class Mood(@DrawableRes val res: Int, val motion: Motion = Motion.Float) {
    Hero(R.drawable.mascot_hero),
    Standing(R.drawable.mascot_hero),
    Waving(R.drawable.mascot_success_hd, Motion.Wave),
    Pointing(R.drawable.mascot_hero),
    ThumbsUp(R.drawable.mascot_success_hd, Motion.Bounce),
    HoldingPhone(R.drawable.mascot_hero),
    Laptop(R.drawable.mascot_thinking_hd, Motion.Settled),
    Calling(R.drawable.mascot_thinking_hd),
    Thinking(R.drawable.mascot_thinking_hd, Motion.Sway),
    Victory(R.drawable.mascot_success_hd, Motion.Bounce),
    Cool(R.drawable.mascot_hero),
    Sleepy(R.drawable.mascot_thinking_hd, Motion.Settled),
    Confused(R.drawable.mascot_blocked_hd, Motion.Sway),
    Excited(R.drawable.mascot_success_hd, Motion.Bounce),
    WithReceipt(R.drawable.mascot_success_hd),
    WithShield(R.drawable.mascot_blocked_hd, Motion.Guard),
}

/** How a mood moves while idle. */
enum class Motion { Float, Bounce, Sway, Wave, Settled, Guard }

/**
 * The animated mascot: idles with its mood's motion over a pulsing brand glow and a ground shadow,
 * pops between expressions with a spring, and shakes once whenever [shakeKey] changes (a blocked
 * attack).
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, height: Dp = 180.dp, shakeKey: Any? = null, glow: Boolean = true, level: Level? = null) {
    val density = LocalDensity.current
    val t = rememberInfiniteTransition(label = "mascot")
    val glowPulse by t.animateFloat(0.30f, 0.55f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")
    val haloTurn by t.animateFloat(0f, 360f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "halo")
    val aura = level?.auraColor ?: TallyColors.Brand

    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeKey) {
        if (shakeKey != null) {
            shake.animateTo(0f, keyframes {
                durationMillis = 520
                -14f at 60; 12f at 140; -9f at 220; 7f at 300; -4f at 380; 0f at 520
            })
        }
    }
    val entrance = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) { entrance.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessLow)) }

    Box(modifier.height(height), contentAlignment = Alignment.BottomCenter) {
        if (glow) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension * 0.62f
                drawCircle(
                    Brush.radialGradient(listOf(aura.copy(alpha = glowPulse), Color.Transparent), center = Offset(size.width / 2, size.height * 0.48f), radius = r),
                    radius = r,
                    center = Offset(size.width / 2, size.height * 0.48f),
                )
                if (level == Level.Legend) {
                    // Legend: a cobalt halo that slowly turns behind the mascot.
                    rotate(haloTurn, Offset(size.width / 2, size.height * 0.48f)) {
                        drawCircle(
                            Brush.sweepGradient(listOf(Color.Transparent, TallyColors.BrandBright, Color(0xFF9CC2FF), TallyColors.BrandBright, Color.Transparent), center = Offset(size.width / 2, size.height * 0.48f)),
                            radius = r * 0.8f, center = Offset(size.width / 2, size.height * 0.48f), style = Stroke(width = 4.dp.toPx()),
                        )
                    }
                }
            }
        }
        if (level != null && level.ordinal >= Level.Gold.ordinal) {
            // Gold and above: a crown badge.
            Icon(
                Icons.Rounded.WorkspacePremium, contentDescription = "${level.title} level",
                tint = level.auraColor,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = height * 0.04f, end = height * 0.08f).size(height * 0.16f),
            )
        }
        AnimatedContent(
            targetState = mood,
            transitionSpec = {
                (fadeIn(tween(220)) + scaleIn(spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow), initialScale = 0.82f))
                    .togetherWith(fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 1.06f))
            },
            label = "mood",
        ) { m ->
            val lift by t.animateFloat(
                0f, 1f,
                infiniteRepeatable(tween(m.motion.period, easing = if (m.motion == Motion.Bounce) LinearEasing else FastOutSlowInEasing), RepeatMode.Reverse),
                label = "lift",
            )
            val sway by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(3200, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "sway")
            val rise = bounceCurve(lift, m.motion)
            val liftPx = with(density) { m.motion.amplitude.toPx() } * rise
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                // Ground shadow: smaller and fainter as the mascot rises.
                Canvas(Modifier.size(height * 0.5f, height * 0.08f)) {
                    val k = 1f - 0.35f * rise
                    drawOval(
                        Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f * k), Color.Transparent)),
                        topLeft = Offset(size.width * (1 - k) / 2, 0f),
                        size = Size(size.width * k, size.height),
                    )
                }
                Image(
                    painterResource(m.res),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            translationY = -liftPx - height.toPx() * 0.03f
                            translationX = shake.value * density.density
                            val breathe = 1f + 0.018f * lift
                            scaleX = entrance.value * breathe
                            scaleY = entrance.value * (breathe + if (m.motion == Motion.Bounce) 0.02f * (1 - lift) else 0f)
                            rotationZ = when (m.motion) {
                                Motion.Sway -> 3f * sway
                                Motion.Wave -> 2f * sway
                                Motion.Guard -> 1.2f * sway
                                else -> 0f
                            }
                        },
                )
            }
        }
    }
}

private val Motion.period get() = when (this) { Motion.Bounce -> 520; Motion.Settled -> 3400; Motion.Wave -> 1400; else -> 2300 }
private val Motion.amplitude get() = when (this) { Motion.Bounce -> 14.dp; Motion.Settled -> 3.dp; Motion.Wave -> 6.dp; else -> 8.dp }

/** Bounces snap off the floor (|sin|-like); everything else eases. */
private fun bounceCurve(x: Float, m: Motion) = if (m == Motion.Bounce) 1f - (1f - x) * (1f - x) else x

/** Each level's aura colour around the mascot. */
val Level.auraColor: Color
    get() = when (this) {
        Level.Starter -> TallyColors.Brand
        Level.Bronze -> Color(0xFFD08A4E)
        Level.Silver -> Color(0xFFC9D3E6)
        Level.Gold -> Color(0xFFFFC94A)
        Level.Legend -> TallyColors.BrandBright
    }
