package app.tally.chain

import app.tally.core.Instruction
import app.tally.core.Message
import app.tally.core.Outcome
import app.tally.core.Purchase
import app.tally.core.Rpc
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
            val sig = runCatching { rpc.sendTransaction(wire, skipPreflight = land) }.getOrElse { e ->
                val code = Regex("custom program error: 0x([0-9a-f]+)").find(e.message ?: "")?.groupValues?.get(1)?.toInt(16)
                return@withContext null to Outcome.Reverted(code, code?.let(app.tally.core.ErrorText::sentence) ?: (e.message ?: "Send failed"))
            }
            val start = System.currentTimeMillis()
            while (System.currentTimeMillis() - start < timeoutMs) {
                val o = Purchase.outcome(runCatching { rpc.getSignatureStatus(sig) }.getOrNull())
                if (o !is Outcome.Pending) return@withContext sig to o
                delay(800)
            }
            sig to Outcome.Pending
        }
}
