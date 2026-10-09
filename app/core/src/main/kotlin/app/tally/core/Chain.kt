package app.tally.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

class RpcException(message: String) : Exception(message)

data class SigStatus(val confirmed: Boolean, val err: String?)

class Simulation(val err: String?, val logs: List<String>, val postAccounts: List<ByteArray?>, val unitsConsumed: Long?)

/**
 * Blocking JSON-RPC client over a pluggable transport (OkHttp in the app, a fake in tests).
 * Call from a background thread.
 */
class Rpc(private val transport: (String) -> String) {
    private var id = 0

    private fun call(method: String, vararg params: JsonElement): JsonElement {
        val body = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", ++id)
            put("method", method)
            put("params", buildJsonArray { params.forEach { add(it) } })
        }.toString()
        var wait = 500L
        repeat(6) { attempt ->
            val raw = runCatching { transport(body) }.getOrElse { if (attempt == 5) throw RpcException(it.message ?: "RPC unreachable (${it.javaClass.simpleName})") else null }
            val res = raw?.let { runCatching { Json.parseToJsonElement(it).jsonObject }.getOrNull() }
            val err = res?.get("error")
            val message = err?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
            val retryable = res == null || (message != null && ("Too many requests" in message || "rate limit" in message.lowercase()))
            if (res != null && err == null) return res["result"] ?: JsonNull
            if (!retryable || attempt == 5) throw RpcException(message ?: err.toString())
            Thread.sleep(wait)
            wait *= 2
        }
        throw RpcException("unreachable")
    }

    private fun str(v: String) = kotlinx.serialization.json.JsonPrimitive(v)
    private fun cfg(vararg pairs: Pair<String, String>) = buildJsonObject { pairs.forEach { (k, v) -> put(k, v) } }

    fun getSlot(): Long = call("getSlot", cfg("commitment" to "confirmed")).jsonPrimitive.long

    fun getLatestBlockhash(): String =
        call("getLatestBlockhash", cfg("commitment" to "confirmed")).jsonObject["value"]!!.jsonObject["blockhash"]!!.jsonPrimitive.content

    fun getBalance(address: String): Long = call("getBalance", str(address), cfg("commitment" to "confirmed")).jsonObject["value"]!!.jsonPrimitive.long

    fun getAccountData(address: String): ByteArray? {
        val v = call("getAccountInfo", str(address), cfg("encoding" to "base64", "commitment" to "confirmed")).jsonObject["value"]
        if (v == null || v is JsonNull) return null
        return Base64.getDecoder().decode(v.jsonObject["data"]!!.jsonArray[0].jsonPrimitive.content)
    }

    /** `(address, data)` of every token account `owner` holds under `programId`. */
    fun getTokenAccountsByOwner(owner: String, programId: String): List<Pair<String, ByteArray>> {
        val v = call(
            "getTokenAccountsByOwner",
            str(owner),
            buildJsonObject { put("programId", programId) },
            cfg("encoding" to "base64", "commitment" to "confirmed"),
        ).jsonObject["value"]!!.jsonArray
        return v.map { e ->
            val o = e.jsonObject
            o["pubkey"]!!.jsonPrimitive.content to Base64.getDecoder().decode(o["account"]!!.jsonObject["data"]!!.jsonArray[0].jsonPrimitive.content)
        }
    }

    fun sendTransaction(wire: ByteArray, skipPreflight: Boolean = false): String =
        call(
            "sendTransaction",
            str(Base64.getEncoder().encodeToString(wire)),
            buildJsonObject {
                put("encoding", "base64")
                put("skipPreflight", skipPreflight)
                put("preflightCommitment", "confirmed")
            },
        ).jsonPrimitive.content

    fun simulate(wire: ByteArray, accounts: List<String> = emptyList()): Simulation {
        val v = call(
            "simulateTransaction",
            str(Base64.getEncoder().encodeToString(wire)),
            buildJsonObject {
                put("encoding", "base64")
                put("sigVerify", false)
                put("replaceRecentBlockhash", true)
                put("commitment", "confirmed")
                if (accounts.isNotEmpty()) {
                    put("accounts", buildJsonObject {
                        put("encoding", "base64")
                        put("addresses", buildJsonArray { accounts.forEach { add(str(it)) } })
                    })
                }
            },
        ).jsonObject["value"]!!.jsonObject
        val err = v["err"].takeUnless { it == null || it is JsonNull }?.toString()
        val logs = v["logs"]?.takeUnless { it is JsonNull }?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        val post = v["accounts"]?.takeUnless { it is JsonNull }?.jsonArray?.map { a ->
            if (a is JsonNull) null else Base64.getDecoder().decode(a.jsonObject["data"]!!.jsonArray[0].jsonPrimitive.content)
        } ?: emptyList()
        return Simulation(err, logs, post, v["unitsConsumed"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.long)
    }

    fun getSignatureStatus(signature: String): SigStatus? {
        val arr = call("getSignatureStatuses", buildJsonArray { add(str(signature)) }, buildJsonObject { put("searchTransactionHistory", true) })
            .jsonObject["value"]!!.jsonArray
        val s = arr.firstOrNull()?.takeUnless { it is JsonNull }?.jsonObject ?: return null
        val level = s["confirmationStatus"]?.jsonPrimitive?.contentOrNull
        val err = s["err"].takeUnless { it == null || it is JsonNull }?.toString()
        return SigStatus(confirmed = level == "confirmed" || level == "finalized", err = err)
    }
}

/** Decoders for the program's accounts (layouts from `programs/tally/src/state`). */
object Accounts {
    class Merchant(val authority: String, val signingKey: String, val payout: String, val name: String, val skrBonded: Long, val bondUnlockSlot: Long, val powDifficulty: Int, val slashedTotal: Long, val openDisputes: Int, val active: Boolean)

