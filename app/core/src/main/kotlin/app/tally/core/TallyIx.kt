package app.tally.core

/** A merchant-signed menu as delivered over QR or NFC (docs/07). */
class SignedMenu(
    val merchant: String,
    val issuedSlot: Long,
    val expiresSlot: Long,
    val menuNonce: Long,
    val items: List<Item>,
    /** The 144-byte header the merchant signed. */
    val header: ByteArray,
    val signerPubkey: ByteArray,
    val signature: ByteArray,
) {
    /** The signed Merkle root (header bytes 80..112). */
    val root: ByteArray get() = header.copyOfRange(80, 112)

    /** The signed item count (header bytes 136..140). */
    val itemCount: Int get() = Le.readU32(header, 136)

    val window: Long get() = expiresSlot - issuedSlot

    companion object {
        /** Merchant side: compute floors, root and header, and sign with a 32-byte Ed25519 seed. */
        fun sign(merchant: String, issuedSlot: Long, expiresSlot: Long, menuNonce: Long, items: List<Item>, seed: ByteArray): SignedMenu {
            val root = MerkleTree(items.map(Menu::leafHash)).root()
            val header = Menu.buildHeader(
                HeaderFields(Base58.decode(Tally.PROGRAM_ID), Base58.decode(merchant), root, issuedSlot, expiresSlot, menuNonce, items.size),
            )
            return SignedMenu(merchant, issuedSlot, expiresSlot, menuNonce, items, header, Ed25519.publicKey(seed), Ed25519.sign(seed, header))
        }
    }
}

class PowSolution(val nonce: ByteArray, val slot: Long)

class PayOfferParams(
    val menu: SignedMenu,
    val index: Int,
    val capsule: String,
    val owner: String,
    val vault: String,
    val mint: String,
    val agent: String,
    /** The merchant's registered payout (read from the Merchant account, never the QR). */
    val payout: String,
    val ownerSigns: Boolean = false,
    val pow: PowSolution? = null,
    val lighthouseVaultAfter: Long? = null,
    val computeUnitLimit: Int? = null,
    val priorityMicroLamports: Long? = null,
)

/** Builds `[ComputeBudget…] · Ed25519 · pay_offer · [Lighthouse]` in the order the program requires. */
object PayOffer {
    private val DISCRIMINATOR = byteArrayOf(122, 224.toByte(), 197.toByte(), 240.toByte(), 101, 128.toByte(), 189.toByte(), 165.toByte())

    fun data(p: PayOfferParams): ByteArray {
        val item = p.menu.items[p.index]
        val proof = MerkleTree(p.menu.items.map(Menu::leafHash)).proof(p.index)
        val b = Borsh().bytes(DISCRIMINATOR)
            .bytes(p.menu.root) // the signed root: a tampered item must fail inclusion
            .u64(p.menu.issuedSlot)
            .u64(p.menu.expiresSlot)
            .u64(p.menu.menuNonce)
            .u32(p.menu.itemCount) // the signed count
            .bytes(item.id)
            .u16(item.category)
            .u64(item.price)
            .u64(item.floor)
            .vec32(proof)
        val pow = p.pow
        if (pow == null) b.u8(0) else b.u8(1).bytes(pow.nonce).u64(pow.slot)
        return b.toByteArray()
    }

