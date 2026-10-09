package app.tally

import android.app.Application
import android.content.Context
import app.tally.agent.GroqAgent
import app.tally.chain.Http
import app.tally.chain.Submitter
import app.tally.core.Rpc
import app.tally.data.ClusterConfig
import app.tally.data.Store
import app.tally.keys.AgentKey

/** Process-wide dependencies (no DI framework needed at this size). */
class AppGraph(context: Context) {
    val config = ClusterConfig.load(context)
    val store = Store(context)
    val agentKey = AgentKey(context, "agent")
    val merchantMenuKey = AgentKey(context, "merchant-menu")
    val demoOwnerKey = AgentKey(context, "demo-owner")
    val agent = GroqAgent(
        apiKey = { store.groqKey ?: BuildConfig.GROQ_API_KEY },
        tryConsumeCall = { store.tryConsumeModelCall(GroqAgent.MAX_CALLS_PER_DAY) },
    )

    val rpcUrl: String
        get() = store.rpcOverride?.takeIf { it.isNotBlank() }
            ?: BuildConfig.RPC_URL.takeIf { it != "https://api.devnet.solana.com" }
            ?: config.rpcUrl

    val rpc: Rpc get() = Http.rpc(rpcUrl)
    val submitter: Submitter get() = Submitter(rpc)
}

class TallyApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val Context.graph: AppGraph get() = (applicationContext as TallyApp).graph
