package app.tally.core

/** Structured capsule rules the owner reviews as chips before signing in Seed Vault. */
data class ParsedRules(val categories: List<Int>, val perTxCap: Long, val dailyCap: Long, val cheapestByDefault: Boolean) {
    fun toRules(expirySlot: Long, minMerchantBond: Long = 1_000_000_000, flags: Int = 0) =
        Rules(perTxCap, dailyCap, Rules.categories(*categories.toIntArray()), minMerchantBond, expirySlot, flags)
}

/**
 * Deterministic natural-language → rules parser (no model involved: the owner's own words are
 * turned into numbers they confirm on screen). Category codes follow docs/07.
 */
object RuleParser {
    val CATEGORY_WORDS = linkedMapOf(
        0 to listOf("food", "lunch", "breakfast", "dinner", "meal", "bakery", "bread", "croissant", "bagel"),
        1 to listOf("drink", "drinks", "juice", "smoothie", "tea"),
        2 to listOf("snack", "snacks", "pastry", "pastries"),
        3 to listOf("coffee", "latte", "espresso", "cafe", "café"),
        4 to listOf("transit", "metro", "bus", "train", "fare", "ride"),
        5 to listOf("grocery", "groceries"),
    )
    val CATEGORY_NAMES = mapOf(0 to "Food", 1 to "Drinks", 2 to "Snacks", 3 to "Coffee", 4 to "Transit", 5 to "Groceries", 6 to "Digital", 7 to "Services")

    private const val MICRO = 1_000_000L
    private val money = Regex("""\$?\s*(\d+(?:\.\d{1,2})?)""")

    private fun micro(s: String) = (s.toBigDecimal() * MICRO.toBigDecimal()).toLong()

    fun parse(text: String): ParsedRules {
        val t = text.lowercase()
        val words = t.split(Regex("[^a-zé]+")).toSet()
        val categories = CATEGORY_WORDS.filter { (_, ws) -> ws.any { it in words } }.keys.toList().ifEmpty { listOf(3) }

        val daily = Regex("""\$?\s*(\d+(?:\.\d{1,2})?)\s*(?:/|a|per|each)?\s*day""").find(t)?.groupValues?.get(1)?.let(::micro)
        val perTx = Regex("""(?:max|up to|at most|limit|under)\s*\$?\s*(\d+(?:\.\d{1,2})?)""").find(t)?.groupValues?.get(1)?.let(::micro)
            ?: money.findAll(t).map { micro(it.groupValues[1]) }.firstOrNull { it != daily }

        val per = perTx ?: 6 * MICRO
        val day = maxOf(daily ?: maxOf(15 * MICRO, per), per)
        return ParsedRules(categories, per, day, cheapestByDefault = true)
    }
}
