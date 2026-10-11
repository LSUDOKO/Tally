package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InsightsReorderTest {
    private val now = 100L * 86_400_000L

    @Test
    fun weeklySummaryCountsOnlyThisWeek() {
        val r = listOf(
            SpendRecord(now - 1_000, 4_500_000, verified = true, merchant = "Blue Door"),
            SpendRecord(now - 86_400_000, 5_900_000, verified = true, merchant = "Blue Door"),
            SpendRecord(now - 2 * 86_400_000, 3_000_000, verified = true, merchant = "Bakery"),
            SpendRecord(now - 3_000, 2_990_000, verified = false, attack = true, blocked = true),
            SpendRecord(now - 4_000, 0, verified = false, savedMicro = 1_400_000),
            SpendRecord(now - 8 * 86_400_000, 9_000_000, verified = true, merchant = "Old"),
        )
        val s = Insights.summary(r, now)
        assertEquals(3, s.purchases)
        assertEquals(13_400_000, s.spentMicro)
        assertEquals(1, s.blocked)
        assertEquals(2_990_000, s.protectedMicro)
        assertEquals(1_400_000, s.savedMicro)
        assertEquals("Blue Door", s.topMerchant)
        assertEquals(2, s.activeDays) // now is midnight, so the first two purchases share a day
    }

    @Test
    fun weeklySummarySplitsSpendByCategory() {
        val r = listOf(
            SpendRecord(now - 1_000, 4_500_000, verified = true, merchant = "Blue Door", category = 3),
            SpendRecord(now - 2_000, 5_900_000, verified = true, merchant = "Blue Door", category = 3),
            SpendRecord(now - 3_000, 2_750_000, verified = true, merchant = "Metro", category = 4),
            SpendRecord(now - 4_000, 9_000_000, verified = false, blocked = true, category = 3),
        )
        val s = Insights.summary(r, now)
        // Largest first; blocked money never counts as spent.
        assertEquals(listOf(3 to 10_400_000L, 4 to 2_750_000L), s.byCategory)
    }

    private val key = ByteArray(32) { 3 }
    private fun item(i: Int, price: Long) = Item(ByteArray(16) { (i + 1).toByte() }, 3, price, 0)
    private fun menu(merchant: String, vararg items: Item) =
        SignedMenu.sign(merchant, 1, 100, 1, Menu.computeFloors(items.toList()), key)
    private val m = "8TfJZMw4tMniEs8F6XbjeokVBHpuf2aCnXrXCbRL7Y5T"
    private val usual = Usual(m, "Blue Door", Hex.encode(ByteArray(16) { 2 }), "Oat latte", 4_500_000, 3)

    @Test
    fun reorderIsOneTapOnlyWhenNothingChanged() {
        val c = Reorder.check(usual, menu(m, item(0, 3_000_000), item(1, 4_500_000)))
        assertEquals(ReorderCheck.Same(1), c)
        assertTrue(c.proceedWithoutAsking)
    }

    @Test
    fun anyChangeStopsWithAReason() {
        val priced = Reorder.check(usual, menu(m, item(1, 4_900_000)))
        assertEquals(ReorderCheck.PriceChanged(0, 4_500_000, 4_900_000), priced)
        assertEquals("Oat latte is now \$4.90 (was \$4.50). Confirm the new price.", Reorder.explain(priced, usual))
        assertEquals(ReorderCheck.ItemMissing, Reorder.check(usual, menu(m, item(0, 4_500_000))))
        assertTrue(Reorder.check(usual, menu("zNLg5ncNoNWdUHjeMXo1JkTmcQ6biZRPFyFJR7CTBJH", item(1, 4_500_000))) is ReorderCheck.OtherMerchant)
    }
}
