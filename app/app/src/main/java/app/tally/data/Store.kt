package app.tally.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One line in Receipts & forensics: a settled purchase or a blocked attempt. */
@Serializable
data class LogEntry(
    val at: Long,
    val title: String,
    val merchant: String,
    val amountMicro: Long,
    val outcome: String, // "paid" | "blocked" | "pending"
    val detail: String,
    val signature: String? = null,
    val attack: Boolean = false,
)

/** Small persisted app state (SharedPreferences + JSON). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tally", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var ownerAddress: String?
        get() = prefs.getString("owner", null)
        set(v) = prefs.edit().putString("owner", v).apply()

    var capsuleId: Long
        get() = prefs.getLong("capsule_id", 1)
        set(v) = prefs.edit().putLong("capsule_id", v).apply()

    var useDemoOwner: Boolean
        get() = prefs.getBoolean("demo_owner", false)
        set(v) = prefs.edit().putBoolean("demo_owner", v).apply()

    var lastPayloadQr: String?
        get() = prefs.getString("last_payload", null)
        set(v) = prefs.edit().putString("last_payload", v).apply()

    var rpcOverride: String?
        get() = prefs.getString("rpc", null)
        set(v) = prefs.edit().putString("rpc", v).apply()

    fun log(): List<LogEntry> = prefs.getString("log", null)?.let { runCatching { json.decodeFromString<List<LogEntry>>(it) }.getOrNull() } ?: emptyList()

    fun append(e: LogEntry) {
        prefs.edit().putString("log", json.encodeToString(listOf(e) + log().take(199))).apply()
    }

    fun attacksBlockedThisWeek(now: Long = System.currentTimeMillis()): Int =
        log().count { it.attack && it.outcome == "blocked" && now - it.at < 7L * 24 * 3600 * 1000 }

    fun spentToday(now: Long = System.currentTimeMillis()): Long =
        log().filter { it.outcome == "paid" && now - it.at < 24L * 3600 * 1000 }.sumOf { it.amountMicro }
}
