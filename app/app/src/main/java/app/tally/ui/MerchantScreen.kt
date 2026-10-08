package app.tally.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tally.core.Accounts
import app.tally.core.usdc
import app.tally.graph
import app.tally.merchant.Catalog
import app.tally.merchant.MerchantTerminal
import app.tally.merchant.QrCode
import app.tally.ui.theme.TallyColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Merchant terminal mode: register with an SKR bond, then broadcast a menu re-signed every ~20 s. */
@Composable
fun MerchantScreen(s: Session) {
    val ctx = LocalContext.current
    val g = ctx.graph
    val terminal = remember { MerchantTerminal(g.merchantMenuKey) }
    val scope = rememberCoroutineScope()
    var reg by remember { mutableStateOf<Accounts.Merchant?>(null) }
    var name by remember { mutableStateOf("Blue Door Coffee") }
    var bond by remember { mutableStateOf("5000") }
    var live by remember { mutableStateOf(false) }
    var qr by remember { mutableStateOf<String?>(null) }
    var nonce by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var busyPow by remember { mutableStateOf(false) }

    suspend fun reload() {
        val auth = s.ownerAddress ?: return
        reg = withContext(Dispatchers.IO) { runCatching { terminal.load(g.rpc, auth) }.getOrNull() }
    }
    LaunchedEffect(s.ownerAddress) { reload() }
    LaunchedEffect(live) {
        while (live) {
            val auth = s.ownerAddress ?: break
            val slot = withContext(Dispatchers.IO) { runCatching { g.rpc.getSlot() }.getOrNull() }
            if (slot != null) qr = terminal.publish(terminal.merchantAddress(auth), slot, nonce++, Catalog.DEFAULT).second
            delay(20_000)
        }
    }
    DisposableEffect(Unit) { onDispose { terminal.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Merchant terminal", onBack = { live = false; s.screen = Screen.Home })
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (s.ownerAddress == null) {
                Body("Connect the merchant's wallet first (Home → Connect).", TallyColors.NeedsYou)
                return@Column
            }
            val r = reg
            if (r == null) {
                Body("Register once: your wallet signs, your SKR bond makes agents trust your menus.", TallyColors.Muted)
                OutlinedTextField(name, { name = it.take(32) }, Modifier.fillMaxWidth(), label = { Text("Shop name") })
                OutlinedTextField(bond, { bond = it.filter(Char::isDigit) }, Modifier.fillMaxWidth(), label = { Text("SKR bond") })
                Primary("Register and bond SKR", {
                    scope.launch {
                        s.busy = "Waiting for wallet"
                        runCatching {
                            terminal.register(s.owner, g.rpc, s.ownerAddress!!, s.config.usdcMint, s.config.skrMint, name, (bond.toLongOrNull() ?: 0) * 1_000_000)
                        }.onFailure { s.toast = it.message }
                        s.busy = null
                        reload()
                    }
                })
            } else {
                Card {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(r.name, color = TallyColors.Text, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Mono("bond ${usdc(r.skrBonded).substringBefore('.')} SKR · slashed ${usdc(r.slashedTotal).substringBefore('.')} · payout ${short(r.payout)}")
                        Mono("menu key ${short(r.signingKey)} ${if (r.signingKey == terminal.signingKey) "(this device)" else "(other device!)"}")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Broadcast menu (QR + NFC)", color = TallyColors.Text, modifier = Modifier.weight(1f))
                    Switch(live, { live = it; if (!it) terminal.stop() })
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Congestion mode (proof of work)", color = TallyColors.Text)
                        Text("Agents must solve a small hash puzzle per payment", color = TallyColors.Muted, fontSize = 12.sp)
                    }
                    Switch(r.powDifficulty > 0, { on ->
                        scope.launch {
                            busyPow = true
                            runCatching { terminal.setPow(s.owner, g.rpc, s.ownerAddress!!, if (on) 8 else 0) }.onFailure { s.toast = it.message }
                            busyPow = false
                            reload()
                        }
                    }, enabled = !busyPow)
                }
                qr?.takeIf { live }?.let { text ->
                    val bmp = remember(text) { QrCode.render(text) }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(androidx.compose.ui.graphics.Color.White, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Image(bmp.asImageBitmap(), contentDescription = "Signed menu QR", modifier = Modifier.fillMaxSize())
                    }
                    Mono("re-signs every ~20 s · window 120 slots · ${Catalog.DEFAULT.size} items · ${text.length} chars")
                }
            }
            Gap(24)
        }
    }
}
