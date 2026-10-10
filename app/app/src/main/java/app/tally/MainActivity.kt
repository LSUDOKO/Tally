package app.tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import app.tally.ui.BottomBar
import app.tally.ui.BrandBackground
import app.tally.ui.Gap
import app.tally.ui.Mascot
import app.tally.ui.Mood
import app.tally.ui.Primary
import app.tally.ui.TabScreens
import app.tally.ui.glass
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import app.tally.ui.theme.TallyColors
import app.tally.ui.theme.TallyTheme
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
        session = Session(this, MwaOwner(sender, graph.config.cluster))
        nfc = NfcReader(this)
        setContent { TallyTheme { Root(session, nfc) } }
    }
}

@Composable
private fun Root(s: Session, nfc: NfcReader) {
    val scope = rememberCoroutineScope()
    var nfcStatus by remember { mutableStateOf(if (!nfc.available) "No NFC on this device" else if (!nfc.enabled) "NFC is off" else "NFC ready: tap the merchant phone") }
    BackHandler(enabled = s.screen != Screen.Home) { s.screen = Screen.Home }
    if (s.screen == Screen.Scan) {
        DisposableEffect(Unit) {
            nfc.start(onPayload = { p -> scope.launch { s.onPayload(p) } }, onError = { nfcStatus = it })
            onDispose { nfc.stop() }
        }
    }
    BrandBackground {
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
                    }
                }
            }
            if (s.screen in TabScreens && s.ownerAddress != null) {
                BottomBar(s.screen, scanEnabled = s.capsule != null) { s.screen = it }
            }
        }
        s.busy?.let { BusyOverlay(it) }
        s.toast?.let { msg -> Notice(msg) { s.toast = null } }
    }
}

/** Work in flight: the mascot thinks on a frosted panel while the wallet or the chain answers. */
@Composable
private fun BusyOverlay(label: String) {
    Box(Modifier.fillMaxSize().background(TallyColors.Ink.copy(alpha = 0.55f)).pointerInput(Unit) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.padding(32.dp).glass().padding(horizontal = 28.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Mascot(Mood.Thinking, Modifier.size(width = 150.dp, height = 150.dp), height = 150.dp)
            Gap(8)
            Text(label, style = MaterialTheme.typography.titleMedium, color = TallyColors.Text, textAlign = TextAlign.Center)
            Gap(12)
            LinearProgressIndicator(Modifier.width(140.dp).clip(RoundedCornerShape(50)), color = TallyColors.BrandBright, trackColor = TallyColors.GlassHigh)
        }
    }
}

/** A message for the user; the mascot reacts to whether it is good news. */
@Composable
private fun Notice(message: String, onDismiss: () -> Unit) {
    val good = listOf("enabled", "proven", "claimed", "rule-based agent", "Paid").any { message.contains(it, ignoreCase = true) }
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
