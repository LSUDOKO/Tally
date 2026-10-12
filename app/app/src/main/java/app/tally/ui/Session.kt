package app.tally.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asImageBitmap
import app.tally.BuildConfig
import app.tally.agent.AgentResult
import app.tally.agent.AgentSource
import app.tally.chain.ConfirmingOwner
import app.tally.merchant.MerchantTerminal
import app.tally.core.Accounts
import app.tally.core.AssociatedToken
import app.tally.core.Lighthouse
import app.tally.core.Sgt
import app.tally.core.SystemProgram
import app.tally.core.sha256
import app.tally.core.Base58
import app.tally.core.Ed25519
import app.tally.core.Cart
import app.tally.core.CartPlan
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
import app.tally.core.Insights
import app.tally.core.Level
import app.tally.core.Reorder
import app.tally.core.ReorderCheck
import app.tally.core.Rewards
import app.tally.core.SpendRecord
import app.tally.core.Summary
import app.tally.core.Usual
import app.tally.data.CapsuleSnapshot
import app.tally.core.Proposal
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
import app.tally.wallet.Cosigned
import app.tally.wallet.DemoOwner
import app.tally.wallet.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

enum class Screen { Home, NewCapsule, Scan, Checkout, Receipts, RedTeam, Merchant, Settings, Rewards }

/** What the user sees after a payment attempt. */
data class PayResult(val ok: Boolean, val title: String, val detail: String, val signature: String?)

/** A purchase waiting on the Second Look sheet. */
/** The one promo id the app offers today. */
const val LAUNCH_PROMO = 1L

class PendingCosign(val index: Int, val wire: ByteArray, val review: Review, val cheaper: Pair<String, Long>?, val reason: String)

/**
 * App state and actions. Pure decisions (verification, policy, transaction bytes) live in
 * `app.tally.core` and are unit-tested; this class wires them to the wallet, RPC and UI.
 */
class Session(val context: Context, private val mwaOwner: Owner) {
    private val g = context.graph

    var screen by mutableStateOf(Screen.Home)
    var busy by mutableStateOf<String?>(null)
    /** True while the app lock has not been passed since it came to the front. */
    var locked by mutableStateOf(false)
    /** USD per SOL from a public price feed; null until it loads (shown beside the fee balance). */
    var solUsd by mutableStateOf<Double?>(null)
    var toast by mutableStateOf<String?>(null)

    var ownerAddress by mutableStateOf(g.store.ownerAddress)
    var capsule by mutableStateOf<Accounts.Capsule?>(null)
    var vaultBalance by mutableStateOf(0L)
    var agentSol by mutableStateOf(0L)
    var slot by mutableStateOf(0L)

    /** Today's spend as the program counts it (zero once the 216,000-slot cycle has rolled over, or with no capsule). */
    val spentToday: Long
        get() = capsule?.let { it.dailyCap - Purchase.dailyRemaining(it.dailyCap, it.spentInCycle, it.cycleStartSlot, slot) } ?: 0L
    /** The owner's Seeker Genesis Token `(token account, mint)`, when this device has one. */
    var sgt by mutableStateOf<Pair<String, String>?>(null)

    /** Whether this capsule's Seeker Genesis Token already claimed the launch promo (its claim PDA exists). */
    var promoClaimed by mutableStateOf(false)

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
    val modelAvailable get() = g.agent.available
    val modelCallsLeft get() = g.modelAllowance.remaining()

    val owner: Owner
        get() = ConfirmingOwner(if (BuildConfig.DEMO_OWNER_ALLOWED && g.store.useDemoOwner) DemoOwner(g.demoOwnerKey.exportSeedForMenuSigning()) else mwaOwner, g.submitter)

    val capsuleAddress: String? get() = ownerAddress?.let { TallyPda.capsule(it, g.store.capsuleId) }

    /** The phone's lock in front of a payment the owner taps, when they turned that on. */
    private var preConfirmed = 0
    private suspend fun ownerConfirms(what: String): Boolean {
        if (!g.store.appLock) return true
        if (preConfirmed > 0) { preConfirmed--; return true }
        val ok = app.tally.security.AppLock.confirm(context, "Confirm with your phone's lock", what)
        if (!ok) toast = "Not paid: the lock wasn't passed."
        return ok
    }

    suspend fun unlock() {
        locked = !app.tally.security.AppLock.confirm(context, "Unlock Tally")
    }

    private suspend fun <T> work(label: String, block: suspend () -> T): T? {
        busy = label
        return try {
            // Actions are started from screen-scoped coroutines; leaving the screen must not abandon a
            // transaction half-way (sent but never confirmed or logged), so they run to completion.
            withContext(NonCancellable) { block() }
        } catch (e: Exception) {
            toast = e.message ?: e.javaClass.simpleName
            null
        } finally {
            busy = null
        }
    }

