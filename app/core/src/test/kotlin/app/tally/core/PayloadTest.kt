package app.tally.core

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PayloadTest {
    private val seed = ByteArray(32) { 7 }
    private val merchant = "8TfJZMw4tMniEs8F6XbjeokVBHpuf2aCnXrXCbRL7Y5T"
    private fun id(i: Int) = ByteArray(16) { (i + 1).toByte() }
    private val rows = Menu.computeFloors(
        listOf(Item(id(0), 3, 4_500_000, 0), Item(id(1), 3, 5_900_000, 0), Item(id(2), 0, 3_000_000, 0)),
    ).zip(listOf("Oat latte (regular)", "Oat latte (large)", "Croissant")).map { (i, n) -> MenuRow(i, n, if (i.category == 0) "Buttery." else null) }

    private fun signed(issued: Long = 1000, expires: Long = 1100) =
        SignedPayload(SignedMenu.sign(merchant, issued, expires, 42, rows.map { it.item }, seed), rows.map { it.name }, rows.map { it.description })

    @Test
    fun base45MatchesRfc9285() {
        assertEquals("BB8", Base45.encode("AB".toByteArray()))
        assertEquals("%69 VD92EX0", Base45.encode("Hello!!".toByteArray()))
        assertArrayEquals("ietf!".toByteArray(), Base45.decode("QED8WEX0"))
    }

    @Test
    fun qrStringIsAlphanumericAndRoundTrips() {
        val qr = Payload.toQr(signed())
        assertTrue(qr.startsWith("TALLY:V1:"))
        assertTrue(qr.all { it in "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:" }, "QR alphanumeric charset only")
        val back = Payload.fromQr(qr)
        assertEquals(42, back.menu.menuNonce)
        assertEquals(listOf("Oat latte (regular)", "Oat latte (large)", "Croissant"), back.names)
        assertEquals("Buttery.", back.descriptions[2])
        assertTrue(Payload.verifyLocally(back, Ed25519.publicKey(seed), currentSlot = 1050).isEmpty())
    }

    @Test
    fun tamperedPriceFailsRoot() {
        val p = signed()
        val bad = SignedPayload(
            SignedMenu(p.menu.merchant, p.menu.issuedSlot, p.menu.expiresSlot, p.menu.menuNonce,
                p.menu.items.mapIndexed { i, it -> if (i == 0) it.copy(price = 9_900_000) else it }, p.menu.header, p.menu.signerPubkey, p.menu.signature),
            p.names, p.descriptions,
        )
        assertTrue(MenuIssue.RootMismatch in Payload.verifyLocally(bad, Ed25519.publicKey(seed), 1050))
    }

    @Test
    fun wrongKeyAndWindowsAreFlagged() {
        val p = signed()
        assertTrue(MenuIssue.BadSignature in Payload.verifyLocally(p, Ed25519.publicKey(ByteArray(32) { 9 }), 1050))
        assertTrue(MenuIssue.Expired in Payload.verifyLocally(p, Ed25519.publicKey(seed), 1101))
        assertTrue(MenuIssue.NotYetValid in Payload.verifyLocally(p, Ed25519.publicKey(seed), 1000 - Payload.RPC_SLOT_SKEW - 1))
        assertTrue(MenuIssue.ExtendedWindow in Payload.verifyLocally(signed(1000, 1200), Ed25519.publicKey(seed), 1050))
    }

    @Test
    fun floorContradictionIsFlagged() {
        val items = listOf(Item(id(0), 3, 3_000_000, 3_000_000), Item(id(1), 3, 5_900_000, 4_500_000))
        val p = SignedPayload(SignedMenu.sign(merchant, 1000, 1100, 1, items, seed), listOf("Drip", "Oat latte"), listOf(null, null))
        assertTrue(MenuIssue.FloorContradiction in Payload.verifyLocally(p, Ed25519.publicKey(seed), 1050))
    }

    @Test
    fun aReSignedMenuReplacesAnExpiredOneOnlyWhenTheOfferIsIdentical() {
        val old = signed(1000, 1100)
        assertTrue(Payload.sameOffer(old, signed(1090, 1240)))
        val pricier = Menu.computeFloors(listOf(Item(id(0), 3, 4_900_000, 0), Item(id(1), 3, 5_900_000, 0), Item(id(2), 0, 3_000_000, 0)))
        val changed = SignedPayload(SignedMenu.sign(merchant, 1090, 1240, 43, pricier, seed), rows.map { it.name }, rows.map { it.description })
        org.junit.jupiter.api.Assertions.assertFalse(Payload.sameOffer(old, changed))
        val renamed = SignedPayload(old.menu, listOf("Oat latte (REGULAR)", "Oat latte (large)", "Croissant"), old.descriptions)
        org.junit.jupiter.api.Assertions.assertFalse(Payload.sameOffer(old, renamed))
    }

    @Test
    fun aCounterOnAFasterRpcIsNotTreatedAsStale() {
        // The counter's RPC can run a few slots ahead of the phone's; only a menu clearly from the
        // future is "not yet valid" (the chain still checks the real slot when the payment lands).
        val key = Ed25519.publicKey(seed)
        assertTrue(Payload.verifyLocally(signed(1000, 1150), key, 995).isEmpty())
        assertTrue(MenuIssue.NotYetValid in Payload.verifyLocally(signed(1000, 1150), key, 970))
    }
}
