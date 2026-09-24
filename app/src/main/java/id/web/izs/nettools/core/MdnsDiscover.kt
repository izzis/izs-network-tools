package id.web.izs.nettools.core

import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.MulticastSocket

/**
 * mDNS / Bonjour discovery (RFC 6762) without root.
 *
 * Printers, Chromecasts, cameras, HomeKit gadgets and friends announce
 * themselves on `224.0.0.251:5353/udp` — like MNDP, an ordinary multicast
 * socket hears them, no raw socket needed. Unlike MNDP these devices
 * always have IPs (they need one to serve anything), so mDNS answers the
 * "who else is on my LAN" half of Neighbor while MNDP covers the
 * "even without IP" half.
 *
 * We ask for the service enumeration plus a short list of common service
 * types, then correlate PTR/SRV/TXT/A records. Pure [parseMessage] is
 * unit-testable (including DNS name-compression pointers); only
 * [discover] touches sockets.
 */
object MdnsDiscover {

    private const val GROUP = "224.0.0.251"
    const val PORT = 5353

    /** Enumeration first, then the household usual suspects. */
    private val QUERIES = listOf(
        "_services._dns-sd._udp.local",
        "_http._tcp.local",
        "_printer._tcp.local",
        "_ipp._tcp.local",
        "_ipps._tcp.local",
        "_googlecast._tcp.local",
        "_hap._tcp.local",
        "_airplay._tcp.local",
        "_smb._tcp.local",
        "_ssh._tcp.local"
    )

    data class Service(
        val instance: String,
        val host: String?,
        val ip: String?,
        val port: Int?,
        val txt: Map<String, String>
    )

    data class Parsed(val services: List<Service>, val hosts: List<Pair<String, String>>)

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    /** Read a possibly-compressed domain name. Returns (name, offset after). */
    private fun readName(b: ByteArray, len: Int, off: Int): Pair<String, Int>? {
        val labels = mutableListOf<String>()
        var o = off
        var jumps = 0
        var end = -1
        while (true) {
            if (o >= len) return null
            val l = b[o].toInt() and 0xFF
            when {
                l == 0 -> {
                    o++
                    break
                }
                l and 0xC0 == 0xC0 -> {
                    if (o + 1 >= len || ++jumps > 8) return null
                    if (end < 0) end = o + 2
                    o = ((l and 0x3F) shl 8) or (b[o + 1].toInt() and 0xFF)
                }
                else -> {
                    if (o + 1 + l > len) return null
                    labels += b.copyOfRange(o + 1, o + 1 + l).toString(Charsets.UTF_8)
                    o += 1 + l
                }
            }
        }
        return labels.joinToString(".") to (if (end >= 0) end else o)
    }

    private data class Rec(val owner: String, val type: Int, val data: ByteArray, val off: Int)

