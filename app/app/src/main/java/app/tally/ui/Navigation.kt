package app.tally.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tally.ui.theme.TallyBrushes
import app.tally.ui.theme.TallyColors

/** Screens reachable from the bottom bar; the rest are pushed on top of them. */
val TabScreens = setOf(Screen.Home, Screen.Receipts, Screen.RedTeam, Screen.Settings)

private class Tab(val screen: Screen, val icon: ImageVector, val label: String)

private val left = listOf(Tab(Screen.Home, Icons.Rounded.Home, "Home"), Tab(Screen.Receipts, Icons.AutoMirrored.Rounded.ReceiptLong, "Receipts"))
private val right = listOf(Tab(Screen.RedTeam, Icons.Rounded.Security, "Red team"), Tab(Screen.Settings, Icons.Rounded.Tune, "Settings"))

/** Floating glass bar with a raised cobalt Scan button in the middle, like the reference's "+". */
@Composable
fun BottomBar(current: Screen, scanEnabled: Boolean, onSelect: (Screen) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp).height(78.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().height(66.dp).glass(RoundedCornerShape(33.dp)).background(TallyColors.Ink.copy(alpha = 0.55f)).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            left.forEach { TabButton(it, current == it.screen) { onSelect(it.screen) } }
            Box(Modifier.size(64.dp))
            right.forEach { TabButton(it, current == it.screen) { onSelect(it.screen) } }
        }
        ScanButton(scanEnabled) { onSelect(Screen.Scan) }
    }
}

@Composable
private fun TabButton(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) Color.White else TallyColors.Muted, label = "tab")
    Box(
        Modifier.size(width = 56.dp, height = 46.dp).clip(RoundedCornerShape(23.dp))
            .then(if (selected) Modifier.background(TallyColors.Brand.copy(alpha = 0.22f)).border(1.dp, TallyColors.BrandBright.copy(alpha = 0.6f), RoundedCornerShape(23.dp)) else Modifier)
            .pressable(onClick, role = Role.Tab),
        contentAlignment = Alignment.Center,
    ) { Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(24.dp)) }
}

@Composable
private fun ScanButton(enabled: Boolean, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "scan").animateFloat(1f, 1.06f, infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse")
    Box(
        Modifier.offset(y = (-14).dp).size(70.dp)
            .graphicsLayer { if (enabled) { scaleX = pulse; scaleY = pulse } }
            .shadow(22.dp, CircleShape, ambientColor = TallyColors.Brand, spotColor = TallyColors.Brand)
            .clip(CircleShape)
            .background(TallyBrushes.PrimaryButton)
            .border(2.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.6f), Color.White.copy(alpha = 0.1f))), CircleShape)
            .pressable(onClick, enabled),
        contentAlignment = Alignment.Center,
    ) { Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Scan or tap to pay", tint = Color.White, modifier = Modifier.size(32.dp)) }
}
