package app.tally.keys

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.tally.core.Base58
import app.tally.core.Ed25519
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The capsule's agent key: an Ed25519 seed encrypted with an AES-256-GCM key that lives in the
 * Android Keystore (TEE/StrongBox-backed where available, non-exportable). The seed is decrypted
 * only for the moment of signing. It can only call `pay_offer` against this one capsule, so a
 * compromise is bounded by the capsule's caps, categories, expiry and the owner's freeze switch.
 */
class AgentKey(private val context: Context, private val name: String = "agent") {
    private val alias = "tally-$name-wrap"
    private val prefs = context.getSharedPreferences("tally-keys", Context.MODE_PRIVATE)

    private fun wrappingKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(alias, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false) // the agent must act unattended
                .build(),
        )
        return gen.generateKey()
    }

    private fun seed(): ByteArray {
        val stored = prefs.getString(name, null)
        if (stored != null) {
            val blob = Base64.decode(stored, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(128, blob, 0, 12))
            return cipher.doFinal(blob, 12, blob.size - 12)
        }
        val fresh = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrappingKey())
        prefs.edit().putString(name, Base64.encodeToString(cipher.iv + cipher.doFinal(fresh), Base64.NO_WRAP)).apply()
        return fresh
    }

    val address: String get() = Base58.encode(Ed25519.publicKey(seed()))

    fun sign(message: ByteArray): ByteArray = seed().let { s -> Ed25519.sign(s, message).also { s.fill(0) } }

    /** Rotation: forget the old seed; the owner then calls `set_agent` with the new address. */
    fun rotate() {
        prefs.edit().remove(name).apply()
    }

    /** Raw seed, used only for the merchant menu-signing key, which signs headers in-app. */
    fun exportSeedForMenuSigning(): ByteArray = seed()
}
