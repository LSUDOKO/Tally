package app.tally.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BakeryDining
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cookie
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.GppBad
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.LocalDrink
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.LocalGasStation
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material.icons.outlined.AddCard
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.PriceChange
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.AttachMoney
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.BuildConfig
import app.tally.agent.GroqAgent
import androidx.compose.material.icons.outlined.Replay
import app.tally.core.Hex
import app.tally.core.Reorder
import app.tally.core.ReorderCheck
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Add
import app.tally.core.MenuIssue
import app.tally.core.Purchase
import app.tally.core.RuleParser
import app.tally.core.Tally
import app.tally.core.usdc
import app.tally.ui.theme.TallyColors
import kotlinx.coroutines.launch

private val Pad = Modifier.padding(horizontal = 20.dp)

@Composable
fun ExplorerLink(session: Session, sig: String?, label: String = "View on Solana Explorer") {
    if (sig == null) return
    val ctx = LocalContext.current
    Row(
        Modifier.padding(top = 8.dp).glass(CircleShape, TallyColors.BrandBright)
            .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.config.explorerTx(sig)))) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, tint = TallyColors.Accent, modifier = Modifier.size(15.dp))
        Text(label, color = TallyColors.Accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------------------- New capsule

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NewCapsuleScreen(s: Session) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("Coffee, max \$6, \$15 a day, cheapest by default") }
    var fund by remember { mutableStateOf("20") }
    var lighthouse by remember { mutableStateOf((s.capsule?.flags ?: 0) and 1 != 0) }
    val existing = s.capsule != null
    val parsed = RuleParser.parse(text)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(if (existing) "Capsule rules" else "New capsule", onBack = { s.screen = Screen.Home })
        Column(Pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.Pointing, Modifier.size(width = 120.dp, height = 170.dp), height = 170.dp)
                Column(Modifier.weight(1f).padding(start = 6.dp)) {
                    HeroTitle(if (existing) "Change the" else "Set the rules", if (existing) "rules." else "once.", align = TextAlign.Start)
                    Gap(6)
                    Body("Say what your agent may buy. You confirm the exact rules before your wallet signs.", TallyColors.Muted)
                }
            }
            GlassField(text, { text = it }, "Your rules", minLines = 2, leading = Icons.Outlined.EditNote)
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Outlined.Draw, TallyColors.Accent, 36.dp)
                        Label("  What your wallet will sign")
                    }
                    Gap(12)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        parsed.categories.forEach { Chip(RuleParser.CATEGORY_NAMES[it] ?: "Category $it", TallyColors.Accent, Icons.Outlined.Category) }
                        Chip("≤ \$${usdc(parsed.perTxCap)} per purchase", TallyColors.Accent, Icons.Outlined.Payments)
                        Chip("≤ \$${usdc(parsed.dailyCap)} per day", TallyColors.Accent, Icons.Outlined.CalendarToday)
                        Chip("Cheapest by default", TallyColors.Accent, Icons.Outlined.Verified)
                        Chip("Bonded merchants only", TallyColors.Accent, Icons.Outlined.VerifiedUser)
                        Chip("Expires in ~30 days", TallyColors.Muted, Icons.Outlined.Schedule)
                    }
                    Gap(12)
                    Body("Anything pricier than the cheapest match, from an unbonded merchant, or on a long-lived menu will need your tap.", TallyColors.Muted)
                }
            }
            ToggleRow(Icons.Outlined.Lightbulb, "Lighthouse assertion", "Every payment must end with an exact vault-balance check (defence in depth).", lighthouse) { lighthouse = it }
            if (!existing) {
                if (s.sgt != null) Chip("Seeker Genesis Token found: this capsule will be Seeker-verified", TallyColors.Paid, Icons.Outlined.VerifiedUser)
                GlassField(fund, { fund = it.filter { c -> c.isDigit() || c == '.' } }, "Fund with (USDC)", singleLine = true, leading = Icons.Outlined.AttachMoney)
                Primary("Approve in wallet", {
                    scope.launch { s.createCapsule(parsed, ((fund.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong(), lighthouse) }
                }, icon = Icons.Outlined.AccountBalanceWallet)
            } else {
                Primary("Update rules in wallet", { scope.launch { s.updateRules(parsed, lighthouse) } }, icon = Icons.Outlined.AccountBalanceWallet)
            }
            Gap(24)
        }
    }
}

