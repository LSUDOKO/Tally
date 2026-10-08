package app.tally.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.tally.BuildConfig
import app.tally.agent.AgentResult
import app.tally.agent.AgentSource
import app.tally.core.Accounts
import app.tally.core.Base58
import app.tally.core.Ed25519
import app.tally.core.Hex
import app.tally.core.Instruction
import app.tally.core.Item
import app.tally.core.Le
import app.tally.core.Menu
import app.tally.core.MenuIssue
import app.tally.core.Outcome
import app.tally.core.ParsedRules
import app.tally.core.PayOffer
import app.tally.core.PayOfferParams
import app.tally.core.Payload
import app.tally.core.Pda
import app.tally.core.PowSolution
import app.tally.core.Programs
import app.tally.core.Purchase
import app.tally.core.Review
import app.tally.core.SecondLook
import app.tally.core.SignedMenu
import app.tally.core.SignedPayload
import app.tally.core.TallyIx
import app.tally.core.TallyPda
import app.tally.core.usdc
import app.tally.data.LogEntry
import app.tally.graph
import app.tally.wallet.DemoOwner
import app.tally.wallet.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class Screen { Home, NewCapsule, Scan, Checkout, Receipts, RedTeam, Merchant, Settings }

/** What the user sees after a payment attempt. */
data class PayResult(val ok: Boolean, val title: String, val detail: String, val signature: String?)

/** A purchase waiting on the Second Look sheet. */
class PendingCosign(val index: Int, val wire: ByteArray, val review: Review, val cheaper: Pair<String, Long>?, val reason: String)

/**
 * App state and actions. Pure decisions (verification, policy, transaction bytes) live in
 * `app.tally.core` and are unit-tested; this class wires them to the wallet, RPC and UI.
 */
class Session(private val context: Context, private val mwaOwner: Owner) {
    private val g = context.graph

    var screen by mutableStateOf(Screen.Home)
    var busy by mutableStateOf<String?>(null)
    var toast by mutableStateOf<String?>(null)

    var ownerAddress by mutableStateOf(g.store.ownerAddress)
    var capsule by mutableStateOf<Accounts.Capsule?>(null)
    var vaultBalance by mutableStateOf(0L)
    var agentSol by mutableStateOf(0L)

    var payload by mutableStateOf<SignedPayload?>(null)
    var merchant by mutableStateOf<Accounts.Merchant?>(null)
    var issues by mutableStateOf<List<MenuIssue>>(emptyList())
    var agentResult by mutableStateOf<AgentResult?>(null)
    var payResult by mutableStateOf<PayResult?>(null)
    var pendingCosign by mutableStateOf<PendingCosign?>(null)
    var redTeam by mutableStateOf<List<PayResult>>(emptyList())
    var logVersion by mutableStateOf(0)

    val config get() = g.config
    val store get() = g.store
    val agentAddress: String get() = g.agentKey.address
    val claudeAvailable get() = g.claude.available

    val owner: Owner
        get() = if (BuildConfig.DEMO_OWNER_ALLOWED && g.store.useDemoOwner) DemoOwner(g.demoOwnerKey.exportSeedForMenuSigning()) else mwaOwner

    val capsuleAddress: String? get() = ownerAddress?.let { TallyPda.capsule(it, g.store.capsuleId) }

    private suspend fun <T> work(label: String, block: suspend () -> T): T? {
        busy = label
        return try {
            block()
        } catch (e: Exception) {
            toast = e.message ?: e.javaClass.simpleName
            null
        } finally {
            busy = null
        }
    }

    // ------------------------------------------------------------------ owner & capsule

    suspend fun connect() = work("Connecting wallet") {
        val addr = owner.connect()
        ownerAddress = addr
        g.store.ownerAddress = addr
        refreshNow()
    }

    suspend fun refresh() = work("Refreshing") { refreshNow() }