    // ------------------------------------------------------------------ owner & capsule

    /** The wallet app and account name the owner signed in with, for the header and Settings. */
    var walletName by mutableStateOf(g.store.walletName)
    var accountLabel by mutableStateOf(g.store.accountLabel)

    suspend fun connect() = work("Waiting for your wallet") {
        val who = owner.signIn()
        g.store.signIn(who.address)
        g.store.walletName = if (BuildConfig.DEMO_OWNER_ALLOWED && g.store.useDemoOwner) "Demo owner" else app.tally.wallet.Wallets.nameFor(context, who.walletUriBase)
        g.store.accountLabel = who.label
        walletName = g.store.walletName
        accountLabel = who.label
        schedule = g.store.schedule
        ownerAddress = who.address
        logVersion++
        refreshNow()
        toast = "Signed in with $walletName" + (who.label?.let { " · $it" } ?: "")
    }

    /**
     * Sign out: the wallet revokes Tally's access, this account's receipts and rewards are set aside
     * on the phone (they come back if the same wallet signs in again), and the app returns to the
     * welcome screen. The capsule and its money stay on chain, untouched.
     */
    suspend fun disconnect() = work("Signing out") {
        val o = ownerAddress
        runCatching { owner.disconnect() }
        if (o != null) g.store.signOut(o) else g.store.ownerAddress = null
        ownerAddress = null
        walletName = null
        accountLabel = null
        capsule = null
        vaultBalance = 0
        sgt = null
        promoClaimed = false
        payload = null
        merchant = null
        agentResult = null
        payResult = null
        pendingCosign = null
        redTeam = emptyList()
        cancelReorder()
        schedule = null
        logVersion++
        screen = Screen.Home
    }

    suspend fun refresh() = work("Refreshing") { refreshNow() }

    /** Every registered counter on this cluster, biggest bond first; null until loaded. */
    var directory by mutableStateOf<List<Pair<String, Accounts.Merchant>>?>(null)

    suspend fun loadDirectory() {
        directory = withContext(Dispatchers.IO) {
            runCatching {
                g.rpc.getProgramAccounts(app.tally.core.Tally.PROGRAM_ID, Accounts.MERCHANT_SIZE)
                    .map { (addr, data) -> addr to Accounts.merchant(data) }
                    .filter { it.second.active }
                    .sortedByDescending { it.second.skrBonded }
            }.getOrDefault(directory ?: emptyList())
        }
    }

    /** Google Maps places by merchant address (null = no match), and their photos once loaded. */
    val places = androidx.compose.runtime.mutableStateMapOf<String, app.tally.chain.Place?>()
    val placePhotos = androidx.compose.runtime.mutableStateMapOf<String, androidx.compose.ui.graphics.ImageBitmap>()