    /** Parse every answer record of one mDNS message (questions skipped). */
    fun parseMessage(buf: ByteArray, len: Int): Parsed {
        if (len < 12 || len > buf.size) return Parsed(emptyList(), emptyList())
        val qd = u16(buf, 4)
        val totalAn = u16(buf, 6) + u16(buf, 8) + u16(buf, 10)
        var o = 12
        repeat(qd) {
            val n = readName(buf, len, o) ?: return Parsed(emptyList(), emptyList())
            o = n.second + 4 // QTYPE + QCLASS
            if (o > len) return Parsed(emptyList(), emptyList())
        }
        val recs = mutableListOf<Rec>()
        // A malformed record stops parsing instead of retrying at the same
        // offset (return@repeat would re-read the broken position forever).
        for (i in 0 until totalAn) {
            val n = readName(buf, len, o) ?: break
            o = n.second
            if (o + 10 > len) break
            val type = u16(buf, o)
            val rdLen = u16(buf, o + 8)
            o += 10
            if (rdLen < 0 || o + rdLen > len) break
            recs += Rec(n.first, type, buf.copyOfRange(o, o + rdLen), o)
            o += rdLen
        }
        val ptrs = mutableListOf<Pair<String, String>>() // (service type key, instance)
        val srvs = mutableMapOf<String, Pair<String, Int>>() // instance key -> (host, port)
        val txts = mutableMapOf<String, Map<String, String>>()
        val addrs = mutableMapOf<String, String>() // host key -> ip
        for (r in recs) {
            when (r.type) {
                // Names inside RDATA may be compression pointers whose OFFSET
                // is relative to the whole message (RFC 1035 §4.1.4) — parse
                // them against the original packet at r.off, not the rdata
                // copy, or a C00C-style pointer lands past the copy and the
                // record is dropped.
                12 -> readName(buf, len, r.off)?.let { ptrs += r.owner.lowercase() to it.first } // PTR
                33 -> { // SRV: pri + weight + port + target
                    if (r.data.size >= 6) {
                        val port = u16(r.data, 4)
                        readName(buf, len, r.off + 6)?.let { srvs[r.owner.lowercase()] = it.first to port }
                    }
                }
                16 -> { // TXT: <len><bytes> strings, k=v on first '='
                    val map = linkedMapOf<String, String>()
                    var p = 0
                    while (p < r.data.size) {
                        val l = r.data[p].toInt() and 0xFF
                        p++
                        if (p + l > r.data.size) break
                        val s = r.data.copyOfRange(p, p + l).toString(Charsets.UTF_8)
                        p += l
                        val eq = s.indexOf('=')
                        if (eq > 0) map[s.take(eq)] = s.drop(eq + 1) else if (s.isNotEmpty()) map[s] = ""
                    }
                    if (map.isNotEmpty()) txts[r.owner.lowercase()] = map
                }
                1 -> if (r.data.size == 4) { // A
                    addrs[r.owner.lowercase()] = r.data.joinToString(".") { (it.toInt() and 0xFF).toString() }
                }
                28 -> if (r.data.size == 16) { // AAAA
                    addrs[r.owner.lowercase()] = (0 until 8).joinToString(":") { g ->
                        "%x".format(u16(r.data, g * 2))
                    }
                }
            }
        }
        // DNS names are case-insensitive: correlate on lowercase keys while
        // keeping the first-seen original spelling for display.
        val services = ptrs.mapNotNull { (_, instance) ->
            if (instance.isEmpty()) null
            else {
                val ikey = instance.lowercase()
                val (host, port) = srvs[ikey] ?: (null to null)
                val ip = host?.let { addrs[it.lowercase()] }
                Service(instance, host, ip, port, txts[ikey] ?: emptyMap())
            }
        }.distinctBy { it.instance.lowercase() }
        val usedHosts = services.mapNotNull { it.host?.lowercase() }.toSet()
        val hosts = addrs.filterKeys { it !in usedHosts }.map { (h, ip) -> h to ip }
        return Parsed(services, hosts)
    }

    /** Pure line formatters (unit-testable). */
    fun formatService(s: Service): String {
        val parts = mutableListOf("MDNS ${s.instance}")
        s.host?.let { parts += it }
        parts += (s.ip ?: "(no IP)")
        s.port?.let { parts += ":$it" }
        s.txt.entries.take(2).forEach { (k, v) -> parts += "[$k=${v.take(24)}]" }
        return parts.joinToString(" ")
    }

    fun formatHost(host: String, ip: String) = "MDNS $host $ip"

    /**
     * Ask one host directly for its name (unicast, QU bit — no multicast
     * lock needed, no broadcast heard). Sends the service queries straight
     * to [ip]:5353 and returns the first hostname that resolves back to
     * that same IP. Strict on purpose: a service *type* is not a name, so
     * anything that doesn't map to [ip] is ignored instead of displayed.
     * Null = silent / no mDNS / blocked. Used per host by IP Scan.
     */
    fun queryHost(ip: String, timeoutMs: Int = 600, port: Int = PORT): String? = try {
        DatagramSocket().use { s ->
            val budget = timeoutMs.coerceIn(200, 2000)
            s.soTimeout = 500
            val addr = InetAddress.getByName(ip)
            for (q in QUERIES) {
                try {
                    val qb = buildQuery(q)
                    s.send(DatagramPacket(qb, qb.size, addr, port))
                } catch (_: Exception) {
                }
            }
            val deadline = System.currentTimeMillis() + budget
            val buf = ByteArray(9000)
            while (System.currentTimeMillis() < deadline) {
                try {
                    val pkt = DatagramPacket(buf, buf.size)
                    s.receive(pkt)
                    val parsed = parseMessage(pkt.data, pkt.length)
                    for (sv in parsed.services) {
                        if (sv.ip == ip) return sv.host ?: sv.instance
                    }
                    for ((h, hip) in parsed.hosts) {
                        if (hip == ip) return h
                    }
                } catch (_: java.net.SocketTimeoutException) {
                } catch (_: Exception) {
                    break
                }
            }
            null
        }
    } catch (_: Exception) {
        null
    }

    private fun buildQuery(name: String): ByteArray {
        val labels = name.split(".").map { it.toByteArray(Charsets.UTF_8) }
        val qnameLen = labels.sumOf { it.size + 1 } + 1
        val q = ByteArray(12 + qnameLen + 4)
        q[5] = 1 // QDCOUNT
        var o = 12
        for (l in labels) {
            q[o++] = l.size.toByte()
            l.copyInto(q, o)
            o += l.size
        }
        o++ // zero terminator (already 0)
        q[o++] = 0x00
        q[o++] = 0x0C // PTR
        q[o++] = 0x80.toByte() // QU bit: please answer unicast…
        q[o] = 0x01 // …class IN (multicast listeners hear it anyway)
        return q
    }

