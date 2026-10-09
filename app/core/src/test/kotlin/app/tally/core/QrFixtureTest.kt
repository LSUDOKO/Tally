package app.tally.core

import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A menu QR made by the TypeScript SDK (fixtures/qr-v1.json) must scan and verify in the app. */
class QrFixtureTest {
    private val v = Fixtures.json("qr-v1.json")

    @Test
    fun sdkMadeQrDecodesAndVerifiesInTheApp() {
        val p = Payload.fromQr(v["qr"]!!.jsonPrimitive.content)
        assertEquals(listOf("Oat latte (regular)", "Oat latte (large)", "Croissant"), p.names)
        assertEquals(listOf("Our most popular. Steamed oat milk, double shot.", "A bigger oat latte.", null), p.descriptions)
        assertEquals("zNLg5ncNoNWdUHjeMXo1JkTmcQ6biZRPFyFJR7CTBJH", p.menu.merchant)
        assertEquals(42, p.menu.menuNonce)
        val key = Hex.decode(v["signerPubkeyHex"]!!.jsonPrimitive.content)
        assertTrue(Payload.verifyLocally(p, key, v["validAtSlot"]!!.jsonPrimitive.int.toLong()).isEmpty())
    }

    @Test
    fun appReEncodesToTheSameMenu() {
        val p = Payload.fromQr(v["qr"]!!.jsonPrimitive.content)
        val again = Payload.fromQr(Payload.toQr(p))
        assertTrue(again.menu.signature.contentEquals(p.menu.signature))
        assertEquals(p.names, again.names)
    }
}
