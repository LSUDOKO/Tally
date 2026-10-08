package app.tally.core

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.math.ec.rfc8032.Ed25519 as Rfc8032

/** Ed25519 via BouncyCastle: merchant menu signing, the fallback agent key, PDA off-curve checks. */
object Ed25519 {
    fun publicKey(seed: ByteArray): ByteArray = Ed25519PrivateKeyParameters(seed, 0).generatePublicKey().encoded

    fun sign(seed: ByteArray, message: ByteArray): ByteArray =
        Ed25519Signer().run {
            init(true, Ed25519PrivateKeyParameters(seed, 0))
            update(message, 0, message.size)
            generateSignature()
        }

    fun verify(publicKey: ByteArray, message: ByteArray, signature: ByteArray): Boolean =
        Ed25519Signer().run {
            init(false, Ed25519PublicKeyParameters(publicKey, 0))
            update(message, 0, message.size)
            verifySignature(signature)
        }

    /** True when `point` decodes to a valid curve point. Program-derived addresses must not. */
    fun isOnCurve(point: ByteArray): Boolean = Rfc8032.validatePublicKeyPartial(point, 0)
}
