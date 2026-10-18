package nl.lloydstellar.robotvocal.score

import nl.lloydstellar.robotvocal.text.Pause
import nl.lloydstellar.robotvocal.text.Stress
import nl.lloydstellar.robotvocal.text.Syllable
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** A syllable on the grid: it starts on [slot] (negative: a pickup in the bar before) and lasts [length] slots. */
data class Placed(val syllable: Int, val slot: Int, val length: Int)

/** Where the syllables of one line go, over [bars] bars of [slotsPerBar] slots. */
data class LinePlacement(val bars: Int, val slotsPerBar: Int, val placed: List<Placed>, val cost: Double) {
    fun startTick(p: Placed): Int = p.slot * Ticks.perSlot(slotsPerBar)
    fun lengthTicks(p: Placed): Int = p.length * Ticks.perSlot(slotsPerBar)
}

/**
 * Puts the syllables of a line in slots, the way a rapper would: stressed
 * syllables on strong positions, function words and unstressed syllables in
 * between, a rest at a comma and a breath at a full stop, the rhyme on the
 * preset's target slot of the last bar. It is dynamic programming over
 * syllables and slots with the cost of docs/research/03, section 4; the
 * weights there are starting values to tune by ear.
 *
 * Syllables in [locks] (syllable index to slot) stay where the user put them
 * and the rest flows around them.
 */
object FlowPlacer {

    /** P(stressed | an onset on this 16th), from the MCFlow corpus (docs/research/03, section 1). */
    private val STRESS_16 = doubleArrayOf(
        0.95, 0.17, 0.73, 0.28, 0.93, 0.18, 0.71, 0.30, 0.93, 0.20, 0.62, 0.47, 0.94, 0.17, 0.76, 0.18,
    )

    /** For triplet bars: a strong first triplet of every beat. */
    private val STRESS_12 = DoubleArray(12) { doubleArrayOf(0.95, 0.30, 0.40)[it % 3] }

    private const val CAP = 4.0
    private const val INF = Double.MAX_VALUE / 4

    fun place(
        syllables: List<Syllable>,
        preset: FlowPreset,
        bpm: Double,
        bars: Int? = null,
        locks: Map<Int, Int> = emptyMap(),
        firstFreeSlot: Int = Int.MIN_VALUE,
    ): LinePlacement {
        val s = preset.slotsPerBar
        if (syllables.isEmpty()) return LinePlacement(bars ?: 1, s, emptyList(), 0.0)
        val n = syllables.size
        val dMin = preset.syllablesPerBar.first
        val dMax = preset.syllablesPerBar.last
        val lowest = max(1, ceil(n / dMax.toDouble()).toInt())
        val highest = max(lowest, min(4, ceil(n / dMin.toDouble()).toInt()))
        val lockedBars = locks.values.maxOrNull()?.let { it / s + 1 } ?: 1
        val candidates = if (bars != null) listOf(bars) else (max(lowest, lockedBars)..max(highest, lockedBars)).toList()

        var best: LinePlacement? = null
        var bestTotal = INF
        for (count in candidates) {
            val placement = solve(syllables, preset, bpm, count, locks, firstFreeSlot) ?: continue
            val total = placement.cost + 1.0 * (count - lowest) + (if (count == 3) 1.5 else 0.0) + 2.5 * sparseBars(placement)
            if (total < bestTotal) {
                bestTotal = total
                best = placement
            }
        }
        return best ?: evenly(syllables, s, bars ?: lowest)
    }

    private fun solve(
        syl: List<Syllable>,
        preset: FlowPreset,
        bpm: Double,
        bars: Int,
        locks: Map<Int, Int>,
        firstFreeSlot: Int,
    ): LinePlacement? {
        val n = syl.size
        val s = preset.slotsPerBar
        val slotMs = 60_000.0 / bpm / (s / 4.0)
        val first = max(-preset.maxPickup, firstFreeSlot)
        val total = bars * s
        val width = total - first
        if (width < n) return null
        val maxHold = s
        val rhyme = syl.indexOfLast { it.stress == Stress.PRIMARY }.takeIf { it >= 0 } ?: (n - 1)

        /* cost[i][j][d]: syllable i on slot first + j, the syllable before it lasted d slots (d = 0 for i = 0) */
        val cost = Array(n) { Array(width) { DoubleArray(maxHold + 1) { INF } } }
        val from = Array(n) { Array(width) { IntArray(maxHold + 1) { -1 } } }

        fun allowed(i: Int, slot: Int): Boolean = locks[i]?.let { it == slot } ?: true

        for (j in 0 until width) {
            val slot = first + j
            if (!allowed(0, slot)) continue
            cost[0][j][0] = onset(syl, 0, slot, preset, bars, rhyme) + 0.15 * max(0, slot)
        }
        for (i in 1 until n) {
            for (j in 0 until width) {
                val slot = first + j
                if (!allowed(i, slot)) continue
                val onsetCost = onset(syl, i, slot, preset, bars, rhyme)
                for (d in 1..min(maxHold, j)) {
                    val pj = j - d
                    val lapse = lapse(syl[i - 1], first + pj, slot, s)
                    var bestPrev = INF
                    var bestD = -1
                    for (pd in 0..maxHold) {
                        val c = cost[i - 1][pj][pd]
                        if (c >= INF) continue
                        val v = c + ioi(syl[i - 1], d, pd, slotMs)
                        if (v < bestPrev) {
                            bestPrev = v
                            bestD = pd
                        }
                    }
                    if (bestD < 0) continue
                    cost[i][j][d] = bestPrev + lapse + onsetCost
                    from[i][j][d] = bestD
                }
            }
        }

        var endJ = -1
        var endD = -1
        var endCost = INF
        for (j in 0 until width) for (d in 0..maxHold) {
            val c = cost[n - 1][j][d]
            if (c < endCost) {
                endCost = c
                endJ = j
                endD = d
            }
        }
        if (endJ < 0) return null

        val slots = IntArray(n)
        var j = endJ
        var d = endD
        for (i in n - 1 downTo 0) {
            slots[i] = first + j
            if (i > 0) {
                val pd = from[i][j][d]
                j -= d
                d = pd
            }
        }
        val placed = (0 until n).map { i ->
            val next = if (i + 1 < n) slots[i + 1] else total
            Placed(i, slots[i], next - slots[i])
        }
        return LinePlacement(bars, s, placed, endCost)
    }

