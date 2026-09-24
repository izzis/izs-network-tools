package id.web.izs.nettools.core

import id.web.izs.nettools.model.HopInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.TimeUnit

/**
 * Unified loop analysis: L2 broadcast-storm check on the LAN gateway plus
 * L3 routing-loop trace to a target. The per-hop primitive ([TraceRunner.probe])
 * is shared with the (now verdict-free) Trace tool; the confirmed-loop
 * early-stop moved here from TraceRunner.
 *
 * Mode comes from the Loop tool's hold dialog (per-session, default BOTH).
 * Empty target means "the gateway itself" for both phases.
 */
object LoopRunner {

    enum class LoopMode(val title: String, val sub: String) {
        BOTH("L2 + L3", "L2+L3"),
        L2_ONLY("L2 only", "L2"),
        L3_ONLY("L3 only", "L3");

        companion object {
            fun of(name: String): LoopMode = try { valueOf(name) } catch (_: Exception) { BOTH }
        }
    }

    const val PING_COUNT = 10

    fun run(
        rawTarget: String,
        mode: LoopMode,
        maxHops: Int,
        timeoutMs: Int,
        pingCount: Int = PING_COUNT,
        onProgress: ((String) -> Unit)? = null
    ): Flow<String> = flow {
        // Strip URL/port clutter ("https://h:8080/p" -> host), like every other tool.
        // Empty target is allowed: both phases fall back to the LAN gateway.
        val typedHost = TargetParser.parse(rawTarget).host.ifEmpty { null }
        emit(";; Loop mode: ${mode.title} (hold Loop to change: L2 only / L3 only / Both)")

        // Step 1 (shared): resolve the gateway whenever any phase needs it —
        // always for L2, and as the L3 fallback when the target bar is empty.
        var gw: GatewayResolver.GatewayInfo? = null
        if (mode != LoopMode.L3_ONLY || typedHost == null) {
            emit(">> Step 1: resolve LAN gateway...")
            val (found, log) = GatewayResolver.resolveVerbose()
            log.forEach { emit(it) }
            gw = found
        }
        val l3Target = typedHost ?: gw?.ip

        if (mode != LoopMode.L3_ONLY) {
            if (gw == null) {
                emit("ERROR: no gateway found (not on WiFi/LAN?). L2 storm check needs a LAN gateway.")
            } else {
                emit(">> Step 2: L2 storm check on ${gw.ip}")
                val l2result = runL2(gw.ip, timeoutMs, pingCount.coerceIn(4, 100), onProgress) { emit(it) }
                if (mode == LoopMode.BOTH && l2result is StormResult.Storm) {
                    emit("Note: L2 storm confirmed — skipping L3 trace (no probe can answer through a storm).")
                    emit("Use L3-only mode (hold Loop) to force the trace anyway.")
                    return@flow
                }
            }
        }
        if (mode != LoopMode.L2_ONLY) {
            if (l3Target == null) {
                emit("ERROR: no L3 target (not on WiFi/LAN and no manual target typed).")
            } else {
                val via = if (typedHost == null) "auto gateway" else "manual target"
                emit(">> Step 3: L3 loop trace to $l3Target [$via, max $maxHops hops]")
                runL3(l3Target, maxHops, onProgress) { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun runL2(
        gwIp: String,
        timeoutMs: Int,
        pingCount: Int,
        onProgress: ((String) -> Unit)?,
        emit: suspend (String) -> Unit
    ): StormResult {
        if (!ExecUtil.pingAvailable()) {
            emit("ERROR: ping binary not found on this device, cannot probe the gateway.")
            return StormResult.Suspected("Suspected storm: could not run the gateway probe.")
        }
        val waitSec = minOf(maxOf(timeoutMs / 1000, 1), 2)
        emit("-- ARP baseline: reading /proc/net/arp...")
        val before = ArpWatcher.read()
        if (before == null) {
            emit("-- /proc/net/arp unreadable, continuing ping-only (verdict will say so)")
        } else {
            val gwMac = before[gwIp]?.mac
            emit("-- ARP baseline: ${before.size} entries" + (if (gwMac != null) ", gateway MAC $gwMac" else ", no gateway entry yet"))
        }
        emit("-- traffic baseline: reading counters (TrafficStats, /proc/net/dev fallback)...")
        val netBefore = NetDevWatcher.read()
        if (netBefore == null) emit("-- traffic counters unreadable, flood check skipped")
        emit("-- pinging gateway $gwIp x$pingCount (watching for DUP! replies, slow RTT, loss)...")
        onProgress?.invoke("Pinging gateway $gwIp x$pingCount...")
        var sent = 0
        var received = 0
        var dups = 0
        var unreachable = false
        val rtts = mutableListOf<Double>()
        var proc: Process? = null
        try {
            proc = ProcessBuilder(
                ExecUtil.pingBin(), "-c", pingCount.toString(), "-W", waitSec.toString(), gwIp
            ).redirectErrorStream(true).start()
            // waitFor first: readText() blocks until EOF (= process exit), so a
            // hung ping would make any later timeout unreachable. ping -c output
            // is small (<< pipe buffer), so waiting before draining is safe.
            val timeoutSec = (pingCount * waitSec + 10).toLong()
            if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
                emit("ERROR: gateway ping hung for ${timeoutSec}s — killing it.")
                return StormResult.Suspected(
                    "Suspected storm: gateway ping hung (process killed) — cannot judge $gwIp."
                )
            }
            val out = proc.inputStream.bufferedReader().readText()
            val statLine = Regex("""(\d+) packets transmitted,\s*(\d+) (?:packets )?received""").find(out)
            if (statLine != null) {
                sent = statLine.groupValues[1].toIntOrNull() ?: pingCount
                received = statLine.groupValues[2].toIntOrNull() ?: 0
            } else {
                sent = pingCount
            }
            for (line in out.lines()) {
                if (line.contains("unreachable", ignoreCase = true)) unreachable = true
                StormDetector.parsePingLine(line)?.let { s ->
                    if (s.dup) dups++
                    s.rttMs?.let { rtts.add(it) }
                }
            }
            if (statLine == null) received = rtts.size
        } catch (e: Exception) {
            emit("ERROR: gateway ping failed: ${e.message}")
            return StormResult.Suspected("Suspected storm: gateway probe crashed mid-run.")
        } finally {
            try { proc?.destroy() } catch (_: Exception) { }
        }
        delay(1000)
        emit("-- re-reading ARP table for MAC changes...")
        val after = ArpWatcher.read()
        val netAfter = NetDevWatcher.read()
        val rxPps = NetDevWatcher.rxPps(netBefore, netAfter)
        val txPps = NetDevWatcher.txPps(netBefore, netAfter)
        if (rxPps != null) emit("-- interface traffic during burst: RX ${"%.0f".format(rxPps)} / TX ${"%.0f".format(txPps ?: 0.0)} packets/sec")
        val samples = listOfNotNull(before, after)
        val arpResult = if (samples.isEmpty()) {
            emit("Note: /proc/net/arp unreadable on this device — verdict is ping-only.")
            ArpWatcher.ArpResult.Unreadable
        } else {
            ArpWatcher.detectFlap(samples, gwIp)
        }
        emit("-- analyzing: ping stats + ARP stability...")
        when (val a = arpResult) {
            is ArpWatcher.ArpResult.Flap -> emit("ARP $gwIp flaps: ${a.macs.joinToString(" <-> ")}")
            is ArpWatcher.ArpResult.Incomplete -> emit("ARP $gwIp: entry incomplete")
            is ArpWatcher.ArpResult.Stable -> {
                val mac = samples.lastNotNullOfOrNull { it[gwIp]?.mac }
                emit("ARP $gwIp -> ${mac ?: "stable"} (stable)")
            }
            is ArpWatcher.ArpResult.Unreadable -> {}
        }
        val stats = StormDetector.PingStats(sent, received, dups, rtts, rxPps, txPps, unreachable)
        val p95 = StormDetector.p95(rtts)
        emit(
            "L2 ping: $sent sent, $received received, $dups dup, " +
                "${"%.0f".format(stats.lossPct)}% loss" +
                (if (p95 != null) ", p95 ${"%.1f".format(p95)} ms" else "") +
                (if (unreachable) ", ICMP unreachable seen" else "")
        )
        val result = StormDetector.analyze(stats, arpResult, gwIp)
        emit(StormDetector.verdictLine(result))
        return result
    }

    private suspend fun runL3(
        target: String,
        maxHops: Int,
        onProgress: ((String) -> Unit)?,
        emit: suspend (String) -> Unit
    ) {
        if (!ExecUtil.pingAvailable()) {
            emit("ERROR: ping binary not found on this device, cannot trace.")
            return
        }
        val destIp = TraceRunner.resolveIp(target)
        emit("-- target resolves to ${destIp ?: "unresolved (tracing by name)"}")
        var prevIp: String? = null
        var resolved = 0
        val hops = mutableListOf<HopInfo>()
        var destReached = false
        var loopStopped = false
        for (ttl in 1..maxHops) {
            onProgress?.invoke("Probing hop $ttl/$maxHops...")
            emit("-- hop $ttl/$maxHops: TTL=$ttl probe (ping -c2)...")
            val h = TraceRunner.probe(target, ttl)
            if (h == null) {
                emit("ERROR: failed to run ping for hop $ttl.")
                break
            }
            hops.add(h)
            if (h.ip != null) resolved++
            val arrived = h.reached || (destIp != null && h.ip == destIp)
            if (arrived) destReached = true
            val line = if (!arrived && h.ip != null && h.ip == prevIp) {
                "${TraceRunner.formatHop(h)}  [same as hop ${ttl - 1}, typical for anycast/MPLS]"
            } else {
                TraceRunner.formatHop(h)
            }
            if (h.ip != null) prevIp = h.ip
            emit(line)
            if (arrived) {
                emit("Destination reached in $ttl hops. Stopping.")
                break
            }
            // Proven circulation stops the run; mere suspicion never does
            // (needs a second confirmation first) — moved here from TraceRunner.
            if (h.ip != null) {
                val confirmed = LoopDetector.detectConfirmed(hops)
                if (confirmed != null) {
                    emit(LoopDetector.verdictLine(confirmed))
                    emit("Note: stopping early, loop confirmed at hop $ttl (further probes would repeat the cycle).")
                    loopStopped = true
                    break
                }
            }
            if (ttl == maxHops) emit("Max hops reached.")
        }
        if (resolved == 0) {
            emit("Note: no hop answered. The destination may block ICMP, or this")
            emit("device's ping may ignore the TTL flag.")
        } else if (!loopStopped) {
            emit(LoopDetector.verdictLine(LoopDetector.analyze(hops, destReached, maxHops)))
        }
    }

    private fun <T, R : Any> List<T>.lastNotNullOfOrNull(selector: (T) -> R?): R? {
        for (i in lastIndex downTo 0) {
            val v = selector(this[i])
            if (v != null) return v
        }
        return null
    }
}
