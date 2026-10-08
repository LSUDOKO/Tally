package app.tally.wallet

import android.net.Uri
import app.tally.core.Base58
import app.tally.core.Ed25519
import app.tally.core.Instruction
import app.tally.core.Message
import app.tally.core.Rpc
import app.tally.core.Signing
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana

class WalletException(message: String) : Exception(message)

/**
 * The capsule owner. In production this is the Seeker's Seed Vault reached through Mobile Wallet
 * Adapter: the owner key never enters this process. A debug-only demo owner exists for emulators
 * without a wallet and is clearly labelled in the UI.
 */
interface Owner {
    val label: String

    /** Connect (Sign-in-with-Solana) and return the owner address. */
    suspend fun connect(): String

    /** Owner pays fees and signs; the wallet sends. Returns the signature (base58). */
    suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String

    /** Add the owner's co-signature to a transaction the agent already signed (cheapest-match exceptions). */
    suspend fun cosign(wireTx: ByteArray): ByteArray
}

class MwaOwner(private val sender: ActivityResultSender, private val cluster: String) : Owner {
    override val label = "Seed Vault (Mobile Wallet Adapter)"

    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://github.com/LSUDOKO/Tally"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Tally",
        ),
    ).apply { blockchain = if (cluster == "mainnet-beta") Solana.Mainnet else Solana.Devnet }

    private var address: String? = null

    private fun <T> unwrap(r: TransactionResult<T>): Pair<T, String> = when (r) {
        is TransactionResult.Success -> {
            val addr = Base58.encode(r.authResult.accounts.first().publicKey)
            address = addr
            r.payload to addr
        }
        is TransactionResult.NoWalletFound -> throw WalletException("No Mobile Wallet Adapter wallet found. On Seeker, Seed Vault is built in.")
        is TransactionResult.Failure -> throw WalletException(r.e.message ?: "Wallet request failed")
    }

    override suspend fun connect(): String {
        val r = adapter.transact(sender, SignInWithSolana.Payload("tally.app", "Sign in to Tally to protect your agent's purchases")) { }
        return unwrap(r).second
    }

    override suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String {
        val owner = address ?: connect()
        val blockhash = rpc.getLatestBlockhash()
        val message = Message.compile(owner, blockhash, instructions)
        val unsigned = Message.serialize(message, Message.signers(owner, instructions).map { null })
        val r = adapter.transact(sender) { signAndSendTransactions(arrayOf(unsigned)) }
        return Base58.encode(unwrap(r).first.signatures.first())
    }

    override suspend fun cosign(wireTx: ByteArray): ByteArray {
        val r = adapter.transact(sender) { signTransactions(arrayOf(wireTx)) }
        return unwrap(r).first.signedPayloads.first()
    }
}

/** Debug builds only: a local owner key for emulators without a wallet. Never shipped in release. */
class DemoOwner(private val seed: ByteArray) : Owner {
    override val label = "Demo owner (debug build, local key)"
    val address: String = Base58.encode(Ed25519.publicKey(seed))

    override suspend fun connect() = address

    override suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String {
        val message = Message.compile(address, rpc.getLatestBlockhash(), instructions)
        val sig = Ed25519.sign(seed, message)
        return rpc.sendTransaction(Message.serialize(message, listOf(sig)))
    }

    override suspend fun cosign(wireTx: ByteArray): ByteArray = Signing.addSignature(wireTx, address) { Ed25519.sign(seed, it) }
}
