package app.tally.merchant

import app.tally.core.AssociatedToken
import app.tally.core.Accounts
import app.tally.core.Base58
import app.tally.core.Ed25519
import app.tally.core.Payload
import app.tally.core.Pda
import app.tally.core.Rpc
import app.tally.core.SignedMenu
import app.tally.core.SignedPayload
import app.tally.core.TallyIx
import app.tally.core.TallyPda
import app.tally.keys.AgentKey
import app.tally.nfc.MerchantBroadcast
import app.tally.wallet.Owner

/**
 * Merchant terminal mode (same APK): registers the merchant with an SKR bond through the
 * merchant's own wallet, then re-signs the menu every ~20 s with a Keystore-wrapped menu key and
 * broadcasts it as a rotating QR and over NFC (HCE).
 */
class MerchantTerminal(private val menuKey: AgentKey) {
    val signingKey: String get() = Base58.encode(Ed25519.publicKey(menuKey.exportSeedForMenuSigning()))

    fun merchantAddress(authority: String) = TallyPda.merchant(authority)

    fun load(rpc: Rpc, authority: String): Accounts.Merchant? = rpc.getAccountData(merchantAddress(authority))?.let(Accounts::merchant)

    suspend fun register(owner: Owner, rpc: Rpc, authority: String, usdcMint: String, skrMint: String, name: String, bondMicro: Long): String =
        owner.signAndSend(
            listOf(
                AssociatedToken.createIdempotent(authority, authority, usdcMint),
                TallyIx.registerMerchant(authority, Pda.ata(authority, usdcMint), skrMint, name, signingKey, bondMicro),
            ),
            rpc,
        )

    suspend fun setPow(owner: Owner, rpc: Rpc, authority: String, difficulty: Int): String =
        owner.signAndSend(listOf(TallyIx.setPowDifficulty(authority, difficulty)), rpc)

    /** Sign a fresh menu valid for `window` slots and publish it to NFC. Returns the QR string. */
    fun publish(merchant: String, slot: Long, nonce: Long, entries: List<Catalog.Entry>, window: Long = 120): Pair<SignedPayload, String> {
        val rows = Catalog.rows(entries)
        val menu = SignedMenu.sign(merchant, slot, slot + window, nonce, rows.map { it.item }, menuKey.exportSeedForMenuSigning())
        val payload = SignedPayload(menu, rows.map { it.name }, rows.map { it.description })
        MerchantBroadcast.payload = Payload.encode(payload)
        return payload to Payload.toQr(payload)
    }

    fun stop() {
        MerchantBroadcast.payload = null
    }
}