// ---------------------------------------------------------------------------- Checkout

/** Category glyphs for menu rows (docs/07 category codes). */
fun categoryIcon(code: Int): ImageVector = when (code) {
    0 -> Icons.Outlined.BakeryDining
    1 -> Icons.Outlined.LocalDrink
    2 -> Icons.Outlined.Cookie
    3 -> Icons.Outlined.LocalCafe
    4 -> Icons.Outlined.Train
    5 -> Icons.Outlined.ShoppingBasket
    else -> Icons.Outlined.ShoppingBag
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(s: Session) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val p = s.payload ?: return run { s.screen = Screen.Scan }
    val m = s.merchant
    var request by remember { mutableStateOf("oat latte") }
    LaunchedEffect(s.payResult) {
        s.payResult?.let { haptics.performHapticFeedback(if (it.ok) HapticFeedbackType.Confirm else HapticFeedbackType.Reject) }
    }
    val pick = s.agentResult?.pick
    val pickIndex = pick?.let { s.itemName(p, it.itemId)?.first }
    val needsOwner = pickIndex != null && s.capsule != null && m != null &&
        Purchase.needsOwner(p.menu.items[pickIndex], m.skrBonded, s.capsule!!.minMerchantBond, p.menu.window)
    val mood = when {
        s.payResult?.ok == true -> Mood.Victory
        s.payResult != null -> Mood.WithShield
        s.agentResult != null && pick == null -> Mood.Confused
        pick != null && needsOwner -> Mood.Pointing
        pick != null -> Mood.ThumbsUp
        else -> Mood.HoldingPhone
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(m?.name ?: "Merchant", onBack = { s.screen = Screen.Home })
        Column(Pad, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded", if ((m?.skrBonded ?: 0) >= (s.capsule?.minMerchantBond ?: 0)) TallyColors.Paid else TallyColors.NeedsYou, Icons.Outlined.VerifiedUser)
                if (p.menu.window <= Tally.MAX_AUTONOMOUS_WINDOW) Chip("Live menu", TallyColors.Accent, Icons.Outlined.Bolt) else Chip("Extended menu", TallyColors.NeedsYou, Icons.Outlined.Schedule)
                if ((m?.powDifficulty ?: 0) > 0) Chip("Busy: PoW", TallyColors.NeedsYou, Icons.Outlined.Memory)
            }
            s.proposal?.takeIf { it.index >= 0 }?.let { pr ->
                val item = p.menu.items[pr.index]
                val owner = s.capsule != null && m != null && Purchase.needsOwner(item, m.skrBonded, s.capsule!!.minMerchantBond, p.menu.window)
                Card(accent = TallyColors.BrandBright) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.SmartToy, TallyColors.Accent, 40.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Label("Proposed by an outside agent")
                                Text("${p.names[pr.index]} · \$${usdc(item.price)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                            }
                        }
                        Gap(8)
                        Body("An agent outside Tally suggested this. Nothing is paid until you tap; the signed menu and your rules are checked on-chain.", TallyColors.Muted)
                        Gap(10)
                        Primary(
                            if (owner) "Review and pay \$${usdc(item.price)}" else "Pay \$${usdc(item.price)}",
                            { scope.launch { s.pay(pr.index, "Proposed by an outside agent") } },
                            color = if (owner) TallyColors.NeedsYou else TallyColors.Brand,
                            icon = if (owner) Icons.Outlined.Visibility else Icons.Outlined.Bolt,
                        )
                    }
                }
            }
            val usual = s.reorder
            val check = s.reorderCheck
            if (usual != null && check != null) {
                val why = Reorder.explain(check, usual)
                Card(accent = if (why == null) TallyColors.Paid else TallyColors.NeedsYou) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Replay, if (why == null) TallyColors.Paid else TallyColors.NeedsYou, 40.dp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Label("Your usual")
                                Text("${usual.name} · \$${usdc(usual.priceMicro)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                            }
                        }
                        Gap(10)
                        if (why == null) {
                            Body("Same counter, same item, same signed price.", TallyColors.Muted)
                            Gap(10)
                            Primary("Pay your usual \$${usdc(usual.priceMicro)}", { scope.launch { s.payUsual() } }, icon = Icons.Outlined.Bolt)
                        } else {
                            Body(why, TallyColors.Text)
                            Gap(10)
                            if (check is ReorderCheck.PriceChanged) {
                                Primary("Pay the new price \$${usdc(check.newMicro)}", { scope.launch { s.payUsual() } }, color = TallyColors.NeedsYou, icon = Icons.Outlined.Visibility)
                                Gap(6)
                            }
                            Secondary("Not now", { s.cancelReorder() })
                        }
                    }
                }
            }
            if (s.issues.isNotEmpty()) {
                Card(accent = TallyColors.Blocked) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.GppBad, TallyColors.Blocked, 36.dp)
                            Label("  Menu check failed", TallyColors.Blocked)
                        }
                        Gap(8)
                        s.issues.forEach {
                            Body(
                                when (it) {
                                    MenuIssue.BadSignature -> "Not signed by this merchant's registered key."
                                    MenuIssue.RootMismatch, MenuIssue.ItemCountMismatch -> "Items don't match what the merchant signed."
                                    MenuIssue.NotYetValid, MenuIssue.Expired -> "This menu isn't fresh. Scan again."
                                    MenuIssue.ExtendedWindow -> "Long-lived menu: every purchase will need your tap."
                                    MenuIssue.WindowTooLong -> "Menu window too long to be accepted."
                                    MenuIssue.FloorContradiction -> "The merchant signed a contradictory cheapest price. Reportable."
                                },
                            )
                        }
                        if (MenuIssue.FloorContradiction in s.issues) {
                            Gap(10)
                            Primary("Report merchant (slash its bond)", { scope.launch { s.reportFraud() } }, color = TallyColors.Blocked, icon = Icons.Outlined.Gavel)
                        }
                    }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("Signed menu · shown to you, not the agent")
                    p.rows().forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(categoryIcon(r.item.category), TallyColors.Accent, 40.dp)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(r.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                                r.description?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Muted) }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${usdc(r.item.price)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                                if (r.item.price == r.item.floor) Text("cheapest", style = androidx.compose.material3.MaterialTheme.typography.labelSmall, color = TallyColors.Paid)
                            }
                            val inCart = i in s.cart
                            Box(
                                Modifier.padding(start = 10.dp).size(34.dp).clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(if (inCart) TallyColors.Brand else TallyColors.Glass).clickable { s.toggleCart(i) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(if (inCart) Icons.Outlined.Check else Icons.Outlined.Add, contentDescription = if (inCart) "Remove ${r.name} from cart" else "Add ${r.name} to cart", tint = TallyColors.Text, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
            s.cartPlan()?.takeIf { it.lines.isNotEmpty() }?.let { plan ->
                Row(
                    Modifier.fillMaxWidth().glass(RoundedCornerShape(50), TallyColors.BrandBright).clickable { s.cartOpen = true }.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.ShoppingCart, contentDescription = null, tint = TallyColors.Text)
                    Text("  Cart · ${plan.lines.size} item${if (plan.lines.size == 1) "" else "s"}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text, modifier = Modifier.weight(1f))
                    Text("\$${usdc(plan.totalMicro)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                }
            }
            s.cartResults.takeIf { it.isNotEmpty() }?.let { results ->
                Card(accent = if (results.all { it.ok }) TallyColors.Paid else TallyColors.Blocked) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Cart result")
                        results.forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Block, contentDescription = null, tint = if (r.ok) TallyColors.Paid else TallyColors.Blocked, modifier = Modifier.size(18.dp))
                                Text("  ${r.title}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TallyColors.Text)
                            }
                        }
                        if (s.cart.isNotEmpty()) Body("Still in your cart: items that need your approval. Ask the agent for each, or pay them one by one.", TallyColors.Muted)
                    }
                }
            }
            val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { request = it }
            }
            GlassField(
                request, { request = it }, "What do you want?", singleLine = true, leading = Icons.Outlined.AutoAwesome,
                trailing = {
                    IconButton({
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "What should your agent buy?"))
                        }
                    }) { Icon(Icons.Outlined.Mic, contentDescription = "Speak your request", tint = TallyColors.Accent) }
                },
            )
            Primary("Ask the agent", { scope.launch { s.askAgent(request) } }, icon = Icons.Outlined.AutoAwesome)
            if (s.agentResult != null || s.payResult != null) {
                Mascot(mood, Modifier.fillMaxWidth(), height = 190.dp, shakeKey = s.payResult?.takeIf { !it.ok })
            }
            s.agentResult?.let { ar ->
                Card(accent = TallyColors.BrandBright) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Outlined.Psychology, TallyColors.Accent, 36.dp)
                            Label("  Agent trace · ${s.agentLabel}")
                        }
                        Gap(4)
                        Mono("request  → \"$request\"")
                        Mono("sees     → item ids, categories, prices, floors, name tokens")
                        if (ar.pick == null) {
                            Body("No item matches. Nothing to pay.", TallyColors.NeedsYou)
                        } else {
                            val (idx, name) = s.itemName(p, ar.pick.itemId) ?: (-1 to "?")
                            val item = p.menu.items[idx]
                            Mono("chosen   → $name \$${usdc(item.price)} (floor \$${usdc(item.floor)})")
                            Mono("reason   → ${ar.pick.reason}")
                            Mono("checks   → ${if (needsOwner) "needs your co-sign" else "autonomous: cheapest match, bonded, live menu"}", if (needsOwner) TallyColors.NeedsYou else TallyColors.Paid)
                            ar.note?.let { Mono("note     → $it") }
                            Gap(10)
                            Primary(
                                if (needsOwner) "Review and pay \$${usdc(item.price)}" else "Pay \$${usdc(item.price)}",
                                { scope.launch { s.pay(idx, ar.pick.reason) } },
                                color = if (needsOwner) TallyColors.NeedsYou else TallyColors.Brand,
                                icon = if (needsOwner) Icons.Outlined.Visibility else Icons.Outlined.Bolt,
                            )
                        }
                    }
                }
            }
            s.payResult?.let { r ->
                Card(accent = if (r.ok) TallyColors.Paid else TallyColors.Blocked) {
                    Column(Modifier.semantics { contentDescription = r.title }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (r.ok) Icons.Outlined.CheckCircle else Icons.Outlined.Block, if (r.ok) TallyColors.Paid else TallyColors.Blocked, 44.dp)
                            Big("  ${r.title}", if (r.ok) TallyColors.Paid else TallyColors.Blocked, 22)
                        }
                        Gap(8)
                        Body(r.detail)
                        ExplorerLink(s, r.signature)
                        val paid = s.lastPaidIndex
                        if (r.ok && paid != null && s.store.usual?.itemIdHex != Hex.encode(p.menu.items[paid].id)) {
                            Gap(10)
                            Secondary("Save as my usual", { s.saveUsual(paid) }, icon = Icons.Outlined.Replay)
                        }
                    }
                }
            }
            Gap(24)
        }
    }

    if (s.cartOpen) CartSheet(s)

    s.pendingCosign?.let { pc ->
        ModalBottomSheet(onDismissRequest = { s.declineCosign() }, containerColor = TallyColors.Surface, scrimColor = TallyColors.Ink.copy(alpha = 0.7f)) {
            Column(Modifier.navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.Pointing, Modifier.size(width = 90.dp, height = 120.dp), height = 120.dp, glow = false)
                    Column(Modifier.padding(start = 8.dp)) {
                        Label("Second look", TallyColors.NeedsYou)
                        Text("You pay (gross)", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TallyColors.Muted)
                        Big("\$${usdc(pc.review.grossOutflow)}", TallyColors.NeedsYou, 40)
                    }
                }
                pc.review.decreases.forEach { (acct, amt) -> Mono("$acct  −\$${usdc(amt)}") }
                SheetRow(Icons.Outlined.Storefront, "To ${m?.name}", "${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded · payout ${short(m?.payout)} from the registry")
                SheetRow(categoryIcon(p.menu.items[pc.index].category), p.names[pc.index], "Agent's reason: ${pc.reason}")
                pc.cheaper?.let { (name, price) -> Card(accent = TallyColors.Paid) { Row(verticalAlignment = Alignment.CenterVertically) { IconTile(Icons.Outlined.Savings, TallyColors.Paid, 36.dp); Body("  Cheaper match: $name · \$${usdc(price)}", TallyColors.Paid) } } }
                if (pc.review.approveEnabled) {
                    Chip("Only: Ed25519 verify · Tally pay_offer · compute budget", TallyColors.Accent, Icons.Outlined.VerifiedUser)
                } else {
                    Card(accent = TallyColors.Blocked) { Body("Unexpected instructions: ${pc.review.unexpectedPrograms.joinToString()}. Approval disabled.", TallyColors.Blocked) }
                }
                Gap(4)
                Primary("Approve in wallet", { scope.launch { s.approveCosign() } }, enabled = pc.review.approveEnabled, color = TallyColors.NeedsYou, icon = Icons.Outlined.Fingerprint)
                Secondary("No, cancel", { s.declineCosign() })
            }
        }
    }
}

