package app.tally.core

import java.io.ByteArrayOutputStream

/**
 * ISO/IEC 7816-4 exchange between the customer's phone (reader) and the merchant's phone
 * (Host Card Emulation), docs/07: SELECT AID → GET LENGTH (80 CA) → GET CHUNK (80 B0, ≤240 B).
 */
object Apdu {
    val AID = byteArrayOf(0xF0.toByte(), 0x54, 0x41, 0x4C, 0x4C, 0x59, 0x01)
    const val CHUNK = 240

    /** Largest menu a reader accepts (the QR path tops out near 2.9 KB; this leaves ample headroom). */
    const val MAX_PAYLOAD = 16 * 1024
    val SW_OK = byteArrayOf(0x90.toByte(), 0x00)
    val SW_INS_NOT_SUPPORTED = byteArrayOf(0x6D, 0x00)
    val SW_WRONG_P1P2 = byteArrayOf(0x6B, 0x00)
    val SW_CONDITIONS = byteArrayOf(0x69, 0x85.toByte())

    fun select(): ByteArray = byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, AID.size.toByte()) + AID
    fun getLength(): ByteArray = byteArrayOf(0x80.toByte(), 0xCA.toByte(), 0x00, 0x00, 0x02)
    fun getChunk(offset: Int, le: Int): ByteArray =
        byteArrayOf(0x80.toByte(), 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), le.toByte())

    /** Merchant side: answer one command APDU for the current signed payload. */
    fun respond(apdu: ByteArray, payload: ByteArray): ByteArray {
        if (apdu.size >= 5 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte()) {
            val aid = apdu.copyOfRange(5, minOf(apdu.size, 5 + (apdu[4].toInt() and 0xff)))
            return if (aid.contentEquals(AID)) byteArrayOf(0x01) + SW_OK else byteArrayOf(0x6A, 0x82.toByte())
        }
        if (apdu.size < 4 || apdu[0] != 0x80.toByte()) return SW_INS_NOT_SUPPORTED
        return when (apdu[1]) {
            0xCA.toByte() -> byteArrayOf((payload.size shr 8).toByte(), payload.size.toByte()) + SW_OK
            0xB0.toByte() -> {
                val offset = ((apdu[2].toInt() and 0xff) shl 8) or (apdu[3].toInt() and 0xff)
                val le = if (apdu.size > 4) (apdu[4].toInt() and 0xff).let { if (it == 0) CHUNK else it } else CHUNK
                if (offset > payload.size) SW_WRONG_P1P2 else payload.copyOfRange(offset, minOf(payload.size, offset + minOf(le, CHUNK))) + SW_OK
            }
            else -> SW_INS_NOT_SUPPORTED
        }
    }

    /** Customer side: run the full exchange through `transceive` and return the payload. */
    fun readAll(transceive: (ByteArray) -> ByteArray): ByteArray {
        check(ok(transceive(select()))) { "Tally AID not selected" }
        val len = transceive(getLength()).also { check(ok(it)) { "length failed" } }
        val total = ((len[0].toInt() and 0xff) shl 8) or (len[1].toInt() and 0xff)
        check(total in 1..MAX_PAYLOAD) { "terminal announced a $total-byte menu (limit $MAX_PAYLOAD)" }
        val out = ByteArrayOutputStream()
        while (out.size() < total) {
            val r = transceive(getChunk(out.size(), minOf(CHUNK, total - out.size())))
            check(ok(r)) { "chunk at ${out.size()} failed" }
            // A terminal that answers OK with no data would otherwise keep the reader looping forever.
            check(r.size > 2) { "terminal made no progress at byte ${out.size()}" }
            out.write(r, 0, minOf(r.size - 2, total - out.size()))
        }
        return out.toByteArray()
    }

    private fun ok(r: ByteArray) = r.size >= 2 && r[r.size - 2] == 0x90.toByte() && r[r.size - 1] == 0x00.toByte()
}

/**
 * One tap, merchant side. The menu is snapshotted at SELECT and served from that snapshot until
 * `reset` (card deactivation), so the terminal's ~20 s re-sign can never splice two menus into one
 * read. Without a prior SELECT, or with the terminal off, every command is refused.
 */
class ApduSession(private val current: () -> ByteArray?) {
    @Volatile private var snapshot: ByteArray? = null

    fun respond(apdu: ByteArray): ByteArray {
        if (apdu.size >= 2 && apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte()) {
            val payload = current() ?: return Apdu.SW_CONDITIONS
            return Apdu.respond(apdu, payload).also { if (it.contentEquals(byteArrayOf(0x01) + Apdu.SW_OK)) snapshot = payload }
        }
        return Apdu.respond(apdu, snapshot ?: return Apdu.SW_CONDITIONS)
    }

    fun reset() {
        snapshot = null
    }
}
