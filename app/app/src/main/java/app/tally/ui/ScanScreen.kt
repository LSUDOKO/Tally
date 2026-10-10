package app.tally.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Nfc
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import app.tally.BuildConfig
import app.tally.core.Payload
import app.tally.core.Proposal
import app.tally.core.SignedPayload
import app.tally.ui.theme.TallyColors
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Scan the counter QR with CameraX + ML Kit, or tap the merchant phone (NFC reader mode is on while open). */
@Composable
fun ScanScreen(s: Session, nfcStatus: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    var handled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }

    val onPayload: (SignedPayload) -> Unit = { p ->
        if (!handled) {
            handled = true
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            scope.launch { s.onPayload(p) }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("Scan or tap", onBack = { s.screen = Screen.Home })
        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().aspectRatio(1f).glass(RoundedCornerShape(32.dp)).padding(6.dp).clip(RoundedCornerShape(28.dp)).background(TallyColors.Ink)) {
                if (granted) CameraQr { text ->
                    if (Proposal.isProposal(text)) {
                        runCatching { Proposal.fromQr(text) }.onSuccess { pr -> if (!handled) { handled = true; haptics.performHapticFeedback(HapticFeedbackType.Confirm); scope.launch { s.onProposal(pr) } } }
                    } else {
                        runCatching { Payload.fromQr(text) }.onSuccess(onPayload)
                    }
                }
                else Body("Camera permission is needed to scan. You can still tap the merchant phone.", TallyColors.Muted)
                ScanFrame()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mascot(Mood.HoldingPhone, Modifier.size(width = 90.dp, height = 130.dp), height = 130.dp, glow = false)
                Column(Modifier.weight(1f).padding(start = 8.dp)) {
                    Text("Point at the counter's QR", style = MaterialTheme.typography.titleMedium, color = TallyColors.Text)
                    Text("or hold your phone against the merchant's phone.", style = MaterialTheme.typography.bodySmall, color = TallyColors.Muted)
                    Gap(8)
                    Chip(nfcStatus, TallyColors.Accent, Icons.Outlined.Nfc)
                }
            }
            s.store.lastPayloadQr?.let { last ->
                Secondary("Use last scanned menu (demo)", { runCatching { Payload.fromQr(last) }.onSuccess(onPayload) }, icon = Icons.Outlined.History)
            }
            // Debug builds: one phone can be both counter and customer (Merchant mode broadcasts here).
            if (BuildConfig.DEMO_OWNER_ALLOWED && s.ownerAddress != null) {
                Secondary("Pay this phone's merchant menu (demo)", { scope.launch { s.payOwnMerchant() } }, icon = Icons.Outlined.Storefront)
            }
        }
    }
}

/** Cobalt corner brackets and a scan line sweeping the viewfinder. */
@Composable
private fun ScanFrame() {
    val sweep by rememberInfiniteTransition(label = "scan").animateFloat(0.12f, 0.88f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "line")
    Canvas(Modifier.fillMaxSize()) {
        val inset = size.minDimension * 0.14f
        val len = size.minDimension * 0.14f
        val stroke = 5.dp.toPx()
        val c = TallyColors.BrandBright
        listOf(Offset(inset, inset) to Offset(1f, 1f), Offset(size.width - inset, inset) to Offset(-1f, 1f), Offset(inset, size.height - inset) to Offset(1f, -1f), Offset(size.width - inset, size.height - inset) to Offset(-1f, -1f)).forEach { (o, d) ->
            drawLine(c, o, Offset(o.x + d.x * len, o.y), stroke, StrokeCap.Round)
            drawLine(c, o, Offset(o.x, o.y + d.y * len), stroke, StrokeCap.Round)
        }
        val y = size.height * sweep
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = 0.35f), Color.Transparent), startY = y - 40f, endY = y + 40f), topLeft = Offset(inset, y - 40f), size = Size(size.width - 2 * inset, 80f))
        drawLine(c, Offset(inset, y), Offset(size.width - inset, y), 2.dp.toPx(), StrokeCap.Round)
    }
}

@Composable
private fun CameraQr(onText: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()) }
    DisposableEffect(Unit) { onDispose { executor.shutdown(); scanner.close() } }
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { c ->
            val view = PreviewView(c)
            val providerFuture = ProcessCameraProvider.getInstance(c)
            providerFuture.addListener({
                val provider = providerFuture.get()
                val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
                val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                analysis.setAnalyzer(executor) { proxy ->
                    val media = proxy.image
                    if (media == null) {
                        proxy.close()
                    } else {
                        scanner.process(InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees))
                            .addOnSuccessListener { codes -> codes.firstNotNullOfOrNull { it.rawValue }?.takeIf { it.startsWith(Payload.QR_PREFIX) || Proposal.isProposal(it) }?.let(onText) }
                            .addOnCompleteListener { proxy.close() }
                    }
                }
                provider.unbindAll()
                provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
            }, ContextCompat.getMainExecutor(ctx))
            view
        },
    )
}