/** Review the whole cart before anything is paid. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartSheet(s: Session) {
    val plan = s.cartPlan() ?: return
    val scope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = { s.cartOpen = false }, containerColor = TallyColors.Surface, scrimColor = TallyColors.Ink.copy(alpha = 0.7f)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 22.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Label("Your cart")
            Big("\$${usdc(plan.totalMicro)}", TallyColors.Text, 38)
            plan.lines.forEach { l ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(categoryIcon(s.payload!!.menu.items[l.index].category), if (l.needsOwner) TallyColors.NeedsYou else TallyColors.Accent, 36.dp)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(l.name, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                        Text(if (l.needsOwner) "Pricier than the cheapest match: needs your approval" else "Within your rules: pays on its own", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = if (l.needsOwner) TallyColors.NeedsYou else TallyColors.Muted)
                    }
                    Text("\$${usdc(l.priceMicro)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                }
            }
            plan.problems.forEach { Chip(it, TallyColors.Blocked, Icons.Outlined.Block) }
            Text("Each item settles as its own on-chain transaction with its own receipt; the chain checks every one against your rules.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Faint)
            Primary(
                if (plan.needsApproval.isEmpty()) "Pay cart \$${usdc(plan.totalMicro)}" else "Pay ${plan.autonomous.size} item${if (plan.autonomous.size == 1) "" else "s"} now",
                { scope.launch { s.payCart() } }, enabled = plan.payable && plan.autonomous.isNotEmpty(), icon = Icons.Outlined.Bolt,
            )
            Secondary("Keep shopping", { s.cartOpen = false })
        }
    }
}

@Composable
private fun SheetRow(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, TallyColors.Accent, 40.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
    }
}

// ---------------------------------------------------------------------------- Receipts

@Composable
fun ReceiptsScreen(s: Session) {
    @Suppress("UNUSED_VARIABLE") val v = s.logVersion
    val log = s.store.log()
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    HeroTitle("Every payment,", "on the record.", align = TextAlign.Start, style = androidx.compose.material3.MaterialTheme.typography.displaySmall)
                    Gap(6)
                    Body("Paid purchases and blocked attempts, each with its devnet transaction.", TallyColors.Muted)
                }
                Mascot(Mood.WithReceipt, Modifier.size(width = 110.dp, height = 150.dp), height = 150.dp)
            }
        }
        if (log.isEmpty()) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Mascot(Mood.Sleepy, Modifier.fillMaxWidth(), height = 160.dp)
                        Body(if (s.capsule == null) "Create a capsule to start making protected payments. Your receipts will appear here." else "Nothing yet. Scan a counter and your receipts will appear here.", TallyColors.Muted)
                        if (s.capsule == null) {
                            Gap(16)
                            Primary("Create a capsule", { s.screen = Screen.NewCapsule })
                        }
                    }
                }
            }
        }
        items(log) { e ->
            val color = when (e.outcome) { "paid" -> TallyColors.Paid; "blocked" -> TallyColors.Blocked; else -> TallyColors.NeedsYou }
            val icon = when (e.outcome) { "paid" -> Icons.Outlined.CheckCircle; "blocked" -> Icons.Outlined.Block; else -> Icons.Outlined.Schedule }
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(icon, color, 42.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(e.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text, maxLines = 2)
                            Text(
                                listOf(e.merchant.ifBlank { null }, android.text.format.DateUtils.getRelativeTimeSpanString(e.at).toString()).filterNotNull().joinToString(" · "),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Muted,
                            )
                        }
                        Text("\$${usdc(e.amountMicro)}", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = if (e.outcome == "paid") TallyColors.Text else color)
                    }
                    Gap(10)
                    Body(e.detail, TallyColors.Muted)
                    if (e.attack || e.disputed) {
                        Gap(8)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (e.attack) Chip("Red team", TallyColors.Blocked, Icons.Outlined.Security)
                            if (e.disputed) Chip("Disputed", TallyColors.NeedsYou, Icons.Outlined.Gavel)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ExplorerLink(s, e.signature, "Explorer")
                        if (e.outcome == "paid" && e.receipt != null && e.signature != null) ShareReceipt(s, e)
                        if (e.outcome == "paid" && e.receipt != null && !e.disputed) {
                            Row(
                                Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(TallyColors.NeedsYou.copy(alpha = 0.14f))
                                    .clickable { scope.launch { s.dispute(e, "Not received as described") } }.padding(horizontal = 12.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(Icons.Outlined.Gavel, contentDescription = null, tint = TallyColors.NeedsYou, modifier = Modifier.size(15.dp))
                                Text("Dispute", color = TallyColors.NeedsYou, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Red team

/** Icon for each scripted attack, by its title. */
private fun attackIcon(title: String): ImageVector = when {
    title.startsWith("Payee") -> Icons.Outlined.SwapHoriz
    title.startsWith("Phantom") -> Icons.Outlined.AddCard
    title.startsWith("Price") -> Icons.Outlined.PriceChange
    title.startsWith("Whisper") -> Icons.Outlined.RecordVoiceOver
    else -> Icons.Outlined.BugReport
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RedTeamScreen(s: Session) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Pad.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HeroTitle("Attack your", "own agent.")
            Mascot(Mood.WithShield, Modifier.fillMaxWidth(), height = 196.dp, shakeKey = s.redTeam.size.takeIf { it > 0 })
            Body("Run four attacks against the last scanned menu. Every attempt reaches devnet, and blocked transactions appear on the explorer.", TallyColors.Muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Payee swap", "Phantom fee", "Price ×2", "Upsell").forEach { Chip(it, TallyColors.Blocked, attackIcon(it)) }
            }
            if (s.payload == null) Chip(if (s.capsule == null) "Create a capsule first" else "Scan a merchant first", TallyColors.NeedsYou, Icons.Outlined.QrCodeScanner)
            Primary("Run 4 attacks", { scope.launch { s.runRedTeam() } }, enabled = s.payload != null, color = TallyColors.Blocked, icon = Icons.Outlined.BugReport)
            s.redTeam.forEach { r ->
                Card(accent = if (r.ok) TallyColors.Blocked else TallyColors.Paid) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(attackIcon(r.title), TallyColors.Blocked, 40.dp)
                            Text(r.title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                            Icon(if (r.ok) Icons.Outlined.Cancel else Icons.Outlined.GppGood, contentDescription = if (r.ok) "Not blocked" else "Blocked", tint = if (r.ok) TallyColors.Blocked else TallyColors.Paid, modifier = Modifier.size(28.dp))
                        }
                        Gap(8)
                        Body(if (r.ok) r.detail else "Reverted on-chain: ${r.detail}", TallyColors.Muted)
                        ExplorerLink(s, r.signature)
                    }
                }
            }
            Gap(24)
        }
    }
}

