package app.tally.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tally.R
import app.tally.core.usdc
import app.tally.ui.theme.TallyColors
import app.tally.ui.theme.TallyBrushes
import kotlinx.coroutines.launch

private val Pad = Modifier.padding(horizontal = 20.dp)

@Composable
fun HomeScreen(s: Session) {
    LaunchedEffect(s.ownerAddress) { if (s.ownerAddress != null) s.refresh() }
    // Back on Home: a reorder that wasn't completed is abandoned, so the next scan is a normal one.
    LaunchedEffect(Unit) { s.cancelReorder() }
    @Suppress("UNUSED_VARIABLE") val v = s.logVersion
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Greeting(s)
        if (s.ownerAddress == null) Welcome(s) else Dashboard(s)
        Gap(28)
    }
}

/** The top row: the mascot avatar, where you are, and the merchant and settings switches. */
@Composable
private fun Greeting(s: Session) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.mascot_avatar), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(CircleShape).border(1.dp, TallyBrushes.GlassRim, CircleShape),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Tally", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text(s.ownerAddress?.let { short(it) } ?: s.config.cluster, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
        GlassIconButton(Icons.Outlined.Storefront, "Merchant mode", { s.screen = Screen.Merchant })
        if (s.ownerAddress != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Outlined.Tune, "Settings", { s.screen = Screen.Settings })
        }
    }
}

// ------------------------------------------------------------------------------- not connected

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(s: Session) {
    val scope = rememberCoroutineScope()
    Column(Pad, horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(10)
        Reveal { HeroTitle("Let your agent pay,", "only for what tallies.") }
        Gap(22)
        Reveal(90, Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(326.dp)) {
                Box(Modifier.fillMaxWidth(0.82f).height(70.dp).align(Alignment.TopCenter).glass(RoundedCornerShape(30.dp)).background(TallyColors.Brand.copy(alpha = 0.10f)))
                Box(Modifier.fillMaxWidth(0.91f).height(70.dp).align(Alignment.TopCenter).offset(y = 10.dp).glass(RoundedCornerShape(30.dp)).background(TallyColors.Brand.copy(alpha = 0.16f)))
                Box(Modifier.fillMaxWidth().height(302.dp).align(Alignment.BottomCenter).clip(RoundedCornerShape(32.dp)).background(TallyBrushes.HeroPanel)
                    .border(1.dp, TallyBrushes.GlassRim, RoundedCornerShape(32.dp))) {
                    Image(painterResource(R.drawable.mascot_editorial), contentDescription = "Tally mascot in cobalt streetwear", contentScale = ContentScale.Crop, alignment = Alignment.TopCenter, modifier = Modifier.fillMaxSize())
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent, TallyColors.Ink.copy(alpha = 0.86f)))))
                    Column(Modifier.align(Alignment.BottomStart).padding(22.dp)) {
                        Label("Tally / protected payments", Color.White.copy(alpha = 0.8f))
                        Gap(4)
                        Text("Pay with proof.", style = MaterialTheme.typography.displaySmall, color = Color.White)
                    }
                }
            }
        }
        Gap(18)
        Reveal(160) {
            Text(
                "Your agent proposes. The merchant signs. Tally checks every payment on-chain.",
                style = MaterialTheme.typography.bodyLarge, color = TallyColors.Muted, textAlign = TextAlign.Center,
            )
        }
        Gap(20)
        Reveal(210, Modifier.fillMaxWidth()) { Primary("Connect wallet", { scope.launch { s.connect() } }, icon = Icons.Outlined.AccountBalanceWallet) }
        Gap(8)
        Text("Your owner key stays in Seed Vault. Tally only ever holds a capped agent key.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Faint, textAlign = TextAlign.Center)
        Gap(18)
        Reveal(210, Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Feature(Icons.Outlined.Draw, "Signed\nmenu", TallyColors.Accent, Modifier.weight(1f))
                Feature(Icons.Outlined.Verified, "Fair\nprice", TallyColors.Accent, Modifier.weight(1f))
                Feature(Icons.Outlined.Link, "On-chain\ncheck", TallyColors.Accent, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun Feature(icon: ImageVector, label: String, tint: Color, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, tint, 38.dp)
        Gap(8)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TallyColors.Text, textAlign = TextAlign.Center)
    }
}

