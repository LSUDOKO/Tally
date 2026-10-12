package app.tally.core

import java.time.DayOfWeek

/**
 * "$15 a day on weekdays, $5 at weekends." The schedule lives on the phone; the capsule's daily cap
 * on chain is what the program enforces, so a change only takes effect once the owner signs it.
 */
class AllowanceSchedule(val weekdayCap: Long, val weekendCap: Long) {
    fun capFor(day: DayOfWeek): Long = if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) weekendCap else weekdayCap

    /** The daily cap to sign today, or null when the capsule already matches the schedule. */
    fun due(currentDailyCap: Long, day: DayOfWeek): Long? = capFor(day).takeIf { it != currentDailyCap }

    /** The program refuses a per-payment cap above the daily cap, so lowering one lowers both. */
    fun perTxFor(currentPerTx: Long, newDaily: Long): Long = minOf(currentPerTx, newDaily)
}