    class Capsule(
        val owner: String, val agent: String, val mint: String, val vault: String,
        val perTxCap: Long, val dailyCap: Long, val spentInCycle: Long, val cycleStartSlot: Long,
        val allowedCategories: LongArray, val minMerchantBond: Long, val expirySlot: Long, val flags: Int,
        val sgtMint: String, val frozen: Boolean, val capsuleId: Long,
    ) {
        fun allows(category: Int) = category in 0..255 && (allowedCategories[category / 64] ushr (category % 64)) and 1L == 1L
    }

    class Receipt(val capsule: String, val merchant: String, val itemId: ByteArray, val category: Int, val price: Long, val floor: Long, val slot: Long, val cosigned: Boolean, val disputed: Boolean)

    private class Reader(val b: ByteArray) {
        var o = 8 // skip the Anchor discriminator
        fun key(): String = Base58.encode(b.copyOfRange(o, o + 32)).also { o += 32 }
        fun bytes(n: Int): ByteArray = b.copyOfRange(o, o + n).also { o += n }
        fun u8(): Int = (b[o].toInt() and 0xff).also { o += 1 }
        fun u16(): Int = ((b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)).also { o += 2 }
        fun u32(): Int = Le.readU32(b, o).also { o += 4 }
        fun u64(): Long = Le.readU64(b, o).also { o += 8 }
        fun bool(): Boolean = u8() != 0
    }

    fun merchant(d: ByteArray) = Reader(d).run {
        Merchant(key(), key(), key(), bytes(32).toString(Charsets.UTF_8).trimEnd('\u0000'), u64(), u64(), u8(), u64(), u32(), bool())
    }

    fun capsule(d: ByteArray) = Reader(d).run {
        Capsule(key(), key(), key(), key(), u64(), u64(), u64(), u64(), LongArray(4) { u64() }, u64(), u64(), u8(), key(), bool(), u64())
    }

    fun receipt(d: ByteArray) = Reader(d).run {
        val capsule = key(); val merchant = key(); bytes(32)
        Receipt(capsule, merchant, bytes(16), u16(), u64(), u64(), u64(), bool(), bool())
    }

    /** SPL token account amount (offset 64). */
    fun tokenAmount(d: ByteArray): Long = Le.readU64(d, 64)
}

object ErrorText {
    private val SENTENCES = mapOf(
        6000 to "This capsule is frozen. Unfreeze it to let your agent pay.",
        6001 to "This capsule has expired. Create a new one to keep paying.",
        6002 to "This merchant is not accepting agent payments right now.",
        6003 to "The merchant's menu signature is missing from the transaction.",
        6004 to "The menu signature was malformed or tampered with.",
        6005 to "That menu wasn't signed by the merchant's registered key.",
        6006 to "The menu details don't match what the merchant signed.",
        6007 to "That menu has expired. Scan the counter again for a fresh one.",
        6008 to "That menu is valid for too long to be trusted automatically.",
        6009 to "That item isn't on the menu the merchant signed.",
        6010 to "Your rules don't allow this category.",
        6011 to "That's above your per-purchase limit.",
        6012 to "That would go over today's limit.",
        6013 to "That's pricier than the cheapest match, so your approval is needed.",
        6014 to "The merchant's payout account uses the wrong token.",
        6015 to "The balance check failed, so nothing was paid.",
        6016 to "The required balance assertion was missing or wrong.",
        6017 to "The merchant is busy and needs a quick proof of work. Try again.",
        6018 to "The proof of work was invalid or too old. Try again.",
        6019 to "An amount overflowed. Nothing was paid.",
        6020 to "You're not allowed to do that.",
        6021 to "The bond is still locked.",
        6022 to "The merchant has an open dispute.",
        6023 to "Those items don't prove a pricing contradiction.",
        6024 to "This device's Seeker Genesis Token couldn't be verified.",
        6025 to "Those capsule rules aren't valid.",
        6026 to "That dispute is already resolved.",
        6027 to "A merchant cannot report itself.",
        6028 to "That evidence was signed with a key retired too long ago.",
        2001 to "The payee or account doesn't match the merchant's registry.",
        3012 to "A required account doesn't exist yet.",
    )

    fun sentence(code: Int): String = SENTENCES[code] ?: "Transaction failed (code $code)."

    /** Program error code from an RPC `err` JSON such as `{"InstructionError":[1,{"Custom":6013}]}`. */
    fun code(errJson: String): Int? = Regex("\"Custom\":(\\d+)").find(errJson)?.groupValues?.get(1)?.toInt()
}

sealed class Outcome {
    data object Pending : Outcome()
    data object Confirmed : Outcome()
    data class Reverted(val code: Int?, val sentence: String) : Outcome()
}

/** Pure purchase rules shared by the UI and tests. */
object Purchase {
    /** Slots kept in reserve so a transaction is not sent into the last moments of a menu window. */
    const val EXPIRY_MARGIN = 10L

    enum class Gate { Ok, Rescan }

    fun needsOwner(item: Item, merchantBond: Long, minBond: Long, window: Long) =
        item.price > item.floor || merchantBond < minBond || window > Tally.MAX_AUTONOMOUS_WINDOW

    fun gate(currentSlot: Long, expiresSlot: Long) = if (currentSlot + EXPIRY_MARGIN > expiresSlot) Gate.Rescan else Gate.Ok

    fun outcome(status: SigStatus?): Outcome = when {
        status == null || !status.confirmed -> Outcome.Pending
        status.err == null -> Outcome.Confirmed
        else -> ErrorText.code(status.err).let { Outcome.Reverted(it, it?.let(ErrorText::sentence) ?: "Transaction failed.") }
    }

}
