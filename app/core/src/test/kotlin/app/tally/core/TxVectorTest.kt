package app.tally.core

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Base64

/** Byte equality with the TypeScript SDK (@solana/kit), pinned by fixtures/tx-v1.json. */
class TxVectorTest {
    private val tx = Fixtures.json("tx-v1.json")
    private val menu = Fixtures.json("menu-v1.json")
    private fun s(o: kotlinx.serialization.json.JsonObject, k: String) = o[k]!!.jsonPrimitive.content
    private val acc = tx["accounts"]!!.jsonObject
    private val pdas = tx["pdas"]!!.jsonObject
    private val owner = s(acc, "owner")
    private val agent = s(acc, "agent")

    @Test
    fun pdasMatchSdk() {
        assertEquals(s(pdas, "config"), TallyPda.config())
        assertEquals(s(pdas, "merchant"), TallyPda.merchant(s(acc, "authority")))
        assertEquals(s(pdas, "capsule"), TallyPda.capsule(owner, 1))
        assertEquals(s(pdas, "key_record"), TallyPda.keyRecord(s(pdas, "merchant"), s(menu, "merchant_signing_pubkey")))
        val item1 = Hex.decode(s(menu["items"]!!.jsonArray[1].jsonObject, "id_hex"))
        assertEquals(s(pdas, "receipt"), TallyPda.receipt(s(pdas, "capsule"), s(pdas, "merchant"), 42, item1))
    }

    private fun payInstructions(): List<Instruction> {
        val items = menu["items"]!!.jsonArray.map {
            val o = it.jsonObject
            Item(Hex.decode(s(o, "id_hex")), o["category"]!!.jsonPrimitive.int, o["price"]!!.jsonPrimitive.long, o["floor"]!!.jsonPrimitive.long)
        }
        val signed = SignedMenu(
            merchant = s(pdas, "merchant"),
            issuedSlot = 1000,
            expiresSlot = 1100,
            menuNonce = 42,
            items = items,
            header = Hex.decode(s(menu, "header_hex")),
            signerPubkey = Base58.decode(s(menu, "merchant_signing_pubkey")),
            signature = Hex.decode(s(menu, "signature_hex")),
        )
        val p = tx["pay_offer"]!!.jsonObject
        return PayOffer.build(
            PayOfferParams(
                menu = signed,
                index = p["item_index"]!!.jsonPrimitive.int,
                capsule = s(pdas, "capsule"),
                owner = owner,
                vault = s(acc, "vault"),
                mint = s(acc, "usdc"),
                agent = agent,
                payout = s(acc, "payout"),
                ownerSigns = true,
                pow = PowSolution(Hex.decode(s(p, "pow_nonce_hex")), p["pow_slot"]!!.jsonPrimitive.long),
                lighthouseVaultAfter = p["lighthouse_vault_after"]!!.jsonPrimitive.long,
                computeUnitLimit = p["compute_unit_limit"]!!.jsonPrimitive.int,
            ),
        )
    }

    @Test
    fun payOfferInstructionsMatchSdk() {
        val ixs = payInstructions()
        val p = tx["pay_offer"]!!.jsonObject
        assertEquals(p["program_ids"]!!.jsonArray.map { it.jsonPrimitive.content }, ixs.map { it.programId })
        assertEquals(p["instruction_data_hex"]!!.jsonArray.map { it.jsonPrimitive.content }, ixs.map { Hex.encode(it.data) })
    }

    @Test
    fun compiledMessageMatchesSdk() {
        val m = tx["message"]!!.jsonObject
        val bytes = Message.compile(feePayer = s(m, "fee_payer"), blockhash = s(m, "blockhash"), instructions = payInstructions())
        assertEquals(s(m, "message_b64"), Base64.getEncoder().encodeToString(bytes))
    }
}
