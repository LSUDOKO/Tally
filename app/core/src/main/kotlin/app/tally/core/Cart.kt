package app.tally.core

/**
 * A multi-item order. Solana caps a transaction at 1,232 bytes and one signed-menu payment already
 * uses about 1,130 (Ed25519 header check + Merkle proof), so each item settles as its own checked
 * transaction with its own receipt. The plan is shown in full before anything is paid.
 */
data class CartLine(val index: Int, val name: String, val priceMicro: Long, val needsOwner: Boolean)

data class CartPlan(val lines: List<CartLine>, val totalMicro: Long, val problems: List<String>) {
    val autonomous get() = lines.filter { !it.needsOwner }
    val needsApproval get() = lines.filter { it.needsOwner }
    val payable get() = problems.isEmpty() && lines.isNotEmpty()
}

object Cart {
    /** One of each item per menu: the receipt PDA is per (capsule, merchant, menu, item), so a repeat would be refused. */
    fun add(cart: List<Int>, index: Int): List<Int> = if (index in cart) cart else cart + index

    fun plan(
        indices: List<Int>,
        items: List<Item>,
        names: List<String>,
        perTxCapMicro: Long,
        dailyRemainingMicro: Long,
        vaultMicro: Long,
        needsOwner: (Item) -> Boolean,
    ): CartPlan {
        val lines = indices.distinct().map { i -> CartLine(i, names[i], items[i].price, needsOwner(items[i])) }
        val total = lines.sumOf { it.priceMicro }
        val problems = buildList {
            lines.filter { it.priceMicro > perTxCapMicro }.forEach { add("${it.name} is over your \$${usdc(perTxCapMicro)} per-purchase limit.") }
            if (total > dailyRemainingMicro) add("The cart is \$${usdc(total)} but only \$${usdc(dailyRemainingMicro)} is left of today's limit.")
            if (total > vaultMicro) add("The cart is \$${usdc(total)} but the vault holds \$${usdc(vaultMicro)}.")
        }
        return CartPlan(lines, total, problems)
    }
}