// ------------------------------------------------------------------------------------ connected

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Dashboard(s: Session) {
    val scope = rememberCoroutineScope()
    val c = s.capsule
    Column(Pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (c == null && s.offline) {
            // Unknown, not absent: never invite a new capsule just because the network is down.
            OfflineCard(s.store.snapshot)
            return@Column
        }
        if (c == null) {
            Card {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(Mood.Pointing, Modifier.fillMaxWidth(), height = 200.dp)
                    Text("Set up your first capsule", style = MaterialTheme.typography.displaySmall, color = TallyColors.Text, textAlign = TextAlign.Center)
                    Gap(6)
                    Body("Say what your agent may buy, in your own words. Your wallet signs the rules on-chain once.", TallyColors.Muted)
                    Gap(16)
                    Primary("Create a capsule", { s.screen = Screen.NewCapsule }, icon = Icons.Outlined.Add)
                }
            }
            return@Column
        }

        if (s.offline) OfflineCard(s.store.snapshot)

        Reveal { DayLine(s) }

        BalanceStack(s)

        PointsCard(s.rewards) { s.screen = Screen.Rewards }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Outlined.Add, "Add \$10") { scope.launch { s.fund(10_000_000) } }
            QuickAction(Icons.Outlined.Download, "Withdraw", enabled = s.vaultBalance > 0) { scope.launch { s.withdraw(s.vaultBalance) } }
            QuickAction(Icons.Outlined.Tune, "Rules") { s.screen = Screen.NewCapsule }
            QuickAction(Icons.Outlined.Storefront, "Merchant") { s.screen = Screen.Merchant }
        }

        s.store.usual?.let { UsualCard(it) { s.startReorder() } }

        if (s.vaultBalance < c.perTxCap && c.agent == s.agentAddress) {
            Banner(Icons.Outlined.Payments, "Your vault has \$${usdc(s.vaultBalance)}, less than one \$${usdc(c.perTxCap)} purchase. Add funds so your agent isn't stopped at the counter.", "Add \$10") { scope.launch { s.fund(10_000_000) } }
        }

        if (c.agent != s.agentAddress) {
            Banner(Icons.Outlined.PhoneAndroid, "This capsule's agent key lives on another phone (or an earlier install), so this phone can't pay from it.", "Make this phone the agent") { scope.launch { s.adoptAgent() } }
        }
        if (s.agentSol < 10_000_000) {
            Banner(Icons.Outlined.LocalGasStation, "Your agent needs a little SOL for network fees and receipts (${"%.4f".format(s.agentSol / 1e9)} SOL left).", "Top up agent with 0.05 SOL") { scope.launch { s.topUpAgent() } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { Stat("Blocked · 7d", "${s.store.attacksBlockedThisWeek()}", TallyColors.Blocked, icon = Icons.Outlined.Shield) }
            Card(Modifier.weight(1f)) { Stat("Spent today", "\$${usdc(s.spentToday)}", TallyColors.Paid, icon = Icons.Outlined.Payments) }
        }

        WeeklyCard(s.weekly)

        if (c.flags and 2 != 0) {
            if (s.promoClaimed) {
                Chip("Seeker launch promo claimed", TallyColors.Paid, Icons.Outlined.CardGiftcard)
            } else {
                Card(accent = TallyColors.BrandBright) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(Mood.Excited, Modifier.size(84.dp), height = 84.dp, glow = false)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Seeker launch promo", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                            Text("One claim per Seeker Genesis Token.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                            Gap(8)
                            Secondary("Claim", { scope.launch { s.claimPromo(LAUNCH_PROMO) } }, icon = Icons.Outlined.CardGiftcard)
                        }
                    }
                }
            }
        }
    }
}

