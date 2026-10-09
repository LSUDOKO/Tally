package app.tally.nfc

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import app.tally.core.ApduSession

/**
 * Merchant side of the NFC tap: answers ISO 7816-4 APDUs with the currently signed menu payload
 * (swapped by the merchant terminal on every ~20 s re-sign; each tap keeps the menu it selected).
 */
class TallyHceService : HostApduService() {
    private val session = ApduSession { MerchantBroadcast.payload }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray = session.respond(commandApdu)

    /** The customer's phone moved away (or selected another app): the next tap starts fresh. */
    override fun onDeactivated(reason: Int) = session.reset()
}

/** The payload the merchant terminal is currently broadcasting (null = terminal off). */
object MerchantBroadcast {
    @Volatile var payload: ByteArray? = null
}
