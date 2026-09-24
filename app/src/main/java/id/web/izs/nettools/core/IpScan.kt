package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.util.concurrent.atomic.AtomicInteger

/**
 * Universal ping scanner without root: single IP, last-octet range
 * (192.168.1.1-50), or CIDR (/24 down to /32). Empty target sweeps
 * the device's own /24. Results stream live as hosts answer.
 *
 * UP lines carry the neighbor MAC from `/proc/net/arp` (a successful ping
 * always leaves an ARP entry, so the lookup is reliable) plus a `[gw]` flag
 * on the default gateway — which for WiFi clients is the AP itself.
 */
object IpScan {

    private const val DEFAULT_PARALLEL = 32
    private val rttRegex = Regex("""time=([\d.]+)\s*ms""")

    data class OwnNet(val ip: String, val prefix: Short, val base24: String)

    fun ownNetwork(): OwnNet? {
        return try {
            val ifs = NetworkInterface.getNetworkInterfaces()?.toList() ?: return null
            for (ni in ifs) {
                if (!ni.isUp || ni.isLoopback) continue
                for (addr in ni.interfaceAddresses) {
                    val ip = addr.address
                    if (ip is Inet4Address && ip.isSiteLocalAddress) {
                        val prefix = addr.networkPrefixLength
                        val parts = ip.hostAddress?.split(".") ?: continue
                        if (parts.size != 4) continue
                        return OwnNet(parts.joinToString("."), prefix, parts.take(3).joinToString("."))
                    }
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    /** The [NetworkInterface] that owns the device's LAN IPv4, if any. */
    fun lanInterface(): NetworkInterface? {
        val own = ownNetwork() ?: return null
        return try {
            NetworkInterface.getByInetAddress(InetAddress.getByName(own.ip))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Directed broadcast for [own]'s real prefix. Hardcoding a `.255` suffix
     * is only correct on /24 — e.g. `192.168.1.50/25` broadcasts to
     * `192.168.1.127`, not `.255`. Null on /31–/32, which have no broadcast.
     */
    fun directedBroadcast(own: OwnNet): String? {
        val o = octets(own.ip) ?: return null
        val prefix = own.prefix.toInt()
        if (prefix < 0 || prefix > 32 || prefix >= 31) return null
        val ipInt = ((o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong())
        val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
        val bcast = ipInt or mask.inv()
        return listOf(24, 16, 8, 0).joinToString(".") { s -> ((bcast shr s) and 0xFF).toInt().toString() }
    }

    /** Single ping. Returns RTT string like "0.42 ms", or null when unreachable. */
    private fun pingOnce(ip: String, waitSec: Int): String? {
        var proc: Process? = null
        return try {
            proc = ProcessBuilder(ExecUtil.pingBin(), "-c", "1", "-W", waitSec.toString(), ip)
                .redirectErrorStream(true)
                .start()
            // waitFor first — readText() blocks until EOF (= process exit), which
            // would make any later timeout unreachable on a hung ping. ping -c1
            // output is tiny (<< pipe buffer), so waiting first is safe.
            val finished = proc.waitFor((waitSec + 2).toLong(), java.util.concurrent.TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return null
            }
            val out = proc.inputStream.bufferedReader().readText()
            if (proc.exitValue() != 0) return null
            rttRegex.find(out)?.groupValues?.get(1)?.let { "$it ms" } ?: "reply"
        } catch (_: Exception) {
            null
        } finally {
            try { proc?.destroy() } catch (_: Exception) { }
        }
    }

    private suspend fun reverseDns(ip: String): String? = withTimeoutOrNull(1200) {
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            try {
                InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip }
            } catch (_: Exception) {
                null
            }
        }
    }

    /** Pure UP-line formatter (unit-testable): neighbor MAC + gateway flag. */
    fun formatUp(ip: String, name: String?, rtt: String, mac: String?, isGateway: Boolean): String {
        val label = if (name != null) " ($name)" else ""
        val macPart = if (mac != null) "  [$mac]" else ""
        val gwPart = if (isGateway) "  [gw]" else ""
        return "UP  $ip$label  $rtt$macPart$gwPart"
    }

    data class Parsed(val hosts: List<String>, val label: String)

    /** Collapse consecutive IPs into compact ranges: 192.168.0.2-99. */
    fun compactRanges(ips: List<Long>): List<String> {
        if (ips.isEmpty()) return emptyList()
        fun s4(v: Long) = listOf(24, 16, 8, 0).map { ((v shr it) and 0xFF).toInt() }
        val out = mutableListOf<String>()
        var start = ips[0]
        var prev = ips[0]
        fun flush() {
            val a = s4(start)
            val b = s4(prev)
            out.add(
                if (start == prev) a.joinToString(".")
                else if (a.take(3) == b.take(3)) "${a.take(3).joinToString(".")}.${a[3]}-${b[3]}"
                else "${a.joinToString(".")}-${b.joinToString(".")}"
            )
        }
        for (i in 1..ips.lastIndex) {
            val v = ips[i]
            if (v == prev + 1) prev = v
            else { flush(); start = v; prev = v }
        }
        flush()
        return out
    }

    private fun ipToLong(ip: String): Long {
        val o = ip.split(".").map { it.toIntOrNull() ?: return Long.MAX_VALUE }
        if (o.size != 4) return Long.MAX_VALUE
        return (o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong()
    }

    private fun octets(s: String): List<Int>? {        val p = s.split(".")
        if (p.size != 4) return null
        val n = p.map { it.toIntOrNull() ?: return null }
        if (n.any { it !in 0..255 }) return null
        return n
    }

    /** Accepts "1.2.3.4", "1.2.3.4/24", "1.2.3.10-50". Throws with a user message when invalid. */
    fun parseRange(raw: String, own: OwnNet?): Parsed {
        val t = raw.trim()
        if (t.isEmpty()) {
            val o = own ?: throw IllegalArgumentException(
                "no WiFi/LAN IPv4 found on this device. Connect to WiFi first, or type a range like 192.168.1.1-50."
            )
            return Parsed((1..254).map { "${o.base24}.$it" }, "${o.base24}.0/24 (auto)")
        }
        // Last-octet range: 192.168.1.10-50
        Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3})\.(\d{1,3})-(\d{1,3})$""")
            .matchEntire(t)?.let { m ->
                val base = octets(m.groupValues[1] + ".0")?.take(3)?.joinToString(".")
                    ?: throw IllegalArgumentException("bad network in '$t'.")
                var a = m.groupValues[2].toInt()
                var b = m.groupValues[3].toInt()
                if (a !in 0..255 || b !in 0..255) throw IllegalArgumentException("range must be 0-255.")
                if (a > b) { val tmp = a; a = b; b = tmp }
                val hosts = (a..b).map { "$base.$it" }
                if (hosts.size > 1024) throw IllegalArgumentException("range too large (max ~1000 hosts).")
                return Parsed(hosts, "$base.$a-$b")
            }
        // IP with optional /prefix.
        Regex("""^(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?:/(\d{1,2}))?$""")
            .matchEntire(t)?.let { m ->
                val o = octets(m.groupValues[1]) ?: throw IllegalArgumentException("bad IP in '$t'.")
                val prefix = m.groupValues[2].toIntOrNull() ?: if (o[3] == 0) 24 else 32
                if (prefix !in 24..32) throw IllegalArgumentException("only /24.. /32 supported (use a narrower range).")
                if (prefix == 32) return Parsed(listOf(o.joinToString(".")), o.joinToString("."))
                val ipInt = ((o[0].toLong() shl 24) or (o[1].toLong() shl 16) or (o[2].toLong() shl 8) or o[3].toLong())
                val mask = (0xFFFFFFFFL shl (32 - prefix)) and 0xFFFFFFFFL
                val net = ipInt and mask
                val bcast = net or mask.inv() and 0xFFFFFFFFL
                fun s4(v: Long) = listOf(24, 16, 8, 0).map { ((v shr it) and 0xFF).toInt() }.joinToString(".")
                val hosts = if (prefix == 31) listOf(s4(net), s4(bcast))
                    else ((net + 1)..<bcast).map { s4(it) }
                return Parsed(hosts, "${s4(net)}/$prefix")
            }
        throw IllegalArgumentException("use IP, A.B.C.X-Y, or A.B.C.D/N (e.g. 192.168.1.1-50).")
    }

    fun sweep(
        rawTarget: String,
        timeoutMs: Int,
        onProgress: ((String) -> Unit)? = null,
        maxParallel: Int = DEFAULT_PARALLEL,
        showOffline: Boolean = false,
        showMac: Boolean = false,
        /** Active network's DNS servers for explicit PTR (bypasses Private DNS). */
        dnsServers: List<String> = emptyList()
    ): Flow<String> =
        callbackFlow {
            if (!ExecUtil.pingAvailable()) {
                trySend("ERROR: ping binary not found on this device.")
                close()
                return@callbackFlow
            }
            val waitSec = minOf(maxOf(timeoutMs / 1000, 1), 2)
            val own = ownNetwork()
            val parsed = try {
                parseRange(rawTarget, own)
            } catch (e: IllegalArgumentException) {
                trySend("ERROR: ${e.message}")
                close()
                return@callbackFlow
            }
            // Never ping ourselves: always replies, just noise.
            val skippedOwn = parsed.hosts.filter { it == own?.ip }
            val targets = parsed.hosts.filter { it != own?.ip }
            if (targets.isEmpty()) {
                trySend(";; nothing to scan (${parsed.label})")
                close()
                return@callbackFlow
            }
            trySend(";; IP scan on ${parsed.label} [backend: ping -c1 + NetBIOS${if (showMac) " + ARP" else ""}, no root]")
            if (own != null) trySend(";; this device: ${own.ip}/${own.prefix}")
            if (skippedOwn.isNotEmpty()) trySend(";; skipping own IP (${skippedOwn.joinToString(",")})")
            val gw = GatewayResolver.resolve()
            val gatewayIp = gw?.ip
            if (gatewayIp != null) {
                val src = when (gw.source) {
                    GatewayResolver.Source.ROUTE_TABLE -> "route table"
                    GatewayResolver.Source.GUESS_DOT_ONE -> "guess .1 — confirm it's your router"
                }
                trySend(";; gateway: $gatewayIp [$src]")
            } else {
                trySend(";; gateway: not found (no route table, no LAN IPv4)")
            }
            if (showMac && ArpWatcher.read() == null) trySend(";; note: /proc/net/arp unreadable — UP lines carry no MAC")
            trySend(";; pinging ${targets.size} hosts...\n")
            val done = AtomicInteger(0)
            val found = AtomicInteger(0)
            val noReply = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            noReply.addAll(targets)
            val sem = Semaphore(maxParallel.coerceIn(8, 256))
            val startedAt = System.currentTimeMillis()
            // Run the sweep as children of the flow's own coroutine — no
            // detached thread + runBlocking. Cancelling the collecting job
            // (Stop) now cancels every in-flight ping instead of leaving an
            // orphan sweep running in the background.
            try {
                coroutineScope {
                    targets.map { ip ->
                        async(Dispatchers.IO) {
                            sem.withPermit {
                                val rtt = pingOnce(ip, waitSec)
                                if (rtt != null) {
                                    found.incrementAndGet()
                                    noReply.remove(ip)
                                    val name = reverseDns(ip)
                                        ?: DnsPtr.query(ip, dnsServers)
                                        ?: NetBios.queryName(ip)
                                        ?: MdnsDiscover.queryHost(ip)
                                    // An answered ping always leaves an ARP entry,
                                    // so the MAC lookup right after is reliable.
                                    val mac = if (showMac) ArpWatcher.read()?.get(ip)?.takeIf { it.complete }?.mac else null
                                    trySend(formatUp(ip, name, rtt, mac, ip == gatewayIp))
                                }
                                val d = done.incrementAndGet()
                                if (d % 25 == 0 || d == targets.size) {
                                    onProgress?.invoke("Scan $d/${targets.size}...")
                                }
                            }
                        }
                    }.awaitAll()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                trySend("ERROR: scan aborted mid-run.")
            } finally {
                val secs = (System.currentTimeMillis() - startedAt) / 1000.0
                val up = found.get()
                val dead = noReply.sortedBy { ipToLong(it) }
                if (showOffline) {
                    trySend(";; done: $up up, ${dead.size} no-reply in ${"%.1f".format(secs)}s")
                } else {
                    trySend(";; done: $up up in ${"%.1f".format(secs)}s")
                }
                if (showOffline && dead.isNotEmpty()) {
                    // Compact RTO list: consecutive IPs collapse to 192.168.0.2-99.
                    val ranges = compactRanges(dead.map { ipToLong(it) })
                    val listed = ranges.take(200)
                    listed.chunked(6).forEach { chunk ->
                        trySend("RTO: ${chunk.joinToString(", ")}")
                    }
                    if (ranges.size > listed.size) trySend("RTO: ... +${ranges.size - listed.size} more ranges")
                }
                close()
            }
        }.flowOn(Dispatchers.IO)
}