/** The capsule as a stack of glass cards (the reference's layered artwork), mascot peeking in. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BalanceStack(s: Session) {
    val c = s.capsule ?: return
    val pct = if (c.dailyCap > 0) (s.spentToday.toFloat() / c.dailyCap).coerceIn(0f, 1f) else 0f
    val bar by animateFloatAsState(pct, tween(900), label = "spent")
    Box(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(
            Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp)).background(TallyColors.GlassStrong.copy(alpha = 0.35f)).padding(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.62f)) {
                Label("Coffee capsule")
                Gap(6)
                Big("\$${usdc(s.vaultBalance)}", TallyColors.Text, 44)
                Text("USDC in vault", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                Gap(16)
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(TallyColors.GlassHigh, cornerRadius = CornerRadius(size.height))
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(TallyColors.Brand, TallyColors.BrandCore)),
                        size = Size(size.width * bar.coerceAtLeast(0.03f), size.height), cornerRadius = CornerRadius(size.height),
                    )
                }
                Gap(8)
                Text("\$${usdc(s.spentToday)} of \$${usdc(c.dailyCap)} today · max \$${usdc(c.perTxCap)} each", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                Gap(12)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.frozen) Chip("Frozen", TallyColors.Blocked, Icons.Outlined.AcUnit)
                    if (c.flags and 2 != 0) Chip("Seeker verified", TallyColors.BrandCore, Icons.Outlined.VerifiedUser)
                    if (c.flags and 1 != 0) Chip("Lighthouse", TallyColors.BrandCore, Icons.Outlined.Wallet)
                }
            }
        }
        Mascot(
            if (c.frozen) Mood.Sleepy else Mood.Cool,
            Modifier.align(Alignment.BottomEnd).offset(x = 6.dp).size(width = 150.dp, height = 210.dp).graphicsLayer { clip = false },
            height = 210.dp, glow = false,
        )
    }
}

/** "Good evening." over one plain line about what the agent can still do today. */
@Composable
private fun DayLine(s: Session) {
    val c = s.capsule ?: return
    val hour = java.time.LocalTime.now().hour
    val part = when (hour) { in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; else -> "Good evening" }
    val left = (c.dailyCap - s.spentToday).coerceAtLeast(0)
    Column(Modifier.padding(top = 6.dp)) {
        Text("$part.", style = MaterialTheme.typography.displayMedium, color = TallyColors.Text)
        Text(
            if (c.frozen) "Your capsule is frozen. Nothing can be spent." else "Your agent can spend \$${usdc(left)} more today.",
            style = MaterialTheme.typography.headlineMedium, color = TallyColors.Muted,
        )
    }
}

private fun Modifier.matchParentWidth(f: Float) = this.fillMaxWidth(f)

@Composable
private fun QuickAction(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box(Modifier.size(56.dp).glass(CircleShape).pressable(onClick, enabled), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, tint = if (enabled) TallyColors.Text else TallyColors.Faint, modifier = Modifier.size(24.dp))
        }
        Gap(6)
        Text(label, style = MaterialTheme.typography.labelMedium, color = TallyColors.Muted, textAlign = TextAlign.Center)
    }
}

/** Something the user should fix, with the one action that fixes it. */
@Composable
private fun Banner(icon: ImageVector, text: String, action: String, onClick: () -> Unit) {
    Card(accent = TallyColors.NeedsYou) {
        Column {
            Row(verticalAlignment = Alignment.Top) {
                IconTile(icon, TallyColors.NeedsYou, 40.dp)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = TallyColors.Text, modifier = Modifier.padding(start = 12.dp).weight(1f))
            }
            Gap(12)
            Primary(action, onClick, color = TallyColors.NeedsYou)
        }
    }
}
