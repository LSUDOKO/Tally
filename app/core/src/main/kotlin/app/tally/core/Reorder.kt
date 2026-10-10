package app.tally.core

/** A saved "my usual": the item, where, and the price it was last paid at. */
data class Usual(val merchant: String, val merchantName: String, val itemIdHex: String, val name: String, val priceMicro: Long, val category: Int)

/**
 * Repeat orders are only one tap when nothing changed. Any difference against the freshly signed
 * menu (another merchant, item gone, new price) stops for the owner, with the reason.
 */
sealed class ReorderCheck {
    data class Same(val index: Int) : ReorderCheck()
    data class PriceChanged(val index: Int, val oldMicro: Long, val newMicro: Long) : ReorderCheck()
    data object ItemMissing : ReorderCheck()
    data class OtherMerchant(val scanned: String) : ReorderCheck()

    val proceedWithoutAsking get() = this is Same
}

object Reorder {
    fun check(usual: Usual, menu: SignedMenu): ReorderCheck {
        if (menu.merchant != usual.merchant) return ReorderCheck.OtherMerchant(menu.merchant)
        val i = menu.items.indexOfFirst { Hex.encode(it.id) == usual.itemIdHex }
        if (i < 0) return ReorderCheck.ItemMissing
        val price = menu.items[i].price
        return if (price == usual.priceMicro) ReorderCheck.Same(i) else ReorderCheck.PriceChanged(i, usual.priceMicro, price)
    }

    /** Plain-language reason shown when a reorder stops for the owner. */
    fun explain(c: ReorderCheck, usual: Usual): String? = when (c) {
        is ReorderCheck.Same -> null
        is ReorderCheck.PriceChanged -> "${usual.name} is now \$${usdc(c.newMicro)} (was \$${usdc(c.oldMicro)}). Confirm the new price."
        ReorderCheck.ItemMissing -> "${usual.name} isn't on today's signed menu. Pick something else."
        is ReorderCheck.OtherMerchant -> "This isn't ${usual.merchantName}. Your usual is only one tap at the same counter."
    }
}