// ---------------------------------------------------------------------------- Settings

@Composable
fun SettingsScreen(s: Session) {
    val scope = rememberCoroutineScope()
    var rpc by remember { mutableStateOf(s.store.rpcOverride ?: "") }
    var apiKey by remember { mutableStateOf(s.store.groqKey ?: "") }
    var demo by remember { mutableStateOf(s.store.useDemoOwner) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Pad.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                HeroTitle("Your keys,", "your rules.", Modifier.weight(1f), align = TextAlign.Start)
                Mascot(Mood.Standing, Modifier.size(width = 100.dp, height = 150.dp), height = 150.dp)
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        androidx.compose.ui.res.painterResource(app.tally.R.drawable.mascot_avatar), contentDescription = null,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.size(52.dp).clip(androidx.compose.foundation.shape.CircleShape),
                    )
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text(s.ownerAddress?.let { short(it) } ?: "Not connected", style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                        Text(s.owner.label, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Muted, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                    androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                    Chip(s.config.cluster, TallyColors.Accent, Icons.Outlined.Hub)
                }
            }

            SectionLabel("Agent")
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    InfoRow(Icons.Outlined.SmartToy, "Agent key", s.agentAddress)
                    InfoRow(Icons.Outlined.LocalGasStation, "Fee balance", "${"%.4f".format(s.agentSol / 1e9)} SOL")
                    InfoRow(Icons.Outlined.Psychology, "Model", if (s.modelAvailable) "${GroqAgent.MODEL} · ${s.modelCallsLeft}/${GroqAgent.MAX_CALLS_PER_DAY} calls left today" else "Rule-based (no API key)")
                    s.capsuleAddress?.let { InfoRow(Icons.Outlined.Inventory2, "Capsule", it) }
                }
            }

            if (s.capsule != null) {
                SectionLabel("Capsule")
                ToggleRow(Icons.Outlined.AcUnit, "Freeze capsule", "Stops every agent payment until you unfreeze.", s.capsule?.frozen == true, TallyColors.Accent) { scope.launch { s.setFrozen(it) } }
                ActionRow(Icons.Outlined.Autorenew, "Rotate agent key", "Retire this phone's agent key; its leftover SOL returns to you.") { scope.launch { s.rotateAgent() } }
                // Two taps: closing returns the money but ends this capsule's rules and history.
                var confirmClose by remember { mutableStateOf(false) }
                ActionRow(
                    Icons.Outlined.DeleteForever,
                    if (confirmClose) "Tap again to close" else "Close capsule",
                    if (confirmClose) "Refunds \$${usdc(s.vaultBalance)} and the account rent to your wallet." else "Return the vault's USDC and end this capsule.",
                    TallyColors.Blocked,
                ) { if (confirmClose) { confirmClose = false; scope.launch { s.closeCapsule() } } else confirmClose = true }
            }

            SectionLabel("Network")
            GlassField(rpc, { rpc = it }, "Custom RPC URL (blank = default)", singleLine = true, leading = Icons.Outlined.Dns)
            Secondary("Save RPC", { s.store.rpcOverride = rpc.trim().ifBlank { null } }, icon = Icons.Outlined.Save)

            SectionLabel("AI agent")
            GlassField(apiKey, { apiKey = it.trim() }, "Groq API key (blank = rule-based agent)", singleLine = true, leading = Icons.Outlined.Key, visualTransformation = PasswordVisualTransformation())
            Body("The model only ever sees item ids, categories, prices, floors and dictionary words, never merchant text. The chain still enforces every rule.", TallyColors.Muted)
            Secondary("Save API key", { s.store.groqKey = apiKey.ifBlank { null }; s.toast = if (apiKey.isBlank()) "Using the rule-based agent" else "Groq agent enabled" }, icon = Icons.Outlined.Save)

            if (BuildConfig.DEMO_OWNER_ALLOWED) {
                SectionLabel("Debug")
                ToggleRow(Icons.Outlined.Science, "Demo owner", "For emulators without a wallet. Release builds always use the wallet.", demo, TallyColors.NeedsYou) {
                    demo = it; s.store.useDemoOwner = it; s.ownerAddress = null; s.store.ownerAddress = null
                }
            }
            Gap(6)
            Mono("program ${Tally.PROGRAM_ID}", TallyColors.Faint)
            Gap(24)
        }
    }
}

