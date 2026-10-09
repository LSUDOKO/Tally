package app.tally.agent

import app.tally.BuildConfig
import app.tally.chain.Http
import app.tally.core.AgentOutput
import app.tally.core.AgentPick
import app.tally.core.AgentRow
import app.tally.core.ChatCompletions
import app.tally.core.Firewall
import app.tally.core.MenuRow
import app.tally.core.RuleAgent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Which agent produced a pick, shown in the reasoning trace. */
enum class AgentSource { Model, Rules }

class AgentResult(val pick: AgentPick?, val source: AgentSource, val note: String?)

/**
 * Hosted agent behind the context firewall, served by Groq: the model only ever sees
 * `Firewall.agentPayload` (item ids, categories, prices, floors, dictionary name tokens). Its reply
 * must be exactly `{item_id, reason}` naming an item on the signed menu, or it is discarded. On any
 * error, refusal, missing key or exhausted daily allowance the deterministic RuleAgent decides.
 * Either way the chain enforces membership, price, payee, freshness and the cheapest-match rule.
 */
class GroqAgent(
    private val apiKey: () -> String = { BuildConfig.GROQ_API_KEY },
    /** Spending guard: returns false once today's call allowance is used up. */
    private val tryConsumeCall: () -> Boolean = { true },
) {
    val available get() = apiKey().isNotBlank()

    /** Blocking; call off the main thread. */
    fun choose(request: String, categories: List<Int>, perTxMax: Long, rows: List<MenuRow>): AgentResult {
        val agentRows: List<AgentRow> = rows.map(Firewall::agentRow)
        val fallback = { note: String -> AgentResult(RuleAgent.choose(request, agentRows), AgentSource.Rules, note) }
        val key = apiKey().trim().ifBlank { return fallback("rule-based agent (no API key configured)") }
        if (!tryConsumeCall()) return fallback("daily model allowance used ($MAX_CALLS_PER_DAY); rule-based agent used")
        return runCatching {
            val body = ChatCompletions.request(MODEL, MAX_TOKENS, request, categories, perTxMax, rows)
            val req = Request.Builder()
                .url(ENDPOINT)
                .header("Authorization", "Bearer $key")
                .post(body.toRequestBody("application/json".toMediaType()))
                .build()
            val raw = Http.client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) return fallback("model unavailable (HTTP ${r.code}); rule-based agent used")
                r.body.string()
            }
            val text = ChatCompletions.replyText(raw) ?: return fallback("model reply was not a completion; rule-based agent used")
            AgentOutput.parse(text, agentRows)?.let { AgentResult(it, AgentSource.Model, null) }
                ?: fallback("model reply rejected by the output schema; rule-based agent used")
        }.getOrElse { fallback("model unavailable (${it.javaClass.simpleName}); rule-based agent used") }
    }

    companion object {
        const val MODEL = "openai/gpt-oss-120b"
        private const val ENDPOINT = "https://api.groq.com/openai/v1/chat/completions"

        /** The reply is a ~65-token JSON object (reasoning included); this only bounds a misbehaving response. */
        const val MAX_TOKENS = 1_024
        const val MAX_CALLS_PER_DAY = 30
    }
}
