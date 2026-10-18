package nl.lloydstellar.robotvocal.score

/**
 * A way of putting syllables in a bar. Each bar pattern has one character per
 * slot: `X` a stressed syllable fits here, `x` an onset is fine, `R` the rhyme
 * target, `r` the second choice for the rhyme, `p` a pickup into the next
 * line, `.` rather a rest or a held syllable. Presets with two patterns
 * alternate them bar by bar.
 *
 * The patterns, densities and tempos come from docs/research/03 (section 5),
 * which bases them on the MCFlow corpus and on the tracks named in [echoes].
 * The electro flows go up to 140 BPM, where most electro is made today.
 */
data class FlowPreset(
    val id: String,
    val name: String,
    val bars: List<String>,
    val syllablesPerBar: IntRange,
    val bpm: IntRange,
    val swing: Double = 0.5,
    val offsetMs: Double = 0.0,
    val gate: Double = 0.85,
    val maxPickup: Int = 3,
    /** Every word starts on a beat ("Numbers", "Technologic"). */
    val wordOnBeat: Boolean = false,
    /** The cost of an onset on a `.` slot: high for the strict robot flows, so they keep their grid. */
    val restCost: Double = 2.0,
    /**
     * The shortest a stressed syllable should be, in ms (see FlowPlacer). Snap
     * flows go lower: there the stressed syllable is the short one.
     */
    val shortestMs: Double = 120.0,
    val echoes: String = "",
) {
    val slotsPerBar: Int = bars.first().length

    init {
        require(bars.all { it.length == slotsPerBar }) { "$id: bars of different lengths" }
        require(slotsPerBar == 16 || slotsPerBar == 12) { "$id: $slotsPerBar slots per bar" }
    }

    fun pattern(bar: Int): String = bars[Math.floorMod(bar, bars.size)]

    companion object {
        val ALL = listOf(
            FlowPreset("robot8", "ROBOT 8THS", listOf("X.x.X.x.X.x.R.x."), 4..8, 110..140, gate = 0.7, maxPickup = 2,
                restCost = 10.0, echoes = "The Robots, Pocket Calculator"),
            FlowPreset("wordbeat", "WORD PER BEAT", listOf("X.x.X.x.X.x.R.x."), 4..8, 115..140, gate = 0.6, maxPickup = 0,
                wordOnBeat = true, restCost = 10.0, echoes = "Numbers, Technologic"),
            FlowPreset("callresp", "CALL AND RESPONSE", listOf("X.x.X.x.X.x.R...", "....X.x.X.x.R..."), 4..7, 115..140,
                gate = 0.75, maxPickup = 1, echoes = "Planet Rock"),
            FlowPreset("electro332", "ELECTRO 3+3+2", listOf("X.xX.xX.X.xX.xR."), 6..10, 105..140, swing = 0.52,
                gate = 0.8, echoes = "Egyptian Lover, Cybotron"),
            FlowPreset("couplet", "OLD-SCHOOL COUPLET", listOf("X.x.X.x.X.xrR..."), 6..9, 95..115, gate = 0.85,
                echoes = "Kurtis Blow"),
            FlowPreset("boombap", "BOOM BAP LAID BACK", listOf("XxXxXxXxXxXrRxXx"), 8..13, 84..100, swing = 0.56,
                offsetMs = 15.0, gate = 0.9, echoes = "the MCFlow norms"),
            FlowPreset("pickup", "PICKUP FLOW", listOf("X.xxX.x.XxxrR.pp"), 8..12, 85..100, swing = 0.54,
                offsetMs = 10.0, gate = 0.9, echoes = "99 Problems"),
            FlowPreset("rapid16", "16TH RAPID FIRE", listOf("XxxxXxxxXxxxRxxx"), 12..16, 80..100, gate = 0.95,
                echoes = "Blazing Arrow"),
            FlowPreset("triplet", "TRIPLET FLOW", listOf("XxxXxxXxxRxx"), 9..12, 120..150, gate = 0.9,
                echoes = "Versace, Bad and Boujee"),
            FlowPreset("halftime", "HALF-TIME TRAP", listOf("X.x.x.xxX.x.x.xx", "X.x.x.xxR...x.xx"), 6..10, 130..160,
                gate = 0.85, echoes = "trap, UK drill"),
            FlowPreset("stutter", "STUTTER SNAP", listOf("Xx..Xx..Xx..Rx.."), 6..8, 130..150, gate = 0.6, restCost = 4.0,
                shortestMs = 95.0, echoes = "Scotch snaps on each beat"),
        )

        val DEFAULT = ALL.first()

        fun byId(id: String): FlowPreset = ALL.firstOrNull { it.id == id } ?: DEFAULT
    }
}
