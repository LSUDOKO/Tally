package app.tally.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Software tap: the customer's reader and the merchant's HCE responder talk through the same APDU
 * sequence the radio carries, with no NFC hardware. What arrives must be the merchant's signed menu,
 * byte for byte, and still verify.
 */
class NfcExchangeTest {
    private val seed = ByteArray(32) { 11 }
    private val merchant = "8TfJZMw4tMniEs8F6XbjeokVBHpuf2aCnXrXCbRL7Y5T"
    private val names = listOf("Oat latte (regular)", "Oat latte (large)", "Cappuccino", "Reserve roast", "Croissant", "Bagel", "Metro single", "Day pass")
    private val categories = listOf(3, 3, 3, 3, 0, 0, 4, 4)
    private val prices = listOf(4_500_000L, 5_900_000, 5_200_000, 18_000_000, 3_000_000, 3_800_000, 2_750_000, 9_000_000)

    private fun signed(nonce: Long = 7): SignedPayload {
        val items = Menu.computeFloors(prices.indices.map { Item(ByteArray(16) { b -> (it * 16 + b).toByte() }, categories[it], prices[it], 0) })
        val menu = SignedMenu.sign(merchant, 1_000, 1_120, nonce, items, seed)
        return SignedPayload(menu, names, names.map { "A description of $it that pads the payload past one chunk." })
    }

    @Test
    fun signedMenuSurvivesTheTapAndStillVerifies() {
        val sent = signed()
        val bytes = Payload.encode(sent)
        assertTrue(bytes.size > Apdu.CHUNK, "the demo menu needs several chunks (${bytes.size} B)")

        val received = Payload.decode(Apdu.readAll { apdu -> Apdu.respond(apdu, bytes) })

        assertArrayEquals(sent.menu.signature, received.menu.signature)
        assertArrayEquals(sent.menu.root, received.menu.root)
        assertEquals(sent.names, received.names)
        assertTrue(Payload.verifyLocally(received, Ed25519.publicKey(seed), currentSlot = 1_050).isEmpty())
    }

    @Test
    fun nfcAndQrCarryTheSameMenu() {
        val sent = signed()
        val viaNfc = Payload.decode(Apdu.readAll { Apdu.respond(it, Payload.encode(sent)) })
        val viaQr = Payload.fromQr(Payload.toQr(sent))
        assertArrayEquals(Payload.encode(viaQr), Payload.encode(viaNfc))
    }
}
