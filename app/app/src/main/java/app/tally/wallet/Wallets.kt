package app.tally.wallet

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri

/** A Mobile Wallet Adapter wallet installed on this phone, with its own name and launcher icon. */
class InstalledWallet(val packageName: String, val name: String, val icon: Drawable)

/** A wallet the user could install, for a phone that has none yet. */
class SuggestedWallet(val name: String, val packageName: String, val blurb: String) {
    val storeUri: Uri get() = Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
}

object Wallets {
    val Suggested = listOf(
        SuggestedWallet("Solflare", "com.solflare.mobile", "Self-custody, works with Seeker"),
        SuggestedWallet("Phantom", "app.phantom", "Popular multi-chain wallet"),
        SuggestedWallet("Backpack", "app.backpack.mobile", "Wallet and exchange in one"),
    )

    /** Every app that answers a Mobile Wallet Adapter association, i.e. every wallet Tally can sign in with. */
    fun installed(context: Context): List<InstalledWallet> {
        val pm = context.packageManager
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("solana-wallet:/v1/associate/local?association=probe&port=1"))
            .addCategory(Intent.CATEGORY_BROWSABLE)
        return pm.queryIntentActivities(probe, 0)
            .distinctBy { it.activityInfo.packageName }
            .map { InstalledWallet(it.activityInfo.packageName, it.activityInfo.applicationInfo.loadLabel(pm).toString(), it.activityInfo.applicationInfo.loadIcon(pm)) }
            .sortedBy { it.name }
    }

    /** The wallet's name from the endpoint it returned, or from the only wallet on the phone. */
    fun nameFor(context: Context, walletUriBase: Uri?): String {
        val host = walletUriBase?.host.orEmpty()
        Suggested.firstOrNull { host.contains(it.name, ignoreCase = true) }?.let { return it.name }
        return installed(context).singleOrNull()?.name ?: "your wallet"
    }
}
