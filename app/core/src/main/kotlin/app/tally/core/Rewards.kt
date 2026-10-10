package app.tally.core

/**
 * One line of payment history as the rewards engine sees it. Built from the app's receipts log;
 * only [verified] purchases (a settled on-chain receipt exists) can ever earn points.
 */
data class SpendRecord(
    val at: Long,
    val amountMicro: Long,
    val verified: Boolean,
    /** The merchant is the owner's own registry entry: paying yourself earns nothing. */
    val selfDealt: Boolean = false,
    val disputed: Boolean = false,
    val attack: Boolean = false,
    val blocked: Boolean = false,
    /** Money kept because the owner declined a pricier pick on Second Look. */
    val savedMicro: Long = 0,
    val merchant: String = "",
)

/** Tally Points: earned only from real, settled purchases; never from opening the app. */
object Rewards {
    const val POINTS_PER_PURCHASE = 10
    const val MIN_PURCHASE_MICRO = 1_000_000L
    const val MAX_COUNTED_PER_DAY = 5
    const val MAX_STREAK_BONUS_DAYS = 6 // 1.0x on day 1, +0.1x per day, capped at 1.5x
    const val SEEKER_MULTIPLIER = 2

    private const val DAY = 86_400_000L

    fun day(at: Long) = at / DAY

    /** A purchase that can earn: settled on-chain, at least $1, not to yourself, not disputed. */
    fun eligible(r: SpendRecord) = r.verified && !r.attack && !r.blocked && !r.selfDealt && !r.disputed && r.amountMicro >= MIN_PURCHASE_MICRO

    data class Tally(val points: Int, val streakDays: Int, val countedPurchases: Int, val level: Level) {
        val next: Level? get() = Level.entries.firstOrNull { it.minPoints > points }
        val toNext: Int get() = next?.let { it.minPoints - points } ?: 0
        /** Progress from this level to the next, 0..1 (1 at the top level). */
        val progress: Float get() = next?.let { (points - level.minPoints).toFloat() / (it.minPoints - level.minPoints) } ?: 1f
    }

    /**
     * Walk purchases oldest first. Each day counts at most [MAX_COUNTED_PER_DAY] purchases; a
     * purchase's points are multiplied by the streak (consecutive purchase days ending that day)
     * and by the Seeker multiplier.
     */
    fun tally(records: List<SpendRecord>, seeker: Boolean, today: Long = day(System.currentTimeMillis())): Tally {
        val byDay = records.filter(::eligible).groupBy { day(it.at) }.toSortedMap()
        var points = 0.0
        var counted = 0
        var streak = 0
        var prevDay: Long? = null
        for ((d, list) in byDay) {
            streak = if (prevDay != null && d == prevDay + 1) streak + 1 else 1
            prevDay = d
            val mult = (1.0 + 0.1 * (minOf(streak, MAX_STREAK_BONUS_DAYS) - 1)) * if (seeker) SEEKER_MULTIPLIER else 1
            val n = minOf(list.size, MAX_COUNTED_PER_DAY)
            counted += n
            points += n * POINTS_PER_PURCHASE * mult
        }
        // A streak is only "current" if its last purchase day is today or yesterday.
        val current = if (prevDay != null && today - prevDay <= 1) streak else 0
        val total = points.toInt()
        return Tally(total, current, counted, Level.of(total))
    }
}

/** Levels unlock the mascot's looks; nothing about payments changes with level. */
enum class Level(val title: String, val minPoints: Int, val perk: String) {
    Starter("Starter", 0, "The Tally crew welcomes you"),
    Bronze("Bronze", 50, "Bronze aura and the thumbs-up celebration"),
    Silver("Silver", 200, "Silver aura and the studio portrait on Home"),
    Gold("Gold", 500, "Gold aura, a crown badge and the victory dance"),
    Legend("Legend", 1_200, "Animated cobalt halo: the rarest look");

    companion object {
        fun of(points: Int) = entries.last { points >= it.minPoints }
    }
}