    private suspend fun refreshNow() = withContext(Dispatchers.IO) {
        val rpc = g.rpc
        agentSol = runCatching { rpc.getBalance(agentAddress) }.getOrDefault(0)
        val c = capsuleAddress?.let { rpc.getAccountData(it) }?.let(Accounts::capsule)
        capsule = c
        vaultBalance = c?.let { rpc.getAccountData(it.vault) }?.let(Accounts::tokenAmount) ?: 0
    }

    suspend fun createCapsule(rules: ParsedRules, fundMicro: Long) = work("Waiting for Seed Vault") {
        val ownerAddr = ownerAddress ?: owner.connect().also { ownerAddress = it; g.store.ownerAddress = it }
        val usdcMint = config.usdcMint.ifBlank { error("Cluster config missing: run scripts/export-app-config.ts") }
        val slot = withContext(Dispatchers.IO) { g.rpc.getSlot() }
        val expiry = slot + 30L * 216_000 // ~30 days
        val ixs = mutableListOf<Instruction>(TallyIx.createCapsule(ownerAddr, g.store.capsuleId, rules.toRules(expiry), agentAddress, usdcMint))
        if (fundMicro > 0) ixs += TallyIx.fund(ownerAddr, TallyPda.capsule(ownerAddr, g.store.capsuleId), usdcMint, fundMicro)
        val sig = owner.signAndSend(ixs, g.rpc)
        log("Capsule created", "Tally", fundMicro, "paid", "Rules signed in ${owner.label}", sig)
        refreshNow()
        screen = Screen.Home
    }

