package app.tally.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.tally.core.Apdu

/**
 * Merchant side of the NFC tap: answers ISO 7816-4 APDUs with the currently signed menu payload
 * (swapped atomically by the merchant terminal on every ~20 s re-sign).
 */
class TallyHceService : HostApduService() {
    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val payload = MerchantBroadcast.payload ?: return byteArrayOf(0x69, 0x85.toByte()) // conditions not satisfied
        return Apdu.respond(commandApdu, payload)
    }

    override fun onDeactivated(reason: Int) = Unit
}

/** The payload the merchant terminal is currently broadcasting (null = terminal off). */
object MerchantBroadcast {
    @Volatile var payload: ByteArray? = null
}
