package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SecondLookTest {
    private val pay = Instruction(Tally.PROGRAM_ID, emptyList(), ByteArray(1))
    private val ed = Instruction(Tally.ED25519_PROGRAM_ID, emptyList(), ByteArray(1))
    private val cb = ComputeBudget.setUnitLimit(60_000)
    private val lh = Lighthouse.assertTokenAmount(Tally.PROGRAM_ID, 1)

    @Test
    fun expectedInstructionsAllowApproval() {
        val v = SecondLook.review(listOf(cb, ed, pay, lh), mapOf("vault" to (20_000_000L to 14_100_000L)))
        assertTrue(v.approveEnabled)
        assertTrue(v.unexpectedPrograms.isEmpty())
        assertEquals(5_900_000, v.grossOutflow)
    }

    @Test
    fun anyExtraInstructionDisablesApproval() {
        val drain = Instruction(Programs.TOKEN, emptyList(), byteArrayOf(3))
        val v = SecondLook.review(listOf(ed, pay, drain), emptyMap())
        assertFalse(v.approveEnabled)
        assertEquals(listOf(Programs.TOKEN), v.unexpectedPrograms)
    }

    @Test
    fun grossOutflowSumsEveryDecreaseNotTheNet() {
        // A net-positive preview can hide a drain: one account loses 5, another gains 9.
        val v = SecondLook.review(listOf(ed, pay), mapOf("a" to (10L to 5L), "b" to (0L to 9L)))
        assertEquals(5, v.grossOutflow)
        assertEquals(listOf("a" to 5L), v.decreases)
    }
}
