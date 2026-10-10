package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProposalTest {
    private val seed = ByteArray(32) { 4 }
    private val items = Menu.computeFloors(listOf(Item(ByteArray(16) { 1 }, 3, 4_500_000, 0), Item(ByteArray(16) { 2 }, 3, 5_900_000, 0)))
    private val payload = SignedPayload(SignedMenu.sign("8TfJZMw4tMniEs8F6XbjeokVBHpuf2aCnXrXCbRL7Y5T", 1, 100, 9, items, seed), listOf("Oat latte", "Large oat latte"), listOf(null, null))
    private val id = Hex.encode(items[1].id)

    @Test
    fun roundTripsAsAQrInTheAlphanumericSet() {
        val qr = Proposal.toQr(id, payload)
        assertTrue(qr.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" })
        val p = Proposal.fromQr(qr)
        assertEquals(1, p.index)
        assertTrue(Payload.verifyLocally(p.payload, Ed25519.publicKey(seed), 50).isEmpty())
    }

    @Test
    fun linkFormDecodes() {
        val link = Proposal.LINK + java.net.URLEncoder.encode(Proposal.toQr(id, payload), "UTF-8")
        assertEquals(1, Proposal.fromLink(link).index)
    }

    @Test
    fun anItemNotOnTheMenuIsRefused() {
        val qr = Proposal.toQr("ff".repeat(16), payload)
        assertThrows(IllegalArgumentException::class.java) { Proposal.fromQr(qr) }
    }
}
