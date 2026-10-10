package app.tally.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BakeryDining
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Cookie
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Gavel
import androidx.compose.material.icons.rounded.GppBad
import androidx.compose.material.icons.rounded.LocalCafe
import androidx.compose.material.icons.rounded.LocalDrink
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.ShoppingBag
import androidx.compose.material.icons.rounded.ShoppingBasket
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Train
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Draw
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VerifiedUser
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
        Modifier.padding(top = 8.dp).clip(RoundedCornerShape(50)).background(TallyColors.Brand.copy(alpha = 0.16f))
            .clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.config.explorerTx(sig)))) }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, tint = TallyColors.Accent, modifier = Modifier.size(15.dp))
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
            GlassField(text, { text = it }, "Your rules", minLines = 2, leading = Icons.Rounded.EditNote)
            Card {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Draw, TallyColors.Accent, 36.dp)
                        Label("  What your wallet will sign")
                    }
                    Gap(12)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        parsed.categories.forEach { Chip(RuleParser.CATEGORY_NAMES[it] ?: "Category $it", TallyColors.Accent, Icons.Rounded.Category) }
                        Chip("≤ \$${usdc(parsed.perTxCap)} per purchase", TallyColors.Accent, Icons.Rounded.Payments)
                        Chip("≤ \$${usdc(parsed.dailyCap)} per day", TallyColors.Accent, Icons.Rounded.CalendarToday)
                        Chip("Cheapest by default", TallyColors.Paid, Icons.Rounded.Verified)
                        Chip("Bonded merchants only", TallyColors.Paid, Icons.Rounded.VerifiedUser)
                        Chip("Expires in ~30 days", TallyColors.Muted, Icons.Rounded.Schedule)
                    }
                    Gap(12)
                    Body("Anything pricier than the cheapest match, from an unbonded merchant, or on a long-lived menu will need your tap.", TallyColors.Muted)
                }
            }
            ToggleRow(Icons.Rounded.Lightbulb, "Lighthouse assertion", "Every payment must end with an exact vault-balance check (defence in depth).", lighthouse) { lighthouse = it }
            if (!existing) {
                if (s.sgt != null) Chip("Seeker Genesis Token found: this capsule will be Seeker-verified", TallyColors.Paid, Icons.Rounded.VerifiedUser)
                GlassField(fund, { fund = it.filter { c -> c.isDigit() || c == '.' } }, "Fund with (USDC)", singleLine = true, leading = Icons.Rounded.AttachMoney)
                Primary("Approve in wallet", {
                    scope.launch { s.createCapsule(parsed, ((fund.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong(), lighthouse) }
                }, icon = Icons.Rounded.AccountBalanceWallet)
            } else {
                Primary("Update rules in wallet", { scope.launch { s.updateRules(parsed, lighthouse) } }, icon = Icons.Rounded.AccountBalanceWallet)
            }
            Gap(24)
        }
    }
}

// ---------------------------------------------------------------------------- Checkout