    fun build(p: PayOfferParams): List<Instruction> {
        val item = p.menu.items[p.index]
        val out = mutableListOf<Instruction>()
        p.computeUnitLimit?.let { out += ComputeBudget.setUnitLimit(it) }
        p.priorityMicroLamports?.let { out += ComputeBudget.setUnitPrice(it) }
        out += Instruction(Tally.ED25519_PROGRAM_ID, emptyList(), Menu.ed25519IxData(p.menu.signerPubkey, p.menu.signature, p.menu.header))
        out += Instruction(
            Tally.PROGRAM_ID,
            listOf(
                AccountMeta(p.capsule, false, true),
                AccountMeta(p.agent, true, true),
                AccountMeta(p.owner, p.ownerSigns, false),
                AccountMeta(p.menu.merchant, false, false),
                AccountMeta(p.mint, false, false),
                AccountMeta(p.vault, false, true),
                AccountMeta(p.payout, false, true),
                AccountMeta(TallyPda.receipt(p.capsule, p.menu.merchant, p.menu.menuNonce, item.id), false, true),
                AccountMeta(Programs.SYSVAR_INSTRUCTIONS, false, false),
                AccountMeta(Programs.SYSVAR_SLOT_HASHES, false, false),
                AccountMeta(Programs.TOKEN, false, false),
                AccountMeta(Programs.SYSTEM, false, false),
            ),
            data(p),
        )
        p.lighthouseVaultAfter?.let { out += Lighthouse.assertTokenAmount(p.vault, it) }
        return out
    }
}

/** Capsule rules (mirrors `instructions::capsule::Rules`). */
class Rules(
    val perTxCap: Long,
    val dailyCap: Long,
    val allowedCategories: LongArray,
    val minMerchantBond: Long,
    val expirySlot: Long,
    val flags: Int = 0,
) {
    fun encode(b: Borsh) = b.u64(perTxCap).u64(dailyCap).also { allowedCategories.forEach(b::u64) }.u64(minMerchantBond).u64(expirySlot).u8(flags)

    companion object {
        fun categories(vararg codes: Int) = LongArray(4).also { a -> codes.forEach { a[it / 64] = a[it / 64] or (1L shl (it % 64)) } }
    }
}

/** Owner and merchant instructions the app sends through MWA. */
object TallyIx {
    private fun disc(vararg b: Int) = ByteArray(b.size) { b[it].toByte() }
    private val CREATE_CAPSULE = disc(195, 104, 42, 180, 127, 169, 62, 3)
    private val CREATE_CAPSULE_WITH_SGT = disc(89, 92, 104, 252, 13, 118, 204, 134)
    private val FUND = disc(218, 188, 111, 221, 152, 113, 174, 7)
    private val WITHDRAW = disc(183, 18, 70, 156, 148, 109, 161, 34)
    private val FREEZE = disc(255, 91, 207, 84, 251, 194, 254, 63)
    private val UNFREEZE = disc(133, 160, 68, 253, 80, 232, 218, 247)
    private val CLOSE_CAPSULE = disc(16, 246, 167, 171, 45, 72, 171, 122)
    private val SET_AGENT = disc(154, 74, 121, 91, 137, 19, 101, 166)
    private val UPDATE_RULES = disc(9, 156, 238, 50, 25, 101, 141, 28)
    private val REGISTER_MERCHANT = disc(238, 245, 77, 132, 161, 88, 216, 248)
    private val SET_POW_DIFFICULTY = disc(246, 197, 49, 165, 77, 79, 63, 196)
    private val ROTATE_SIGNING_KEY = disc(229, 103, 107, 44, 84, 162, 133, 170)
    private val OPEN_DISPUTE = disc(137, 25, 99, 119, 23, 223, 161, 42)
    private val CLAIM_PROMO = disc(236, 205, 177, 4, 130, 30, 37, 238)
    private val PROVE_FLOOR_FRAUD = disc(38, 11, 22, 2, 124, 42, 99, 200)

    private fun w(k: String) = AccountMeta(k, false, true)
    private fun r(k: String) = AccountMeta(k, false, false)
    private fun signerW(k: String) = AccountMeta(k, true, true)
    private fun signerR(k: String) = AccountMeta(k, true, false)

