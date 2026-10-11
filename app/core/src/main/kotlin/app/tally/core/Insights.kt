package app.tally.core

/** What the agent did for the owner over a window of time (the weekly summary). */
data class Summary(
    val purchases: Int,
    val spentMicro: Long,
    /** Attacks and unwanted payments the chain refused, and what they would have taken. */
    val blocked: Int,
    val protectedMicro: Long,
    /** Upsells the owner declined on Second Look: price difference kept. */
    val savedMicro: Long,
    val topMerchant: String?,
    val activeDays: Int,
    /** Spend per menu category, largest first (categories unknown to the log are left out). */
    val byCategory: List<Pair<Int, Long>> = emptyList(),
)

object Insights {
    const val WEEK = 7L * 86_400_000L

    fun summary(records: List<SpendRecord>, now: Long = System.currentTimeMillis(), window: Long = WEEK): Summary {
        val recent = records.filter { now - it.at in 0 until window }
        val paid = recent.filter { it.verified && !it.blocked && !it.attack }
        val blocked = recent.filter { it.blocked }
        return Summary(
            purchases = paid.size,
            spentMicro = paid.sumOf { it.amountMicro },
            blocked = blocked.size,
            protectedMicro = blocked.sumOf { it.amountMicro },
            savedMicro = recent.sumOf { it.savedMicro },
            topMerchant = paid.filter { it.merchant.isNotBlank() }.groupingBy { it.merchant }.eachCount().maxByOrNull { it.value }?.key,
            activeDays = paid.map { Rewards.day(it.at) }.distinct().size,
            byCategory = paid.filter { it.category >= 0 }.groupBy { it.category }.map { (c, rs) -> c to rs.sumOf { it.amountMicro } }.sortedByDescending { it.second },
        )
    }
}
