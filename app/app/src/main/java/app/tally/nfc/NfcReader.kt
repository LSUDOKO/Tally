package app.tally.nfc

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import app.tally.core.Apdu
import app.tally.core.Payload
import app.tally.core.SignedPayload

/** Customer side of the NFC tap: reader mode + IsoDep APDU exchange with the merchant phone. */
class NfcReader(private val activity: Activity) {
    private val adapter: NfcAdapter? = NfcAdapter.getDefaultAdapter(activity)

    val available get() = adapter != null
    val enabled get() = adapter?.isEnabled == true

    fun start(onPayload: (SignedPayload) -> Unit, onError: (String) -> Unit) {
        adapter?.enableReaderMode(
            activity,
            { tag: Tag ->
                val iso = IsoDep.get(tag) ?: return@enableReaderMode onError("Not a Tally terminal")
                runCatching {
                    iso.connect()
                    iso.timeout = 2_000
                    iso.use { Payload.decode(Apdu.readAll { apdu -> it.transceive(apdu) }) }
                }.onSuccess(onPayload).onFailure { onError(it.message ?: "NFC read failed") }
            },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK or NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
            null,
        )
    }

    fun stop() {
        adapter?.disableReaderMode(activity)
    }
}
