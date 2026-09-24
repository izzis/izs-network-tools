package id.web.izs.nettools.core

/**
 * Pure broadcast-storm analysis over a finished gateway ping burst plus
 * ARP samples. No Android APIs, no I/O — intentionally unit-testable,
 * mirroring [LoopDetector].
 *
 * Heuristics (rootless-honest):
 * - 2+ `(DUP!)` replies = STORM. Two duplicate events mean frames
 *   circulated or are flooded back — the strongest rootless L2-loop signal.
 * - Otherwise STORM needs TWO independent heavy signals among:
 *   ≥20% loss with ≥2 lost packets, p95 > 500 ms, a one-way RX flood
 *   (≥ FLOOD_PPS with quiet TX), an ARP MAC flap, or any DUP! reply.
 *   A real cable loop (ports bridged on one switch) trips loss + flood +
 *   DUP together; a single anomaly never convicts.
 * - A single anomaly (1 DUP, slow/lossy gateway, lone ARP flap, lone
 *   heavy-loss sample) = SUSPECTED only — suspicion must never convict
 *   alone (same philosophy as LoopDetector's single-repeat rule).
 * - A fully silent gateway is judged by traffic + errors, not by silence
 *   alone: silent under a packet flood (high RX pps) = STORM — a real storm
 *   kills the very replies we measure. Silent with "unreachable" errors and
 *   a quiet interface = NOT a storm (cable down / no route). Silent with
 *   plain timeouts and a quiet interface = SUSPECTED (ambiguous).
 * - ARP flap alone (without a second heavy signal) is SUSPECTED — flapping
 *   can also come from roaming, HSRP/VRRP, or AP steering.
 * - Verdict text never claims evidence that was not measured: unreadable
 *   ARP says "ping-only", unreadable counters say so instead of "quiet".
 */
sealed interface StormResult {
    data class NoStorm(val message: String) : StormResult
    data class Suspected(val message: String) : StormResult
    data class Storm(val message: String) : StormResult
}

object StormDetector {

    data class PingStats(
        val sent: Int,
        val received: Int,
        val dups: Int,
        val rttsMs: List<Double>,
        /** RX packets/sec on the phone during the burst (NetDevWatcher), null when unreadable. */
        val rxPps: Double? = null,
        /** TX packets/sec beside it: the alibi check (downloads always ACK). */
        val txPps: Double? = null,
        /** Ping output contained "unreachable" (no route / ARP failed), not just timeouts. */
        val netUnreachable: Boolean = false
    ) {
        val lossPct: Double
            get() = if (sent <= 0) 100.0 else (sent - received).coerceAtLeast(0) * 100.0 / sent
    }

    /** Sustained RX flood: a storm drowns an idle phone (tens of pps) by 100x. */
    const val FLOOD_PPS = 1000.0

    /** Elevated traffic: suspicious only in combination with other signals. */
    const val BUSY_PPS = 300.0

    /**
     * Quiet TX ceiling: our own pings are ~1 pps, background chatter stays far
     * below this. A download ACK stream (hundreds of pps) lands above it and
     * disqualifies the one-way-flood conviction.
     */
    const val TX_QUIET_PPS = 100.0

    data class PingSample(val rttMs: Double?, val dup: Boolean)

    private val rtt = Regex("""time[=<]([0-9.]+)\s*ms""")

    /**
     * Parse one ping output line. Returns null for non-reply lines
     * (headers, statistics, timeouts, ICMP errors). A `(DUP!)` reply counts
     * as a reply AND sets [PingSample.dup]. Matching is case-insensitive so
     * toybox/iputils wording variants ("(DUP!)", "duplicate") all hit.
     */
    fun parsePingLine(line: String): PingSample? {
        if (!line.contains("bytes from")) return null
        val ms = rtt.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
        // Parenthesized marker or the word "duplicate": both are the ping
        // binary's own wording, never part of a "bytes from <host>" address.
        val lower = line.lowercase()
        return PingSample(ms, lower.contains("(dup!)") || lower.contains("duplicate"))
    }

    fun p95(rtts: List<Double>): Double? {
        if (rtts.isEmpty()) return null
        val s = rtts.sorted()
        val idx = ((s.size * 95 + 99) / 100 - 1).coerceIn(0, s.size - 1)
        return s[idx]
    }

