@file:OptIn(ExperimentalSerializationApi::class)

package app.tally.core

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

/** RFC 9285 Base45: the densest encoding that stays inside QR alphanumeric mode. */
object Base45 {
    private const val CHARSET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ $%*+-./:"

    fun encode(input: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        while (i + 1 < input.size) {
            var x = ((input[i].toInt() and 0xff) shl 8) + (input[i + 1].toInt() and 0xff)
            repeat(3) { sb.append(CHARSET[x % 45]); x /= 45 }
            i += 2
        }
        if (i < input.size) {
            var x = input[i].toInt() and 0xff
            repeat(2) { sb.append(CHARSET[x % 45]); x /= 45 }
        }
        return sb.toString()
    }

    fun decode(s: String): ByteArray {
        val v = s.map { CHARSET.indexOf(it).also { d -> require(d >= 0) { "invalid base45 '$it'" } } }
        val out = mutableListOf<Byte>()
        var i = 0
        while (i + 2 < v.size) {
            val x = v[i] + v[i + 1] * 45 + v[i + 2] * 45 * 45
            require(x <= 0xffff) { "base45 overflow" }
            out += (x shr 8).toByte()
            out += x.toByte()
            i += 3
        }
        if (i < v.size) {
            require(v.size - i == 2) { "dangling base45 character" }
            out += (v[i] + v[i + 1] * 45).toByte()
        }
        return out.toByteArray()
    }
}

/** A signed menu plus its display-channel text (names, descriptions) for the human. */
class SignedPayload(val menu: SignedMenu, val names: List<String>, val descriptions: List<String?>) {
    fun rows() = menu.items.mapIndexed { i, it -> MenuRow(it, names[i], descriptions.getOrNull(i)) }
}

enum class MenuIssue { BadSignature, RootMismatch, ItemCountMismatch, NotYetValid, Expired, ExtendedWindow, WindowTooLong, FloorContradiction }

/** Wire format shared by QR and NFC (docs/07): a compact CBOR map. */
object Payload {
    const val QR_PREFIX = "TALLY:V1:"

    @Serializable
    private class WireItem(@ByteString val id: ByteArray, val c: Int, val p: Long, val f: Long, val n: String, val d: String? = null)

    @Serializable
    private class Wire(
        val v: Int,
        @ByteString val m: ByteArray,
        @ByteString val r: ByteArray,
        val iss: Long,
        val ex: Long,
        val n: Long,
        @ByteString val k: ByteArray,
        @ByteString val s: ByteArray,
        val i: List<WireItem>,
    )

    private val cbor = Cbor { ignoreUnknownKeys = true }

    fun encode(p: SignedPayload): ByteArray = cbor.encodeToByteArray(
        Wire(
            v = 1,
            m = Base58.decode(p.menu.merchant),
            r = p.menu.root,
            iss = p.menu.issuedSlot,
            ex = p.menu.expiresSlot,
            n = p.menu.menuNonce,
            k = p.menu.signerPubkey,
            s = p.menu.signature,
            i = p.menu.items.mapIndexed { idx, it -> WireItem(it.id, it.category, it.price, it.floor, p.names[idx], p.descriptions.getOrNull(idx)) },
        ),
    )

    fun decode(bytes: ByteArray): SignedPayload {
        val w = cbor.decodeFromByteArray<Wire>(bytes)
        require(w.v == 1) { "unsupported menu version ${w.v}" }
        val merchant = Base58.encode(w.m)
        val header = Menu.buildHeader(HeaderFields(Base58.decode(Tally.PROGRAM_ID), w.m, w.r, w.iss, w.ex, w.n, w.i.size))
        val items = w.i.map { Item(it.id, it.c, it.p, it.f) }
        return SignedPayload(SignedMenu(merchant, w.iss, w.ex, w.n, items, header, w.k, w.s), w.i.map { it.n }, w.i.map { it.d })
    }

    fun toQr(p: SignedPayload): String = QR_PREFIX + Base45.encode(encode(p))

    /**
     * A counter's re-signed menu offers exactly what the owner was looking at: same merchant, same
     * items (ids, categories, prices, floors) in the same order, same names. Only then may it
     * replace a menu that ran out while the owner decided.
     */
    fun sameOffer(old: SignedPayload, fresh: SignedPayload): Boolean =
        old.menu.merchant == fresh.menu.merchant && old.menu.items == fresh.menu.items && old.names == fresh.names

    fun fromQr(text: String): SignedPayload {
        require(text.startsWith(QR_PREFIX)) { "not a Tally menu" }
        return decode(Base45.decode(text.removePrefix(QR_PREFIX)))
    }

    /** Phone-side checks before any transaction (docs/07, steps 1–6). Empty = good to go. */
    fun verifyLocally(p: SignedPayload, registeredSigningKey: ByteArray, currentSlot: Long): List<MenuIssue> {
        val m = p.menu
        val issues = mutableListOf<MenuIssue>()
        if (!m.signerPubkey.contentEquals(registeredSigningKey) || !Ed25519.verify(registeredSigningKey, m.header, m.signature)) issues += MenuIssue.BadSignature
        if (!MerkleTree(m.items.map(Menu::leafHash)).root().contentEquals(m.root)) issues += MenuIssue.RootMismatch
        if (m.itemCount != m.items.size) issues += MenuIssue.ItemCountMismatch
        if (currentSlot < m.issuedSlot) issues += MenuIssue.NotYetValid
        if (currentSlot > m.expiresSlot) issues += MenuIssue.Expired
        if (m.window > Tally.MAX_AUTONOMOUS_WINDOW) issues += MenuIssue.ExtendedWindow
        if (m.window > 9_000) issues += MenuIssue.WindowTooLong
        if (Menu.findFloorContradiction(m.items) != null) issues += MenuIssue.FloorContradiction
        return issues
    }
}
