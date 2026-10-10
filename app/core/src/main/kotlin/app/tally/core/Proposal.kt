package app.tally.core

/**
 * A purchase proposed by an outside agent (an MCP client, a chat bot): today's signed menu plus the
 * item it picked. It only fills in the checkout; the owner still taps to pay, the menu's signature
 * and freshness are checked as usual, and the chain enforces every rule.
 *
 * QR form (alphanumeric-only, so it fits a QR code): `TALLY:PROPOSE:<ITEM-ID-HEX-UPPER>:TALLY:V1:...`
 * Link form: `tally://checkout?p=<the QR form, URL-encoded>`
 */
class Proposal(val itemIdHex: String, val payload: SignedPayload) {
    val index: Int get() = payload.menu.items.indexOfFirst { Hex.encode(it.id) == itemIdHex }

    companion object {
        const val PREFIX = "TALLY:PROPOSE:"
        const val LINK = "tally://checkout?p="

        fun toQr(itemIdHex: String, payload: SignedPayload) = PREFIX + itemIdHex.uppercase() + ":" + Payload.toQr(payload)

        fun isProposal(text: String) = text.startsWith(PREFIX)

        fun fromQr(text: String): Proposal {
            require(text.startsWith(PREFIX)) { "not a Tally proposal" }
            val rest = text.removePrefix(PREFIX)
            val sep = rest.indexOf(':')
            require(sep == 32) { "malformed item id" }
            val id = rest.substring(0, sep).lowercase()
            require(id.all { it in "0123456789abcdef" }) { "malformed item id" }
            val p = Proposal(id, Payload.fromQr(rest.substring(sep + 1)))
            require(p.index >= 0) { "the proposed item is not on the signed menu" }
            return p
        }

        /** Accepts the link form (already URL-decoded by the platform) or the bare QR form. */
        fun fromLink(link: String): Proposal = fromQr(if (link.startsWith(LINK)) java.net.URLDecoder.decode(link.removePrefix(LINK), "UTF-8") else link)
    }
}
