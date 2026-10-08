package app.tally.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Every instruction the app builds must equal the Codama-generated client's (fixtures/ix-v1.json). */
class IxVectorTest {
    private val v = Fixtures.json("ix-v1.json")
    private val inp = v["inputs"]!!.jsonObject
    private fun s(k: String) = inp[k]!!.jsonPrimitive.content
    private val owner get() = s("owner")
    private val rules = Rules(6_000_000, 15_000_000, longArrayOf(8, 0, 0, Long.MIN_VALUE), 1_000_000_000, 999_999_999, flags = 1)

    private fun check(name: String, ix: Instruction) {
        val e = v[name]!!.jsonObject
        assertEquals(e["program"]!!.jsonPrimitive.content, ix.programId, "$name program")
        assertEquals(e["data_hex"]!!.jsonPrimitive.content, Hex.encode(ix.data), "$name data")
        val metas = e["accounts"]!!.jsonArray.map { it.jsonObject.let { a: JsonObject -> AccountMeta(a["address"]!!.jsonPrimitive.content, a["signer"]!!.jsonPrimitive.boolean, a["writable"]!!.jsonPrimitive.boolean) } }
        assertEquals(metas, ix.accounts, "$name accounts")
    }

    @Test fun createCapsule() = check("create_capsule", TallyIx.createCapsule(owner, 1, rules, s("agent"), s("usdc")))
    @Test fun createCapsuleWithSgt() = check("create_capsule_with_sgt", TallyIx.createCapsule(owner, 1, rules, s("agent"), s("usdc"), sgt = s("sgtAccount") to s("sgtMint")))
    @Test fun fund() = check("fund", TallyIx.fund(owner, s("capsule"), s("usdc"), 20_000_000))
    @Test fun withdraw() = check("withdraw", TallyIx.withdraw(owner, s("capsule"), s("usdc"), 5_000_000))
    @Test fun freeze() = check("freeze", TallyIx.freeze(owner, s("capsule")))
    @Test fun setAgent() = check("set_agent", TallyIx.setAgent(owner, s("capsule"), s("agent")))
    @Test fun updateRules() = check("update_rules", TallyIx.updateRules(owner, s("capsule"), rules))
    @Test fun registerMerchant() = check("register_merchant", TallyIx.registerMerchant(owner, Pda.ata(owner, s("usdc")), s("skr"), "Blue Door Coffee", s("signingKey"), 5_000_000_000))
    @Test fun setPow() = check("set_pow_difficulty", TallyIx.setPowDifficulty(owner, 8))
    @Test fun openDispute() = check("open_dispute", TallyIx.openDispute(owner, s("capsule"), s("receipt"), s("merchant"), ByteArray(32) { 9 }))
    @Test fun claimPromo() = check("claim_promo", TallyIx.claimPromo(owner, s("capsule"), s("sgtMint"), 7))

    @Test
    fun proveFloorFraud() {
        fun leaf(n: Int) = Item(ByteArray(16) { n.toByte() }, 3, n * 1_000_000L, 3_000_000)
        check(
            "prove_floor_fraud",
            TallyIx.proveFloorFraud(
                reporter = owner, merchant = s("merchant"), skrMint = s("skr"), signingKey = s("signingKey"),
                menuRoot = ByteArray(32) { 5 }, issuedSlot = 100, expiresSlot = 200, menuNonce = 42, itemCount = 9,
                leafA = leaf(3), proofA = listOf(ByteArray(32) { 1 }), leafB = leaf(5), proofB = listOf(ByteArray(32) { 2 }, ByteArray(32) { 4 }),
            ),
        )
    }
}