/** Category glyphs for menu rows (docs/07 category codes). */
fun categoryIcon(code: Int): ImageVector = when (code) {
    0 -> Icons.Rounded.BakeryDining
    1 -> Icons.Rounded.LocalDrink
    2 -> Icons.Rounded.Cookie
    3 -> Icons.Rounded.LocalCafe
    4 -> Icons.Rounded.Train
    5 -> Icons.Rounded.ShoppingBasket
    else -> Icons.Rounded.ShoppingBag
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
                Chip("${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded", if ((m?.skrBonded ?: 0) >= (s.capsule?.minMerchantBond ?: 0)) TallyColors.Paid else TallyColors.NeedsYou, Icons.Rounded.VerifiedUser)
                if (p.menu.window <= Tally.MAX_AUTONOMOUS_WINDOW) Chip("Live menu", TallyColors.Accent, Icons.Rounded.Bolt) else Chip("Extended menu", TallyColors.NeedsYou, Icons.Rounded.Schedule)
                if ((m?.powDifficulty ?: 0) > 0) Chip("Busy: PoW", TallyColors.NeedsYou, Icons.Rounded.Memory)
            }
            if (s.issues.isNotEmpty()) {
                Card(accent = TallyColors.Blocked) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.GppBad, TallyColors.Blocked, 36.dp)
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
                            Primary("Report merchant (slash its bond)", { scope.launch { s.reportFraud() } }, color = TallyColors.Blocked, icon = Icons.Rounded.Gavel)
                        }
                    }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Label("Signed menu · shown to you, not the agent")
                    p.rows().forEach { r ->
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
                        }
                    }
                }
            }
            val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { request = it }
            }
            GlassField(
                request, { request = it }, "What do you want?", singleLine = true, leading = Icons.Rounded.AutoAwesome,
                trailing = {
                    IconButton({
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "What should your agent buy?"))
                        }
                    }) { Icon(Icons.Filled.Mic, contentDescription = "Speak your request", tint = TallyColors.Accent) }
                },
            )
            Primary("Ask the agent", { scope.launch { s.askAgent(request) } }, icon = Icons.Rounded.AutoAwesome)
            if (s.agentResult != null || s.payResult != null) {
                Mascot(mood, Modifier.fillMaxWidth(), height = 190.dp, shakeKey = s.payResult?.takeIf { !it.ok })
            }
            s.agentResult?.let { ar ->
                Card(accent = TallyColors.BrandBright) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(Icons.Rounded.Psychology, TallyColors.Accent, 36.dp)
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
                                icon = if (needsOwner) Icons.Rounded.Visibility else Icons.Rounded.Bolt,
                            )
                        }
                    }
                }
            }
            s.payResult?.let { r ->
                Card(accent = if (r.ok) TallyColors.Paid else TallyColors.Blocked) {
                    Column(Modifier.semantics { contentDescription = r.title }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconTile(if (r.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Block, if (r.ok) TallyColors.Paid else TallyColors.Blocked, 44.dp)
                            Big("  ${r.title}", if (r.ok) TallyColors.Paid else TallyColors.Blocked, 22)
                        }
                        Gap(8)
                        Body(r.detail)
                        ExplorerLink(s, r.signature)
                    }
                }
            }
            Gap(24)
        }
    }

    s.pendingCosign?.let { pc ->
        ModalBottomSheet(onDismissRequest = { s.declineCosign() }, containerColor = TallyColors.Surface, scrimColor = TallyColors.Ink.copy(alpha = 0.7f)) {
            Column(Modifier.padding(horizontal = 22.dp).padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Mascot(Mood.Pointing, Modifier.size(width = 90.dp, height = 120.dp), height = 120.dp, glow = false)
                    Column(Modifier.padding(start = 8.dp)) {
                        Label("Second look", TallyColors.NeedsYou)
                        Text("You pay (gross)", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = TallyColors.Muted)
                        Big("\$${usdc(pc.review.grossOutflow)}", TallyColors.NeedsYou, 40)
                    }
                }
                pc.review.decreases.forEach { (acct, amt) -> Mono("$acct  −\$${usdc(amt)}") }
                SheetRow(Icons.Rounded.Storefront, "To ${m?.name}", "${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded · payout ${short(m?.payout)} from the registry")
                SheetRow(categoryIcon(p.menu.items[pc.index].category), p.names[pc.index], "Agent's reason: ${pc.reason}")
                pc.cheaper?.let { (name, price) -> Card(accent = TallyColors.Paid) { Row(verticalAlignment = Alignment.CenterVertically) { IconTile(Icons.Rounded.Savings, TallyColors.Paid, 36.dp); Body("  Cheaper match: $name · \$${usdc(price)}", TallyColors.Paid) } } }
                if (pc.review.approveEnabled) {
                    Chip("Only: Ed25519 verify · Tally pay_offer · compute budget", TallyColors.Paid, Icons.Rounded.VerifiedUser)
                } else {
                    Card(accent = TallyColors.Blocked) { Body("Unexpected instructions: ${pc.review.unexpectedPrograms.joinToString()}. Approval disabled.", TallyColors.Blocked) }
                }
                Gap(4)
                Primary("Approve in wallet", { scope.launch { s.approveCosign() } }, enabled = pc.review.approveEnabled, color = TallyColors.NeedsYou, icon = Icons.Rounded.Fingerprint)
                Secondary("No, cancel", { s.declineCosign() })
            }
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
    Column(Modifier.fillMaxSize()) {
        TopBar("Receipts & forensics", onBack = { s.screen = Screen.Home })
        if (log.isEmpty()) Body("Nothing yet. Paid purchases and blocked attempts appear here with explorer links.", TallyColors.Muted)
        LazyColumn(Pad, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(log) { e ->
                val color = when (e.outcome) { "paid" -> TallyColors.Paid; "blocked" -> TallyColors.Blocked; else -> TallyColors.NeedsYou }
                Card {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Dot(color)
                            Text(e.title, color = TallyColors.Text, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            Text("\$${usdc(e.amountMicro)}", color = color)
                        }
                        Text(e.detail, color = TallyColors.Muted, fontSize = 13.sp)
                        if (e.attack) Chip("red team", TallyColors.Blocked)
                        if (e.disputed) Chip("disputed", TallyColors.NeedsYou)
                        ExplorerLink(s, e.signature)
                        if (e.outcome == "paid" && e.receipt != null && !e.disputed) {
                            Text(
                                "Dispute this purchase",
                                color = TallyColors.NeedsYou,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(top = 6.dp).clickable { scope.launch { s.dispute(e, "Not received as described") } },
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- Red team

@Composable
fun RedTeamScreen(s: Session) {
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Red team", onBack = { s.screen = Screen.Home })
        Column(Pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Body("Let an attacker drive your agent against the last scanned menu. Every attempt is a real devnet transaction; reverts show on the explorer. No money moves unless an attack succeeds.", TallyColors.Muted)
            Primary("Run 4 attacks", { scope.launch { s.runRedTeam() } }, enabled = s.payload != null, color = TallyColors.Blocked)
            if (s.payload == null) Body("Scan a merchant first.", TallyColors.NeedsYou)
            s.redTeam.forEach { r ->
                Card(accent = if (r.ok) TallyColors.Blocked else TallyColors.Paid) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(if (r.ok) "✗" else "✓", color = if (r.ok) TallyColors.Blocked else TallyColors.Paid, fontSize = 20.sp)
                            Text(r.title, color = TallyColors.Text, fontWeight = FontWeight.SemiBold)
                        }
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
        TopBar("Settings", onBack = { s.screen = Screen.Home })
        Column(Pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Keys")
                    Mono("owner   ${s.ownerAddress ?: "not connected"}  (${s.owner.label})")
                    Mono("agent   ${s.agentAddress}")
                    Mono("agent SOL for fees: ${"%.4f".format(s.agentSol / 1e9)}")
                    Mono("capsule ${s.capsuleAddress ?: "—"}")
                    Mono("agent model: ${if (s.modelAvailable) "${GroqAgent.MODEL} on Groq, ${s.modelCallsLeft}/${GroqAgent.MAX_CALLS_PER_DAY} calls left today" else "rule-based (no API key)"}")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Secondary(if (s.capsule?.frozen == true) "Unfreeze capsule" else "Freeze capsule", { scope.launch { s.setFrozen(s.capsule?.frozen != true) } }, Modifier.weight(1f), enabled = s.capsule != null)
                Secondary("Rotate agent key", { scope.launch { s.rotateAgent() } }, Modifier.weight(1f), enabled = s.capsule != null)
            }
            if (s.capsule != null) {
                // Two taps: closing returns the money but ends this capsule's rules and history.
                var confirmClose by remember { mutableStateOf(false) }
                Secondary(
                    if (confirmClose) "Tap again: close and refund \$${usdc(s.vaultBalance)}" else "Close capsule",
                    { if (confirmClose) { confirmClose = false; scope.launch { s.closeCapsule() } } else confirmClose = true },
                )
            }
            OutlinedTextField(rpc, { rpc = it }, Modifier.fillMaxWidth(), label = { Text("Custom RPC URL (blank = default)") })
            Secondary("Save RPC", { s.store.rpcOverride = rpc.trim().ifBlank { null } })
            OutlinedTextField(
                apiKey, { apiKey = it.trim() }, Modifier.fillMaxWidth(), singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Groq API key (blank = rule-based agent)") },
            )
            Body("The model only ever sees item ids, categories, prices, floors and dictionary words, never merchant text. The chain still enforces every rule.", TallyColors.Muted)
            Secondary("Save API key", { s.store.groqKey = apiKey.ifBlank { null }; s.toast = if (apiKey.isBlank()) "Using the rule-based agent" else "Groq agent enabled" })
            if (BuildConfig.DEMO_OWNER_ALLOWED) {
                Card(accent = TallyColors.NeedsYou) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Demo owner (debug build)", color = TallyColors.Text, fontWeight = FontWeight.SemiBold)
                            Text("For emulators without a wallet. Release builds always use Seed Vault.", color = TallyColors.Muted, fontSize = 12.sp)
                        }
                        Switch(demo, { demo = it; s.store.useDemoOwner = it; s.ownerAddress = null; s.store.ownerAddress = null })
                    }
                }
            }
            Mono("cluster ${s.config.cluster} · program ${Tally.PROGRAM_ID}")
            Gap(24)
        }
    }
}
