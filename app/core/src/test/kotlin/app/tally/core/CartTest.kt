package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CartTest {
    private val items = Menu.computeFloors(
        listOf(Item(ByteArray(16) { 1 }, 3, 4_500_000, 0), Item(ByteArray(16) { 2 }, 3, 5_900_000, 0), Item(ByteArray(16) { 3 }, 0, 3_000_000, 0), Item(ByteArray(16) { 4 }, 3, 18_000_000, 0)),
    )
    private val names = listOf("Oat latte", "Large oat latte", "Croissant", "Reserve roast")
    private val needs = { it: Item -> it.price > it.floor }

    @Test
    fun eachItemOnceAndATotal() {
        assertEquals(listOf(0, 2), Cart.add(Cart.add(Cart.add(emptyList(), 0), 2), 0))
        val p = Cart.plan(listOf(0, 2), items, names, 6_000_000, 15_000_000, 20_000_000, needs)
        assertEquals(7_500_000, p.totalMicro)
        assertTrue(p.payable)
        assertEquals(2, p.autonomous.size)
    }

    @Test
    fun pricierPicksAreFlaggedForApproval() {
        val p = Cart.plan(listOf(0, 1), items, names, 6_000_000, 15_000_000, 20_000_000, needs)
        assertEquals(listOf("Large oat latte"), p.needsApproval.map { it.name })
    }

    @Test
    fun limitsAreCheckedBeforeAnythingIsPaid() {
        val p = Cart.plan(listOf(0, 2, 3), items, names, 6_000_000, 10_000_000, 9_000_000, needs)
        assertFalse(p.payable)
        assertEquals(3, p.problems.size) // per-purchase limit, daily limit, vault balance
    }
}