    suspend fun loadPlace(merchant: String) {
        if (merchant in places) return
        val place = withContext(Dispatchers.IO) { runCatching { app.tally.chain.CloudPlaces.lookup(config.cloudUrl, merchant) }.getOrNull() }
        places[merchant] = place
        val path = place?.photo ?: return
        withContext(Dispatchers.IO) {
            runCatching { app.tally.chain.CloudPlaces.photo(config.cloudUrl, path) }.getOrNull()
                ?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size) }
        }?.let { placePhotos[merchant] = it.asImageBitmap() }
    }

    /** True while the chain can't be reached; Home then shows the last snapshot read-only. */
    var offline by mutableStateOf(false)

    private suspend fun refreshNow() = withContext(Dispatchers.IO) {
        val rpc = g.rpc
        val online = runCatching { rpc.getSlot() }
        if (online.isFailure) {
            offline = true
            return@withContext
        }
        offline = false
        slot = online.getOrThrow()
        agentSol = runCatching { rpc.getBalance(agentAddress) }.getOrDefault(0)
        solUsd = app.tally.chain.Prices.solUsd() ?: solUsd
        val c = capsuleAddress?.let { rpc.getAccountData(it) }?.let(Accounts::capsule)
        capsule = c
        vaultBalance = c?.let { rpc.getAccountData(it.vault) }?.let(Accounts::tokenAmount) ?: 0
        sgt = ownerAddress?.let { findSgt(it) }
        promoClaimed = c?.takeIf { it.flags and 2 != 0 }?.let { rpc.getAccountData(TallyPda.sgtClaim(it.sgtMint, LAUNCH_PROMO)) != null } ?: false
        g.store.snapshot = c?.let { CapsuleSnapshot(System.currentTimeMillis(), vaultBalance, spentToday, it.dailyCap, it.perTxCap, it.frozen, it.flags and 2 != 0) }
        checkLevelUp()
    }

    /** Look for a Seeker Genesis Token of the configured collection in the owner's Token-2022 accounts. */
    private fun findSgt(owner: String): Pair<String, String>? = runCatching {
        if (config.sgtGroup.isBlank()) return null
        val ownerKey = Base58.decode(owner)
        g.rpc.getTokenAccountsByOwner(owner, Programs.TOKEN_2022).firstNotNullOfOrNull { (acct, data) ->
            val (mint, _) = Sgt.holding(data, ownerKey) ?: return@firstNotNullOfOrNull null
            val mintData = g.rpc.getAccountData(mint) ?: return@firstNotNullOfOrNull null
            if (Sgt.groupOf(mintData, Base58.decode(mint)) == config.sgtGroup) acct to mint else null
        }
    }.getOrNull()

    suspend fun createCapsule(rules: ParsedRules, fundMicro: Long, requireLighthouse: Boolean = false) = work("Waiting for Seed Vault") {
        val ownerAddr = ownerAddress ?: owner.connect().also { ownerAddress = it; g.store.ownerAddress = it }
        val usdcMint = config.usdcMint.ifBlank { error("Cluster config missing: run scripts/export-app-config.ts") }
        val slot = withContext(Dispatchers.IO) { g.rpc.getSlot() }
        val expiry = slot + 30L * 216_000 // ~30 days
        val r = rules.toRules(expiry, flags = if (requireLighthouse) 1 else 0)
        val seeker = withContext(Dispatchers.IO) { findSgt(ownerAddr) }
        val ixs = mutableListOf<Instruction>(TallyIx.createCapsule(ownerAddr, g.store.capsuleId, r, agentAddress, usdcMint, sgt = seeker))
        if (fundMicro > 0) ixs += TallyIx.fund(ownerAddr, TallyPda.capsule(ownerAddr, g.store.capsuleId), usdcMint, fundMicro)
        val sig = owner.signAndSend(ixs, g.rpc)
        log("Capsule created", "Tally", fundMicro, "paid", "Rules signed in ${owner.label}", sig)
        refreshNow()
        screen = Screen.Home
    }

    suspend fun updateRules(rules: ParsedRules, requireLighthouse: Boolean) = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        val expiry = capsule?.expirySlot ?: (withContext(Dispatchers.IO) { g.rpc.getSlot() } + 30L * 216_000)
        // The program keeps FLAG_SGT_VERIFIED itself; owner rules may not set it.
        val r = rules.toRules(expiry, flags = if (requireLighthouse) 1 else 0)
        owner.signAndSend(listOf(TallyIx.updateRules(o, cap, r)), g.rpc)
        refreshNow()
        screen = Screen.Home
    }

    /** Today's daily cap from the owner's schedule, when the capsule on chain doesn't match it yet. */
    var schedule by mutableStateOf(g.store.schedule)
    val scheduleDue: Long?
        get() = capsule?.let { c -> schedule?.due(c.dailyCap, java.time.LocalDate.now().dayOfWeek) }

    fun saveSchedule(v: app.tally.core.AllowanceSchedule?) {
        g.store.schedule = v
        schedule = v
    }

    /** Sign today's scheduled cap: same rules, new daily cap (and a per-payment cap no higher than it). */
    suspend fun applySchedule() = work("Waiting for your wallet") {
        val o = ownerAddress ?: return@work
        val c = capsule ?: return@work
        val target = schedule?.due(c.dailyCap, java.time.LocalDate.now().dayOfWeek) ?: return@work
        val r = app.tally.core.Rules(schedule!!.perTxFor(c.perTxCap, target), target, c.allowedCategories, c.minMerchantBond, c.expirySlot, flags = c.flags and 1)
        owner.signAndSend(listOf(TallyIx.updateRules(o, capsuleAddress!!, r)), g.rpc)
        refreshNow()
        toast = "Today's allowance enabled: \$${app.tally.core.usdc(target)} a day."
    }

    suspend fun fund(amountMicro: Long) = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        val c = capsule ?: return@work
        owner.signAndSend(listOf(TallyIx.fund(o, capsuleAddress!!, c.mint, amountMicro)), g.rpc)
        refreshNow()
    }

    suspend fun withdraw(amountMicro: Long) = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        val c = capsule ?: return@work
        owner.signAndSend(listOf(AssociatedToken.createIdempotent(o, o, c.mint), TallyIx.withdraw(o, capsuleAddress!!, c.mint, amountMicro)), g.rpc)
        refreshNow()
    }

    /** The agent pays network fees and receipt rent; the owner tops it up with SOL. */
    suspend fun topUpAgent(lamports: Long = 50_000_000) = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        owner.signAndSend(listOf(SystemProgram.transfer(o, agentAddress, lamports)), g.rpc)
        refreshNow()
    }

    /** Seeker-only promo: one on-chain claim per Seeker Genesis Token per promo id. */
    suspend fun claimPromo(promoId: Long = 1) = work("Claiming Seeker promo") {
        val o = ownerAddress ?: return@work
        val c = capsule ?: return@work
        // The claim PDA is the one-per-token nullifier: a second claim fails creating it (system error 0).
        val sig = runCatching { owner.signAndSend(listOf(TallyIx.claimPromo(o, capsuleAddress!!, c.sgtMint, promoId)), g.rpc) }
            .getOrElse { e -> if (e.message?.startsWith("Already paid") == true || e.message?.contains("0x0") == true) error("This Seeker Genesis Token already claimed promo #$promoId. One claim per device.") else throw e }
        promoClaimed = true
        log("Seeker promo #$promoId claimed", "Tally", 0, "paid", "One claim per Seeker Genesis Token", sig)
    }

    /** Anyone can slash a merchant whose own signed menu contradicts its certified cheapest price. */
    suspend fun reportFraud() = work("Reporting merchant") {
        val o = ownerAddress ?: owner.connect()
        val p = payload ?: return@work
        val ixs = TallyIx.fraudReport(o, p.menu, config.skrMint) ?: error("This menu has no provable contradiction")
        val sig = owner.signAndSend(listOf(AssociatedToken.createIdempotent(o, o, config.skrMint)) + ixs, g.rpc)
        log("Reported ${merchant?.name}: contradictory floor", merchant?.name ?: "", 0, "blocked", "Fraud proof accepted: part of the merchant's SKR bond was slashed (10% to you, 90% to the compensation pool).", sig, attack = true)
        toast = "Fraud proven on-chain. The merchant's bond was slashed."
    }

    /** Dispute a settled purchase (wrong goods, not delivered); the configured arbiter resolves it. */
    suspend fun dispute(entry: LogEntry, reason: String) = work("Opening dispute") {
        val o = ownerAddress ?: return@work
        val receipt = entry.receipt ?: error("This entry has no on-chain receipt")
        val merchantPda = entry.merchantPda ?: error("Unknown merchant")
        val sig = owner.signAndSend(listOf(TallyIx.openDispute(o, capsuleAddress!!, receipt, merchantPda, sha256(reason.toByteArray()))), g.rpc)
        g.store.markDisputed(receipt)
        log("Dispute opened: ${entry.title}", entry.merchant, entry.amountMicro, "pending", "Reason: $reason. The merchant's bond can't be withdrawn while this is open.", sig)
    }

    /**
     * Close the capsule: the vault's USDC and both accounts' rent go back to the owner. Creating a
     * new one afterwards picks up a Seeker Genesis Token the owner received since.
     */
    suspend fun closeCapsule() = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        val c = capsule ?: return@work
        val refund = vaultBalance
        val sig = owner.signAndSend(listOf(AssociatedToken.createIdempotent(o, o, c.mint), TallyIx.closeCapsule(o, capsuleAddress!!, c.mint)), g.rpc)
        log("Capsule closed", "Tally", 0, "paid", "\$${usdc(refund)} USDC and the account rent returned to your wallet.", sig)
        refreshNow()
    }

    suspend fun setFrozen(frozen: Boolean) = work(if (frozen) "Freezing" else "Unfreezing") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        owner.signAndSend(listOf(if (frozen) TallyIx.freeze(o, cap) else TallyIx.unfreeze(o, cap)), g.rpc)
        refreshNow()
    }

    /** Point the capsule at this phone's existing agent key (after a reinstall or on a new phone). */
    suspend fun adoptAgent() = work("Waiting for Seed Vault") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        owner.signAndSend(listOf(TallyIx.setAgent(o, cap, agentAddress)), g.rpc)
        refreshNow()
    }

    suspend fun rotateAgent() = work("Rotating agent key") {
        val o = ownerAddress ?: return@work
        val cap = capsuleAddress ?: return@work
        // Sweep the retiring key's fee balance back to the owner first; once rotated it can never sign again.
        val leftover = withContext(Dispatchers.IO) { runCatching { g.rpc.getBalance(agentAddress) }.getOrDefault(0) }
        if (leftover > 10_000) {
            val sweep = g.submitter.buildAgentSigned(agentAddress, listOf(SystemProgram.transfer(agentAddress, o, leftover - 5_000))) { g.agentKey.sign(it) }
            g.submitter.sendAndConfirm(sweep)
        }
        g.agentKey.rotate()
        owner.signAndSend(listOf(TallyIx.setAgent(o, cap, agentAddress)), g.rpc)
        refreshNow()
    }

    // ------------------------------------------------------------------ scan → checkout

    /** A purchase proposed by an outside agent (MCP, chat): it only pre-fills checkout. */
    var proposal by mutableStateOf<Proposal?>(null)

    suspend fun onProposal(pr: Proposal) {
        onPayload(pr.payload)
        if (payload === pr.payload) proposal = pr
    }

    suspend fun onPayload(p: SignedPayload) = work("Verifying signed menu") {
        proposal = null
        val (m, slot) = withContext(Dispatchers.IO) {
            val data = g.rpc.getAccountData(p.menu.merchant) ?: error("Unknown merchant: not in the Tally registry")
            Accounts.merchant(data) to g.rpc.getSlot()
        }
        merchant = m
        payload = p
        issues = Payload.verifyLocally(p, Base58.decode(m.signingKey), slot) + (if (!m.active) listOf(MenuIssue.BadSignature) else emptyList())
        g.store.lastPayloadQr = Payload.toQr(p)
        reorderCheck = reorder?.let { Reorder.check(it, p.menu) }
        cart = emptyList()
        cartResults = emptyList()
        agentResult = null
        payResult = null
        screen = Screen.Checkout
    }

    /** Debug builds: sign a fresh menu with this phone's own merchant key and check out against it. */
    suspend fun payOwnMerchant() {
        val auth = ownerAddress ?: return
        val slot = work("Signing a fresh menu") { withContext(Dispatchers.IO) { g.rpc.getSlot() } } ?: return
        onPayload(signOwnMenu(auth, slot))
    }

    /**
     * The current menu if it is still comfortably live. In debug loopback (this phone is the merchant)
     * an expiring menu is simply re-signed; otherwise null means "scan the counter again".
     */
    private suspend fun livePayload(): SignedPayload? {
        val p = payload ?: return null
        val slot = withContext(Dispatchers.IO) { g.rpc.getSlot() }
        if (Purchase.gate(slot, p.menu.expiresSlot) != Purchase.Gate.Rescan) return p
        val own = ownerAddress?.takeIf { BuildConfig.DEMO_OWNER_ALLOWED && p.menu.merchant == TallyPda.merchant(it) } ?: return null
        return signOwnMenu(own, slot).also { payload = it }
    }

    private fun signOwnMenu(auth: String, slot: Long): SignedPayload {
        val terminal = MerchantTerminal(g.merchantMenuKey)
        val (p, _) = terminal.publish(terminal.merchantAddress(auth), slot, System.currentTimeMillis(), g.store.catalog)
        terminal.stop()
        return p
    }

    suspend fun askAgent(request: String) = work("Agent is choosing") {
        val p = payload ?: return@work
        val c = capsule
        agentResult = withContext(Dispatchers.IO) {
            g.agent.choose(request, c?.let { cap -> (0 until 256).filter(cap::allows) } ?: listOf(3), c?.perTxCap ?: 6_000_000, p.rows())
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

    private fun params(p: SignedPayload, index: Int, ownerSigns: Boolean, overridePayout: String? = null, pow: PowSolution? = null, lighthouseVaultAfter: Long? = null): PayOfferParams {
        val c = capsule ?: error("Create a capsule first")
        val m = merchant ?: error("Scan a merchant first")
        return PayOfferParams(
            menu = p.menu, index = index, capsule = capsuleAddress!!, owner = c.owner, vault = c.vault, mint = c.mint,
            agent = agentAddress, payout = overridePayout ?: m.payout, ownerSigns = ownerSigns, pow = pow, lighthouseVaultAfter = lighthouseVaultAfter,
            computeUnitLimit = 80_000, priorityMicroLamports = 10_000,
        )
    }

    /** Agent pays; if the chain will demand the owner (above floor, low bond, extended window), open Second Look first. */
    // ------------------------------------------------------------------ cart

    var cart by mutableStateOf<List<Int>>(emptyList())
    var cartOpen by mutableStateOf(false)
    var cartResults by mutableStateOf<List<PayResult>>(emptyList())

    fun toggleCart(index: Int) {
        cart = if (index in cart) cart - index else Cart.add(cart, index)
    }

    /** The cart checked against today's limits, or null without a menu and capsule. */
    fun cartPlan(): CartPlan? {
        val p = payload ?: return null
        val c = capsule ?: return null
        val m = merchant ?: return null
        return Cart.plan(
            cart, p.menu.items, p.names, c.perTxCap,
            Purchase.dailyRemaining(c.dailyCap, c.spentInCycle, c.cycleStartSlot, slot), vaultBalance,
        ) { Purchase.needsOwner(it, m.skrBonded, c.minMerchantBond, p.menu.window) }
    }

    /** Pay the cart's autonomous items one checked transaction at a time; stop at the first failure. */
    suspend fun payCart() {
        val plan = cartPlan()?.takeIf { it.payable } ?: return
        cartOpen = false
        // One lock prompt for the whole cart, not one per line.
        if (!ownerConfirms("Pay ${plan.autonomous.size} items")) return
        preConfirmed = plan.autonomous.size
        val results = mutableListOf<PayResult>()
        for (line in plan.autonomous) {
            pay(line.index, "Cart: ${line.name}")
            payResult?.let { results += it.copy(title = "${line.name}: ${it.title}") }
            if (payResult?.ok != true) break
        }
        cartResults = results
        // Items that need the owner stay in the cart to be reviewed one by one.
        cart = plan.needsApproval.map { it.index }
    }

    /** Index of the item in the latest payment attempt (offered as "save as my usual" once paid). */
    var lastPaidIndex by mutableStateOf<Int?>(null)

    suspend fun pay(index: Int, reason: String) = work("Paying") {
        lastPaidIndex = index
        payload ?: return@work
        if (!ownerConfirms("Pay ${payload?.names?.getOrNull(index) ?: "this item"}")) return@work
        val m = merchant ?: return@work
        val c = capsule ?: error("Create a capsule first")
        val p = livePayload() ?: run {
            toast = "That menu is about to expire. Scan the counter again."
            screen = Screen.Scan
            return@work
        }
        val item = p.menu.items[index]
        val needsOwner = Purchase.needsOwner(item, m.skrBonded, c.minMerchantBond, p.menu.window)
        val (ixs, wire) = agentSignedPay(p, index, needsOwner)
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

    private suspend fun agentSignedPay(p: SignedPayload, index: Int, ownerSigns: Boolean): Pair<List<Instruction>, ByteArray> {
        val m = merchant ?: error("Scan a merchant first")
        val c = capsule ?: error("Create a capsule first")
        val pow = withContext(Dispatchers.Default) { powFor(m, capsuleAddress!!, p.menu.menuNonce) }
        val lighthouse = if (c.flags and 1 != 0) vaultBalance - p.menu.items[index].price else null
        val ixs = PayOffer.build(params(p, index, ownerSigns, pow = pow, lighthouseVaultAfter = lighthouse))
        return ixs to g.submitter.buildAgentSigned(agentAddress, ixs) { g.agentKey.sign(it) }
    }

    suspend fun approveCosign() = work("Waiting for Seed Vault") {
        val pc = pendingCosign ?: return@work
        pendingCosign = null
        val before = payload
        // A person reading Second Look can outlast the menu's short window; never co-sign a stale menu.
        val p = livePayload() ?: run {
            toast = "That menu expired while you were reviewing. Scan the counter again."
            screen = Screen.Scan
            return@work
        }
        val wire = if (p === before) pc.wire else agentSignedPay(p, pc.index, ownerSigns = true).second
        android.util.Log.i("Tally", "co-signing item ${pc.index} (${p.names[pc.index]}), menu ${if (p === before) "unchanged" else "re-signed"}")
        when (val c = owner.cosign(wire)) {
            is Cosigned.Signed -> submit(c.wire, p.names[pc.index], p.menu.items[pc.index], attack = false)
            is Cosigned.Sent -> submit(null, p.names[pc.index], p.menu.items[pc.index], attack = false, sentBy = c.signature)
        }
    }

    fun declineCosign() {
        val pc = pendingCosign ?: return
        pendingCosign = null
        val p = payload ?: return
        val kept = pc.cheaper?.let { (_, cheaper) -> (p.menu.items[pc.index].price - cheaper).coerceAtLeast(0) } ?: 0
        log("Declined: ${p.names[pc.index]}", merchant?.name ?: "", p.menu.items[pc.index].price, "blocked", "You declined a pricier-than-cheapest pick on the Second Look sheet.", null, attack = true, savedMicro = kept)
        payResult = PayResult(false, "You said no", "Nothing was paid.", null)
    }

    /** Send `wire` and wait, or (when the wallet already sent it) just wait on `sentBy`. */
    private suspend fun submit(wire: ByteArray?, name: String, item: Item, attack: Boolean, land: Boolean = false, sentBy: String? = null): PayResult {
        val (sig, outcome) = if (sentBy != null) sentBy to g.submitter.awaitOutcome(sentBy) else g.submitter.sendAndConfirm(wire!!, land = land)
        val r = when (outcome) {
            is Outcome.Confirmed -> PayResult(true, "Paid ${usdc(item.price)} USDC", "$name — straight to ${merchant?.name ?: "the merchant"}'s registered account.", sig)
            is Outcome.Reverted -> PayResult(false, "Blocked on-chain", outcome.sentence, sig)
            is Outcome.Pending -> PayResult(false, "Still confirming", "The network is slow; check Receipts in a moment.", sig)
        }
        val receipt = payload?.let { pl -> capsuleAddress?.let { TallyPda.receipt(it, pl.menu.merchant, pl.menu.menuNonce, item.id) } }
        log(name, merchant?.name ?: "", item.price, if (r.ok) "paid" else if (outcome is Outcome.Pending) "pending" else "blocked", r.detail, sig, attack, receipt.takeIf { r.ok }, payload?.menu?.merchant, category = item.category)
        payResult = r
        if (r.ok && !attack) app.tally.notify.Notifier.paid(context, name, merchant?.name ?: "the merchant", usdc(item.price))
        refreshNow()
        return r
    }

    /** Raised by the notification or widget Freeze button; the owner still confirms in the wallet. */
    var askFreeze by mutableStateOf(false)

    // ------------------------------------------------------------------ red team (docs/14)

    suspend fun runRedTeam() = work("Running attacks") {
        payload ?: error("Scan a merchant menu first")
        // Otherwise the attacks would all fail on staleness and prove nothing about the specific checks.
        val p = livePayload() ?: error("That menu has expired. Scan the counter again, then run the attacks.")
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
        // Target the cheapest at-floor item the capsule's rules actually allow, so each attack is stopped
        // by the check it is aimed at rather than by the category rule.
        val allowed = p.menu.items.indices.filter { i -> capsule?.allows(p.menu.items[i].category) ?: true }.ifEmpty { error("Your rules allow nothing on this menu") }
        val floorIdx = allowed.minBy { p.menu.items[it].price + if (p.menu.items[it].price == p.menu.items[it].floor) 0 else Long.MAX_VALUE / 2 }
        // A real, initialised USDC account that is not the merchant's registered payout (the owner's own).
        // Attacks carry a valid proof of work and Lighthouse assertion when those are on, so they fail
        // only on the check they target.
        val pow = withContext(Dispatchers.Default) { powFor(merchant ?: error("Scan a merchant first"), capsuleAddress!!, p.menu.menuNonce) }
        val lighthouseOn = (capsule?.flags ?: 0) and 1 != 0
        fun hostile(menu: SignedPayload, index: Int, payout: String? = null) = PayOffer.build(
            params(menu, index, false, overridePayout = payout, pow = pow, lighthouseVaultAfter = if (lighthouseOn) vaultBalance - menu.menu.items[index].price else null),
        )
        val mint = capsule?.mint ?: config.usdcMint
        val ownerAta = Pda.ata(ownerAddress!!, mint)
        // When this phone is also the merchant, the owner's account *is* the payout; aim at the agent's own
        // USDC account instead (created in the same transaction so the swap reaches the payee check).
        val (evil, setup) = if (ownerAta == merchant?.payout) Pda.ata(agentAddress, mint) to listOf(AssociatedToken.createIdempotent(agentAddress, agentAddress, mint)) else ownerAta to emptyList()
        // 1. payee swap
        attack("Payee swap → attacker account", setup + hostile(p, floorIdx, evil), p.menu.items[floorIdx])
        // 2. phantom add-on: an unsigned $2.99 fee appended to the signed menu
        val fee = Item(ByteArray(16) { 0xfe.toByte() }, p.menu.items[floorIdx].category, 2_990_000, 2_990_000)
        val tampered = SignedPayload(SignedMenu(p.menu.merchant, p.menu.issuedSlot, p.menu.expiresSlot, p.menu.menuNonce, p.menu.items + fee, p.menu.header, p.menu.signerPubkey, p.menu.signature), p.names + "Service fee", p.descriptions + null)
        attack("Phantom add-on: unsigned \$2.99 fee", hostile(tampered, tampered.menu.items.size - 1), fee)
        // 3. price tampering: same item, inflated price
        val inflated = SignedPayload(SignedMenu(p.menu.merchant, p.menu.issuedSlot, p.menu.expiresSlot, p.menu.menuNonce, p.menu.items.mapIndexed { i, it -> if (i == floorIdx) it.copy(price = it.price * 2, floor = it.price * 2) else it }, p.menu.header, p.menu.signerPubkey, p.menu.signature), p.names, p.descriptions)
        attack("Price tampering: 2× the signed price", hostile(inflated, floorIdx), inflated.menu.items[floorIdx])
        // 4. Whisper upsell without the owner: pick the priciest same-category item
        // Keep the upsell inside the per-purchase and daily limits, so it meets the cheapest-match rule
        // rather than a budget check.
        val budget = capsule?.let { c -> minOf(c.perTxCap, Purchase.dailyRemaining(c.dailyCap, c.spentInCycle, c.cycleStartSlot, withContext(Dispatchers.IO) { g.rpc.getSlot() })) } ?: Long.MAX_VALUE
        val upsell = allowed.filter { p.menu.items[it].category == p.menu.items[floorIdx].category && it != floorIdx && p.menu.items[it].price <= budget }.maxByOrNull { p.menu.items[it].price }
        if (upsell != null) attack("Whisper upsell: \"regular is discontinued\"", hostile(p, upsell), p.menu.items[upsell])
        else log("Whisper upsell skipped", merchant?.name ?: "", 0, "pending", "No pricier item fits today's remaining budget; the daily limit already blocks it.", null, attack = true)
        redTeam = results.toList()
    }

    // ------------------------------------------------------------------ log

    private fun log(title: String, merchant: String, amount: Long, outcome: String, detail: String, sig: String?, attack: Boolean = false, receipt: String? = null, merchantPda: String? = null, savedMicro: Long = 0, category: Int = -1) {
        g.store.append(LogEntry(System.currentTimeMillis(), title, merchant, amount, outcome, detail, sig, attack, receipt, merchantPda, savedMicro = savedMicro, category = category))
        logVersion++
        app.tally.widget.TallyWidget.refresh(context)
        checkLevelUp()
    }

    // ------------------------------------------------------------------ rewards, summary, usual

    /** The owner's own counter (if registered): payments to it never earn points. */
    private val ownMerchantPda get() = ownerAddress?.let { TallyPda.merchant(it) }

    private fun records(): List<SpendRecord> {
        @Suppress("UNUSED_VARIABLE") val v = logVersion
        return g.store.records(ownMerchantPda)
    }

    val rewards: Rewards.Tally get() = Rewards.tally(records(), seeker = (capsule?.flags ?: 0) and 2 != 0)
    val weekly: Summary get() = Insights.summary(records())

    /** Set when the owner reaches a level they haven't been congratulated on yet. */
    var levelUp by mutableStateOf<Level?>(null)

    private fun checkLevelUp() {
        val now = rewards.level
        val seen = runCatching { Level.valueOf(g.store.celebratedLevel) }.getOrDefault(Level.Starter)
        if (now.ordinal > seen.ordinal) {
            g.store.celebratedLevel = now.name
            levelUp = now
        }
    }

    /** The usual being reordered, and how today's signed menu compares with it. */
    var reorder by mutableStateOf<Usual?>(null)
    var reorderCheck by mutableStateOf<ReorderCheck?>(null)

    fun startReorder() {
        reorder = g.store.usual ?: return
        reorderCheck = null
        screen = Screen.Scan
    }

    fun cancelReorder() {
        reorder = null
        reorderCheck = null
    }

    /** Remember the item just bought here as the owner's usual. */
    fun saveUsual(index: Int) {
        val p = payload ?: return
        val m = merchant ?: return
        val item = p.menu.items[index]
        g.store.usual = Usual(p.menu.merchant, m.name, Hex.encode(item.id), p.names[index], item.price, item.category)
        logVersion++
        toast = "Saved: ${p.names[index]} at ${m.name} is your usual. Next time it's one tap, as long as nothing changes."
    }

    /** Pay the usual. Same item and price: straight to payment; any change was shown and this tap confirms it. */
    suspend fun payUsual() {
        val u = reorder ?: return
        val idx = when (val c = reorderCheck) {
            is ReorderCheck.Same -> c.index
            is ReorderCheck.PriceChanged -> c.index
            else -> return
        }
        pay(idx, "Your usual: ${u.name}")
        if (payResult?.ok == true) {
            payload?.let { p -> if (reorderCheck is ReorderCheck.PriceChanged) g.store.usual = u.copy(priceMicro = p.menu.items[idx].price) }
            cancelReorder()
        }
    }

    fun itemName(p: SignedPayload, itemIdHex: String) = p.menu.items.indexOfFirst { Hex.encode(it.id) == itemIdHex }.let { if (it >= 0) it to p.names[it] else null }

    val agentLabel get() = if (agentResult?.source == AgentSource.Model) "Groq model (structured fields only)" else "Rule agent"
}
