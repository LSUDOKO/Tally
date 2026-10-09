package app.tally.core

/** Protocol constants shared with the on-chain program (docs/06, As built). */
object Tally {
    const val PROGRAM_ID = "3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk"
    const val LIGHTHOUSE_ID = "L2TExMFKdjpN9kozasaurPirfHy9P8sbXoAN1qA3S95"
    const val ED25519_PROGRAM_ID = "Ed25519SigVerify111111111111111111111111111"
    const val HEADER_LEN = 144
    const val MAX_AUTONOMOUS_WINDOW = 150L

    /** Length of a capsule's spending day, in slots (crates/tally-core CYCLE_SLOTS). */
    const val CYCLE_SLOTS = 216_000L
    const val MAX_PROOF_DEPTH = 16
}
