package app.tally.core

/** What the Second Look sheet shows before the owner signs in Seed Vault (docs/08). */
class Review(
    val grossOutflow: Long,
    val decreases: List<Pair<String, Long>>,
    val unexpectedPrograms: List<String>,
) {
    /** Approval is disabled whenever the transaction contains anything beyond the expected set. */
    val approveEnabled: Boolean get() = unexpectedPrograms.isEmpty()
}

object SecondLook {
    val EXPECTED_PROGRAMS = setOf(Programs.COMPUTE_BUDGET, Tally.ED25519_PROGRAM_ID, Tally.PROGRAM_ID, Tally.LIGHTHOUSE_ID)

    /**
     * @param balances pre/post token amounts per account, from simulation. Gross outflow sums every
     * decrease, never the net, so a drain cannot hide behind an unrelated gain.
     */
    fun review(instructions: List<Instruction>, balances: Map<String, Pair<Long, Long>>): Review {
        val decreases = balances.mapNotNull { (k, v) -> (v.first - v.second).takeIf { it > 0 }?.let { k to it } }
        return Review(
            grossOutflow = decreases.sumOf { it.second },
            decreases = decreases,
            unexpectedPrograms = instructions.map { it.programId }.filter { it !in EXPECTED_PROGRAMS }.distinct(),
        )
    }
}
