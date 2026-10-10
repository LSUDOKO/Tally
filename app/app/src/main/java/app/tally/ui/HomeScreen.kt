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
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CardGiftcard
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Wallet
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Draw
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
import kotlinx.coroutines.launch

private val Pad = Modifier.padding(horizontal = 20.dp)

@Composable
fun HomeScreen(s: Session) {
    LaunchedEffect(s.ownerAddress) { if (s.ownerAddress != null) s.refresh() }
    @Suppress("UNUSED_VARIABLE") val v = s.logVersion
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Greeting(s)
        if (s.ownerAddress == null) Welcome(s) else Dashboard(s)
        Gap(28)
    }
}

/** "Hello 👋" header with the mascot avatar, the cluster and the merchant switch. */
@Composable
private fun Greeting(s: Session) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(
            painterResource(R.drawable.mascot_avatar), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(46.dp).clip(CircleShape).border(2.dp, Brush.verticalGradient(listOf(TallyColors.BrandBright, TallyColors.BrandDeep)), CircleShape),
        )
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Hello 👋", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text(s.ownerAddress?.let { short(it) } ?: "Tally · ${s.config.cluster}", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
        GlassIconButton(Icons.Rounded.Storefront, "Merchant mode", { s.screen = Screen.Merchant })
        if (s.ownerAddress != null) {
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            GlassIconButton(Icons.Rounded.Tune, "Settings", { s.screen = Screen.Settings })
        }
    }
}

