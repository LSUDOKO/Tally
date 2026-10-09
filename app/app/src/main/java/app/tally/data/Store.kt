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
    /** Receipt PDA of a settled purchase (enables disputes) and the merchant registry entry. */
    val receipt: String? = null,
    val merchantPda: String? = null,
    val disputed: Boolean = false,
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

    /** Counts one hosted-model call against today's allowance; false once `max` calls were made today. */
    fun tryConsumeModelCall(max: Int, today: Long = System.currentTimeMillis() / 86_400_000): Boolean {
        val used = if (prefs.getLong("model_day", -1) == today) prefs.getInt("model_calls", 0) else 0
        if (used >= max) return false
        prefs.edit().putLong("model_day", today).putInt("model_calls", used + 1).apply()
        return true
    }

    /** The user's own Groq API key (app-private storage; never part of the APK). */
    var groqKey: String?
        get() = prefs.getString("groq_key", null)
        set(v) = prefs.edit().putString("groq_key", v).apply()

    /** The merchant terminal's editable catalog (defaults to the demo café). */
    var catalog: List<app.tally.merchant.Catalog.Entry>
        get() = prefs.getString("catalog", null)?.let { runCatching { json.decodeFromString<List<app.tally.merchant.Catalog.Entry>>(it) }.getOrNull() }
            ?: app.tally.merchant.Catalog.DEFAULT
        set(v) = prefs.edit().putString("catalog", json.encodeToString(v)).apply()

    fun log(): List<LogEntry> = prefs.getString("log", null)?.let { runCatching { json.decodeFromString<List<LogEntry>>(it) }.getOrNull() } ?: emptyList()

    fun append(e: LogEntry) {
        prefs.edit().putString("log", json.encodeToString(listOf(e) + log().take(199))).apply()
    }

    fun markDisputed(receipt: String) {
        prefs.edit().putString("log", json.encodeToString(log().map { if (it.receipt == receipt) it.copy(disputed = true) else it })).apply()
    }

    fun attacksBlockedThisWeek(now: Long = System.currentTimeMillis()): Int =
        log().count { it.attack && it.outcome == "blocked" && now - it.at < 7L * 24 * 3600 * 1000 }

    /** Purchases only (they carry a receipt, or slipped through red team); funding the capsule is not spending. */
    fun spentToday(now: Long = System.currentTimeMillis()): Long =
        log().filter { it.outcome == "paid" && (it.receipt != null || it.attack) && now - it.at < 24L * 3600 * 1000 }.sumOf { it.amountMicro }
}