    suspend fun setFrozen(frozen: Boolean) = work(if (frozen) "Freezing" else "Unfreezing") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        owner.signAndSend(listOf(if (frozen) TallyIx.freeze(o, cap) else TallyIx.unfreeze(o, cap)), g.rpc)
        refreshNow()
    }

    suspend fun rotateAgent() = work("Rotating agent key") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        g.agentKey.rotate()
        owner.signAndSend(listOf(TallyIx.setAgent(o, cap, agentAddress)), g.rpc)
        refreshNow()
    }

    // ------------------------------------------------------------------ scan → checkout

    suspend fun onPayload(p: SignedPayload) = work("Verifying signed menu") {
        val (m, slot) = withContext(Dispatchers.IO) {
            val data = g.rpc.getAccountData(p.menu.merchant) ?: error("Unknown merchant: not in the Tally registry")
            Accounts.merchant(data) to g.rpc.getSlot()
        }
        merchant = m
        payload = p
        issues = Payload.verifyLocally(p, Base58.decode(m.signingKey), slot) + (if (!m.active) listOf(MenuIssue.BadSignature) else emptyList())
        g.store.lastPayloadQr = Payload.toQr(p)
        agentResult = null
        payResult = null
        screen = Screen.Checkout
    }

    suspend fun askAgent(request: String) = work("Agent is choosing") {
        val p = payload ?: return@work
        val c = capsule
        agentResult = withContext(Dispatchers.IO) {
            g.claude.choose(request, c?.let { cap -> (0 until 256).filter(cap::allows) } ?: listOf(3), c?.perTxCap ?: 6_000_000, p.rows())
        }
    }

    private fun powFor(m: Accounts.Merchant, capsuleAddr: String, nonce: Long): PowSolution? {
        if (m.powDifficulty == 0) return null
        val sh = g.rpc.getAccountData(Programs.SYSVAR_SLOT_HASHES) ?: error("SlotHashes unavailable")
        val slot = Le.readU64(sh, 8)
        val hash = sh.copyOfRange(16, 48)
        val nonceBytes = Menu.solveHashcash(Menu.powChallenge(nonce, Base58.decode(capsuleAddr), hash), m.powDifficulty)
        return PowSolution(nonceBytes, slot)
    }

    private fun params(p: SignedPayload, index: Int, ownerSigns: Boolean, overridePayout: String? = null, pow: PowSolution? = null): PayOfferParams {
        val c = capsule ?: error("Create a capsule first")
        val m = merchant ?: error("Scan a merchant first")
        return PayOfferParams(
            menu = p.menu, index = index, capsule = capsuleAddress!!, owner = c.owner, vault = c.vault, mint = c.mint,
            agent = agentAddress, payout = overridePayout ?: m.payout, ownerSigns = ownerSigns, pow = pow,
            computeUnitLimit = 80_000, priorityMicroLamports = 10_000,
        )
    }

    /** Agent pays; if the chain will demand the owner (above floor, low bond, extended window), open Second Look first. */
    suspend fun pay(index: Int, reason: String) = work("Paying") {
        val p = payload ?: return@work
        val m = merchant ?: return@work
        val c = capsule ?: error("Create a capsule first")
        val item = p.menu.items[index]
        val slot = withContext(Dispatchers.IO) { g.rpc.getSlot() }
        if (Purchase.gate(slot, p.menu.expiresSlot) == Purchase.Gate.Rescan) {
            toast = "That menu is about to expire. Scan the counter again."
            screen = Screen.Scan
            return@work
        }
        val needsOwner = Purchase.needsOwner(item, m.skrBonded, c.minMerchantBond, p.menu.window)
        val pow = withContext(Dispatchers.Default) { powFor(m, capsuleAddress!!, p.menu.menuNonce) }
        val ixs = PayOffer.build(params(p, index, needsOwner, pow = pow))
        val wire = g.submitter.buildAgentSigned(agentAddress, ixs) { g.agentKey.sign(it) }
        if (needsOwner) {
            val post = withContext(Dispatchers.IO) { g.rpc.simulate(wire, listOf(c.vault)) }
            val after = post.postAccounts.firstOrNull()?.let(Accounts::tokenAmount) ?: (vaultBalance - item.price)
            val review = SecondLook.review(ixs, mapOf("Capsule vault" to (vaultBalance to after)))
            val cheapest = p.rows().filter { it.item.category == item.category }.minByOrNull { it.item.price }
            pendingCosign = PendingCosign(index, wire, review, cheapest?.takeIf { it.item.price < item.price }?.let { it.name to it.item.price }, reason)
            return@work
        }
        submit(wire, p.names[index], item, attack = false)
    }

    suspend fun approveCosign() = work("Waiting for Seed Vault") {
        val pc = pendingCosign ?: return@work
        pendingCosign = null
        val signed = owner.cosign(pc.wire)
        val p = payload ?: return@work
        submit(signed, p.names[pc.index], p.menu.items[pc.index], attack = false)
    }

    fun declineCosign() {
        val pc = pendingCosign ?: return
        pendingCosign = null
        val p = payload ?: return
        log("Declined: ${p.names[pc.index]}", merchant?.name ?: "", p.menu.items[pc.index].price, "blocked", "You declined a pricier-than-cheapest pick on the Second Look sheet.", null, attack = true)
        payResult = PayResult(false, "You said no", "Nothing was paid.", null)
    }

    private suspend fun submit(wire: ByteArray, name: String, item: Item, attack: Boolean, land: Boolean = false): PayResult {
        val (sig, outcome) = g.submitter.sendAndConfirm(wire, land = land)
        val r = when (outcome) {
            is Outcome.Confirmed -> PayResult(true, "Paid ${usdc(item.price)} USDC", "$name — straight to ${merchant?.name ?: "the merchant"}'s registered account.", sig)
            is Outcome.Reverted -> PayResult(false, "Blocked on-chain", outcome.sentence, sig)
            is Outcome.Pending -> PayResult(false, "Still confirming", "The network is slow; check Receipts in a moment.", sig)
        }
        log(name, merchant?.name ?: "", item.price, if (r.ok) "paid" else if (outcome is Outcome.Pending) "pending" else "blocked", r.detail, sig, attack)
        payResult = r
        refreshNow()
        return r
    }

    // ------------------------------------------------------------------ red team (docs/14)

    suspend fun runRedTeam() = work("Running attacks") {
        val p = payload ?: error("Scan a merchant menu first")
        val results = mutableListOf<PayResult>()
        suspend fun attack(title: String, ixs: List<Instruction>, item: Item) {
            val wire = g.submitter.buildAgentSigned(agentAddress, ixs) { g.agentKey.sign(it) }
            val (sig, o) = g.submitter.sendAndConfirm(wire, land = true)
            val r = when (o) {
                is Outcome.Reverted -> PayResult(false, title, o.sentence, sig)
                is Outcome.Confirmed -> PayResult(true, title, "Paid — this attack was NOT blocked.", sig)
                Outcome.Pending -> PayResult(false, title, "Pending", sig)
            }
            log(title, merchant?.name ?: "", item.price, if (r.ok) "paid" else "blocked", r.detail, sig, attack = true)
            results += r
            redTeam = results.toList()
        }
        val floorIdx = p.menu.items.indices.minBy { p.menu.items[it].price + if (p.menu.items[it].price == p.menu.items[it].floor) 0 else Long.MAX_VALUE / 2 }
        val evil = Pda.ata(Base58.encode(Ed25519.publicKey(ByteArray(32) { 66 })), capsule?.mint ?: config.usdcMint)
        // 1. payee swap
        attack("Payee swap → attacker account", PayOffer.build(params(p, floorIdx, false, overridePayout = evil)), p.menu.items[floorIdx])
        // 2. phantom add-on: an unsigned $2.99 fee appended to the signed menu
        val fee = Item(ByteArray(16) { 0xfe.toByte() }, p.menu.items[floorIdx].category, 2_990_000, 2_990_000)
        val tampered = SignedPayload(SignedMenu(p.menu.merchant, p.menu.issuedSlot, p.menu.expiresSlot, p.menu.menuNonce, p.menu.items + fee, p.menu.header, p.menu.signerPubkey, p.menu.signature), p.names + "Service fee", p.descriptions + null)
        attack("Phantom add-on: unsigned \$2.99 fee", PayOffer.build(params(tampered, tampered.menu.items.size - 1, false)), fee)
        // 3. price tampering: same item, inflated price
        val inflated = SignedPayload(SignedMenu(p.menu.merchant, p.menu.issuedSlot, p.menu.expiresSlot, p.menu.menuNonce, p.menu.items.mapIndexed { i, it -> if (i == floorIdx) it.copy(price = it.price * 2, floor = it.price * 2) else it }, p.menu.header, p.menu.signerPubkey, p.menu.signature), p.names, p.descriptions)
        attack("Price tampering: 2× the signed price", PayOffer.build(params(inflated, floorIdx, false)), inflated.menu.items[floorIdx])
        // 4. Whisper upsell without the owner: pick the priciest same-category item
        val upsell = p.menu.items.indices.filter { p.menu.items[it].category == p.menu.items[floorIdx].category && it != floorIdx }.maxByOrNull { p.menu.items[it].price }
        if (upsell != null) attack("Whisper upsell: \"regular is discontinued\"", PayOffer.build(params(p, upsell, false)), p.menu.items[upsell])
        redTeam = results.toList()
    }

    // ------------------------------------------------------------------ log

    private fun log(title: String, merchant: String, amount: Long, outcome: String, detail: String, sig: String?, attack: Boolean = false) {
        g.store.append(LogEntry(System.currentTimeMillis(), title, merchant, amount, outcome, detail, sig, attack))
        logVersion++
        app.tally.widget.TallyWidget.refresh(context)
    }

    fun itemName(p: SignedPayload, itemIdHex: String) = p.menu.items.indexOfFirst { Hex.encode(it.id) == itemIdHex }.let { if (it >= 0) it to p.names[it] else null }

    val agentLabel get() = if (agentResult?.source == AgentSource.Claude) "Claude (structured fields only)" else "Rule agent"
}