    fun createCapsule(owner: String, capsuleId: Long, rules: Rules, agent: String, usdcMint: String, sgt: Pair<String, String>? = null): Instruction {
        val capsule = TallyPda.capsule(owner, capsuleId)
        val vault = Pda.ata(capsule, usdcMint)
        val data = Borsh().bytes(if (sgt == null) CREATE_CAPSULE else CREATE_CAPSULE_WITH_SGT).u64(capsuleId).also { rules.encode(it) }.pubkey(agent).toByteArray()
        val metas = mutableListOf(signerW(owner), r(TallyPda.config()), w(capsule), r(usdcMint), w(vault))
        if (sgt != null) metas += listOf(r(sgt.first), r(sgt.second))
        metas += listOf(r(Programs.TOKEN), r(Programs.ASSOCIATED_TOKEN), r(Programs.SYSTEM))
        return Instruction(Tally.PROGRAM_ID, metas, data)
    }

    private fun moveFunds(disc: ByteArray, owner: String, capsule: String, mint: String, amount: Long) =
        Instruction(
            Tally.PROGRAM_ID,
            listOf(signerR(owner), r(capsule), r(mint), w(Pda.ata(capsule, mint)), w(Pda.ata(owner, mint)), r(Programs.TOKEN)),
            Borsh().bytes(disc).u64(amount).toByteArray(),
        )

    fun fund(owner: String, capsule: String, mint: String, amount: Long) = moveFunds(FUND, owner, capsule, mint, amount)
    fun withdraw(owner: String, capsule: String, mint: String, amount: Long) = moveFunds(WITHDRAW, owner, capsule, mint, amount)

    private fun ownerOp(owner: String, capsule: String, data: ByteArray) =
        Instruction(Tally.PROGRAM_ID, listOf(signerR(owner), w(capsule)), data)

    fun freeze(owner: String, capsule: String) = ownerOp(owner, capsule, FREEZE)
    fun unfreeze(owner: String, capsule: String) = ownerOp(owner, capsule, UNFREEZE)

    /** Close the capsule: the vault's balance goes back to the owner, then vault and capsule rent. */
    fun closeCapsule(owner: String, capsule: String, mint: String) = Instruction(
        Tally.PROGRAM_ID,
        listOf(signerW(owner), w(capsule), r(mint), w(Pda.ata(capsule, mint)), w(Pda.ata(owner, mint)), r(Programs.TOKEN)),
        CLOSE_CAPSULE,
    )
    fun setAgent(owner: String, capsule: String, agent: String) = ownerOp(owner, capsule, Borsh().bytes(SET_AGENT).pubkey(agent).toByteArray())
    fun updateRules(owner: String, capsule: String, rules: Rules) =
        ownerOp(owner, capsule, Borsh().bytes(UPDATE_RULES).also { rules.encode(it) }.toByteArray())

    fun registerMerchant(authority: String, payout: String, skrMint: String, name: String, signingKey: String, bond: Long): Instruction {
        val merchant = TallyPda.merchant(authority)
        val nameBytes = ByteArray(32).also { name.toByteArray().copyInto(it, endIndex = minOf(32, name.length)) }
        return Instruction(
            Tally.PROGRAM_ID,
            listOf(
                signerW(authority), r(TallyPda.config()), w(merchant), r(payout), r(skrMint),
                w(Pda.ata(authority, skrMint)), w(TallyPda.bondVault(merchant)), w(TallyPda.keyRecord(merchant, signingKey)),
                r(Programs.TOKEN), r(Programs.SYSTEM),
            ),
            Borsh().bytes(REGISTER_MERCHANT).bytes(nameBytes).pubkey(signingKey).u64(bond).toByteArray(),
        )
    }

    fun setPowDifficulty(authority: String, difficulty: Int) = Instruction(
        Tally.PROGRAM_ID,
        listOf(signerR(authority), w(TallyPda.merchant(authority))),
        Borsh().bytes(SET_POW_DIFFICULTY).u8(difficulty).toByteArray(),
    )

