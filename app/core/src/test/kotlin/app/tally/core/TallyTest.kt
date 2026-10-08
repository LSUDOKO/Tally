package app.tally.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class TallyTest {
    @Test
    fun programIdMatchesDeployment() {
        assertEquals("3ofJTv8xX68aFTQJLNZV2tDXESPuCjcfRHpeLd37djqk", Tally.PROGRAM_ID)
    }
}
