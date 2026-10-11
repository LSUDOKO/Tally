package app.tally.ui

import androidx.compose.foundation.Image
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import app.tally.ui.theme.TallyBrushes
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
    var catalog by remember { mutableStateOf(g.store.catalog) }
    var editing by remember { mutableStateOf(false) }

    suspend fun reload() {
        val auth = s.ownerAddress ?: return
        reg = withContext(Dispatchers.IO) { runCatching { terminal.load(g.rpc, auth) }.getOrNull() }
    }
    LaunchedEffect(s.ownerAddress) { reload() }
    LaunchedEffect(live) {
        while (live) {
            val auth = s.ownerAddress ?: break
            val slot = withContext(Dispatchers.IO) { runCatching { g.rpc.getSlot() }.getOrNull() }
            if (slot != null) qr = terminal.publish(terminal.merchantAddress(auth), slot, nonce++, catalog).second
            delay(20_000)
        }
    }
    DisposableEffect(Unit) { onDispose { terminal.stop() } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        TopBar("Merchant terminal", onBack = { live = false; s.screen = Screen.Home })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (s.ownerAddress == null) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    HeroTitle("Your counter,", "ready when you are.")
                    Mascot(Mood.Laptop, Modifier.fillMaxWidth(), height = 240.dp)
                    Card {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Body("Connect the merchant wallet to register your counter and publish signed menus.", TallyColors.Muted)
                            Primary("Connect wallet", { scope.launch { s.connect() } }, icon = Icons.Outlined.AccountBalanceWallet)
                        }
                    }
                }
                return@Column
            }
            val r = reg
            if (r == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        HeroTitle("Open your", "counter.", align = TextAlign.Start)
                        Gap(6)
                        Body("Register once. Your wallet signs, and your SKR bond is what lets agents trust your menus.", TallyColors.Muted)
                    }
                    Mascot(Mood.Laptop, Modifier.size(width = 130.dp, height = 140.dp), height = 140.dp)
                }
                GlassField(name, { name = it.take(32) }, "Shop name", singleLine = true, leading = Icons.Outlined.Storefront)
                GlassField(bond, { bond = it.filter(Char::isDigit) }, "SKR bond", singleLine = true, leading = Icons.Outlined.Savings)
                Primary("Register and bond SKR", {
                    scope.launch {
                        s.busy = "Waiting for wallet"
                        runCatching {
                            terminal.register(s.owner, g.rpc, s.ownerAddress!!, s.config.usdcMint, s.config.skrMint, name, (bond.toLongOrNull() ?: 0) * 1_000_000)
                        }.onFailure { s.toast = it.message }
                        s.busy = null
                        reload()
                    }
                }, icon = Icons.Outlined.AccountBalanceWallet)
            } else {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), TallyColors.Brand)
                            .padding(20.dp),
                    ) {
                        Label("Your counter")
                        Gap(4)
                        Text(r.name, style = MaterialTheme.typography.displaySmall, color = TallyColors.Text, modifier = Modifier.fillMaxWidth(0.62f))
                        Gap(14)
                        Row(Modifier.fillMaxWidth(0.66f), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            Stat("Bond", "${usdc(r.skrBonded).substringBefore('.')} SKR", TallyColors.Text)
                            Stat("Slashed", usdc(r.slashedTotal).substringBefore('.'), if (r.slashedTotal > 0) TallyColors.Blocked else TallyColors.Text)
                        }
                        Gap(10)
                        Text("payout ${short(r.payout)} · menu key ${short(r.signingKey)}${if (r.signingKey == terminal.signingKey) " (this phone)" else " (other phone)"}", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted, modifier = Modifier.fillMaxWidth(0.62f))
                    }
                    Mascot(Mood.Laptop, Modifier.align(Alignment.BottomEnd).size(width = 120.dp, height = 140.dp), height = 140.dp, glow = false)
                }
                if (r.signingKey != terminal.signingKey) {
                    // Menus signed here would fail verification until the registry points at this phone's key.
                    Card(accent = TallyColors.NeedsYou) {
                        Column {
                            Body("This phone's menu key isn't the registered one (new phone or reinstall), so its menus won't verify.", TallyColors.Text)
                            Gap(10)
                            Primary("Use this phone's menu key", {
                                scope.launch {
                                    s.busy = "Waiting for wallet"
                                    runCatching { terminal.adoptThisDevice(s.owner, g.rpc, s.ownerAddress!!, r.signingKey) }.onFailure { s.toast = it.message }
                                    s.busy = null
                                    reload()
                                }
                            }, color = TallyColors.NeedsYou, icon = Icons.Outlined.Key)
                        }
                    }
                }
                ToggleRow(Icons.Outlined.Podcasts, "Broadcast menu", "Show a live QR and answer NFC taps; re-signed every ~20 s.", live) { live = it; if (!it) terminal.stop() }
                ToggleRow(Icons.Outlined.Memory, "Congestion mode", "Agents solve a small hash puzzle per payment (proof of work).", r.powDifficulty > 0, TallyColors.NeedsYou, enabled = !busyPow) { on ->
                    scope.launch {
                        busyPow = true
                        runCatching { terminal.setPow(s.owner, g.rpc, s.ownerAddress!!, if (on) 8 else 0) }.onFailure { s.toast = it.message }
                        busyPow = false
                        reload()
                    }
                }
                qr?.takeIf { live }?.let { text ->
                    val bmp = remember(text) { QrCode.render(text) }
                    val ring by rememberInfiniteTransition(label = "live").animateFloat(0.25f, 0.8f, infiniteRepeatable(tween(1300), RepeatMode.Reverse), label = "ring")
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.White)
                            .border(4.dp, TallyColors.BrandBright.copy(alpha = ring), RoundedCornerShape(28.dp)).padding(18.dp),
                    ) {
                        Image(bmp.asImageBitmap(), contentDescription = "Signed menu QR", modifier = Modifier.fillMaxSize())
                    }
                    Chip("Live · re-signs every ~20 s · ${catalog.size} items", TallyColors.Paid, Icons.Outlined.Podcasts)
                }
                // Any Solana wallet can still pay this counter the plain way; Tally's signed menu stays the protected path.
                var solanaPay by remember { mutableStateOf(false) }
                ToggleRow(Icons.Outlined.QrCode2, "Solana Pay QR", "A standard transfer request for ${s.config.usdcMint.ifBlank { "USDC" }.take(4)}… USDC that any wallet can scan. No menu, no rules: a tip jar.", solanaPay) { solanaPay = it }
                if (solanaPay) {
                    val url = remember(r.payout) { "solana:${r.payout}?spl-token=${s.config.usdcMint}&label=${java.net.URLEncoder.encode(r.name, "UTF-8")}&message=${java.net.URLEncoder.encode("Tally counter", "UTF-8")}" }
                    val bmp = remember(url) { QrCode.render(url) }
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp)).background(Color.White).padding(18.dp)) {
                        Image(bmp.asImageBitmap(), contentDescription = "Solana Pay QR", modifier = Modifier.fillMaxSize())
                    }
                    Mono(url.substringBefore('?'), TallyColors.Faint)
                }
                Secondary(if (editing) "Done editing menu" else "Edit menu (${catalog.size} items)", { editing = !editing; if (!editing) g.store.catalog = catalog }, icon = Icons.Outlined.EditNote)
                if (editing) {
                    catalog.forEachIndexed { i, e ->
                        Card {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconTile(categoryIcon(e.category), TallyColors.Accent, 40.dp)
                                    GlassField(e.name, { v -> catalog = catalog.toMutableList().also { it[i] = e.copy(name = v.take(40)) } }, "Item", Modifier.padding(start = 10.dp), singleLine = true)
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    GlassField(usdc(e.priceMicro), { v -> v.toDoubleOrNull()?.let { d -> catalog = catalog.toMutableList().also { it[i] = e.copy(priceMicro = (d * 1_000_000).toLong()) } } }, "Price (USDC)", Modifier.weight(1f), singleLine = true)
                                    GlassField("${e.category}", { v -> v.toIntOrNull()?.takeIf { it in 0..255 }?.let { c -> catalog = catalog.toMutableList().also { it[i] = e.copy(category = c) } } }, "Category", Modifier.weight(1f), singleLine = true)
                                }
                                Box(Modifier.clip(RoundedCornerShape(50)).clickable { catalog = catalog.filterIndexed { k, _ -> k != i } }) { Chip("Remove", TallyColors.Blocked, Icons.Outlined.Delete) }
                            }
                        }
                    }
                    Secondary("Add item", { catalog = catalog + Catalog.Entry("New item", 3, 4_000_000) }, icon = Icons.Outlined.Add)
                    Body("Category floors (cheapest per category) are computed and signed for you. Codes: 0 food · 1 drinks · 2 snacks · 3 coffee · 4 transit · 5 groceries.", TallyColors.Muted)
                }
            }
            Gap(24)
        }
    }
}
