package app.tally.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ApduTest {
    private fun serve(payload: ByteArray, apdu: ByteArray) = Apdu.respond(apdu, payload)

    @Test
    fun selectReturnsVersionAndOk() {
        assertArrayEquals(byteArrayOf(1, 0x90.toByte(), 0), serve(ByteArray(3), Apdu.select()))
    }

    @Test
    fun readsWholePayloadAcrossChunksAtExactBoundaries() {
        for (size in listOf(1, 239, 240, 241, 480, 481, 2_000)) {
            val payload = ByteArray(size) { (it % 251).toByte() }
            val out = Apdu.readAll { serve(payload, it) }
            assertArrayEquals(payload, out, "size $size")
        }
    }

    @Test
    fun unknownInstructionIsRejected() {
        assertArrayEquals(Apdu.SW_INS_NOT_SUPPORTED, serve(ByteArray(3), byteArrayOf(0x80.toByte(), 0x42, 0, 0)))
    }

    @Test
    fun lengthIsBigEndianU16() {
        val r = serve(ByteArray(300), Apdu.getLength())
        assertEquals(300, ((r[0].toInt() and 0xff) shl 8) or (r[1].toInt() and 0xff))
    }
}