@Composable
private fun SectionLabel(text: String) = Label(text, TallyColors.Accent)

/** A labelled value with its icon; tap to copy long values such as addresses. */
@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Row(Modifier.fillMaxWidth().clickable { clipboard.setText(androidx.compose.ui.text.AnnotatedString(value)) }, verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, TallyColors.Accent, 38.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium, color = TallyColors.Muted)
            Text(if (value.length > 24) short(value) else value, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TallyColors.Text)
        }
        if (value.length > 24) Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy $label", tint = TallyColors.Faint, modifier = Modifier.size(18.dp))
    }
}

/** A tappable settings row with an icon, title, explanation and a chevron. */
@Composable
private fun ActionRow(icon: ImageVector, title: String, detail: String, tint: androidx.compose.ui.graphics.Color = TallyColors.Accent, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass().pressable(onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, tint, 40.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = if (tint == TallyColors.Blocked) TallyColors.Blocked else TallyColors.Text)
            Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = TallyColors.Faint)
    }
}

/** Share a settled purchase as a short, verifiable receipt (anyone can open the transaction). */
@Composable
private fun ShareReceipt(s: Session, e: app.tally.data.LogEntry) {
    val ctx = LocalContext.current
    Row(
        Modifier.padding(top = 8.dp).glass(CircleShape)
            .clickable {
                val text = "Paid \$${usdc(e.amountMicro)} to ${e.merchant} for ${e.title}. " +
                    "My agent paid it and Tally checked the item, price and payee on-chain before the money moved.\n" +
                    "Verify it: ${s.config.explorerTx(e.signature!!)}"
                ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share receipt"))
            }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Outlined.Share, contentDescription = null, tint = TallyColors.Text, modifier = Modifier.size(15.dp))
        Text("Share", color = TallyColors.Text, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
    }
}
