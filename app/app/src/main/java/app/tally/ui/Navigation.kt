package app.tally.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.tally.ui.theme.TallyColors

/** Screens reachable from the bottom bar; the rest are pushed on top of them. */
val TabScreens = setOf(Screen.Home, Screen.Receipts, Screen.RedTeam, Screen.Settings)

private class Tab(val screen: Screen, val icon: ImageVector, val label: String)

private val left = listOf(Tab(Screen.Home, Icons.Outlined.Home, "Home"), Tab(Screen.Receipts, Icons.AutoMirrored.Outlined.ReceiptLong, "Receipts"))
private val right = listOf(Tab(Screen.RedTeam, Icons.Outlined.Security, "Red team"), Tab(Screen.Settings, Icons.Outlined.Tune, "Settings"))

/** A floating smoked-glass bar with the scan orb set into its middle. */
@Composable
fun BottomBar(current: Screen, scanEnabled: Boolean, onSelect: (Screen) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 10.dp).height(80.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).glass(CircleShape, null).background(TallyColors.GlassStrong).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            left.forEach { TabButton(it, current == it.screen) { onSelect(it.screen) } }
            Box(Modifier.size(64.dp))
            right.forEach { TabButton(it, current == it.screen) { onSelect(it.screen) } }
        }
        ScanOrb(scanEnabled) { onSelect(Screen.Scan) }
    }
}

@Composable
private fun TabButton(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    val tint by animateColorAsState(if (selected) TallyColors.Text else TallyColors.Faint, label = "tab")
    val lit by animateFloatAsState(if (selected) 1f else 0f, tween(260), label = "lit")
    Column(
        Modifier.size(width = 60.dp, height = 52.dp).pressable(onClick, role = Role.Tab),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(tab.icon, contentDescription = tab.label, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(5.dp))
        // The selected tab is marked by a short lit bar, not a filled pill.
        Box(Modifier.size(width = 16.dp, height = 3.dp).graphicsLayer { alpha = lit; scaleX = 0.4f + 0.6f * lit }.background(TallyColors.BrandBright, CircleShape))
    }
}

@Composable
private fun ScanOrb(enabled: Boolean, onClick: () -> Unit) {
    val breathe by rememberInfiniteTransition(label = "scan").animateFloat(1f, 1.05f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "breathe")
    OrbButton(
        Icons.Outlined.QrCodeScanner, "Scan or tap to pay", onClick,
        Modifier.offset(y = (-14).dp).graphicsLayer { if (enabled) { scaleX = breathe; scaleY = breathe } },
        size = 64.dp, enabled = enabled,
    )
}
