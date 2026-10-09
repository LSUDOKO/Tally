package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DailyAllowanceTest {
    private class MemoryCounter : DailyAllowance.Counter {
        var day = -1L
        var used = 0
        override fun read() = day to used
        override fun write(day: Long, used: Int) { this.day = day; this.used = used }
    }

    @Test
    fun allowsUpToTheLimitThenRefuses() {
        val a = DailyAllowance(3, MemoryCounter())
        repeat(3) { assertTrue(a.tryConsume(today = 100)) }
        assertFalse(a.tryConsume(today = 100))
        assertEquals(0, a.remaining(today = 100))
    }

    @Test
    fun aNewDayResetsTheCount() {
        val c = MemoryCounter()
        val a = DailyAllowance(2, c)
        repeat(2) { a.tryConsume(today = 5) }
        assertFalse(a.tryConsume(today = 5))
        assertTrue(a.tryConsume(today = 6))
        assertEquals(1, a.remaining(today = 6))
    }

    @Test
    fun refusalsDoNotCount() {
        val c = MemoryCounter()
        val a = DailyAllowance(1, c)
        a.tryConsume(today = 1)
        repeat(5) { a.tryConsume(today = 1) }
        assertEquals(1, c.used)
    }
}
