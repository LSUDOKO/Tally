package app.tally.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
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

    @Test
    fun aReSignMidTapDoesNotSpliceTwoMenus() {
        val old = ByteArray(700) { 1 }
        val new = ByteArray(700) { 2 }
        var current = old
        val session = ApduSession { current }
        var chunks = 0
        val out = Apdu.readAll { apdu ->
            session.respond(apdu).also { if (apdu[1] == 0xB0.toByte() && ++chunks == 1) current = new }
        }
        assertArrayEquals(old, out)
    }

    @Test
    fun nextTapSeesTheNewMenu() {
        var current = ByteArray(10) { 1 }
        val session = ApduSession { current }
        Apdu.readAll(session::respond)
        session.reset()
        current = ByteArray(10) { 2 }
        assertArrayEquals(current, Apdu.readAll(session::respond))
    }

    @Test
    fun terminalOffOrNoSelectIsRefused() {
        assertArrayEquals(Apdu.SW_CONDITIONS, ApduSession { null }.respond(Apdu.select()))
        assertArrayEquals(Apdu.SW_CONDITIONS, ApduSession { ByteArray(5) }.respond(Apdu.getLength()))
    }

    @Test
    fun aTerminalThatStopsSendingCannotHangTheReader() {
        val stalled = { apdu: ByteArray -> if (apdu[1] == 0xB0.toByte()) Apdu.SW_OK else serve(ByteArray(500), apdu) }
        val e = assertThrows(IllegalStateException::class.java) { Apdu.readAll(stalled) }
        assertTrue("no progress" in e.message!!, e.message)
    }

    @Test
    fun anOversizedMenuIsRefusedBeforeReading() {
        var chunkRequests = 0
        val huge = { apdu: ByteArray ->
            when (apdu[1]) {
                0xCA.toByte() -> byteArrayOf(0xFF.toByte(), 0xFF.toByte()) + Apdu.SW_OK
                0xB0.toByte() -> { chunkRequests++; ByteArray(240) + Apdu.SW_OK }
                else -> serve(ByteArray(1), apdu)
            }
        }
        assertThrows(IllegalStateException::class.java) { Apdu.readAll(huge) }
        assertEquals(0, chunkRequests)
    }
}
