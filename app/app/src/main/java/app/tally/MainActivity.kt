package app.tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import app.tally.ui.BottomBar
import app.tally.ui.Backdrop
import app.tally.ui.BrandBackground
import app.tally.ui.Gap
import app.tally.ui.Mascot
import app.tally.ui.Mood
import app.tally.ui.Primary
import app.tally.ui.Secondary
import app.tally.ui.TabScreens
import app.tally.ui.glass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.tally.nfc.NfcReader
import app.tally.ui.CheckoutScreen
import app.tally.ui.HomeScreen
import app.tally.ui.MerchantScreen
import app.tally.ui.NewCapsuleScreen
import app.tally.ui.ReceiptsScreen
import app.tally.ui.RedTeamScreen
import app.tally.ui.ScanScreen
import app.tally.ui.Screen
import app.tally.ui.Session
import app.tally.ui.SettingsScreen
import app.tally.ui.RewardsScreen
import app.tally.ui.LevelUpDialog
import app.tally.ui.theme.TallyColors
import app.tally.ui.theme.TallyTheme
import app.tally.ui.theme.TallyFonts
import app.tally.wallet.MwaOwner
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var session: Session
    private lateinit var nfc: NfcReader

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // MWA's sender must be created before the activity is STARTED.
        val sender = ActivityResultSender(this)
        session = Session(this, MwaOwner(sender, graph.config.cluster, graph.store))
        nfc = NfcReader(this)
        handle(intent)
        setContent { TallyTheme { Root(session, nfc) } }
    }

    override fun onStart() {
        super.onStart()
        // Every return to the front asks for the phone's lock, when the owner turned that on.
        if (session.store.appLock) session.locked = true
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    /** Notification and widget Freeze buttons open the freeze confirmation. */
    private fun handle(intent: android.content.Intent?) {
        if (intent?.action == app.tally.notify.Notifier.ACTION_FREEZE) session.askFreeze = true
        // tally://checkout?p=... from an outside agent: pre-fill checkout; the owner still decides.
        val link = intent?.data?.takeIf { intent.action == android.content.Intent.ACTION_VIEW && it.scheme == "tally" }
        if (link != null) {
            val pr = runCatching { app.tally.core.Proposal.fromLink(link.toString()) }
            pr.onSuccess { p -> lifecycleScope.launch { session.onProposal(p) } }
                .onFailure { session.toast = "That Tally link isn't a valid proposal: ${it.message}" }
        }
    }
}

@Composable
private fun Root(s: Session, nfc: NfcReader) {
    val scope = rememberCoroutineScope()
    var nfcStatus by remember { mutableStateOf(if (!nfc.available) "No NFC on this device" else if (!nfc.enabled) "NFC is off" else "NFC ready: tap the merchant phone") }
    BackHandler(enabled = s.screen != Screen.Home) { s.screen = Screen.Home }
    // Ask once for notifications after connecting, so every agent payment can be checked ("Was this you?").
    val notifyPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    androidx.compose.runtime.LaunchedEffect(s.ownerAddress) {
        if (s.ownerAddress != null && android.os.Build.VERSION.SDK_INT >= 33 && !app.tally.notify.Notifier.canPost(s.context)) {
            notifyPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    if (s.screen == Screen.Scan) {
        DisposableEffect(Unit) {
            nfc.start(onPayload = { p -> scope.launch { s.onPayload(p) } }, onError = { nfcStatus = it })
            onDispose { nfc.stop() }
        }
    }
    // Light follows the moment: brand dawn at home, a focused night for paying, dusk for reading.
    val backdrop = when (s.screen) {
        Screen.Home -> Backdrop.Dawn
        Screen.Scan, Screen.Checkout, Screen.NewCapsule, Screen.Merchant -> Backdrop.Night
        Screen.Receipts, Screen.RedTeam, Screen.Settings, Screen.Rewards -> Backdrop.Dusk
    }
    BrandBackground(backdrop) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            AnimatedContent(
                targetState = s.screen,
                transitionSpec = {
                    val forward = targetState !in TabScreens || initialState in TabScreens
                    (fadeIn(tween(260)) + slideInVertically(tween(320)) { if (forward) it / 14 else -it / 14 })
                        .togetherWith(fadeOut(tween(180)))
                },
                modifier = Modifier.weight(1f),
                label = "screen",
            ) { screen ->
                Box(Modifier.fillMaxSize()) {
                    when (screen) {
                        Screen.Home -> HomeScreen(s)
                        Screen.NewCapsule -> NewCapsuleScreen(s)
                        Screen.Scan -> ScanScreen(s, nfcStatus)
                        Screen.Checkout -> CheckoutScreen(s)
                        Screen.Receipts -> ReceiptsScreen(s)
                        Screen.RedTeam -> RedTeamScreen(s)
                        Screen.Merchant -> MerchantScreen(s)
                        Screen.Settings -> SettingsScreen(s)
                        Screen.Rewards -> RewardsScreen(s)
                    }
                }
            }
            if (s.screen in TabScreens && s.ownerAddress != null) {
                BottomBar(s.screen, scanEnabled = s.capsule != null || BuildConfig.APPLICATION_ID.endsWith(".qa")) { s.screen = it }
            }
        }
        s.busy?.let { BusyOverlay(it) }
        if (s.locked) LockOverlay(s)
        s.levelUp?.let { lvl -> LevelUpDialog(lvl) { s.levelUp = null } }
        if (s.askFreeze) FreezeDialog(s) { s.askFreeze = false }
        s.toast?.let { msg -> Notice(msg) { s.toast = null } }
    }
}