    /**
     * Point the merchant at a new menu-signing key (e.g. after reinstalling or replacing the
     * terminal phone). The old key's record stays as evidence for fraud proofs.
     */
    fun rotateSigningKey(authority: String, oldKey: String, newKey: String): Instruction {
        val merchant = TallyPda.merchant(authority)
        return Instruction(
            Tally.PROGRAM_ID,
            listOf(signerW(authority), w(merchant), w(TallyPda.keyRecord(merchant, oldKey)), w(TallyPda.keyRecord(merchant, newKey)), r(Programs.SYSTEM)),
            Borsh().bytes(ROTATE_SIGNING_KEY).pubkey(newKey).toByteArray(),
        )
    }

    /** Capsule owner disputes a settled receipt (wrong goods, not delivered); the arbiter resolves it. */
    fun openDispute(owner: String, capsule: String, receipt: String, merchant: String, evidenceHash: ByteArray) = Instruction(
        Tally.PROGRAM_ID,
        listOf(signerW(owner), r(capsule), w(receipt), w(merchant), w(TallyPda.dispute(receipt)), r(Programs.SYSTEM)),
        Borsh().bytes(OPEN_DISPUTE).bytes(evidenceHash).toByteArray(),
    )

    /** One Seeker-only promo claim per device (SGT) per promo id. */
    fun claimPromo(owner: String, capsule: String, sgtMint: String, promoId: Long) = Instruction(
        Tally.PROGRAM_ID,
        listOf(signerW(owner), r(capsule), w(TallyPda.sgtClaim(sgtMint, promoId)), r(Programs.SYSTEM)),
        Borsh().bytes(CLAIM_PROMO).u64(promoId).toByteArray(),
    )

    private fun leaf(b: Borsh, i: Item) = b.bytes(i.id).u16(i.category).u64(i.price).u64(i.floor)

    /**
     * Anyone may prove that a merchant's own signed menu contradicts its certified floor
     * (`leafA.price < leafB.floor`, same category). Must be preceded by the Ed25519 instruction
     * over the menu header (see [fraudReport]).
     */
    fun proveFloorFraud(
        reporter: String, merchant: String, skrMint: String, signingKey: String,
        menuRoot: ByteArray, issuedSlot: Long, expiresSlot: Long, menuNonce: Long, itemCount: Int,
        leafA: Item, proofA: List<ByteArray>, leafB: Item, proofB: List<ByteArray>,
    ): Instruction {
        val b = Borsh().bytes(PROVE_FLOOR_FRAUD).bytes(menuRoot).u64(issuedSlot).u64(expiresSlot).u64(menuNonce).u32(itemCount)
        leaf(b, leafA).vec32(proofA)
        leaf(b, leafB).vec32(proofB)
        return Instruction(
            Tally.PROGRAM_ID,
            listOf(
                signerW(reporter), r(TallyPda.config()), w(merchant), w(TallyPda.bondVault(merchant)), r(skrMint),
                w(Pda.ata(reporter, skrMint)), w(TallyPda.pool()), r(TallyPda.keyRecord(merchant, signingKey)),
                w(TallyPda.fraudRecord(merchant, menuRoot)), r(Programs.SYSVAR_INSTRUCTIONS), r(Programs.TOKEN), r(Programs.SYSTEM),
            ),
            b.toByteArray(),
        )
    }

    /** The complete fraud report for a scanned menu: `[Ed25519 verify(header), prove_floor_fraud]`. */
    fun fraudReport(reporter: String, menu: SignedMenu, skrMint: String): List<Instruction>? {
        val (cheap, claimed) = Menu.findFloorContradiction(menu.items) ?: return null
        val tree = MerkleTree(menu.items.map(Menu::leafHash))
        return listOf(
            Instruction(Tally.ED25519_PROGRAM_ID, emptyList(), Menu.ed25519IxData(menu.signerPubkey, menu.signature, menu.header)),
            proveFloorFraud(
                reporter, menu.merchant, skrMint, Base58.encode(menu.signerPubkey),
                menu.root, menu.issuedSlot, menu.expiresSlot, menu.menuNonce, menu.itemCount,
                menu.items[cheap], tree.proof(cheap), menu.items[claimed], tree.proof(claimed),
            ),
        )
    }
}
