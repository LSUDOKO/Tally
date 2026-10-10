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
import app.tally.R
import app.tally.ui.theme.TallyColors

/** The mascot's expressions, each tied to a moment in the app. */
enum class Mood(@DrawableRes val res: Int, val motion: Motion = Motion.Float) {
    Hero(R.drawable.mascot_hero),
    Standing(R.drawable.mascot_standing),
    Waving(R.drawable.mascot_waving, Motion.Wave),
    Pointing(R.drawable.mascot_pointing),
    ThumbsUp(R.drawable.mascot_thumbs_up, Motion.Bounce),
    HoldingPhone(R.drawable.mascot_holding_phone),
    Laptop(R.drawable.mascot_laptop, Motion.Settled),
    Calling(R.drawable.mascot_calling),
    Thinking(R.drawable.mascot_thinking, Motion.Sway),
    Victory(R.drawable.mascot_victory, Motion.Bounce),
    Cool(R.drawable.mascot_cool),
    Sleepy(R.drawable.mascot_sleepy, Motion.Settled),
    Confused(R.drawable.mascot_confused, Motion.Sway),
    Excited(R.drawable.mascot_excited, Motion.Bounce),
    WithReceipt(R.drawable.mascot_with_receipt),
    WithShield(R.drawable.mascot_with_shield, Motion.Guard),
}

/** How a mood moves while idle. */
enum class Motion { Float, Bounce, Sway, Wave, Settled, Guard }

/**
 * The animated mascot: idles with its mood's motion over a pulsing brand glow and a ground shadow,
 * pops between expressions with a spring, and shakes once whenever [shakeKey] changes (a blocked
 * attack).
 */
@Composable
fun Mascot(mood: Mood, modifier: Modifier = Modifier, height: Dp = 180.dp, shakeKey: Any? = null, glow: Boolean = true) {
    val density = LocalDensity.current
    val t = rememberInfiniteTransition(label = "mascot")
    val glowPulse by t.animateFloat(0.30f, 0.55f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "glow")

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
                    Brush.radialGradient(listOf(TallyColors.Brand.copy(alpha = glowPulse), Color.Transparent), center = Offset(size.width / 2, size.height * 0.48f), radius = r),
                    radius = r,
                    center = Offset(size.width / 2, size.height * 0.48f),
                )
            }
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
