package app.tally.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import app.tally.R
import app.tally.ui.theme.TallyColors
import app.tally.wallet.InstalledWallet
import app.tally.wallet.Wallets
import kotlinx.coroutines.launch

/**
 * Sign in: the wallets on this phone with their own icons, what signing in does and doesn't do, and
 * where to get a wallet when there is none. Every wallet goes through Mobile Wallet Adapter's Sign
 * in with Solana, so the owner key never leaves the wallet.
 */
@Composable
fun ConnectSheet(s: Session, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = s.context
    val wallets = remember { Wallets.installed(context) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).navigationBarsPadding(), contentAlignment = Alignment.BottomCenter) {
            Column(
                Modifier.fillMaxWidth().glass(RoundedCornerShape(32.dp)).background(TallyColors.Surface.copy(alpha = 0.94f)).padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TallyMark(52.dp)
                Gap(14)
                Text("Sign in to Tally", style = MaterialTheme.typography.headlineSmall, color = TallyColors.Text)
                Gap(6)
                Text(
                    if (wallets.isEmpty()) "Tally signs in with a Solana wallet. Install one, then come back."
                    else "Choose the wallet that holds your money. Tally never sees its key.",
                    style = MaterialTheme.typography.bodyMedium, color = TallyColors.Muted, textAlign = TextAlign.Center,
                )
                Gap(18)
                if (wallets.isNotEmpty()) {
                    Label(if (wallets.size == 1) "On this phone" else "On this phone · you'll pick one next", TallyColors.Accent)
                    Gap(8)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        wallets.forEach { w ->
                            WalletRow(w.bitmap(), null, w.name, "Detected", Icons.Outlined.ChevronRight) {
                                onDismiss(); scope.launch { s.connect() }
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Wallets.Suggested.forEach { w ->
                            WalletRow(null, Icons.Outlined.AccountBalanceWallet, w.name, w.blurb, Icons.Outlined.Download) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, w.storeUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                    }
                }
                Gap(18)
                Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Promise("Sign in with Solana proves this address is yours. No payment, no fee.")
                    Promise("Your wallet asks before every transaction. Tally only holds a capped agent key.")
                    Promise("Sign out any time in Settings; your capsule stays on chain.")
                }
                Gap(18)
                Secondary("Not now", onDismiss)
            }
        }
    }
}

@Composable
private fun Promise(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Outlined.Verified, contentDescription = null, tint = TallyColors.Paid, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
    }
}

@Composable
private fun WalletRow(icon: ImageBitmap?, fallback: ImageVector?, name: String, detail: String, trailing: ImageVector, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp)).pressable(onClick).padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)))
        else IconTile(fallback ?: Icons.Outlined.AccountBalanceWallet, TallyColors.Accent, 40.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
        }
        Icon(trailing, contentDescription = null, tint = TallyColors.Faint)
    }
}

private fun InstalledWallet.bitmap(): ImageBitmap = icon.toBitmap(128, 128).asImageBitmap()

/** The wallet's own icon when it is on this phone, otherwise the mascot. */
@Composable
fun WalletAvatar(s: Session, size: Dp) {
    val icon = remember(s.walletName) { Wallets.installed(s.context).firstOrNull { it.name == s.walletName }?.bitmap() }
    if (icon != null) Image(icon, contentDescription = s.walletName, modifier = Modifier.size(size).clip(CircleShape))
    else Image(painterResource(R.drawable.mascot_avatar), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(size).clip(CircleShape))
}

/** Settings: who is signed in, with switch and sign-out. */
@Composable
fun AccountCard(s: Session) {
    val scope = rememberCoroutineScope()
    var confirm by remember { mutableStateOf(false) }
    Card {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WalletAvatar(s, 52.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(s.accountLabel ?: "Your wallet", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(s.walletName, short(s.ownerAddress)).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(8.dp))
                Chip(s.config.cluster, TallyColors.Accent)
            }
            Gap(14)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Secondary("Switch", { scope.launch { s.disconnect(); s.connect() } }, Modifier.weight(1f), icon = Icons.Outlined.SwapHoriz)
                Secondary("Sign out", { confirm = true }, Modifier.weight(1f), icon = Icons.Outlined.Logout, color = TallyColors.Blocked)
            }
        }
    }
    if (confirm) SignOutDialog(s) { confirm = false }
}

@Composable
private fun SignOutDialog(s: Session, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glass().background(TallyColors.Surface.copy(alpha = 0.94f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            WalletAvatar(s, 56.dp)
            Gap(12)
            Text("Sign out of Tally?", style = MaterialTheme.typography.headlineSmall, color = TallyColors.Text, textAlign = TextAlign.Center)
            Gap(8)
            Text(
                "${s.walletName ?: "Your wallet"} opens for a moment to revoke Tally's access. Your capsule and its " +
                    "money stay on chain, and your receipts come back when you sign in with this wallet again.",
                style = MaterialTheme.typography.bodyMedium, color = TallyColors.Muted, textAlign = TextAlign.Center,
            )
            Gap(18)
            Primary("Sign out", { onDismiss(); scope.launch { s.disconnect() } }, color = TallyColors.Blocked, icon = Icons.Outlined.Logout)
            Gap(8)
            Secondary("Cancel", onDismiss)
        }
    }
}

/**
 * Where this counter is, from Google Maps via Tally cloud. A name match is labelled as one; only a
 * place a maintainer confirmed says so. The chain, not Google, decides whether Tally pays.
 */
@Composable
fun PlaceCard(s: Session, merchant: String, onChainName: String) {
    androidx.compose.runtime.LaunchedEffect(merchant) { s.loadPlace(merchant) }
    if (merchant !in s.places) return
    val place = s.places[merchant] ?: return
    val context = s.context
    Card {
        Column {
            s.placePhotos[merchant]?.let { img ->
                Image(img, contentDescription = place.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(20.dp)))
                Gap(12)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconTile(Icons.Outlined.Place, TallyColors.Accent, 38.dp)
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(place.name, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(place.address, style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Gap(10)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                place.rating?.let { Chip("★ ${"%.1f".format(it)}" + (place.ratings?.let { n -> " · $n" } ?: ""), TallyColors.Accent) }
                if (place.pinned) Chip("Confirmed location", TallyColors.Paid, Icons.Outlined.Verified)
                else Chip("Google match for the name", TallyColors.NeedsYou)
            }
            Gap(8)
            Text(
                if (place.pinned) "Confirmed for this counter on Tally cloud. Tally still pays only on the merchant's on-chain key and bond."
                else "Best Google Maps match for \"$onChainName\". It may not be this counter: Tally pays on the merchant's on-chain key and bond, never on this.",
                style = MaterialTheme.typography.bodySmall, color = TallyColors.Faint,
            )
            place.mapsUri?.let { uri ->
                Gap(10)
                Secondary("Open in Maps", { context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }, icon = Icons.Outlined.Map)
            }
        }
    }
}
