package id.web.izs.nettools.core

/**
 * Default-gateway discovery without root.
 *
 * Pure parsing ([gatewayFromRouteTable]) is unit-testable; only [readRouteTable]
 * and [resolve] touch the filesystem. Resolution order:
 * 1. `/proc/net/route` default route (most honest),
 * 2. `<own /24>.1` guess from [IpScan.ownNetwork] (common convention),
 * 3. null when neither is available (caller must say so honestly).
 */
object GatewayResolver {

    enum class Source { ROUTE_TABLE, GUESS_DOT_ONE }

    data class GatewayInfo(val ip: String, val source: Source)

    /**
     * Parse Linux `/proc/net/route` text. The default route has
     * `Destination == 00000000`; its Gateway column is little-endian hex,
     * e.g. `0120A8C0` = bytes C0 A8 20 01 = 192.168.32.1.
     */
    fun gatewayFromRouteTable(text: String): String? {
        for (raw in text.lines()) {
            val cols = raw.trim().split(Regex("\\s+"))
            if (cols.size < 8) continue
            if (cols[0] == "Iface" || cols[0].isEmpty()) continue
            if (cols[1] != "00000000") continue
            return hexLeToIp(cols[2]) ?: continue
        }
        return null
    }

    private fun hexLeToIp(hex: String): String? {
        val v = hex.toLongOrNull(16) ?: return null
        // Low byte first: 0x0120A8C0 -> 192.168.32.1.
        val b0 = (v and 0xFF).toInt()
        val b1 = ((v shr 8) and 0xFF).toInt()
        val b2 = ((v shr 16) and 0xFF).toInt()
        val b3 = ((v shr 24) and 0xFF).toInt()
        return "$b0.$b1.$b2.$b3"
    }

    fun readRouteTable(): String? = try {
        java.io.File("/proc/net/route").takeIf { it.canRead() }?.readText()
    } catch (_: Exception) {
        null
    }

    /** Best-effort gateway: route table first, `.1` guess second, null last. */
    fun resolve(): GatewayInfo? {
        readRouteTable()?.let { gatewayFromRouteTable(it)?.let { ip -> return GatewayInfo(ip, Source.ROUTE_TABLE) } }
        IpScan.ownNetwork()?.let { return GatewayInfo("${it.base24}.1", Source.GUESS_DOT_ONE) }
        return null
    }

    /**
     * Same as [resolve] but with a step-by-step log for verbose console output.
     * Returns the gateway (null when none found) plus the log lines.
     */
    fun resolveVerbose(): Pair<GatewayInfo?, List<String>> {
        val lines = mutableListOf("checking /proc/net/route for a default route...")
        val route = readRouteTable()
        if (route == null) {
            lines += "/proc/net/route unreadable on this device"
        } else {
            val ip = gatewayFromRouteTable(route)
            if (ip != null) {
                lines += "default gateway $ip [route table]"
                return GatewayInfo(ip, Source.ROUTE_TABLE) to lines
            }
            lines += "no default-route entry, falling back to a guess..."
        }
        val own = IpScan.ownNetwork()
        if (own != null) {
            val guess = "${own.base24}.1"
            lines += "this device is ${own.ip}, guessing gateway $guess [.1 — confirm it's your router]"
            return GatewayInfo(guess, Source.GUESS_DOT_ONE) to lines
        }
        lines += "no LAN IPv4 on this device either"
        return null to lines
    }
}
