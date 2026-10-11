package app.tally.chain

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

/**
 * USD per SOL for showing the agent's fee balance in money people understand. Public feeds, no key:
 * Jupiter first, CoinGecko as the fallback. A failure just leaves the dollar figure off.
 */
object Prices {
    private const val SOL = "So11111111111111111111111111111111111111112"
    private val json = Json { ignoreUnknownKeys = true }

    fun solUsd(): Double? = runCatching { jupiter() }.getOrNull() ?: runCatching { coingecko() }.getOrNull()

    private fun get(url: String): String =
        Http.client.newCall(Request.Builder().url(url).header("accept", "application/json").build()).execute().use {
            check(it.isSuccessful) { "HTTP ${it.code}" }
            it.body.string()
        }

    private fun jupiter(): Double = json.parseToJsonElement(get("https://lite-api.jup.ag/price/v3?ids=$SOL")).jsonObject[SOL]!!.jsonObject["usdPrice"]!!.jsonPrimitive.double

    private fun coingecko(): Double = json.parseToJsonElement(get("https://api.coingecko.com/api/v3/simple/price?ids=solana&vs_currencies=usd")).jsonObject["solana"]!!.jsonObject["usd"]!!.jsonPrimitive.double
}
