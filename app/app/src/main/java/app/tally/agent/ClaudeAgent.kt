package app.tally.agent

import app.tally.BuildConfig
import app.tally.core.AgentOutput
import app.tally.core.AgentPick
import app.tally.core.AgentRow
import app.tally.core.Firewall
import app.tally.core.MenuRow
import app.tally.core.RuleAgent
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.OutputConfig

/** Which agent produced a pick, shown in the reasoning trace. */
enum class AgentSource { Claude, Rules }

class AgentResult(val pick: AgentPick?, val source: AgentSource, val note: String?)

/**
 * Hosted agent behind the context firewall: the model only ever sees `Firewall.agentPayload`
 * (item ids, categories, prices, floors, dictionary name tokens). Its reply must be exactly
 * `{item_id, reason}` naming an item on the signed menu, or it is discarded. On any error,
 * refusal or missing API key the deterministic RuleAgent decides. Either way the chain enforces
 * membership, price, payee, freshness and the cheapest-match rule.
 */
class ClaudeAgent(
    private val apiKey: () -> String = { BuildConfig.ANTHROPIC_API_KEY },
    /** Spending guard: returns false once today's call allowance is used up. */
    private val tryConsumeCall: () -> Boolean = { true },
) {
    private var cached: Pair<String, AnthropicClient>? = null

    /** Rebuilt whenever the user changes the key in Settings. */
    private val client: AnthropicClient?
        get() {
            val key = apiKey().trim().takeIf { it.isNotBlank() } ?: return null
            cached?.takeIf { it.first == key }?.let { return it.second }
            return AnthropicOkHttpClient.builder().apiKey(key).build().also { cached = key to it }
        }

    val available get() = apiKey().isNotBlank()

    /** Blocking; call off the main thread. */
    fun choose(request: String, categories: List<Int>, perTxMax: Long, rows: List<MenuRow>): AgentResult {
        val agentRows: List<AgentRow> = rows.map(Firewall::agentRow)
        val fallback = { note: String -> AgentResult(RuleAgent.choose(request, agentRows), AgentSource.Rules, note) }
        val c = client ?: return fallback("rule-based agent (no API key configured)")
        if (!tryConsumeCall()) return fallback("daily Claude allowance used (${MAX_CALLS_PER_DAY}); rule-based agent used")
        return runCatching {
            val params = MessageCreateParams.builder()
                .model("claude-opus-5-5")
                .maxTokens(MAX_TOKENS)
                .outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.LOW).build())
                .system(Firewall.SYSTEM_PROMPT)
                .addUserMessage(Firewall.agentPayload(request, categories, perTxMax, rows))
                .build()
            val response = c.messages().create(params)
            if (response.stopReason().map { it.toString() }.orElse("") == "refusal") return fallback("model declined; rule-based agent used")
            val text = response.content().flatMap { b -> b.text().map { listOf(it.text()) }.orElse(emptyList()) }.joinToString("")
            AgentOutput.parse(text, agentRows)?.let { AgentResult(it, AgentSource.Claude, null) }
                ?: fallback("model reply rejected by the output schema; rule-based agent used")
        }.getOrElse { fallback("model unavailable (${it.javaClass.simpleName}); rule-based agent used") }
    }

    companion object {
        /** The reply is a ~50-token JSON object; this only bounds a misbehaving response. */
        const val MAX_TOKENS = 1_024L
        const val MAX_CALLS_PER_DAY = 30
    }
}
