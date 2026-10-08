package app.tally.core


/** Helpers to place signatures into a serialized legacy transaction's signature slots. */
object Signing {
    private fun shortVecLen(b: ByteArray): Pair<Int, Int> {
        var len = 0
        var size = 0
        var shift = 0
        while (true) {
            val v = b[size].toInt() and 0xff
            len = len or ((v and 0x7f) shl shift)
            size++
            if (v and 0x80 == 0) return len to size
            shift += 7
        }
    }

    /** Sign the message embedded in `wireTx` and write the signature into `signer`'s slot. */
    fun addSignature(wireTx: ByteArray, signer: String, sign: (ByteArray) -> ByteArray): ByteArray {
        val (count, prefix) = shortVecLen(wireTx)
        val messageStart = prefix + 64 * count
        val message = wireTx.copyOfRange(messageStart, wireTx.size)
        // Account keys start after the 3-byte header and the shortvec key count.
        val (keyCount, kPrefix) = shortVecLen(message.copyOfRange(3, message.size))
        val keysStart = 3 + kPrefix
        val slot = (0 until minOf(count, keyCount)).firstOrNull {
            Base58.encode(message.copyOfRange(keysStart + 32 * it, keysStart + 32 * it + 32)) == signer
        } ?: error("$signer is not a required signer")
        val out = wireTx.copyOf()
        sign(message).copyInto(out, prefix + 64 * slot)
        return out
    }
}
