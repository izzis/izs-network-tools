package id.web.izs.nettools.core

/**
 * ARP-table polling without root.
 *
 * `/proc/net/arp` is world-readable on most devices (verified on Linux and
 * typical Android ROMs; when unreadable the caller must say so instead of
 * faking a clean bill). Pure parsing ([parse]) is unit-testable.
 *
 * A switching loop makes the gateway MAC flap: the same IP answers from
 * different MACs across samples, or the entry churns through `incomplete`.
 */
object ArpWatcher {

    data class Entry(val ip: String, val mac: String?, val complete: Boolean)

    sealed interface ArpResult {
        data object Stable : ArpResult
        data class Flap(val ip: String, val macs: List<String>) : ArpResult
        data object Incomplete : ArpResult
        data object Unreadable : ArpResult
    }

    private val zeroMac = Regex("^(00:){5}00$")

    /** Parse Linux `/proc/net/arp` text into IP -> entry. */
    fun parse(text: String): Map<String, Entry> {
        val out = linkedMapOf<String, Entry>()
        for (raw in text.lines()) {
            val cols = raw.trim().split(Regex("\\s+"))
            if (cols.size < 6) continue
            val ip = cols[0]
            if (ip == "IP" || !ip[0].isDigit()) continue
            val flags = cols[2]
            val macRaw = cols[3].lowercase()
            val complete = flags != "0x0" && !zeroMac.matches(macRaw)
            out[ip] = Entry(ip, if (complete) macRaw else null, complete)
        }
        return out
    }

    fun read(): Map<String, Entry>? = try {
        java.io.File("/proc/net/arp").takeIf { it.canRead() }?.readText()?.let { parse(it) }
    } catch (_: Exception) {
        null
    }

    /**
     * Compare gateway entries across time-ordered [samples].
     * Two or more distinct MACs for [gatewayIp] = flap (loop symptom).
     * No complete entry in any sample = incomplete (churn or silent LAN).
     */
    fun detectFlap(samples: List<Map<String, Entry>>, gatewayIp: String): ArpResult {
        if (samples.isEmpty()) return ArpResult.Unreadable
        val macs = samples.mapNotNull { it[gatewayIp]?.takeIf { e -> e.complete }?.mac }.distinct()
        if (macs.size >= 2) return ArpResult.Flap(gatewayIp, macs)
        if (macs.isEmpty()) return ArpResult.Incomplete
        return ArpResult.Stable
    }
}
