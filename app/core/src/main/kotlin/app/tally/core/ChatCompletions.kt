package app.tally.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The OpenAI-compatible chat-completions wire format used by the hosted agent (Groq). The body
 * carries only the system prompt and `Firewall.agentPayload`, never merchant prose.
 */
object ChatCompletions {
    fun request(model: String, maxTokens: Int, request: String, categories: List<Int>, perTxMax: Long, rows: List<MenuRow>): String =
        buildJsonObject {
            put("model", model)
            put("max_completion_tokens", maxTokens)
            put("reasoning_effort", "low")
            put("temperature", 0)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                add(buildJsonObject { put("role", "system"); put("content", Firewall.SYSTEM_PROMPT) })
                add(buildJsonObject { put("role", "user"); put("content", Firewall.agentPayload(request, categories, perTxMax, rows)) })
            }
        }.toString()

    /** The first choice's message text, or null if the response is not a well-formed completion. */
    fun replyText(raw: String): String? = runCatching {
        Json.parseToJsonElement(raw).jsonObject["choices"]!!.jsonArray.first()
            .jsonObject["message"]!!.jsonObject["content"]!!.jsonPrimitive.content
    }.getOrNull()
}
