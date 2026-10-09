package app.tally.merchant

import app.tally.core.Item
import app.tally.core.Menu
import app.tally.core.MenuRow
import app.tally.core.sha256

/** The demo café catalog (matches fixtures/menu-v1.json). Merchants edit prices in the terminal. */
object Catalog {
    @kotlinx.serialization.Serializable
    data class Entry(val name: String, val category: Int, val priceMicro: Long, val description: String? = null)

    val DEFAULT = listOf(
        Entry("Oat latte (regular)", 3, 4_500_000, "Our most popular. Steamed oat milk, double shot."),
        Entry("Oat latte (large)", 3, 5_900_000, "A bigger oat latte."),
        Entry("Cappuccino", 3, 5_200_000),
        Entry("Reserve roast", 3, 18_000_000, "Single-origin pour over. Limited."),
        Entry("Croissant", 0, 3_000_000, "Baked this morning."),
        Entry("Bagel", 0, 3_800_000),
        Entry("Metro single", 4, 2_750_000),
        Entry("Day pass", 4, 9_000_000),
    )

    fun itemId(index: Int): ByteArray = sha256("tally-item-$index".toByteArray()).copyOf(16)

    fun rows(entries: List<Entry> = DEFAULT): List<MenuRow> {
        val items = Menu.computeFloors(entries.mapIndexed { i, e -> Item(itemId(i), e.category, e.priceMicro, 0) })
        return items.mapIndexed { i, it -> MenuRow(it, entries[i].name, entries[i].description) }
    }
}
