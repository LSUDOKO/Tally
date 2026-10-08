package app.tally.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cluster addresses written by `scripts/export-app-config.ts` into assets/cluster.json
 * (test USDC / mock SKR mints, SGT collection). Can be overridden in Settings.
 */
@Serializable
data class ClusterConfig(
    val cluster: String = "devnet",
    val rpcUrl: String = "https://api.devnet.solana.com",
    val usdcMint: String = "",
    val skrMint: String = "",
    val sgtGroup: String = "",
) {
    val isReady get() = usdcMint.isNotBlank()

    fun explorerTx(sig: String) = "https://explorer.solana.com/tx/$sig?cluster=$cluster"
    fun explorerAddress(a: String) = "https://explorer.solana.com/address/$a?cluster=$cluster"

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun load(context: Context): ClusterConfig = runCatching {
            context.assets.open("cluster.json").bufferedReader().use { json.decodeFromString<ClusterConfig>(it.readText()) }
        }.getOrDefault(ClusterConfig())
    }
}
