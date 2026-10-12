package app.tally.wallet

import android.net.Uri
import android.util.Log
import app.tally.core.Base58
import app.tally.core.Ed25519
import app.tally.core.Instruction
import app.tally.core.Message
import app.tally.core.Rpc
import app.tally.core.Signing
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.AdapterOperations
import com.solana.mobilewalletadapter.clientlib.protocol.JsonRpc20Client
import com.solana.mobilewalletadapter.clientlib.protocol.MobileWalletAdapterClient
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.mobilewalletadapter.common.signin.SignInWithSolana
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WalletException(message: String) : Exception(message)

/** Who signed in: the address, the account's name in the wallet, and the wallet's endpoint (names the app). */
class SignedIn(val address: String, val label: String?, val walletUriBase: Uri?)

/** The owner's co-signature: either the signed bytes for Tally to submit, or a signature the wallet already sent. */
sealed class Cosigned {
    class Signed(val wire: ByteArray) : Cosigned()
    class Sent(val signature: String) : Cosigned()
}

/**
 * The capsule owner. In production this is the Seeker's Seed Vault reached through Mobile Wallet
 * Adapter: the owner key never enters this process. A debug-only demo owner exists for emulators
 * without a wallet and is clearly labelled in the UI.
 */
interface Owner {
    val label: String

    /** Sign in with Solana and return who signed in. */
    suspend fun signIn(): SignedIn

    /** Connect (Sign-in-with-Solana) and return the owner address. */
    suspend fun connect(): String = signIn().address

    /** Revoke Tally's access in the wallet and forget the session. */
    suspend fun disconnect() {}

    /** Owner pays fees and signs; the wallet sends. Returns the signature (base58). */
    suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String

    /** Add the owner's co-signature to a transaction the agent already signed (cheapest-match exceptions). */
    suspend fun cosign(wireTx: ByteArray): Cosigned
}

class MwaOwner(private val sender: ActivityResultSender, private val cluster: String, private val store: app.tally.data.Store) : Owner {
    override val label = "Seed Vault (Mobile Wallet Adapter)"

    private val adapter = MobileWalletAdapter(
        connectionIdentity = ConnectionIdentity(
            identityUri = Uri.parse("https://github.com/LSUDOKO/Tally"),
            iconUri = Uri.parse("favicon.ico"),
            identityName = "Tally",
        ),
    ).apply {
        blockchain = if (cluster == "mainnet-beta") Solana.Mainnet else Solana.Devnet
        // A saved session reauthorizes silently instead of asking the owner to sign in again.
        authToken = store.walletToken
    }

    private var address: String? = store.walletToken?.let { store.ownerAddress }

    private fun <T> unwrap(r: TransactionResult<T>): Pair<T, String> = when (r) {
        is TransactionResult.Success -> {
            val addr = Base58.encode(r.authResult.accounts.first().publicKey)
            address = addr
            store.walletToken = r.authResult.authToken
            r.payload to addr
        }
        is TransactionResult.NoWalletFound -> throw WalletException("No Mobile Wallet Adapter wallet found. On Seeker, Seed Vault is built in.")
        is TransactionResult.Failure -> throw WalletException(explain(r.e))
    }

    /**
     * Runs a wallet request on the saved session. A wallet that restarted (or revoked the session)
     * rejects it before the request itself is shown, which left the owner looking at an empty
     * wallet. Then: forget the session and ask once more with a fresh authorization.
     */
    private suspend fun <T> transact(block: suspend AdapterOperations.(MobileWalletAdapterClient.AuthorizationResult) -> T): TransactionResult<T> {
        var reached = false
        val first = adapter.transact(sender, null) { auth -> reached = true; block(this, auth) }
        if (first is TransactionResult.Failure && !reached && adapter.authToken != null) {
            Log.w("Tally", "wallet rejected the saved session (${remoteCode(first.e)}); authorizing again")
            adapter.authToken = null
            store.walletToken = null
            return adapter.transact(sender, null) { auth -> block(this, auth) }
        }
        return first
    }

