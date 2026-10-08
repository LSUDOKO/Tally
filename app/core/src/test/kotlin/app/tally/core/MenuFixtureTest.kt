package app.tally.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Byte-exact parity with crates/tally-core, pinned by fixtures/menu-v1.json. */
class MenuFixtureTest {
    private val fx: JsonObject = Fixtures.json("menu-v1.json")

    private fun s(o: JsonObject, k: String) = o[k]!!.jsonPrimitive.content
    private fun items(arr: JsonArray) = arr.map {
        val o = it.jsonObject
        Item(Hex.decode(s(o, "id_hex")), o["category"]!!.jsonPrimitive.int, o["price"]!!.jsonPrimitive.long, o["floor"]!!.jsonPrimitive.long)
    }
    private fun header(root: ByteArray, hf: JsonObject) = Menu.buildHeader(
        HeaderFields(
            programId = Base58.decode(s(fx, "program_id")),
            merchantPda = Base58.decode(s(fx, "merchant_pda")),
            menuRoot = root,
            issuedSlot = hf["issued_slot"]!!.jsonPrimitive.long,
            expiresSlot = hf["expires_slot"]!!.jsonPrimitive.long,
            menuNonce = hf["menu_nonce"]!!.jsonPrimitive.long,
            itemCount = hf["item_count"]!!.jsonPrimitive.int,
        ),
    )

    @Test
    fun honestMenuReproduces() {
        val items = items(fx["items"]!!.jsonArray)
        assertEquals(items.map { it.floor }, Menu.computeFloors(items.map { it.copy(floor = 0) }).map { it.floor })
        val leaves = items.map(Menu::leafHash)
        val tree = MerkleTree(leaves)
        assertEquals(s(fx, "root_hex"), Hex.encode(tree.root()))
        fx["items"]!!.jsonArray.forEachIndexed { i, x ->
            assertEquals(s(x.jsonObject, "leaf_hex"), Hex.encode(leaves[i]))
            assertEquals(x.jsonObject["proof_hex"]!!.jsonArray.map { it.jsonPrimitive.content }, tree.proof(i).map(Hex::encode))
            assertTrue(Menu.verifyProof(leaves[i], tree.proof(i), tree.root()))
        }
        assertEquals(s(fx, "header_hex"), Hex.encode(header(tree.root(), fx["header_fields"]!!.jsonObject)))
    }

    @Test
    fun ed25519DataAndSignatureVerify() {
        val pk = Base58.decode(s(fx, "merchant_signing_pubkey"))
        val sig = Hex.decode(s(fx, "signature_hex"))
        val header = Hex.decode(s(fx, "header_hex"))
        assertEquals(s(fx, "ed25519_ix_data_hex"), Hex.encode(Menu.ed25519IxData(pk, sig, header)))
        assertTrue(Ed25519.verify(pk, header, sig))
        val seed = Hex.decode(s(fx, "merchant_signing_seed_hex"))
        assertArrayEquals(pk, Ed25519.publicKey(seed))
        assertArrayEquals(sig, Ed25519.sign(seed, header))
    }

    @Test
    fun fraudFixtureContradicts() {
        val f = fx["fraud"]!!.jsonObject
        assertEquals(f["cheap_index"]!!.jsonPrimitive.int to f["claimed_index"]!!.jsonPrimitive.int, Menu.findFloorContradiction(items(f["items"]!!.jsonArray)))
        assertNull(Menu.findFloorContradiction(items(fx["items"]!!.jsonArray)))
    }

    @Test
    fun powVector() {
        val p = fx["pow"]!!.jsonObject
        val c = Menu.powChallenge(p["menu_nonce"]!!.jsonPrimitive.long, Base58.decode(s(p, "capsule")), Hex.decode(s(p, "slot_hash_hex")))
        assertEquals(s(p, "challenge_hex"), Hex.encode(c))
        assertTrue(Menu.verifyHashcash(c, Hex.decode(s(p, "nonce_hex")), p["difficulty"]!!.jsonPrimitive.int))
    }

    @Test
    fun oddSizedMenusRoundTrip() {
        for (n in 1..17) {
            val leaves = (0 until n).map { k -> Menu.leafHash(Item(ByteArray(16) { (k + 1).toByte() }, 3, 1, 1)) }
            val t = MerkleTree(leaves)
            leaves.forEachIndexed { i, l -> assertTrue(Menu.verifyProof(l, t.proof(i), t.root()), "n=$n i=$i") }
        }
    }
}
