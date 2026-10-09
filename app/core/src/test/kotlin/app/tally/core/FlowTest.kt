package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.Base64

class FlowTest {
    private val item = Item(ByteArray(16), 3, 4_500_000, 4_500_000)
    private val pricier = item.copy(price = 5_900_000)

    @Test
    fun ownerNeededOnlyAboveFloorLowBondOrExtendedWindow() {
        assertFalse(Purchase.needsOwner(item, merchantBond = 5_000, minBond = 1_000, window = 100))
        assertTrue(Purchase.needsOwner(pricier, 5_000, 1_000, 100))
        assertTrue(Purchase.needsOwner(item, 10, 1_000, 100))
        assertTrue(Purchase.needsOwner(item, 5_000, 1_000, 151))
    }

    @Test
    fun aMenuAboutToExpireMustBeRescanned() {
        assertEquals(Purchase.Gate.Ok, Purchase.gate(currentSlot = 1_000, expiresSlot = 1_100))
        assertEquals(Purchase.Gate.Rescan, Purchase.gate(currentSlot = 1_095, expiresSlot = 1_100))
        assertEquals(Purchase.Gate.Rescan, Purchase.gate(currentSlot = 1_200, expiresSlot = 1_100))
    }

    @Test
    fun programErrorsBecomeSentences() {
        val err = """{"InstructionError":[1,{"Custom":6013}]}"""
        assertEquals(6013, ErrorText.code(err))
        assertEquals("That's pricier than the cheapest match, so your approval is needed.", ErrorText.sentence(6013))
        assertEquals("Transaction failed (code 9999).", ErrorText.sentence(9999))
        assertEquals("Already paid: this item was bought from this exact menu. Tally never charges twice.", ErrorText.sentence(0))
        assertEquals(null, ErrorText.code("""{"InstructionError":[0,"InvalidAccountData"]}"""))
    }

    @Test
    fun signatureStatusMapsToOutcome() {
        assertEquals(Outcome.Pending, Purchase.outcome(null))
        assertEquals(Outcome.Confirmed, Purchase.outcome(SigStatus(confirmed = true, err = null)))
        val reverted = Purchase.outcome(SigStatus(confirmed = true, err = """{"InstructionError":[1,{"Custom":6009}]}"""))
        assertTrue(reverted is Outcome.Reverted && reverted.code == 6009)
    }

    @Test
    fun decodesCapsuleAndMerchantAccounts() {
        val capsule = Borsh().bytes(ByteArray(8)).pubkey(Tally.PROGRAM_ID).pubkey(Tally.PROGRAM_ID).pubkey(Tally.PROGRAM_ID).pubkey(Tally.PROGRAM_ID)
            .u64(6_000_000).u64(15_000_000).u64(4_500_000).u64(77).u64(8).u64(0).u64(0).u64(0).u64(1_000).u64(999_999).u8(2)
            .pubkey(Tally.PROGRAM_ID).bool(false).u64(1).u8(255).toByteArray()
        val c = Accounts.capsule(capsule)
        assertEquals(15_000_000, c.dailyCap)
        assertEquals(4_500_000, c.spentInCycle)
        assertTrue(c.allows(3))
        assertFalse(c.allows(0))
        assertEquals(2, c.flags)
        val merchant = Borsh().bytes(ByteArray(8)).pubkey(Tally.PROGRAM_ID).pubkey(Tally.LIGHTHOUSE_ID).pubkey(Tally.PROGRAM_ID)
            .bytes(ByteArray(32).also { "Blue Door Coffee".toByteArray().copyInto(it) }).u64(5_000_000_000).u64(0).u8(8).u64(0).u32(0).bool(true).u8(254).toByteArray()
        val m = Accounts.merchant(merchant)
        assertEquals("Blue Door Coffee", m.name)
        assertEquals(Tally.LIGHTHOUSE_ID, m.signingKey)
        assertEquals(8, m.powDifficulty)
        assertTrue(m.active)
    }

    @Test
    fun parsesRpcResponses() {
        val rpc = Rpc { body ->
            when {
                "getSlot" in body -> """{"jsonrpc":"2.0","result":12345,"id":1}"""
                "getLatestBlockhash" in body -> """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":{"blockhash":"4vJ9JU1bJJE96FWSJKvHsmmFADCg4gpZQff4P3bkLKi","lastValidBlockHeight":9}},"id":1}"""
                "getAccountInfo" in body -> """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":{"data":["${Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3))}","base64"],"owner":"x","lamports":1,"executable":false}},"id":1}"""
                "getTokenAccountsByOwner" in body -> """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":[{"pubkey":"Acc1","account":{"data":["${Base64.getEncoder().encodeToString(byteArrayOf(9))}","base64"]}}]},"id":1}"""
                "getSignatureStatuses" in body -> """{"jsonrpc":"2.0","result":{"context":{"slot":1},"value":[{"confirmationStatus":"confirmed","err":{"InstructionError":[1,{"Custom":6007}]}}]},"id":1}"""
                else -> """{"jsonrpc":"2.0","error":{"code":-32601,"message":"nope"},"id":1}"""
            }
        }
        assertEquals(12345, rpc.getSlot())
        assertEquals("4vJ9JU1bJJE96FWSJKvHsmmFADCg4gpZQff4P3bkLKi", rpc.getLatestBlockhash())
        assertEquals(listOf<Byte>(1, 2, 3), rpc.getAccountData("x")!!.toList())
        assertEquals("Acc1", rpc.getTokenAccountsByOwner("o", Programs.TOKEN_2022).single().first)
        val st = rpc.getSignatureStatus("sig")!!
        assertEquals(6007, ErrorText.code(st.err!!))
    }

    @Test
    fun dailyBudgetResetsWithTheProgramsCycle() {
        assertEquals(4_600_000, Purchase.dailyRemaining(15_000_000, 10_400_000, cycleStartSlot = 1_000, currentSlot = 5_000))
        assertEquals(0, Purchase.dailyRemaining(15_000_000, 16_000_000, 1_000, 5_000))
        assertEquals(15_000_000, Purchase.dailyRemaining(15_000_000, 10_400_000, 1_000, 1_000 + Tally.CYCLE_SLOTS))
    }
}