    override suspend fun signIn(): SignedIn {
        adapter.authToken = null
        val r = adapter.transact(sender, SignInWithSolana.Payload("tally.app", "Sign in to Tally to protect your agent's purchases")) { it }
        val (auth, addr) = unwrap(r)
        return SignedIn(addr, auth.accounts.first().accountLabel ?: auth.accountLabel, auth.walletUriBase)
    }

    override suspend fun disconnect() {
        // Best effort: the wallet opens briefly to revoke the token. Signing out locally never depends on it.
        if (adapter.authToken != null) runCatching { adapter.disconnect(sender) }
        adapter.authToken = null
        address = null
    }

    override suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String {
        val owner = address ?: connect()
        val blockhash = withContext(Dispatchers.IO) { rpc.getLatestBlockhash() }
        val message = Message.compile(owner, blockhash, instructions)
        val unsigned = Message.serialize(message, Message.signers(owner, instructions).map { null })
        val r = transact { signAndSendTransactions(arrayOf(unsigned)) }
        return Base58.encode(unwrap(r).first.signatures.first())
    }

    /**
     * Mobile Wallet Adapter 2.0 made sign-only optional. Wallets that offer it return the signed
     * bytes; wallets that only sign-and-send (e.g. Solflare) add their signature to the
     * agent-signed transaction and submit it themselves.
     */
    override suspend fun cosign(wireTx: ByteArray): Cosigned {
        val r = transact {
            val caps = getCapabilities()
            Log.i("Tally", "wallet capabilities: signAndSend=${caps.supportsSignAndSendTransactions} optional=${caps.supportedOptionalFeatures.joinToString()}")
            if (SIGN_ONLY in caps.supportedOptionalFeatures) Cosigned.Signed(signTransactions(arrayOf(wireTx)).signedPayloads.first())
            else Cosigned.Sent(Base58.encode(signAndSendTransactions(arrayOf(wireTx)).signatures.first()))
        }
        return unwrap(r).first
    }

    private companion object {
        const val SIGN_ONLY = "solana:signTransactions"

        // Mobile Wallet Adapter error codes (spec, "Errors").
        const val AUTHORIZATION_FAILED = -1
        const val NOT_SIGNED = -3
        const val NOT_SUBMITTED = -4

        fun remoteCode(e: Throwable): Int? = generateSequence(e) { it.cause }
            .filterIsInstance<JsonRpc20Client.JsonRpc20RemoteException>().firstOrNull()?.code

        /** Words for the owner instead of an exception class name. */
        fun explain(e: Throwable): String = when (remoteCode(e)) {
            NOT_SIGNED -> "You declined in the wallet. Nothing was paid."
            AUTHORIZATION_FAILED -> "The wallet didn't authorize Tally. Try again, or sign in again from Settings."
            NOT_SUBMITTED -> "The wallet signed but couldn't send it. Nothing was paid; try again."
            null -> e.message?.takeUnless { it.startsWith("com.") || it.isBlank() } ?: "The wallet didn't answer. Open it once, then try again."
            else -> "The wallet couldn't finish the request (code ${remoteCode(e)}). Nothing was paid; try again."
        }
    }
}

/** Debug builds only: a local owner key for emulators without a wallet. Never shipped in release. */
class DemoOwner(private val seed: ByteArray) : Owner {
    override val label = "Demo owner (debug build, local key)"
    val address: String = Base58.encode(Ed25519.publicKey(seed))

    override suspend fun signIn() = SignedIn(address, "Demo owner", null)

    override suspend fun signAndSend(instructions: List<Instruction>, rpc: Rpc): String {
        val message = Message.compile(address, withContext(Dispatchers.IO) { rpc.getLatestBlockhash() }, instructions)
        val sig = Ed25519.sign(seed, message)
        return withContext(Dispatchers.IO) { rpc.sendTransaction(Message.serialize(message, listOf(sig))) }
    }

    override suspend fun cosign(wireTx: ByteArray): Cosigned = Cosigned.Signed(Signing.addSignature(wireTx, address) { Ed25519.sign(seed, it) })
}
