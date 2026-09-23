package id.web.izs.nettools.core

/**
 * Pure broadcast-storm analysis over a finished gateway ping burst plus
 * ARP samples. No Android APIs, no I/O — intentionally unit-testable,
 * mirroring [LoopDetector].
 *
 * Heuristics (rootless-honest):
 * - 2+ `(DUP!)` replies = STORM. Duplicate echo replies mean frames
 *   circulate or are flooded back — the strongest rootless L2-loop signal.
 * - A single DUP is SUSPECTED only (same philosophy as LoopDetector's
 *   single-repeat rule: suspicion must never convict alone).
 * - Exploded gateway RTT (p95 > 100 ms on a LAN) plus partial loss =
 *   SUSPECTED; p95 > 500 ms with loss, or any RTT/loss anomaly combined
 *   with an ARP flap, = STORM.
 * - A fully silent gateway is judged by traffic + errors, not by silence
 *   alone: silent under a packet flood (high RX pps) = STORM — a real storm
 *   kills the very replies we measure. Silent with "unreachable" errors and
 *   a quiet interface = NOT a storm (cable down / no route). Silent with
 *   plain timeouts and a quiet interface = SUSPECTED (ambiguous).
 * - ARP flap alone (without ping anomaly) is SUSPECTED — flapping can
 *   also come from roaming, HSRP/VRRP, or AP steering.
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
                return StormResult.NoStorm(
                    "No storm: gateway $gatewayIp unreachable (${stats.sent} sent, " +
                        "ICMP errors, quiet interface — cable down or no route, " +
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
        val p95ms = p95(stats.rttsMs)
        val loss = stats.lossPct

        // 1. Duplicate replies: the smoking gun.
        if (stats.dups >= 2) {
            return StormResult.Storm(
                "STORM DETECTED: ${stats.dups} duplicate replies (DUP!) from $gatewayIp " +
                    "(frames circulating — L2 loop suspected)"
            )
        }

        val rttBad = p95ms != null && p95ms > 500.0
        val rttOdd = p95ms != null && p95ms > 100.0
        val lossBad = loss >= 10.0

        // 2. Strong combined signals.
        if (rttBad && lossBad) {
            return StormResult.Storm(
                "STORM DETECTED: gateway $gatewayIp p95 ${"%.0f".format(p95ms)} ms " +
                    "with ${"%.0f".format(loss)}% loss (broadcast storm suspected)"
            )
        }
        if (flap != null && (rttOdd || lossBad || stats.dups == 1)) {
            return StormResult.Storm(
                "STORM DETECTED: gateway $gatewayIp flaps between MACs " +
                    "${flap.macs.joinToString(" <-> ")} with ping anomalies " +
                    "(L2 loop suspected)"
            )
        }

        // 3. Single anomalies: suspicion only.
        if (stats.dups == 1) {
            return StormResult.Suspected(
                "Suspected storm: 1 duplicate reply (DUP!) from $gatewayIp " +
                    "(single dupe — could be a transient flood, not proof of a loop)."
            )
        }
        if (rttOdd || lossBad) {
            val why = buildList {
                if (rttOdd) add("p95 ${"%.0f".format(p95ms)} ms")
                if (lossBad) add("${"%.0f".format(loss)}% loss")
            }.joinToString(", ")
            return StormResult.Suspected(
                "Suspected storm: gateway $gatewayIp slow/lossy ($why — " +
                    "storm or just bad WiFi, cannot tell apart without DUP/ARP proof)."
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
        return StormResult.NoStorm(
            "No storm: gateway $gatewayIp healthy " +
                "(${stats.received}/${stats.sent} replies" +
                (if (p95ms != null) ", p95 ${"%.1f".format(p95ms)} ms" else "") +
                ", ARP stable)."
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
