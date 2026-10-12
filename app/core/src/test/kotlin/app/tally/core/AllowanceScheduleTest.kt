package app.tally.core

import java.time.DayOfWeek
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AllowanceScheduleTest {
    private val s = AllowanceSchedule(weekdayCap = 15_000_000, weekendCap = 5_000_000)

    @Test
    fun weekdaysAndWeekendsGetTheirOwnCap() {
        assertEquals(15_000_000, s.capFor(DayOfWeek.MONDAY))
        assertEquals(15_000_000, s.capFor(DayOfWeek.FRIDAY))
        assertEquals(5_000_000, s.capFor(DayOfWeek.SATURDAY))
        assertEquals(5_000_000, s.capFor(DayOfWeek.SUNDAY))
    }

    @Test
    fun aChangeIsDueOnlyWhenTheCapsuleDisagrees() {
        assertNull(s.due(currentDailyCap = 15_000_000, day = DayOfWeek.TUESDAY))
        assertEquals(5_000_000, s.due(currentDailyCap = 15_000_000, day = DayOfWeek.SATURDAY))
        assertEquals(15_000_000, s.due(currentDailyCap = 5_000_000, day = DayOfWeek.MONDAY))
    }

    @Test
    fun perPaymentCapNeverExceedsTheNewDailyCap() {
        assertEquals(5_000_000, s.perTxFor(currentPerTx = 10_000_000, newDaily = 5_000_000))
        assertEquals(4_000_000, s.perTxFor(currentPerTx = 4_000_000, newDaily = 5_000_000))
    }
}