    /**
     * Ask for local services and collect answers for [listenMs] (2–10 s).
     * Needs WiFi multicast to actually arrive — hence the lock. Joins and
     * queries are pinned to the LAN [NetworkInterface] when one is found,
     * so a cellular/VPN default route cannot steal the traffic.
     */
    fun discover(
        wifi: WifiManager?,
        onProgress: ((String) -> Unit)? = null,
        listenMs: Int = 3000
    ): Flow<String> = callbackFlow {
        trySend(";; mDNS discovery on $GROUP:$PORT [backend: udp multicast, no root]")
        val budget = listenMs.coerceIn(2000, 10000)
        val lock = MulticastLock.acquire(wifi, "izs-mdns")
        if (lock == null) trySend(";; note: multicast lock unavailable — mDNS results may be partial")
        // Hand the socket between the reader thread and awaitClose without a
        // data race; interrupt() cannot unblock DatagramSocket.receive, so
        // Stop works by closing this socket.
        val sockRef = java.util.concurrent.atomic.AtomicReference<MulticastSocket?>(null)
        val lockReleased = java.util.concurrent.atomic.AtomicBoolean(false)
        fun releaseLockOnce() {
            if (lockReleased.compareAndSet(false, true)) MulticastLock.release(lock)
        }
        val reader = Thread {
            try {
                val group = InetAddress.getByName(GROUP)
                val iface = IpScan.lanInterface()
                val s = try {
                    MulticastSocket(PORT).apply {
                        soTimeout = 500
                        if (iface != null) {
                            joinGroup(java.net.InetSocketAddress(group, PORT), iface)
                            setNetworkInterface(iface)
                        } else {
                            joinGroup(group)
                        }
                    }
                } catch (e: Exception) {
                    trySend("ERROR: cannot bind UDP :$PORT (${e.message}) — another listener may hold it.")
                    return@Thread
                }
                sockRef.set(s)
                // IPv6 mDNS on FF02::FB%lan — best-effort, skipped when the
                // interface has no IPv6 scope available.
                val v6group = if (iface != null) {
                    try {
                        java.net.InetAddress.getByName("FF02::FB%${iface.index}")
                    } catch (_: Exception) {
                        null
                    }
                } else null
                if (v6group != null && iface != null) {
                    try {
                        s.joinGroup(java.net.InetSocketAddress(v6group, PORT), iface)
                    } catch (_: Exception) {
                    }
                }
                for (q in QUERIES) {
                    val qb = buildQuery(q)
                    try {
                        s.send(DatagramPacket(qb, qb.size, group, PORT))
                    } catch (_: Exception) {
                    }
                    if (v6group != null) {
                        try {
                            s.send(DatagramPacket(qb, qb.size, v6group, PORT))
                        } catch (_: Exception) {
                        }
                    }
                }
                val seenServices = mutableSetOf<String>()
                val seenHosts = mutableSetOf<String>()
                var nServices = 0
                var nHosts = 0
                val deadline = System.currentTimeMillis() + budget
                val buf = ByteArray(9000) // mDNS datagrams run large (TXT blobs)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        val parsed = parseMessage(pkt.data, pkt.length)
                        for (sv in parsed.services) {
                            if (seenServices.add(sv.instance.lowercase())) {
                                nServices++
                                trySend(formatService(sv))
                            }
                        }
                        for ((h, ip) in parsed.hosts) {
                            if (seenHosts.add("$h|$ip")) {
                                nHosts++
                                trySend(formatHost(h, ip))
                            }
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (_: Exception) {
                        break
                    }
                    val left = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    onProgress?.invoke("Listening mDNS... ${left}s")
                }
                trySend(";; done: $nServices service(s), $nHosts host(s) in ${budget / 1000}s")
            } catch (e: Exception) {
                trySend("ERROR: mDNS listen failed (${e.message})")
            } finally {
                try {
                    sockRef.get()?.close()
                } catch (_: Exception) {
                }
                close()
            }
        }
        reader.isDaemon = true
        reader.start()
        awaitClose {
            // Closing the socket is what actually unblocks receive() — an
            // interrupt() would not. Join the reader before releasing the
            // multicast lock so WiFi filtering cannot resume under a live
            // listener; release runs exactly once either way.
            try {
                sockRef.get()?.close()
            } catch (_: Exception) {
            }
            try {
                reader.join(1500)
            } catch (_: Exception) {
            }
            releaseLockOnce()
        }
    }.flowOn(Dispatchers.IO)
}
