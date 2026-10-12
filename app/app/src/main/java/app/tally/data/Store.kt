package app.tally.data

import android.content.Context
import app.tally.core.DailyAllowance
import app.tally.core.SpendRecord
import app.tally.core.Usual
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
    /** Kept by declining a pricier pick on Second Look (price difference to the cheaper match). */
    val savedMicro: Long = 0,
    val category: Int = -1,
)

/** The capsule as last seen online (read-only offline view). */
@Serializable
data class CapsuleSnapshot(val at: Long, val vaultMicro: Long, val spentTodayMicro: Long, val dailyCapMicro: Long, val perTxCapMicro: Long, val frozen: Boolean, val seeker: Boolean)

/** Small persisted app state (SharedPreferences + JSON). */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("tally", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    var ownerAddress: String?
        get() = prefs.getString("owner", null)
        set(v) = prefs.edit().putString("owner", v).apply()

    /** The wallet's Mobile Wallet Adapter auth token, so later requests reauthorize without a new sign-in. */
    var walletToken: String?
        get() = prefs.getString("wallet_token", null)
        set(v) = prefs.edit().putString("wallet_token", v).apply()

    /** Which wallet app signed in (e.g. "Solflare") and the account's own label in it. */
    var walletName: String?
        get() = prefs.getString("wallet_name", null)
        set(v) = prefs.edit().putString("wallet_name", v).apply()

    var accountLabel: String?
        get() = prefs.getString("account_label", null)
        set(v) = prefs.edit().putString("account_label", v).apply()

    /**
     * Signing out sets this account's receipts, usual order, level and snapshot aside under its
     * address; signing in brings back whatever that address left on this phone. Two people sharing
     * a phone never see each other's history.
     */
    fun signOut(owner: String) {
        val e = prefs.edit()
        for (k in PerAccount) {
            prefs.getString(k, null)?.let { e.putString("$k@$owner", it) } ?: e.remove("$k@$owner")
            e.remove(k)
        }
        e.remove("owner").remove("wallet_token").remove("wallet_name").remove("account_label").apply()
    }

    fun signIn(owner: String) {
        val e = prefs.edit()
        for (k in PerAccount) prefs.getString("$k@$owner", null)?.let { e.putString(k, it).remove("$k@$owner") }
        e.putString("owner", owner).apply()
    }

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

    /** Where today's hosted-model call count is kept for the agent's DailyAllowance. */
    val modelCalls = object : DailyAllowance.Counter {
        override fun read() = prefs.getLong("model_day", -1) to prefs.getInt("model_calls", 0)
        override fun write(day: Long, used: Int) { prefs.edit().putLong("model_day", day).putInt("model_calls", used).commit() }
    }

    /** Ask for the phone's lock on launch and before every payment the owner taps. */
    var appLock: Boolean
        get() = prefs.getBoolean("app_lock", false)
        set(v) = prefs.edit().putBoolean("app_lock", v).apply()

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

    /** "$15 on weekdays, $5 at weekends", or null when the owner keeps one daily cap. */
    var schedule: app.tally.core.AllowanceSchedule?
        get() = prefs.getString("schedule", null)?.split(',')?.takeIf { it.size == 2 }?.let { (w, e) ->
            runCatching { app.tally.core.AllowanceSchedule(w.toLong(), e.toLong()) }.getOrNull()
        }
        set(v) = prefs.edit().putString("schedule", v?.let { "${it.weekdayCap},${it.weekendCap}" }).apply()

    /** The owner's saved repeat order, if any. */
    var usual: Usual?
        get() = prefs.getString("usual", null)?.let { runCatching { json.decodeFromString<Usual>(it) }.getOrNull() }
        set(v) = prefs.edit().putString("usual", v?.let { json.encodeToString(it) }).apply()

    /** Highest level the owner has already been congratulated on. */
    var celebratedLevel: String
        get() = prefs.getString("celebrated_level", "Starter")!!
        set(v) = prefs.edit().putString("celebrated_level", v).apply()

    /** Last capsule state seen online, shown read-only when the network is down. */
    var snapshot: CapsuleSnapshot?
        get() = prefs.getString("snapshot", null)?.let { runCatching { json.decodeFromString<CapsuleSnapshot>(it) }.getOrNull() }
        set(v) = prefs.edit().putString("snapshot", v?.let { json.encodeToString(it) }).apply()

    /** History as the rewards engine sees it; [ownMerchantPda] marks payments to the owner's own counter. */
    fun records(ownMerchantPda: String?): List<SpendRecord> = log().map {
        SpendRecord(
            at = it.at,
            amountMicro = it.amountMicro,
            verified = it.outcome == "paid" && it.receipt != null,
            selfDealt = ownMerchantPda != null && it.merchantPda == ownMerchantPda,
            disputed = it.disputed,
            attack = it.attack,
            blocked = it.outcome == "blocked",
            savedMicro = it.savedMicro,
            merchant = it.merchant,
            category = it.category,
        )
    }

    fun attacksBlockedThisWeek(now: Long = System.currentTimeMillis()): Int =
        log().count { it.attack && it.outcome == "blocked" && now - it.at < 7L * 24 * 3600 * 1000 }

    /** Purchases only (they carry a receipt, or slipped through red team); funding the capsule is not spending. */
    fun spentToday(now: Long = System.currentTimeMillis()): Long =
        log().filter { it.outcome == "paid" && (it.receipt != null || it.attack) && now - it.at < 24L * 3600 * 1000 }.sumOf { it.amountMicro }

    private companion object {
        val PerAccount = listOf("log", "usual", "celebrated_level", "snapshot", "schedule")
    }
}
