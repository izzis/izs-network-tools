package id.web.izs.nettools.core

import id.web.izs.nettools.model.HopInfo

/**
 * Pure routing-loop analysis over a finished traceroute hop list.
 *
 * No Android APIs, no I/O — intentionally unit-testable.
 *
 * Heuristics (rootless-honest):
 * - Non-consecutive repeat (IP seen at hops N and M, M - N > 1) = LOOP.
 *   This also covers alternating A-B-A-B cycles (A reappears with a gap).
 * - Tiny IP set dominating a long trace (>= 6 resolved hops, <= 3 unique,
 *   each seen >= 2x) = LOOP — includes the contiguous X,X,Y,Y,Z,Z shape
 *   (short cycle / bridged ports churning the path) and A-B-C-A-B-C...
 * - A single lone consecutive duplicate (hop 4 == hop 5) alone is NOT a
 *   loop; that is the known anycast/MPLS case already annotated by
 *   TraceRunner.
 * - Full-length run that never reached the destination with some hops
 *   answering = SUSPECTED (loop or filtering — can't tell apart without root).
 * - All-silent runs are NOT a loop (ICMP blocked / TTL ignored).
 */
sealed interface LoopResult {
    data class NoLoop(val message: String) : LoopResult
    data class Loop(val message: String) : LoopResult
    data class Suspected(val message: String) : LoopResult
}

object LoopDetector {

    fun analyze(
        hops: List<HopInfo>,
        destReached: Boolean,
        maxHops: Int
    ): LoopResult {
        val answered = hops.filter { it.ip != null }
        if (answered.isEmpty()) {
            return LoopResult.NoLoop("No loop: no hop answered (nothing to analyze).")
        }
        val ttlsByIp = linkedMapOf<String, MutableList<Int>>()
        answered.forEach { h -> ttlsByIp.getOrPut(h.ip!!) { mutableListOf() }.add(h.ttl) }

        // 1. Non-consecutive repeats: same IP with a gap in between.
        val repeats = ttlsByIp.filter { (_, ttls) ->
            ttls.size >= 2 && ttls.zipWithNext().any { (a, b) -> b - a > 1 }
        }
        if (repeats.isNotEmpty()) {
            val detail = repeats.entries.take(3).joinToString("; ") { (ip, ttls) ->
                "$ip seen at hops ${ttls.joinToString(", ")}"
            }
            return LoopResult.Loop("LOOP DETECTED: $detail (routing loop suspected)")
        }

        // (Alternating A-B-A-B cycles never reach the rules below: A always
        // reappears with a gap and rule 1 already convicts them.)

        // 2. Tiny set dominating a long trace — including contiguous repeats
        // (X,X,Y,Y,Z,Z) and A-B-C-A-B-C... (rule 1 saw no gap above).
        val distinct = ttlsByIp.size
        if (answered.size >= 6 && distinct <= 3 && ttlsByIp.values.all { it.size >= 2 }) {
            return LoopResult.Loop(
                "LOOP DETECTED: only $distinct unique IPs across ${answered.size} answered hops " +
                    "(routing loop suspected)"
            )
        }

        // 3. Ran the full length without arriving, but something answered:
        // loop or filter — honest about the ambiguity.
        if (!destReached && hops.size >= maxHops) {
            return LoopResult.Suspected(
                "Suspected loop or filtering: max hops ($maxHops) reached " +
                    "without reaching the destination."
            )
        }

        val unique = distinct
        return if (destReached) {
            LoopResult.NoLoop("No loop: $unique unique hop${if (unique == 1) "" else "s"}, destination reached.")
        } else {
            LoopResult.NoLoop("No loop: $unique unique hop${if (unique == 1) "" else "s"}, no repeats seen.")
        }
    }

    /** One verdict line as emitted into the console. */
    fun verdictLine(result: LoopResult): String = when (result) {
        is LoopResult.Loop -> result.message
        is LoopResult.Suspected -> result.message
        is LoopResult.NoLoop -> result.message
    }

    /**
     * Mid-run stop predicate: returns [Loop] only when the hops collected
     * SO FAR *prove* circulation, null to keep probing. Deliberately
     * stricter than [analyze] — a single non-consecutive repeat is merely
     * suspicion (it still verdicts as LOOP at end of run) and must never
     * cut a trace short on its own. Proof requires one of:
     * - the same IP at 3+ TTLs with at least one gap (A ... A ... A:
     *   the packet demonstrably came back twice, not a one-off anomaly;
     *   this also covers an alternating cycle observed twice — A-B-A-B-A-B),
     * - a tiny IP set dominating a long trace (same rule as [analyze]).
     */
    fun detectConfirmed(hops: List<HopInfo>): LoopResult.Loop? {
        val answered = hops.filter { it.ip != null }
        if (answered.isEmpty()) return null

        // 1. Triple-seen IP with a gap: came back twice.
        val ttlsByIp = linkedMapOf<String, MutableList<Int>>()
        answered.forEach { h -> ttlsByIp.getOrPut(h.ip!!) { mutableListOf() }.add(h.ttl) }
        for ((ip, ttls) in ttlsByIp) {
            // Span wider than a consecutive run => at least one gap in there.
            if (ttls.size >= 3 && ttls.last() - ttls.first() > ttls.size - 1) {
                return LoopResult.Loop(
                    "LOOP DETECTED: $ip seen at hops ${ttls.joinToString(", ")} (routing loop confirmed)"
                )
            }
        }

        // 2. Tiny set dominating a long trace (A-B-A-B-A-B above already
        // convicted via rule 1: A sits at a gap of 2).
        if (answered.size >= 6 && ttlsByIp.size <= 3 && ttlsByIp.values.all { it.size >= 2 }) {
            return LoopResult.Loop(
                "LOOP DETECTED: only ${ttlsByIp.size} unique IPs across ${answered.size} answered hops " +
                    "(routing loop confirmed)"
            )
        }
        return null
    }

    /** Rebuild a verdict from a console line (for the UI banner). Null = not a verdict. */
    fun verdictOfLine(line: String): LoopResult? {
        val t = line.trimStart()
        return when {
            t.startsWith("LOOP DETECTED") -> LoopResult.Loop(t)
            t.startsWith("Suspected loop") -> LoopResult.Suspected(t)
            t.startsWith("No loop:") -> LoopResult.NoLoop(t)
            else -> null
        }
    }
}
