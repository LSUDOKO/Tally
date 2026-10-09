package app.tally.ui

import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
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

private val Pad = Modifier.padding(horizontal = 16.dp)

@Composable
fun ExplorerLink(session: Session, sig: String?, label: String = "View on Solana Explorer") {
    if (sig == null) return
    val ctx = LocalContext.current
    Text(
        label,
        color = TallyColors.Accent,
        fontSize = 14.sp,
        modifier = Modifier.padding(top = 6.dp).clickable { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(session.config.explorerTx(sig)))) },
    )
}

// ---------------------------------------------------------------------------- Home

@Composable
fun HomeScreen(s: Session) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(s.ownerAddress) { if (s.ownerAddress != null) s.refresh() }
    @Suppress("UNUSED_VARIABLE") val v = s.logVersion
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Tally") { Text("devnet", color = TallyColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(end = 12.dp)) }
        Column(Pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Body("Your agent proposes. The merchant signs. Tally checks it on-chain.", TallyColors.Muted)
            if (s.ownerAddress == null) {
                Card(accent = TallyColors.Accent) {
                    Column {
                        Big("Let your agent pay —\nonly for what tallies.", size = 24)
                        Gap()
                        Body("Connect your Seeker wallet. Your owner key stays in Seed Vault; the app only ever holds a capped agent key.", TallyColors.Muted)
                        Gap(16)
                        Primary("Connect with Seed Vault", { scope.launch { s.connect() } })
                    }
                }
            } else {
                val c = s.capsule
                Card {
                    Column {
                        Label("Coffee capsule")
                        Gap(6)
                        if (c == null) {
                            Body("No capsule yet. Set your rules once; Seed Vault signs them on-chain.", TallyColors.Muted)
                            Gap(12)
                            Primary("Create a capsule", { s.screen = Screen.NewCapsule })
                        } else {
                            Big("\$${usdc(s.vaultBalance)}")
                            Text("USDC in vault", color = TallyColors.Muted, fontSize = 13.sp)
                            Gap(14)
                            val pct = if (c.dailyCap > 0) (c.spentInCycle.toFloat() / c.dailyCap).coerceIn(0f, 1f) else 0f
                            Box(Modifier.fillMaxWidth().background(TallyColors.SurfaceHigh, RoundedCornerShape(50)).padding(0.dp)) {
                                Box(Modifier.fillMaxWidth(pct.coerceAtLeast(0.02f)).background(TallyColors.Paid, RoundedCornerShape(50)).padding(vertical = 4.dp))
                            }
                            Gap(6)
                            Text("\$${usdc(c.spentInCycle)} of \$${usdc(c.dailyCap)} today · max \$${usdc(c.perTxCap)} per purchase", color = TallyColors.Muted, fontSize = 13.sp)
                            Gap(10)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (c.frozen) Chip("Frozen", TallyColors.Blocked)
                                if (c.flags and 2 != 0) Chip("Seeker verified", TallyColors.Paid)
                                if (c.flags and 1 != 0) Chip("Lighthouse required", TallyColors.Accent)
                            }
                            Gap(12)
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Secondary("Add \$10", { scope.launch { s.fund(10_000_000) } }, Modifier.weight(1f))
                                Secondary("Withdraw", { scope.launch { s.withdraw(s.vaultBalance) } }, Modifier.weight(1f), enabled = s.vaultBalance > 0)
                                Secondary("Rules", { s.screen = Screen.NewCapsule }, Modifier.weight(1f))
                            }
                        }
                    }
                }
                if (s.capsule != null && s.agentSol < 10_000_000) {
                    Card(accent = TallyColors.NeedsYou) {
                        Column {
                            Body("Your agent needs a little SOL for network fees and receipts (${"%.4f".format(s.agentSol / 1e9)} SOL left).", TallyColors.NeedsYou)
                            Gap(10)
                            Primary("Top up agent with 0.05 SOL", { scope.launch { s.topUpAgent() } }, color = TallyColors.NeedsYou)
                        }
                    }
                }
                if (s.capsule != null && (s.capsule!!.flags and 2) != 0) {
                    Secondary("Claim Seeker launch promo (one per device)", { scope.launch { s.claimPromo(1) } })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Card(Modifier.weight(1f)) { Stat("Attacks blocked · 7d", "${s.store.attacksBlockedThisWeek()}", TallyColors.Blocked) }
                    Card(Modifier.weight(1f)) { Stat("Spent today", "\$${usdc(s.store.spentToday())}", TallyColors.Paid) }
                }
                Primary("Scan or tap to pay", { s.screen = Screen.Scan }, enabled = s.capsule != null)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Secondary("Receipts", { s.screen = Screen.Receipts }, Modifier.weight(1f))
                    Secondary("Red team", { s.screen = Screen.RedTeam }, Modifier.weight(1f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Secondary("Merchant mode", { s.screen = Screen.Merchant }, Modifier.weight(1f))
                Secondary("Settings", { s.screen = Screen.Settings }, Modifier.weight(1f))
            }
            Gap(24)
        }
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
        Column(Pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Body("Say what your agent may buy. You'll confirm the exact rules before Seed Vault signs.", TallyColors.Muted)
            OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth(), label = { Text("Your rules") }, minLines = 2)
            Card {
                Column {
                    Label("What will be signed")
                    Gap(10)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        parsed.categories.forEach { Chip(RuleParser.CATEGORY_NAMES[it] ?: "Category $it") }
                        Chip("≤ \$${usdc(parsed.perTxCap)} per purchase")
                        Chip("≤ \$${usdc(parsed.dailyCap)} per day")
                        Chip("Cheapest by default", TallyColors.NeedsYou)
                        Chip("Bonded merchants only", TallyColors.NeedsYou)
                        Chip("Expires in ~30 days", TallyColors.Muted)
                    }
                    Gap(10)
                    Body("Anything pricier than the cheapest match, from an unbonded merchant, or on a long-lived menu will need your tap.", TallyColors.Muted)
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Require a Lighthouse assertion", color = TallyColors.Text, fontWeight = FontWeight.SemiBold)
                        Text("Every payment must end with an exact vault-balance check (defence in depth).", color = TallyColors.Muted, fontSize = 13.sp)
                    }
                    Switch(lighthouse, { lighthouse = it })
                }
            }
            if (!existing) {
                if (s.sgt != null) Chip("Seeker Genesis Token found: this capsule will be Seeker-verified", TallyColors.Paid)
                OutlinedTextField(fund, { fund = it.filter { c -> c.isDigit() || c == '.' } }, Modifier.fillMaxWidth(), label = { Text("Fund with (USDC)") })
                Primary("Approve with Seed Vault", {
                    scope.launch { s.createCapsule(parsed, ((fund.toDoubleOrNull() ?: 0.0) * 1_000_000).toLong(), lighthouse) }
                })
            } else {
                Primary("Update rules with Seed Vault", { scope.launch { s.updateRules(parsed, lighthouse) } })
            }
            Gap(24)
        }
    }
}

