package app.tally.core

import java.io.ByteArrayOutputStream

data class AccountMeta(val pubkey: String, val isSigner: Boolean, val isWritable: Boolean)

class Instruction(val programId: String, val accounts: List<AccountMeta>, val data: ByteArray)

object Programs {
    const val SYSTEM = "11111111111111111111111111111111"
    const val TOKEN = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    const val TOKEN_2022 = "TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb"
    const val ASSOCIATED_TOKEN = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    const val COMPUTE_BUDGET = "ComputeBudget111111111111111111111111111111"
    const val SYSVAR_INSTRUCTIONS = "Sysvar1nstructions1111111111111111111111111"
    const val SYSVAR_SLOT_HASHES = "SysvarS1otHashes111111111111111111111111111"
}

/** Program-derived addresses (`find_program_address`). */
object Pda {
    private val MARKER = "ProgramDerivedAddress".toByteArray()

    fun find(seeds: List<ByteArray>, programId: String): Pair<String, Int> {
        val program = Base58.decode(programId)
        for (bump in 255 downTo 0) {
            val candidate = sha256(*(seeds + listOf(byteArrayOf(bump.toByte()), program, MARKER)).toTypedArray())
            if (!Ed25519.isOnCurve(candidate)) return Base58.encode(candidate) to bump
        }
        error("no viable bump")
    }

    fun ata(owner: String, mint: String, tokenProgram: String = Programs.TOKEN): String =
        find(listOf(Base58.decode(owner), Base58.decode(tokenProgram), Base58.decode(mint)), Programs.ASSOCIATED_TOKEN).first
}

/** Tally PDAs (seeds as in docs/06, "As built"). */
object TallyPda {
    private fun s(v: String) = v.toByteArray()
    private fun k(v: String) = Base58.decode(v)
    private fun find(vararg seeds: ByteArray) = Pda.find(seeds.toList(), Tally.PROGRAM_ID).first

    fun config() = find(s("config"))
    fun pool() = find(s("pool"))
    fun merchant(authority: String) = find(s("merchant"), k(authority))
    fun bondVault(merchant: String) = find(s("bond"), k(merchant))
    fun keyRecord(merchant: String, signingKey: String) = find(s("key"), k(merchant), k(signingKey))
    fun capsule(owner: String, capsuleId: Long) = find(s("capsule"), k(owner), Le.u64(capsuleId))
    fun receipt(capsule: String, merchant: String, menuNonce: Long, itemId: ByteArray) =
        find(s("receipt"), k(capsule), k(merchant), Le.u64(menuNonce), itemId)
    fun dispute(receipt: String) = find(s("dispute"), k(receipt))
}

/** Minimal Borsh writer for instruction arguments. */
class Borsh {
    private val out = ByteArrayOutputStream()
    fun bytes(b: ByteArray) = apply { out.write(b) }
    fun u8(v: Int) = apply { out.write(v and 0xff) }
    fun bool(v: Boolean) = u8(if (v) 1 else 0)
    fun u16(v: Int) = bytes(Le.u16(v))
    fun u32(v: Int) = bytes(Le.u32(v))
    fun u64(v: Long) = bytes(Le.u64(v))
    fun pubkey(v: String) = bytes(Base58.decode(v).also { require(it.size == 32) })
    fun vec32(items: List<ByteArray>) = apply {
        u32(items.size)
        items.forEach { bytes(it) }
    }
    fun toByteArray(): ByteArray = out.toByteArray()
}

/** Compact-u16 ("shortvec") length prefix. */
internal fun shortVec(n: Int): ByteArray {
    val out = ByteArrayOutputStream()
    var v = n
    while (true) {
        var b = v and 0x7f
        v = v ushr 7
        if (v == 0) {
            out.write(b)
            break
        }
        b = b or 0x80
        out.write(b)
    }
    return out.toByteArray()
}

/**
 * Legacy transaction message compiler, byte-compatible with @solana/kit: fee payer first, then
 * signers before non-signers, writable before read-only, ties broken by kit's address collation
 * (case-insensitive base58 order, lowercase first on case-only differences).
 */
