package app.tally

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    Surface(Modifier.fillMaxSize(), color = TallyColors.Ink) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            when (s.screen) {
                Screen.Home -> HomeScreen(s)
                Screen.NewCapsule -> NewCapsuleScreen(s)
                Screen.Scan -> ScanScreen(s, nfcStatus)
                Screen.Checkout -> CheckoutScreen(s)
                Screen.Receipts -> ReceiptsScreen(s)
                Screen.RedTeam -> RedTeamScreen(s)
                Screen.Merchant -> MerchantScreen(s)
                Screen.Settings -> SettingsScreen(s)
            }
            s.busy?.let { label ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Surface(color = TallyColors.SurfaceHigh, shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = TallyColors.Accent)
                            Text(label, color = TallyColors.Text, modifier = Modifier.padding(top = 12.dp))
                        }
                    }
                }
            }
            s.toast?.let { msg ->
                AlertDialog(
                    onDismissRequest = { s.toast = null },
                    confirmButton = { TextButton({ s.toast = null }) { Text("OK") } },
                    text = { Text(msg) },
                    containerColor = TallyColors.Surface,
                )
            }
        }
    }
}
