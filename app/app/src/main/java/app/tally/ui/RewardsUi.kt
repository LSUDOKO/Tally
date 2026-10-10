package app.tally.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.tally.core.Level
import app.tally.core.Rewards
import app.tally.core.Summary
import app.tally.core.Usual
import app.tally.core.usdc
import app.tally.data.CapsuleSnapshot
import app.tally.ui.theme.TallyColors

// ------------------------------------------------------------------------------- Home cards

/** Points, level and streak, with the mascot wearing the level's look. */
@Composable
fun PointsCard(t: Rewards.Tally, onOpen: () -> Unit) {
    val bar by animateFloatAsState(t.progress, tween(900), label = "level")
    Row(
        Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), t.level.auraColor).pressable(onOpen).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Mascot(Mood.ThumbsUp, Modifier.size(width = 84.dp, height = 96.dp), height = 96.dp, level = t.level)
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Label(t.level.title, t.level.auraColor)
            Row(verticalAlignment = Alignment.Bottom) {
                Big("${t.points}", TallyColors.Text, 30)
                Text(" pts", style = MaterialTheme.typography.labelLarge, color = TallyColors.Muted, modifier = Modifier.padding(bottom = 5.dp))
            }
            ProgressBar(bar, t.level.auraColor)
            Gap(6)
            Text(
                t.next?.let { "${it.toNext(t)} to ${it.title}" } ?: "Top level reached",
                style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted,
            )
        }
        Column(Modifier.padding(start = 10.dp).width(56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.LocalFireDepartment, contentDescription = null, tint = if (t.streakDays > 0) TallyColors.NeedsYou else TallyColors.Faint, modifier = Modifier.size(28.dp))
            Text("${t.streakDays}", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text(if (t.streakDays == 1) "day" else "days", style = MaterialTheme.typography.labelSmall, color = TallyColors.Muted)
        }
    }
}

private fun Level.toNext(t: Rewards.Tally) = (minPoints - t.points).coerceAtLeast(0)

@Composable
private fun ProgressBar(fraction: Float, color: androidx.compose.ui.graphics.Color) {
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        drawRoundRect(TallyColors.GlassHigh, cornerRadius = CornerRadius(size.height))
        drawRoundRect(
            Brush.horizontalGradient(listOf(color.copy(alpha = 0.7f), color)),
            size = Size(size.width * fraction.coerceIn(0.03f, 1f), size.height), cornerRadius = CornerRadius(size.height),
        )
    }
}

/** One-tap reorder of the saved usual (it still checks today's signed menu first). */
@Composable
fun UsualCard(u: Usual, onReorder: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp)).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(categoryIcon(u.category), TallyColors.Accent, 44.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Label("Your usual")
            Text(u.name, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text("\$${usdc(u.priceMicro)} · ${u.merchantName}", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
        Box(Modifier.clip(RoundedCornerShape(50)).pressable(onReorder)) {
            Chip("Order", TallyColors.Accent, Icons.Outlined.Replay)
        }
    }
}

/** The week at a glance: what the agent bought, what it kept and what Tally stopped. */
@Composable
fun WeeklyCard(w: Summary) {
    Card {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("This week")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniStat(Icons.Outlined.Payments, "\$${usdc(w.spentMicro)}", "${w.purchases} bought", TallyColors.Paid, Modifier.weight(1f))
                MiniStat(Icons.Outlined.Savings, "\$${usdc(w.savedMicro)}", "kept", TallyColors.Accent, Modifier.weight(1f))
                MiniStat(Icons.Outlined.Shield, "${w.blocked}", "blocked", TallyColors.Blocked, Modifier.weight(1f))
            }
            w.topMerchant?.let {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Storefront, contentDescription = null, tint = TallyColors.Muted, modifier = Modifier.size(16.dp))
                    Text("  Most visited: $it · active ${w.activeDays} of 7 days", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                }
            }
            if (w.protectedMicro > 0) {
                Text("Tally refused \$${usdc(w.protectedMicro)} in payments you didn't intend.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
            }
        }
    }
}