object Message {
    private class Entry(var signer: Boolean, var writable: Boolean, val feePayer: Boolean)

    internal val addressComparator = Comparator<String> { a, b ->
        val primary = a.lowercase().compareTo(b.lowercase())
        if (primary != 0) return@Comparator primary
        for (i in a.indices) {
            if (a[i] != b[i]) return@Comparator if (a[i].isLowerCase()) -1 else 1
        }
        0
    }

    fun orderedAccounts(feePayer: String, instructions: List<Instruction>): List<Pair<String, AccountMeta>> {
        val map = linkedMapOf(feePayer to Entry(signer = true, writable = true, feePayer = true))
        for (ix in instructions) {
            for (a in ix.accounts) {
                val e = map.getOrPut(a.pubkey) { Entry(a.isSigner, a.isWritable, false) }
                e.signer = e.signer || a.isSigner
                e.writable = e.writable || a.isWritable
            }
            map.getOrPut(ix.programId) { Entry(signer = false, writable = false, feePayer = false) }
        }
        return map.entries.sortedWith { (la, le), (ra, re) ->
            when {
                le.feePayer != re.feePayer -> if (le.feePayer) -1 else 1
                le.signer != re.signer -> if (le.signer) -1 else 1
                le.writable != re.writable -> if (le.writable) -1 else 1
                else -> addressComparator.compare(la, ra)
            }
        }.map { (k, e) -> k to AccountMeta(k, e.signer, e.writable) }
    }

    fun compile(feePayer: String, blockhash: String, instructions: List<Instruction>): ByteArray {
        val accounts = orderedAccounts(feePayer, instructions)
        val index = accounts.mapIndexed { i, (k, _) -> k to i }.toMap()
        val signers = accounts.count { it.second.isSigner }
        val roSigners = accounts.count { it.second.isSigner && !it.second.isWritable }
        val roUnsigned = accounts.count { !it.second.isSigner && !it.second.isWritable }
        val out = ByteArrayOutputStream()
        out.write(signers)
        out.write(roSigners)
        out.write(roUnsigned)
        out.write(shortVec(accounts.size))
        accounts.forEach { out.write(Base58.decode(it.first)) }
        out.write(Base58.decode(blockhash))
        out.write(shortVec(instructions.size))
        for (ix in instructions) {
            out.write(index.getValue(ix.programId))
            out.write(shortVec(ix.accounts.size))
            ix.accounts.forEach { out.write(index.getValue(it.pubkey)) }
            out.write(shortVec(ix.data.size))
            out.write(ix.data)
        }
        return out.toByteArray()
    }

    /** Ordered list of required signer addresses for a compiled message. */
    fun signers(feePayer: String, instructions: List<Instruction>): List<String> =
        orderedAccounts(feePayer, instructions).filter { it.second.isSigner }.map { it.first }

    /** Wire transaction: shortvec(sig count) || signatures || message. Missing signatures are zeroed. */
    fun serialize(message: ByteArray, signatures: List<ByteArray?>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(shortVec(signatures.size))
        signatures.forEach { out.write(it ?: ByteArray(64)) }
        out.write(message)
        return out.toByteArray()
    }
}

object ComputeBudget {
    fun setUnitLimit(units: Int) = Instruction(Programs.COMPUTE_BUDGET, emptyList(), byteArrayOf(2) + Le.u32(units))
    fun setUnitPrice(microLamports: Long) = Instruction(Programs.COMPUTE_BUDGET, emptyList(), byteArrayOf(3) + Le.u64(microLamports))
}

object Lighthouse {
    /** `AssertTokenAccount { Amount == amount }`: discriminator 9, Amount variant 2, Equal operator 0. */
    fun assertTokenAmount(tokenAccount: String, amount: Long) =
        Instruction(Tally.LIGHTHOUSE_ID, listOf(AccountMeta(tokenAccount, false, false)), byteArrayOf(9, 0, 2) + Le.u64(amount) + byteArrayOf(0))
}
