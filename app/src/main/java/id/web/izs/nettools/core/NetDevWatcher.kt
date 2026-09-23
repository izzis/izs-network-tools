package id.web.izs.nettools.core

import android.net.TrafficStats

/**
 * Interface traffic-rate sampling without root.
 *
 * Two sources, best first:
 * 1. [TrafficStats] — plain Android API, readable by any app, no permission,
 *    no root. Survives the `/proc` lockdown on modern Android (API 29+
 *    SELinux rules hide `/proc/net` paths from apps on many ROMs).
 * 2. `/proc/net/dev` fallback (world-readable on older ROMs and Linux).
 * Two samples around a measurement window give RX/TX packets/sec. RX alone
 * is the flood signal; TX is the alibi check — [TrafficStats] is
 * device-wide, so a download over mobile data also raises RX. A storm flood
 * is one-way (RX≫TX, TX is just our own pings); downloads always carry TX
 * ACKs. Pure parsing ([parse]) is unit-testable; [read] needs
 * a device (TrafficStats stubs throw on JVM unit tests — never call it there).
 */
object NetDevWatcher {

    data class Sample(
        val atMs: Long,
        val rxPackets: Long,
        val rxBytes: Long,
        val txPackets: Long,
        val txBytes: Long
    )

    /** Parse Linux `/proc/net/dev` text: summed RX/TX over non-`lo` interfaces. */
    fun parse(text: String, atMs: Long = System.currentTimeMillis()): Sample {
        var rxP = 0L
        var rxB = 0L
        var txP = 0L
        var txB = 0L
        for (raw in text.lines()) {
            val name = raw.substringBefore(':').trim()
            if (name.isEmpty() || name == "lo" || !raw.contains(':')) continue
            if (!name[0].isLetterOrDigit()) continue
            val cols = raw.substringAfter(':').trim().split(Regex("\\s+"))
            if (cols.size < 2) continue
            // Header lines ("packets", "bytes") never reach here: they lack
            // a colon, and interface rows always start with an iface name.
            // Transmit counters sit at columns 8-9 when present.
            rxB += cols[0].toLongOrNull() ?: continue
            rxP += cols[1].toLongOrNull() ?: 0L
            if (cols.size >= 10) {
                txB += cols[8].toLongOrNull() ?: 0L
                txP += cols[9].toLongOrNull() ?: 0L
            }
        }
        return Sample(atMs, rxP, rxB, txP, txB)
    }

    fun read(): Sample? {
        readTrafficStats()?.let { return it }
        return try {
            java.io.File("/proc/net/dev").takeIf { it.canRead() }?.readText()?.let { parse(it) }
        } catch (_: Exception) {
            null
        }
    }

    /** Device-wide counters via the Android API. Null when unsupported. */
    fun readTrafficStats(): Sample? = try {
        val rxP = TrafficStats.getTotalRxPackets()
        val rxB = TrafficStats.getTotalRxBytes()
        val txP = TrafficStats.getTotalTxPackets()
        val txB = TrafficStats.getTotalTxBytes()
        val bad = TrafficStats.UNSUPPORTED.toLong()
        if (rxP == bad || rxB == bad || txP == bad || txB == bad) null
        else Sample(System.currentTimeMillis(), rxP, rxB, txP, txB)
    } catch (_: Exception) {
        null // JVM unit tests (android.jar stubs) land here — callers handle null.
    }

    /** RX packets/sec between two samples. Null when clocks/counters are unusable. */
    fun rxPps(before: Sample?, after: Sample?): Double? =
        rate(before, after) { it.rxPackets }

    /** TX packets/sec between two samples. Null when clocks/counters are unusable. */
    fun txPps(before: Sample?, after: Sample?): Double? =
        rate(before, after) { it.txPackets }

    private fun rate(before: Sample?, after: Sample?, sel: (Sample) -> Long): Double? {
        if (before == null || after == null) return null
        val dtSec = (after.atMs - before.atMs) / 1000.0
        if (dtSec <= 0) return null
        val d = sel(after) - sel(before)
        if (d < 0) return null // counter wrapped/reset mid-window
        return d / dtSec
    }
}