// ---------------------------------------------------------------------------- Checkout

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
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar(m?.name ?: "Merchant", onBack = { s.screen = Screen.Home })
        Column(Pad, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded", if ((m?.skrBonded ?: 0) >= (s.capsule?.minMerchantBond ?: 0)) TallyColors.Paid else TallyColors.NeedsYou)
                if (p.menu.window <= Tally.MAX_AUTONOMOUS_WINDOW) Chip("Live menu") else Chip("Extended menu", TallyColors.NeedsYou)
                if ((m?.powDifficulty ?: 0) > 0) Chip("Busy: proof of work", TallyColors.NeedsYou)
            }
            if (s.issues.isNotEmpty()) {
                Card(accent = TallyColors.Blocked) {
                    Column {
                        Label("Menu check failed", TallyColors.Blocked)
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
                            Primary("Report merchant (slash its bond)", { scope.launch { s.reportFraud() } }, color = TallyColors.Blocked)
                        }
                    }
                }
            }
            Card {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Label("Signed menu (shown to you, not the agent)")
                    p.rows().forEachIndexed { i, r ->
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.name, color = TallyColors.Text, fontSize = 15.sp, modifier = Modifier.weight(1f))
                                Text("\$${usdc(r.item.price)}", color = if (r.item.price == r.item.floor) TallyColors.Paid else TallyColors.Text, fontWeight = FontWeight.SemiBold)
                            }
                            r.description?.let { Text(it, color = TallyColors.Muted, fontSize = 12.sp) }
                        }
                    }
                }
            }
            val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
                res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { request = it }
            }
            OutlinedTextField(
                request, { request = it }, Modifier.fillMaxWidth(), label = { Text("What do you want?") },
                trailingIcon = {
                    IconButton({
                        runCatching {
                            speech.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_PROMPT, "What should your agent buy?"))
                        }
                    }) { Icon(Icons.Filled.Mic, contentDescription = "Speak your request", tint = TallyColors.Accent) }
                },
            )
            Primary("Ask the agent", { scope.launch { s.askAgent(request) } })
            s.agentResult?.let { ar ->
                val pick = ar.pick
                Card(accent = TallyColors.Accent) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Agent trace · ${s.agentLabel}")
                        Mono("request  → \"$request\"")
                        Mono("sees     → item ids, categories, prices, floors, name tokens")
                        if (pick == null) {
                            Body("No item matches. Nothing to pay.", TallyColors.NeedsYou)
                        } else {
                            val (idx, name) = s.itemName(p, pick.itemId) ?: (-1 to "?")
                            val item = p.menu.items[idx]
                            Mono("chosen   → $name \$${usdc(item.price)} (floor \$${usdc(item.floor)})")
                            Mono("reason   → ${pick.reason}")
                            val c = s.capsule
                            val needs = c != null && m != null && Purchase.needsOwner(item, m.skrBonded, c.minMerchantBond, p.menu.window)
                            Mono("checks   → ${if (needs) "needs your co-sign" else "autonomous: cheapest match, bonded, live menu"}", if (needs) TallyColors.NeedsYou else TallyColors.Paid)
                            ar.note?.let { Mono("note     → $it") }
                            Gap(8)
                            Primary(if (needs) "Review and pay \$${usdc(item.price)}" else "Pay \$${usdc(item.price)}", { scope.launch { s.pay(idx, pick.reason) } })
                        }
                    }
                }
            }
            s.payResult?.let { r ->
                Card(accent = if (r.ok) TallyColors.Paid else TallyColors.Blocked) {
                    Column(Modifier.semantics { contentDescription = r.title }) {
                        Big(r.title, if (r.ok) TallyColors.Paid else TallyColors.Blocked, 22)
                        Gap(4)
                        Body(r.detail)
                        ExplorerLink(s, r.signature)
                    }
                }
            }
            Gap(24)
        }
    }

    s.pendingCosign?.let { pc ->
        ModalBottomSheet(onDismissRequest = { s.declineCosign() }, containerColor = TallyColors.Surface) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Label("Second look", TallyColors.NeedsYou)
                Text("You pay (gross)", color = TallyColors.Muted)
                Big("\$${usdc(pc.review.grossOutflow)} USDC", TallyColors.NeedsYou, 38)
                pc.review.decreases.forEach { (acct, amt) -> Mono("$acct  −\$${usdc(amt)}") }
                Body("To: ${m?.name} · ${usdc(m?.skrBonded ?: 0).substringBefore('.')} SKR bonded · payout ${short(m?.payout)} (from registry)")
                Body("Item: ${p.names[pc.index]}")
                Body("Agent's reason: ${pc.reason}", TallyColors.Muted)
                pc.cheaper?.let { (name, price) -> Card(accent = TallyColors.Paid) { Body("Cheaper match: $name · \$${usdc(price)}", TallyColors.Paid) } }
                if (pc.review.approveEnabled) {
                    Mono("Transaction contains: Ed25519 verify · Tally pay_offer · compute budget — nothing else", TallyColors.Paid)
                } else {
                    Card(accent = TallyColors.Blocked) { Body("Unexpected instructions: ${pc.review.unexpectedPrograms.joinToString()}. Approval disabled.", TallyColors.Blocked) }
                }
                Gap(4)
                Primary("Approve with Seed Vault", { scope.launch { s.approveCosign() } }, enabled = pc.review.approveEnabled, color = TallyColors.NeedsYou)
                Secondary("No, cancel", { s.declineCosign() })
                Gap(12)
            }
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