    fun analyze(
        stats: PingStats,
        arp: ArpWatcher.ArpResult,
        gatewayIp: String
    ): StormResult {
        // 0. Silent gateway: the old code said "not a storm" and stopped.
        // That misses the worst case — a storm that kills every reply. Judge
        // by what the wire looked like instead (RX flood? hard errors?).
        if (stats.received == 0) {
            val pps = stats.rxPps
            val txQuiet = stats.txPps == null || stats.txPps < TX_QUIET_PPS
            if (pps != null && pps >= FLOOD_PPS && txQuiet) {
                return StormResult.Storm(
                    "STORM DETECTED: gateway $gatewayIp silent while this device " +
                        "receives ${"%.0f".format(pps)} packets/sec " +
                        "(replies drowned by a flood — L2 loop suspected)"
                )
            }
            if (pps != null && pps >= FLOOD_PPS) {
                return StormResult.Suspected(
                    "Suspected storm: gateway $gatewayIp silent under heavy " +
                        "two-way traffic (${"%.0f".format(pps)} RX pps — looks more " +
                        "like downloads than a one-way flood)."
                )
            }
            if (stats.netUnreachable && (pps == null || pps < BUSY_PPS)) {
                val wire = if (pps == null) "traffic counters unreadable" else "quiet interface"
                return StormResult.NoStorm(
                    "No storm: gateway $gatewayIp unreachable (${stats.sent} sent, " +
                        "ICMP errors, $wire — cable down or no route, " +
                        "not a loop signature)."
                )
            }
            if (pps != null && pps >= BUSY_PPS) {
                return StormResult.Suspected(
                    "Suspected storm: gateway $gatewayIp silent with elevated " +
                        "traffic (${"%.0f".format(pps)} packets/sec — storm may be " +
                        "killing replies, or the LAN is just busy)."
                )
            }
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp silent (${stats.sent} sent, " +
                    "timeouts only — a storm can kill every reply, but so can a " +
                    "dead cable or ICMP blocking; cannot tell apart)."
            )
        }
        val flap = arp as? ArpWatcher.ArpResult.Flap
        val p95v = p95(stats.rttsMs)
        val loss = stats.lossPct
        val lost = (stats.sent - stats.received).coerceAtLeast(0)
        val txQuiet = stats.txPps == null || stats.txPps < TX_QUIET_PPS

        // 1. Duplicate replies: the smoking gun (two events = majemuk).
        if (stats.dups >= 2) {
            return StormResult.Storm(
                "STORM DETECTED: ${stats.dups} duplicate replies (DUP!) from $gatewayIp " +
                    "(frames circulating — L2 loop suspected)"
            )
        }

        // Heavy signals, each independent; STORM needs two of them.
        val lossHeavy = loss >= 20.0 && lost >= 2
        val rttBad = p95v != null && p95v > 500.0
        val flood = stats.rxPps != null && stats.rxPps >= FLOOD_PPS && txQuiet
        val heavySignals = buildList {
            if (lossHeavy) add("${"%.0f".format(loss)}% loss ($lost/${stats.sent} lost)")
            // rttBad already implies p95v != null; flood already implies rxPps != null.
            if (rttBad) add("p95 ${"%.0f".format(p95v)} ms")
            if (flood) add("RX flood ${"%.0f".format(stats.rxPps)} pps")
            if (flap != null) add("ARP flap ${flap.macs.joinToString(" <-> ")}")
            if (stats.dups == 1) add("1 duplicate reply (DUP!)")
        }
        if (heavySignals.size >= 2) {
            return StormResult.Storm(
                "STORM DETECTED: gateway $gatewayIp — ${heavySignals.joinToString(" + ")} " +
                    "(two independent signals — broadcast storm / L2 loop suspected)"
            )
        }

        // 2. Single anomaly: suspicion only, never conviction.
        val rttOdd = p95v != null && p95v > 100.0
        val lossBad = loss >= 10.0
        if (stats.dups == 1) {
            return StormResult.Suspected(
                "Suspected storm: 1 duplicate reply (DUP!) from $gatewayIp " +
                    "(single dupe — could be a transient flood, not proof of a loop)."
            )
        }
        if (flood) {
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp answering while this device " +
                    "receives ${"%.0f".format(stats.rxPps)} packets/sec " +
                    "(one-way flood present, but replies still arrive — a second " +
                    "signal is needed to confirm a storm)."
            )
        }
        if (rttOdd || lossBad) {
            val why = buildList {
                if (rttOdd) add("p95 ${"%.0f".format(p95v)} ms")
                if (lossBad) add("${"%.0f".format(loss)}% loss")
            }.joinToString(", ")
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp slow/lossy ($why — " +
                    "not enough alone to confirm; storm or just bad WiFi, " +
                    "needs a second signal (DUP/flood/flap) to confirm)."
            )
        }
        if (flap != null) {
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp seen on MACs " +
                    "${flap.macs.joinToString(", ")} but ping looks clean " +
                    "(could be roaming/VRRP, not necessarily a loop)."
            )
        }
        if (arp is ArpWatcher.ArpResult.Incomplete) {
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp ARP entry incomplete " +
                    "while ping answers (churn — watch for a loop)."
            )
        }
        val arpTxt = if (arp is ArpWatcher.ArpResult.Unreadable) {
            "ARP unreadable — ping-only verdict"
        } else {
            "ARP stable"
        }
        return StormResult.NoStorm(
            "No storm: gateway $gatewayIp healthy " +
                "(${stats.received}/${stats.sent} replies" +
                (if (p95v != null) ", p95 ${"%.1f".format(p95v)} ms" else "") +
                ", $arpTxt)."
        )
    }

    /** One verdict line as emitted into the console. */
    fun verdictLine(result: StormResult): String = when (result) {
        is StormResult.Storm -> result.message
        is StormResult.Suspected -> result.message
        is StormResult.NoStorm -> result.message
    }

    /** Rebuild a verdict from a console line (for the UI banner). Null = not a verdict. */
    fun verdictOfLine(line: String): StormResult? {
        val t = line.trimStart()
        return when {
            t.startsWith("STORM DETECTED") -> StormResult.Storm(t)
            t.startsWith("Suspected storm") -> StormResult.Suspected(t)
            t.startsWith("No storm:") -> StormResult.NoStorm(t)
            else -> null
        }
    }
}
