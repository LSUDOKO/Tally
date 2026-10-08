package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SgtTest {
    private val group = Base58.decode("3trSufZ4QHhHioTqosoK6cNaz1amDgGP1ku4ZCMwj4A2")
    private val mint = Base58.decode("Fd9zbb1tGnvtgT9UL4rCHyP2CCovpVnupcixfpKHbyuX")
    private val owner = Base58.decode("24R4jfY7SG1SZpRfxtGhKUPJhaivuMj93Q82t4KDW6re")

    /** Token-2022 mint bytes: base (165, padded) + account type 1 + TLV entries. */
    private fun mintWith(vararg tlv: Pair<Int, ByteArray>): ByteArray {
        var b = ByteArray(165) + byteArrayOf(1)
        for ((t, v) in tlv) b += Le.u16(t) + Le.u16(v.size) + v
        return b
    }

    private fun tokenAccount(m: ByteArray, o: ByteArray, amount: Long) = m + o + Le.u64(amount) + ByteArray(165 - 72)

    @Test
    fun recognisesAMemberOfTheConfiguredGroup() {
        val m = mintWith(22 to ByteArray(64), 23 to (mint + group + Le.u64(1)))
        assertEquals(Base58.encode(group), Sgt.groupOf(m, mint))
        assertEquals(1L, Sgt.holding(tokenAccount(mint, owner, 1), owner)?.second)
    }

    @Test
    fun rejectsWrongMintOrMissingExtension() {
        assertNull(Sgt.groupOf(mintWith(22 to ByteArray(64)), mint))
        assertNull(Sgt.groupOf(mintWith(23 to (ByteArray(32) + group + Le.u64(1))), mint))
        assertNull(Sgt.groupOf(ByteArray(82), mint))
    }

    @Test
    fun holdingRequiresOwnerAndAmountOne() {
        assertNull(Sgt.holding(tokenAccount(mint, ByteArray(32), 1), owner))
        assertNull(Sgt.holding(tokenAccount(mint, owner, 0), owner))
    }
}