// ------------------------------------------------------------------------------- not connected

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Welcome(s: Session) {
    val scope = rememberCoroutineScope()
    Column(Pad, horizontalAlignment = Alignment.CenterHorizontally) {
        Gap(8)
        HeroTitle("Let your agent pay,", "only for what tallies.")
        Gap(4)
        Mascot(Mood.Waving, Modifier.fillMaxWidth(), height = 280.dp)
        Text(
            "Your agent proposes. The merchant signs. Tally checks it on-chain, so a tricked agent still can't overpay.",
            style = MaterialTheme.typography.bodyLarge, color = TallyColors.Muted, textAlign = TextAlign.Center,
        )
        Gap(18)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Feature(Icons.Rounded.Draw, "Merchant\nsigned", TallyColors.Accent, Modifier.weight(1f))
            Feature(Icons.Rounded.Verified, "Cheapest\nmatch", TallyColors.Paid, Modifier.weight(1f))
            Feature(Icons.Rounded.Link, "On-chain\nchecks", TallyColors.BrandBright, Modifier.weight(1f))
        }
        Gap(22)
        Primary("Connect wallet", { scope.launch { s.connect() } }, icon = Icons.Rounded.AccountBalanceWallet)
        Gap(8)
        Text("Your owner key stays in Seed Vault. Tally only ever holds a capped agent key.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Faint, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Feature(icon: ImageVector, label: String, tint: Color, modifier: Modifier) {
    Column(modifier.glass(RoundedCornerShape(20.dp)).padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconTile(icon, tint, 40.dp)
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
        if (c == null) {
            Card {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Mascot(Mood.Pointing, Modifier.fillMaxWidth(), height = 200.dp)
                    Text("Set up your first capsule", style = MaterialTheme.typography.displaySmall, color = TallyColors.Text, textAlign = TextAlign.Center)
                    Gap(6)
                    Body("Say what your agent may buy, in your own words. Your wallet signs the rules on-chain once.", TallyColors.Muted)
                    Gap(16)
                    Primary("Create a capsule", { s.screen = Screen.NewCapsule }, icon = Icons.Rounded.Add)
                }
            }
            return@Column
        }

        BalanceStack(s)

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            QuickAction(Icons.Rounded.Add, "Add \$10") { scope.launch { s.fund(10_000_000) } }
            QuickAction(Icons.Rounded.Download, "Withdraw", enabled = s.vaultBalance > 0) { scope.launch { s.withdraw(s.vaultBalance) } }
            QuickAction(Icons.Rounded.Tune, "Rules") { s.screen = Screen.NewCapsule }
            QuickAction(Icons.Rounded.Storefront, "Merchant") { s.screen = Screen.Merchant }
        }

        if (c.agent != s.agentAddress) {
            Banner(Icons.Rounded.PhoneAndroid, "This capsule's agent key lives on another phone (or an earlier install), so this phone can't pay from it.", "Make this phone the agent") { scope.launch { s.adoptAgent() } }
        }
        if (s.agentSol < 10_000_000) {
            Banner(Icons.Rounded.LocalGasStation, "Your agent needs a little SOL for network fees and receipts (${"%.4f".format(s.agentSol / 1e9)} SOL left).", "Top up agent with 0.05 SOL") { scope.launch { s.topUpAgent() } }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(Modifier.weight(1f)) { Stat("Blocked · 7d", "${s.store.attacksBlockedThisWeek()}", TallyColors.Blocked, icon = Icons.Rounded.Shield) }
            Card(Modifier.weight(1f)) { Stat("Spent today", "\$${usdc(s.spentToday)}", TallyColors.Paid, icon = Icons.Rounded.Payments) }
        }

        if (c.flags and 2 != 0) {
            if (s.promoClaimed) {
                Chip("Seeker launch promo claimed", TallyColors.Paid, Icons.Rounded.CardGiftcard)
            } else {
                Card(accent = TallyColors.BrandBright) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mascot(Mood.Excited, Modifier.size(84.dp), height = 84.dp, glow = false)
                        Column(Modifier.weight(1f).padding(start = 10.dp)) {
                            Text("Seeker launch promo", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                            Text("One claim per Seeker Genesis Token.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                            Gap(8)
                            Secondary("Claim", { scope.launch { s.claimPromo(LAUNCH_PROMO) } }, icon = Icons.Rounded.CardGiftcard)
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
    Box(Modifier.fillMaxWidth().padding(top = 26.dp)) {
        // Back layers.
        Box(Modifier.matchParentWidth(0.84f).align(Alignment.TopCenter).offset(y = (-24).dp).height(70.dp).clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF7C5CFF).copy(alpha = 0.55f), TallyColors.BrandBright.copy(alpha = 0.55f)))))
        Box(Modifier.matchParentWidth(0.92f).align(Alignment.TopCenter).offset(y = (-12).dp).height(70.dp).clip(RoundedCornerShape(26.dp)).background(Brush.horizontalGradient(listOf(TallyColors.BrandBright.copy(alpha = 0.7f), Color(0xFF59B8FF).copy(alpha = 0.6f)))))
        // Front card.
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1C4FD8), TallyColors.Brand.copy(alpha = 0.85f), Color(0xFF0B1F5C))))
                .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.05f))), RoundedCornerShape(28.dp))
                .padding(20.dp),
        ) {
            Column(Modifier.fillMaxWidth(0.62f)) {
                Label("Coffee capsule", Color.White.copy(alpha = 0.75f))
                Gap(6)
                Big("\$${usdc(s.vaultBalance)}", Color.White, 38)
                Text("USDC in vault", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
                Gap(16)
                Canvas(Modifier.fillMaxWidth().height(8.dp)) {
                    drawRoundRect(Color.White.copy(alpha = 0.2f), cornerRadius = CornerRadius(size.height))
                    drawRoundRect(
                        Brush.horizontalGradient(listOf(Color(0xFF7CFFC4), TallyColors.Paid)),
                        size = Size(size.width * bar.coerceAtLeast(0.03f), size.height), cornerRadius = CornerRadius(size.height),
                    )
                }
                Gap(8)
                Text("\$${usdc(s.spentToday)} of \$${usdc(c.dailyCap)} today · max \$${usdc(c.perTxCap)} each", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                Gap(12)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (c.frozen) Chip("Frozen", TallyColors.Blocked, Icons.Rounded.AcUnit)
                    if (c.flags and 2 != 0) Chip("Seeker verified", Color.White, Icons.Rounded.VerifiedUser)
                    if (c.flags and 1 != 0) Chip("Lighthouse", Color.White, Icons.Rounded.Wallet)
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
