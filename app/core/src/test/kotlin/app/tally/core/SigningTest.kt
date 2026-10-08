package app.tally.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SigningTest {
    private val agentSeed = ByteArray(32) { 1 }
    private val ownerSeed = ByteArray(32) { 2 }
    private val agent = Base58.encode(Ed25519.publicKey(agentSeed))
    private val owner = Base58.encode(Ed25519.publicKey(ownerSeed))

    @Test
    fun ownerCosignFillsItsOwnSlotAndKeepsTheAgentSignature() {
        val ix = Instruction(Tally.PROGRAM_ID, listOf(AccountMeta(agent, true, true), AccountMeta(owner, true, false)), byteArrayOf(7))
        val message = Message.compile(agent, "4vJ9JU1bJJE96FWSJKvHsmmFADCg4gpZQff4P3bkLKi", listOf(ix))
        val signers = Message.signers(agent, listOf(ix))
        val agentSig = Ed25519.sign(agentSeed, message)
        val partial = Message.serialize(message, signers.map { if (it == agent) agentSig else null })
        val full = Signing.addSignature(partial, owner) { Ed25519.sign(ownerSeed, it) }
        val slots = signers.mapIndexed { i, s -> s to full.copyOfRange(1 + 64 * i, 1 + 64 * i + 64) }.toMap()
        assertArrayEquals(agentSig, slots[agent])
        assertTrue(Ed25519.verify(Ed25519.publicKey(ownerSeed), message, slots[owner]!!))
    }
}
