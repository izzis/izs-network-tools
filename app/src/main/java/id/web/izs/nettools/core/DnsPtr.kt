package id.web.izs.nettools.core

import org.xbill.DNS.Lookup
import org.xbill.DNS.PTRRecord
import org.xbill.DNS.Record
import org.xbill.DNS.ReverseMap
import org.xbill.DNS.SimpleResolver
import org.xbill.DNS.Type
import java.time.Duration

/**
 * Reverse-DNS (PTR) against explicit servers without root.
 *
 * The system resolver is at the mercy of the phone's DNS setup: Private
 * DNS or a guest-WiFi DNS bypasses the office DNS that actually holds the
 * LAN's PTR records (which is why a wired desktop sees names the phone
 * doesn't). Asking the active network's own DNS servers directly via
 * dnsjava sidesteps that — plain UDP/53 to a LAN server still works.
 *
 * Pure [firstName] is unit-testable; [queryServer]/[query] touch sockets.
 * Used by IP Scan between the system lookup and NetBIOS.
 */
object DnsPtr {

    /** First PTR target in [records], trailing dot trimmed. Null when none. */
    fun firstName(records: Array<Record>?): String? {
        records ?: return null
        for (r in records) {
            if (r is PTRRecord) {
                val name = r.target.toString().trimEnd('.')
                if (name.isNotEmpty()) return name
            }
        }
        return null
    }

    /** Ask one DNS server for the PTR of [ip]. Null on any failure. */
    fun queryServer(ip: String, server: String, timeoutMs: Int = 1000): String? = try {
        val rev = try {
            ReverseMap.fromAddress(ip)
        } catch (_: Exception) {
            return null
        }
        val lookup = Lookup(rev, Type.PTR)
        lookup.setResolver(SimpleResolver(server).apply { timeout = Duration.ofMillis(timeoutMs.coerceIn(300, 3000).toLong()) })
        firstName(lookup.run())
    } catch (_: Exception) {
        null
    }

    /** Try each server in order; first hit wins. Empty list = no-op. */
    fun query(ip: String, servers: List<String>): String? {
        for (s in servers.take(3)) {
            queryServer(ip, s)?.let { return it }
        }
        return null
    }
}
