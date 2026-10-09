package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuleParserTest {
    @Test
    fun parsesTheDemoSentence() {
        val r = RuleParser.parse("Coffee, max \$6, \$15 a day, cheapest by default")
        assertEquals(listOf(3), r.categories)
        assertEquals(6_000_000, r.perTxCap)
        assertEquals(15_000_000, r.dailyCap)
        assertTrue(r.cheapestByDefault)
    }

    @Test
    fun understandsSeveralCategoriesAndDecimals() {
        val r = RuleParser.parse("lunch and transit, up to 12.50 each, 30/day")
        assertEquals(listOf(0, 4), r.categories)
        assertEquals(12_500_000, r.perTxCap)
        assertEquals(30_000_000, r.dailyCap)
    }

    @Test
    fun bakeryMeansTheFoodCategory() {
        val r = RuleParser.parse("Coffee and bakery, max 6, 40 a day")
        assertEquals(listOf(0, 3), r.categories)
        assertEquals(6_000_000, r.perTxCap)
        assertEquals(40_000_000, r.dailyCap)
    }

    @Test
    fun fallsBackToSafeDefaults() {
        val r = RuleParser.parse("coffee")
        assertEquals(listOf(3), r.categories)
        assertEquals(6_000_000, r.perTxCap)
        assertEquals(15_000_000, r.dailyCap)
    }

    @Test
    fun dailyCapNeverBelowPerPurchase() {
        val r = RuleParser.parse("coffee max 20, 10 a day")
        assertTrue(r.dailyCap >= r.perTxCap)
    }
}
