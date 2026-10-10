package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RewardsTest {
    private val day = 86_400_000L
    private fun buy(d: Long, usd: Double = 4.5, verified: Boolean = true, self: Boolean = false, disputed: Boolean = false, attack: Boolean = false) =
        SpendRecord(at = d * day + 3_600_000, amountMicro = (usd * 1_000_000).toLong(), verified = verified, selfDealt = self, disputed = disputed, attack = attack)

    @Test
    fun onlySettledPurchasesFromOthersEarn() {
        val t = Rewards.tally(listOf(buy(10), buy(10, verified = false), buy(10, self = true), buy(10, disputed = true), buy(10, attack = true), buy(10, usd = 0.5)), seeker = false, today = 10)
        assertEquals(1, t.countedPurchases)
        assertEquals(10, t.points)
    }

    @Test
    fun atMostFivePurchasesCountPerDay() {
        val t = Rewards.tally(List(8) { buy(3) }, seeker = false, today = 3)
        assertEquals(5, t.countedPurchases)
        assertEquals(50, t.points)
    }

    @Test
    fun streaksRaiseTheMultiplierUpToOnePointFive() {
        // Days 1..8 in a row: multipliers 1.0, 1.1, ..., 1.5, 1.5, 1.5.
        val t = Rewards.tally((1L..8L).map { buy(it) }, seeker = false, today = 8)
        assertEquals(8, t.streakDays)
        assertEquals((10 * (1.0 + 1.1 + 1.2 + 1.3 + 1.4 + 1.5 + 1.5 + 1.5)).toInt(), t.points)
    }

    @Test
    fun aGapResetsTheStreakAndAnOldStreakIsNotCurrent() {
        assertEquals(1, Rewards.tally(listOf(buy(1), buy(2), buy(5)), seeker = false, today = 5).streakDays)
        assertEquals(0, Rewards.tally(listOf(buy(1), buy(2)), seeker = false, today = 9).streakDays)
    }

    @Test
    fun seekersEarnDouble() {
        assertEquals(20, Rewards.tally(listOf(buy(1)), seeker = true, today = 1).points)
    }

    @Test
    fun levelsAndProgress() {
        assertEquals(Level.Starter, Level.of(0))
        assertEquals(Level.Bronze, Level.of(50))
        assertEquals(Level.Gold, Level.of(999))
        val t = Rewards.Tally(125, 0, 0, Level.of(125))
        assertEquals(Level.Silver, t.next)
        assertEquals(75, t.toNext)
        assertEquals(0.5f, t.progress)
        assertNull(Rewards.Tally(5_000, 0, 0, Level.Legend).next)
    }
}
