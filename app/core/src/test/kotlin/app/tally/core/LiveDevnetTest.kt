package app.tally.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

/**
 * Opt-in end-to-end test of the Android client's transaction path against the live devnet
 * deployment seeded by scripts/seed-demo.ts: Kotlin signs the menu, builds pay_offer, signs as
 * the agent and settles; then a payee swap must revert on-chain.
 *
 *   TALLY_LIVE=1 ./gradlew :core:test --tests '*LiveDevnetTest*'
 */
class LiveDevnetTest {
    private val dir = File(System.getProperty("user.home"), ".config/tally/devnet")
    private val http = HttpClient.newHttpClient()
    private val rpc = Rpc { body ->
        val req = HttpRequest.newBuilder(URI(System.getenv("TALLY_RPC_URL") ?: "https://api.devnet.solana.com"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build()
        http.send(req, HttpResponse.BodyHandlers.ofString()).body()
    }

    private fun bytes(name: String) = Json.parseToJsonElement(File(dir, name).readText()).jsonArray.map { it.jsonPrimitive.int.toByte() }.toByteArray()
    private fun state(k: String) = Json.parseToJsonElement(File(dir, "state.json").readText()).jsonObject[k]!!.jsonPrimitive.content

    private fun pay(index: Int, payoutOverride: String? = null): Outcome {
        val agentSeed = bytes("capsule-agent.json").copyOf(32)
        val agent = Base58.encode(Ed25519.publicKey(agentSeed))
        val owner = Base58.encode(bytes("capsule-owner.json").copyOfRange(32, 64))
        val merchant = Accounts.merchant(rpc.getAccountData(state("merchant"))!!)
        val capsule = Accounts.capsule(rpc.getAccountData(state("capsule"))!!)
        val entries = listOf(
            Item(sha256("tally-item-0".toByteArray()).copyOf(16), 3, 4_500_000, 0),
            Item(sha256("tally-item-1".toByteArray()).copyOf(16), 3, 5_900_000, 0),
            Item(sha256("tally-item-4".toByteArray()).copyOf(16), 0, 3_000_000, 0),
        )
        val slot = rpc.getSlot()
        val menu = SignedMenu.sign(state("merchant"), slot, slot + 120, System.nanoTime(), Menu.computeFloors(entries), bytes("merchant-menu-key.secret.json"))
        assertEquals(merchant.signingKey, Base58.encode(menu.signerPubkey), "menu key matches registry")
        val ixs = PayOffer.build(
            PayOfferParams(menu, index, state("capsule"), owner, capsule.vault, capsule.mint, agent, payoutOverride ?: merchant.payout, computeUnitLimit = 80_000),
        )
        val message = Message.compile(agent, rpc.getLatestBlockhash(), ixs)
        val wire = Message.serialize(message, listOf(Ed25519.sign(agentSeed, message)))
        val sig = rpc.sendTransaction(wire, skipPreflight = true)
        repeat(60) {
            val o = Purchase.outcome(rpc.getSignatureStatus(sig))
            if (o !is Outcome.Pending) return o.also { println("$it  https://explorer.solana.com/tx/$sig?cluster=devnet") }
            Thread.sleep(1000)
        }
        return Outcome.Pending
    }

    @Test
    fun kotlinClientPaysAndPayeeSwapReverts() {
        assumeTrue(System.getenv("TALLY_LIVE") == "1" && File(dir, "state.json").exists(), "set TALLY_LIVE=1 after seeding devnet")
        // Owner path: raise the daily cap so the test can repeat on the same day.
        val ownerSeed = bytes("capsule-owner.json").copyOf(32)
        val owner = Base58.encode(Ed25519.publicKey(ownerSeed))
        val c = Accounts.capsule(rpc.getAccountData(state("capsule"))!!)
        val rules = Rules(6_000_000, 1_000_000_000, c.allowedCategories, c.minMerchantBond, c.expirySlot, flags = 0)
        val msg = Message.compile(owner, rpc.getLatestBlockhash(), listOf(TallyIx.updateRules(owner, state("capsule"), rules)))
        val upd = rpc.sendTransaction(Message.serialize(msg, listOf(Ed25519.sign(ownerSeed, msg))))
        repeat(60) { if (Purchase.outcome(rpc.getSignatureStatus(upd)) == Outcome.Confirmed) return@repeat; Thread.sleep(1000) }
        Thread.sleep(2000)
        assertEquals(Outcome.Confirmed, pay(0))
        // A real, initialised USDC account that is not the merchant's registered payout.
        val evil = Pda.ata(Base58.encode(bytes("capsule-owner.json").copyOfRange(32, 64)), state("usdcMint"))
        val swapped = pay(0, payoutOverride = evil)
        assertTrue(swapped is Outcome.Reverted && swapped.code == 2001, "payee swap must revert with ConstraintHasOne, got $swapped")
        val upsell = pay(1)
        assertTrue(upsell is Outcome.Reverted && upsell.code == 6013, "upsell without owner must need co-sign, got $upsell")
    }
}