@Composable
private fun MiniStat(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, tint: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.Start) {
        IconTile(icon, tint, 32.dp)
        Gap(6)
        Text(value, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TallyColors.Muted)
    }
}

/** Shown when the chain can't be reached: the last known capsule, read-only. */
@Composable
fun OfflineCard(snap: CapsuleSnapshot?) {
    Card(accent = TallyColors.NeedsYou) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Outlined.CloudOff, TallyColors.NeedsYou, 40.dp)
            Column(Modifier.padding(start = 12.dp)) {
                Text("Offline", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                Text(
                    snap?.let { "Last seen: \$${usdc(it.vaultMicro)} in vault, \$${usdc(it.spentTodayMicro)} of \$${usdc(it.dailyCapMicro)} spent. Payments need a fresh signed menu and the network." }
                        ?: "Payments need a fresh signed menu and the network. Rules and receipts stay readable.",
                    style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted,
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------- Rewards screen

@Composable
fun RewardsScreen(s: Session) {
    val t = s.rewards
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Tally Points", onBack = { s.screen = Screen.Home })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Mascot(Mood.Victory, Modifier.fillMaxWidth(), height = 230.dp, level = t.level)
                Label("${t.level.title} level", t.level.auraColor)
                Big("${t.points} points", TallyColors.Text, 34)
                Text(t.level.perk, style = MaterialTheme.typography.bodyMedium, color = TallyColors.Muted, textAlign = TextAlign.Center)
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("How points work")
                    Rule(Icons.Outlined.Verified, "+${Rewards.POINTS_PER_PURCHASE} for every settled purchase of \$1 or more", TallyColors.Paid)
                    Rule(Icons.Outlined.LocalFireDepartment, "Daily streaks multiply points, up to 1.5×", TallyColors.NeedsYou)
                    Rule(Icons.Outlined.EmojiEvents, "Seeker-verified capsules earn 2×", TallyColors.Accent)
                    Rule(Icons.Outlined.Lock, "Paying your own counter, disputed buys, red-team runs and anything over ${Rewards.MAX_COUNTED_PER_DAY} a day earn nothing", TallyColors.Muted)
                }
            }
            Label("Levels")
            Level.entries.forEach { lvl ->
                val unlocked = t.points >= lvl.minPoints
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), if (lvl == t.level) lvl.auraColor else null).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(64.dp).clip(CircleShape).background(TallyColors.Glass).border(2.dp, lvl.auraColor.copy(alpha = if (unlocked) 0.9f else 0.25f), CircleShape), contentAlignment = Alignment.Center) {
                        if (unlocked) Mascot(Mood.Standing, Modifier.size(60.dp), height = 60.dp, glow = true, level = lvl)
                        else Icon(Icons.Outlined.Lock, contentDescription = "Locked", tint = TallyColors.Faint)
                    }
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(lvl.title, style = MaterialTheme.typography.titleMedium, color = if (unlocked) TallyColors.Text else TallyColors.Muted)
                        Text(lvl.perk, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                    }
                    Text(if (lvl.minPoints == 0) "start" else "${lvl.minPoints}", style = MaterialTheme.typography.labelMedium, color = lvl.auraColor)
                }
            }
            Text("Points are counted on this phone from your settled on-chain receipts. They can't be bought, sold or transferred.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Faint)
            Gap(24)
        }
    }
}

@Composable
private fun Rule(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, 32.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TallyColors.Text, modifier = Modifier.padding(start = 10.dp))
    }
}

/** One-time celebration when a new level unlocks. */
@Composable
fun LevelUpDialog(level: Level, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), level.auraColor).background(TallyColors.Surface.copy(alpha = 0.9f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.Victory, Modifier.fillMaxWidth(), height = 200.dp, level = level, shakeKey = level)
            Label("Level up", level.auraColor)
            Text(level.title, style = MaterialTheme.typography.displayMedium, color = TallyColors.Text)
            Gap(6)
            Text(level.perk, style = MaterialTheme.typography.bodyMedium, color = TallyColors.Muted, textAlign = TextAlign.Center)
            Gap(18)
            Primary("Nice", onDismiss)
        }
    }
}
