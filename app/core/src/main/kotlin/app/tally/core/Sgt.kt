package app.tally.core

/**
 * Seeker Genesis Token lookup, mirroring the program's `checks/sgt.rs`: a Token-2022 account the
 * owner holds with amount 1, whose mint carries a `TokenGroupMember` (TLV type 23) naming itself
 * and the configured SGT collection.
 */
object Sgt {
    private const val BASE = 165
    private const val TOKEN_GROUP_MEMBER = 23

    /** `(mint, amount)` when `data` is a token account owned by `owner` with amount 1. */
    fun holding(data: ByteArray, owner: ByteArray): Pair<String, Long>? {
        if (data.size < BASE) return null
        if (!data.copyOfRange(32, 64).contentEquals(owner)) return null
        val amount = Le.readU64(data, 64)
        return if (amount == 1L) Base58.encode(data.copyOfRange(0, 32)) to amount else null
    }

    /** The group (base58) a Token-2022 mint is a member of, or null. */
    fun groupOf(mintData: ByteArray, mint: ByteArray): String? {
        if (mintData.size <= BASE || mintData[BASE].toInt() != 1) return null
        var i = BASE + 1
        while (i + 4 <= mintData.size) {
            val type = (mintData[i].toInt() and 0xff) or ((mintData[i + 1].toInt() and 0xff) shl 8)
            val len = (mintData[i + 2].toInt() and 0xff) or ((mintData[i + 3].toInt() and 0xff) shl 8)
            val start = i + 4
            val end = start + len
            if (end > mintData.size) return null
            if (type == TOKEN_GROUP_MEMBER && len >= 64) {
                if (!mintData.copyOfRange(start, start + 32).contentEquals(mint)) return null
                return Base58.encode(mintData.copyOfRange(start + 32, start + 64))
            }
            if (type == 0) return null
            i = end
        }
        return null
    }
}
