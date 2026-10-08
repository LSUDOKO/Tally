package app.tally.core

import java.security.MessageDigest

/** One catalog leaf. Display fields (name, description) are never part of it. */
data class Item(val id: ByteArray, val category: Int, val price: Long, val floor: Long) {
    override fun equals(other: Any?) =
        other is Item && id.contentEquals(other.id) && category == other.category && price == other.price && floor == other.floor

    override fun hashCode() = id.contentHashCode() * 31 + price.hashCode()
}

class HeaderFields(
    val programId: ByteArray,
    val merchantPda: ByteArray,
    val menuRoot: ByteArray,
    val issuedSlot: Long,
    val expiresSlot: Long,
    val menuNonce: Long,
    val itemCount: Int,
)

fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run {
    parts.forEach { update(it) }
    digest()
}

/** Byte-exact port of crates/tally-core (docs/07). */
object Menu {
    private val DOMAIN = "TALLY_MENU_V1\u0000\u0000\u0000".toByteArray(Charsets.US_ASCII)

    fun buildHeader(f: HeaderFields): ByteArray {
        val h = DOMAIN + f.programId + f.merchantPda + f.menuRoot + Le.u64(f.issuedSlot) + Le.u64(f.expiresSlot) +
            Le.u64(f.menuNonce) + Le.u32(f.itemCount) + byteArrayOf(0, 1, 0, 0)
        check(h.size == Tally.HEADER_LEN) { "header is ${h.size} bytes" }
        return h
    }

    fun leafHash(i: Item): ByteArray = sha256(byteArrayOf(0), i.id, Le.u16(i.category), Le.u64(i.price), Le.u64(i.floor))

    fun nodeHash(a: ByteArray, b: ByteArray): ByteArray =
        if (compare(a, b) <= 0) sha256(byteArrayOf(1), a, b) else sha256(byteArrayOf(1), b, a)

    private fun compare(a: ByteArray, b: ByteArray): Int {
        for (k in a.indices) {
            val d = (a[k].toInt() and 0xff) - (b[k].toInt() and 0xff)
            if (d != 0) return d
        }
        return 0
    }

    fun verifyProof(leaf: ByteArray, proof: List<ByteArray>, root: ByteArray): Boolean =
        proof.size <= Tally.MAX_PROOF_DEPTH && proof.fold(leaf, ::nodeHash).contentEquals(root)

    fun computeFloors(items: List<Item>): List<Item> =
        items.map { i -> i.copy(floor = items.filter { it.category == i.category }.minOf { it.price }) }

    /** `(cheap, claimed)` when a leaf is priced below another same-category leaf's certified floor. */
    fun findFloorContradiction(items: List<Item>): Pair<Int, Int>? {
        for (c in items.indices) for (k in items.indices)
            if (items[c].category == items[k].category && items[c].price < items[k].floor) return c to k
        return null
    }

    /** Single-signature Ed25519 precompile data with every argument inside the instruction. */
    fun ed25519IxData(pubkey: ByteArray, signature: ByteArray, message: ByteArray): ByteArray {
        val max = 0xffff
        val offsets = listOf(48, max, 16, max, 112, message.size, max).fold(byteArrayOf(1, 0)) { acc, v -> acc + Le.u16(v) }
        return offsets + pubkey + signature + message
    }

    fun powChallenge(menuNonce: Long, capsule: ByteArray, slotHash: ByteArray): ByteArray =
        sha256("TALLY_POW_V1".toByteArray(), Le.u64(menuNonce), capsule, slotHash)

    fun leadingZeroBits(h: ByteArray): Int {
        var n = 0
        for (b in h) {
            val v = b.toInt() and 0xff
            if (v == 0) n += 8 else return n + Integer.numberOfLeadingZeros(v) - 24
        }
        return n
    }

    fun verifyHashcash(challenge: ByteArray, nonce: ByteArray, difficulty: Int) = leadingZeroBits(sha256(challenge, nonce)) >= difficulty

    fun solveHashcash(challenge: ByteArray, difficulty: Int): ByteArray {
        var i = 0L
        while (true) {
            val n = Le.u64(i++)
            if (verifyHashcash(challenge, n, difficulty)) return n
        }
    }
}

/** Sorted-pair Merkle tree; an odd node at the end of a level is promoted unchanged. */
class MerkleTree(leaves: List<ByteArray>) {
    private val levels: List<List<ByteArray>>

    init {
        require(leaves.isNotEmpty()) { "empty menu" }
        val ls = mutableListOf(leaves)
        while (ls.last().size > 1) {
            ls += ls.last().chunked(2).map { if (it.size == 2) Menu.nodeHash(it[0], it[1]) else it[0] }
        }
        levels = ls
    }

    fun root(): ByteArray = levels.last()[0]

    fun proof(index: Int): List<ByteArray> {
        var i = index
        val out = mutableListOf<ByteArray>()
        for (level in levels.dropLast(1)) {
            val sib = i xor 1
            if (sib < level.size) out += level[sib]
            i /= 2
        }
        return out
    }
}
