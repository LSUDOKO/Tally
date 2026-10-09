package app.tally.core

/**
 * A per-day call budget (used to cap hosted-model spend). Days are UTC day numbers; the count
 * resets when the day changes, and a refused call is not counted.
 */
class DailyAllowance(private val max: Int, private val counter: Counter) {
    /** Where the (day, used) pair lives: app preferences on the phone, memory in tests. */
    interface Counter {
        fun read(): Pair<Long, Int>
        fun write(day: Long, used: Int)
    }

    private fun usedOn(today: Long) = counter.read().let { (day, used) -> if (day == today) used else 0 }

    @Synchronized
    fun tryConsume(today: Long = utcDay()): Boolean {
        val used = usedOn(today)
        if (used >= max) return false
        counter.write(today, used + 1)
        return true
    }

    fun remaining(today: Long = utcDay()) = (max - usedOn(today)).coerceAtLeast(0)

    companion object {
        fun utcDay(nowMillis: Long = System.currentTimeMillis()) = nowMillis / 86_400_000
    }
}