    /** Bars with fewer than two onsets: a line in a song fills its bars. */
    private fun sparseBars(p: LinePlacement): Int = (0 until p.bars).count { bar ->
        p.placed.count { Math.floorDiv(it.slot, p.slotsPerBar) == bar } < 2
    }

    private fun stressTable(s: Int) = if (s == 12) STRESS_12 else STRESS_16

    /** The cost of syllable [i] starting on [slot]. */
    private fun onset(syl: List<Syllable>, i: Int, slot: Int, preset: FlowPreset, bars: Int, rhyme: Int): Double {
        val s = preset.slotsPerBar
        val k = Math.floorMod(slot, s)
        val bar = Math.floorDiv(slot, s)
        val mark = preset.pattern(bar)[k]
        val p = stressTable(s)[k]
        val x = syl[i]
        var c = if (mark == '.') preset.restCost else 0.0
        if (i == 0 && slot >= s) c += 4.0 // a line does not leave its first bar empty
        c += when (x.stress) {
            Stress.PRIMARY -> min(CAP, -ln(p)) + if (mark == 'x' || mark == 'p') 0.5 else 0.0
            Stress.SECONDARY -> min(CAP, -0.5 * ln(p))
            Stress.NONE -> min(CAP, -(if (x.function) 0.7 else 1.0) * ln(1 - p)) + if (mark == 'X') 0.2 else 0.0
        }
        if (slot < 0) c += if (x.stress == Stress.NONE) 0.3 * abs(slot) else 3.0
        if (preset.wordOnBeat && x.wordStart && k % (s / 4) != 0) c += 2.0
        if (i == rhyme) {
            val lastBar = bar == bars - 1
            val pattern = preset.pattern(bars - 1)
            c += when {
                mark == 'R' && lastBar -> 0.0
                mark == 'r' && lastBar -> 0.5
                else -> {
                    val nearest = pattern.indices.filter { pattern[it] == 'R' }.minOfOrNull { abs(it - k) } ?: 0
                    1.5 + 0.3 * nearest
                }
            }
            if (!lastBar) c += 3.0
        }
        return c
    }

    /** The cost of syllable [x] lasting [d] slots when the one before it lasted [pd] (0: none before). */
    private fun ioi(x: Syllable, d: Int, pd: Int, slotMs: Double): Double {
        var c = if (d * slotMs < 120) 5.0 else 0.0
        if (!x.wordEnd) c += 0.8 * max(0, d - 2)
        if (x.wordEnd && x.pause == null) c += 0.4 * max(0, d - 2)
        if (x.pause == Pause.COMMA && d < 3) c += 1.5
        if (x.pause == Pause.STOP && d < 4) c += 2.0
        if (x.heavy && x.pause != null && d == 1) c += 1.0
        if (pd > 0 && d != pd && d != 2 * pd && 2 * d != pd) c += 0.3
        return c
    }

    /** Beats skipped between two onsets, unless the earlier syllable ends a phrase. */
    private fun lapse(x: Syllable, from: Int, to: Int, s: Int): Double {
        if (x.pause != null) return 0.0
        val beat = s / 4
        var count = 0
        for (slot in from + 1 until to) if (Math.floorMod(slot, beat) == 0) count++
        return 0.6 * count
    }

    /** When nothing fits (more syllables than slots): one per slot from the downbeat. */
    private fun evenly(syl: List<Syllable>, s: Int, bars: Int): LinePlacement {
        val count = max(bars, ceil(syl.size / s.toDouble()).toInt())
        return LinePlacement(count, s, syl.indices.map { Placed(it, it, 1) }, INF)
    }
}