/** The app behind the phone's lock: nothing shows until it is passed. */
@Composable
private fun LockOverlay(s: Session) {
    val scope = rememberCoroutineScope()
    androidx.compose.runtime.LaunchedEffect(Unit) { s.unlock() }
    Box(Modifier.fillMaxSize().background(TallyColors.Ink).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            app.tally.ui.TallyMark(64.dp)
            Spacer(Modifier.height(18.dp))
            Text("Tally is locked", style = MaterialTheme.typography.headlineSmall, color = TallyColors.Text)
            Spacer(Modifier.height(18.dp))
            app.tally.ui.Secondary("Unlock", { scope.launch { s.unlock() } }, Modifier.width(180.dp))
        }
    }
}

/** An opaque, dedicated work state: the previous route is fully masked. */
@Composable
private fun BusyOverlay(label: String) {
    val progress by rememberInfiniteTransition(label = "work progress").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1450), RepeatMode.Restart), label = "progress"
    )
    Box(Modifier.fillMaxSize().background(TallyColors.Ink).pointerInput(Unit) {
        awaitPointerEventScope { while (true) awaitPointerEvent().changes.forEach { it.consume() } }
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                Brush.radialGradient(listOf(TallyColors.Brand.copy(alpha = 0.34f), Color.Transparent), center = Offset(size.width * 0.5f, size.height * 0.43f), radius = size.width * 0.72f),
                radius = size.width * 0.72f,
                center = Offset(size.width * 0.5f, size.height * 0.43f),
            )
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.Thinking, Modifier.size(width = 184.dp, height = 200.dp), height = 200.dp, glow = false)
            Gap(18)
            Text("Tally is on it.", fontFamily = TallyFonts.Display, style = MaterialTheme.typography.displayMedium, color = TallyColors.Text, textAlign = TextAlign.Center)
            Gap(8)
            Text(label, style = MaterialTheme.typography.bodyLarge, color = TallyColors.Muted, textAlign = TextAlign.Center)
            Gap(26)
            Canvas(Modifier.width(216.dp).height(5.dp)) {
                drawRoundRect(TallyColors.GlassHigh, cornerRadius = CornerRadius(size.height / 2))
                val segment = size.width * 0.34f
                drawRoundRect(
                    Brush.horizontalGradient(listOf(TallyColors.BrandBright, Color.White.copy(alpha = 0.92f))),
                    topLeft = Offset(progress * (size.width - segment), 0f),
                    size = Size(segment, size.height),
                    cornerRadius = CornerRadius(size.height / 2),
                )
            }
            Gap(14)
            Text("Verifying the details", style = MaterialTheme.typography.labelMedium, color = TallyColors.Faint)
        }
    }
}

/** A message for the user; the mascot reacts to whether it is good news. */
@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    val good = listOf("enabled", "proven", "claimed", "rule-based agent", "Paid", "Signed in").any { message.contains(it, ignoreCase = true) }
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glass().background(TallyColors.Surface.copy(alpha = 0.92f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(if (good) Mood.ThumbsUp else Mood.Confused, Modifier.size(width = 150.dp, height = 140.dp), height = 140.dp)
            Gap(8)
            Text(message, style = MaterialTheme.typography.bodyLarge, color = TallyColors.Text, textAlign = TextAlign.Center)
            Gap(18)
            Primary("Got it", onDismiss)
        }
    }
}

/** "Wasn't you?": freezing stops every agent payment until the owner unfreezes. */
@Composable
private fun FreezeDialog(s: Session, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    Dialog(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().glass().background(TallyColors.Surface.copy(alpha = 0.94f)).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.WithShield, Modifier.fillMaxWidth(), height = 170.dp, shakeKey = Unit)
            Text("Freeze your capsule?", style = MaterialTheme.typography.displaySmall, color = TallyColors.Text, textAlign = TextAlign.Center)
            Gap(8)
            Text(
                if (s.capsule?.frozen == true) "It's already frozen. Nothing can be spent until you unfreeze it in Settings."
                else "Your agent won't be able to pay anywhere until you unfreeze it. Your wallet signs the freeze.",
                style = MaterialTheme.typography.bodyMedium, color = TallyColors.Muted, textAlign = TextAlign.Center,
            )
            Gap(18)
            if (s.capsule?.frozen != true) {
                Primary("Freeze in wallet", { onDismiss(); scope.launch { s.setFrozen(true) } }, color = TallyColors.Blocked)
                Gap(8)
            }
            Secondary(if (s.capsule?.frozen == true) "OK" else "It was me", onDismiss)
        }
    }
}
