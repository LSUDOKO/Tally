package app.tally.chain

import app.tally.core.Instruction
import app.tally.core.Message
import app.tally.core.Outcome
import app.tally.core.Purchase
import app.tally.core.Rpc
import app.tally.wallet.Owner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun rpc(url: String) = Rpc { body ->
        val req = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { it.body.string() }
    }
}

/** Submits agent-signed transactions and polls them to a final outcome (never re-signs a different tx). */
class Submitter(private val rpc: Rpc) {
    /** Compile a transaction paid and signed by the agent; other signer slots are left empty. */
    suspend fun buildAgentSigned(agent: String, instructions: List<Instruction>, sign: (ByteArray) -> ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            val message = Message.compile(agent, rpc.getLatestBlockhash(), instructions)
            val signers = Message.signers(agent, instructions)
            Message.serialize(message, signers.map { if (it == agent) sign(message) else null })
        }

    /**
     * Send and wait. `land = true` skips preflight so even a revert lands on-chain with an
     * explorer-visible error (used by red-team mode).
     */
    suspend fun sendAndConfirm(wire: ByteArray, land: Boolean = false, timeoutMs: Long = 45_000): Pair<String?, Outcome> =
        withContext(Dispatchers.IO) {
            val sig = runCatching { rpc.sendTransaction(wire, skipPreflight = land) }.recoverCatching { e ->
                // A load-balanced RPC node can trail the one that handed out the blockhash and answer
                // "Blockhash not found" for a transaction that is still valid; the leader takes it.
                if (land || !e.message.orEmpty().contains("Blockhash not found")) throw e
                android.util.Log.w("Tally", "preflight node lacks the blockhash; sending without preflight")
                rpc.sendTransaction(wire, skipPreflight = true)
            }.getOrElse { e ->
                val code = Regex("custom program error: 0x([0-9a-f]+)").find(e.message ?: "")?.groupValues?.get(1)?.toInt(16)
                android.util.Log.w("Tally", "send rejected: ${e.message}")
                // Unknown codes keep the node's own words so the cause is never hidden behind a number.
                val known = code?.let(app.tally.core.ErrorText::sentence)?.takeUnless { it.startsWith("Transaction failed") }
                return@withContext null to Outcome.Reverted(code, known ?: (e.message ?: "Send failed").take(240))
            }
            sig to awaitOutcome(sig, timeoutMs, resend = wire)
        }

    /** Poll a sent signature until it confirms or reverts (or the timeout passes). */
    suspend fun awaitOutcome(sig: String, timeoutMs: Long = 45_000, resend: ByteArray? = null): Outcome = withContext(Dispatchers.IO) {
        val start = System.currentTimeMillis()
        var polls = 0
        while (System.currentTimeMillis() - start < timeoutMs) {
            val o = Purchase.outcome(runCatching { rpc.getSignatureStatus(sig) }.getOrNull())
            if (o !is Outcome.Pending) return@withContext o
            // Rebroadcast the same signed bytes while unconfirmed (idempotent: one signature, one landing).
            if (resend != null && ++polls % 3 == 0 && System.currentTimeMillis() - start < 30_000) {
                runCatching { rpc.sendTransaction(resend, skipPreflight = true) }
            }
            delay(800)
        }
        Outcome.Pending
    }
}

/** Owner whose sends return only once the chain has confirmed them, so screens refresh to real state. */
class ConfirmingOwner(private val inner: Owner, private val submitter: Submitter) : Owner by inner {
    override suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String {
        val sig = inner.signAndSend(instructions, rpc)
        when (val o = submitter.awaitOutcome(sig)) {
            is Outcome.Reverted -> throw IllegalStateException(o.sentence)
            else -> Unit
        }
        return sig
    }
}
