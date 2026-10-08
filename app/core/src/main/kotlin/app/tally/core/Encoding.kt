package app.tally.core

import java.math.BigInteger

object Hex {
    fun encode(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }
    fun decode(s: String): ByteArray = ByteArray(s.length / 2) { s.substring(2 * it, 2 * it + 2).toInt(16).toByte() }
}

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val B58 = BigInteger.valueOf(58)

    fun encode(input: ByteArray): String {
        var n = BigInteger(1, input)
        val sb = StringBuilder()
        while (n > BigInteger.ZERO) {
            val qr = n.divideAndRemainder(B58)
            sb.append(ALPHABET[qr[1].toInt()])
            n = qr[0]
        }
        repeat(input.takeWhile { it == 0.toByte() }.size) { sb.append('1') }
        return sb.reverse().toString()
    }

    fun decode(s: String): ByteArray {
        var n = BigInteger.ZERO
        for (c in s) {
            val d = ALPHABET.indexOf(c)
            require(d >= 0) { "invalid base58 character '$c'" }
            n = n.multiply(B58).add(BigInteger.valueOf(d.toLong()))
        }
        val raw = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        val zeros = s.takeWhile { it == '1' }.length
        return ByteArray(zeros) + (if (n == BigInteger.ZERO) ByteArray(0) else raw)
    }
}

/** Little-endian writers/readers matching Rust's `to_le_bytes`. */
object Le {
    fun u16(v: Int) = byteArrayOf(v.toByte(), (v shr 8).toByte())
    fun u32(v: Int) = ByteArray(4) { (v shr (8 * it)).toByte() }
    fun u64(v: Long) = ByteArray(8) { (v ushr (8 * it)).toByte() }
    fun readU32(b: ByteArray, o: Int) = (0 until 4).fold(0) { acc, i -> acc or ((b[o + i].toInt() and 0xff) shl (8 * i)) }
    fun readU64(b: ByteArray, o: Int) = (0 until 8).fold(0L) { acc, i -> acc or ((b[o + i].toLong() and 0xff) shl (8 * i)) }
}
