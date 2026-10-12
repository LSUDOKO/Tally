package app.tally.chain

import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.Request

/** A counter's Google Maps place, as Tally cloud reports it. [pinned] means a maintainer confirmed it. */
@Serializable
data class Place(
    val pinned: Boolean = false,
    val name: String = "",
    val address: String = "",
    val rating: Double? = null,
    val ratings: Int? = null,
    val mapsUri: String? = null,
    val photo: String? = null,
)

/**
 * Where a counter is in the real world, from Tally cloud (which holds the Google Places key, so it
 * never ships in the app). Purely informative: the chain still checks the merchant's key and bond.
 */
object CloudPlaces {
    private val json = Json { ignoreUnknownKeys = true }

    // The free cloud can take ~40 s to wake up.
    private val client = Http.client.newBuilder().readTimeout(60, TimeUnit.SECONDS).build()

    private fun get(url: String): ByteArray? =
        client.newCall(Request.Builder().url(url).build()).execute().use { if (it.isSuccessful) it.body.bytes() else null }

    fun lookup(cloud: String, merchant: String): Place? {
        val body = get("$cloud/places/merchant/$merchant") ?: return null
        val place = json.parseToJsonElement(body.decodeToString()).jsonObject["place"] ?: return null
        return runCatching { json.decodeFromJsonElement<Place>(place) }.getOrNull()
    }

    fun photo(cloud: String, path: String): ByteArray? = get(cloud + path)
}
