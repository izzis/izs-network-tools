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

/**
 * SSDP/UPnP discovery without root.
 *
 * Smart TVs, routers, NAS boxes and consoles answer `M-SEARCH` on
 * `239.255.255.250:1900/udp` — again plain UDP, no raw socket. Replies
 * come back unicast to our (ephemeral) port, so unlike the mDNS phase
 * there is no fixed-port conflict to handle; the multicast lock only
 * helps the query reach sleepy stacks.
 *
 * Pure [parseResponse] is unit-testable; only [discover] touches sockets.
 */
object SsdpDiscover {

    private const val GROUP = "239.255.255.250"
    const val PORT = 1900

    private fun msearch() = (
        "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: $GROUP:$PORT\r\n" +
            "MAN: \"ns=01\"\r\n" +
            "MX: 2\r\n" +
            "ST: ssdp:all\r\n" +
            "\r\n"
        ).toByteArray(Charsets.UTF_8)

    data class Hit(val location: String, val st: String, val usn: String?, val server: String?)

    private val statusOk = Regex("""^HTTP/\d(?:\.\d)?[ \t]+200(?:[ \t]|$)""")

    /**
     * Parse one M-SEARCH response. Null unless it is `200 OK` with
     * a LOCATION (a device without a description URL names nothing).
     */
    fun parseResponse(text: String): Hit? {
        val lines = text.lines()
        if (lines.isEmpty() || !statusOk.containsMatchIn(lines[0].trim())) return null
        val headers = mutableMapOf<String, String>()
        for (raw in lines.drop(1)) {
            val line = raw.trim()
            if (line.isEmpty()) break
            val colon = line.indexOf(':')
            if (colon > 0) headers[line.take(colon).trim().lowercase()] = line.drop(colon + 1).trim()
        }
        val location = headers["location"]?.ifEmpty { null } ?: return null
        val st = headers["st"] ?: "(no ST)"
        return Hit(location, st, headers["usn"], headers["server"])
    }

    /**
     * One physical device = one key. `ssdp:all` makes a compliant device
     * answer once per service description with a distinct USN but the same
     * LOCATION, so dedup must key on the USN's uuid prefix (or LOCATION),
     * never the full USN.
     */
    fun deviceKey(h: Hit): String =
        h.usn?.substringBefore("::")?.takeIf { it.isNotBlank() } ?: h.location

    /** Pure line formatter (unit-testable). */
    fun formatLine(h: Hit): String {
        var line = "SSDP ${h.st} ${h.location}"
        h.server?.trim()?.takeIf { it.isNotEmpty() }?.let {
            line += " [${it.take(48)}]"
        }
        return line
    }

    /**
     * Send M-SEARCH a few times, collect unicast answers for [listenMs].
     * Egress is pinned to the LAN interface when found (a cellular/VPN
     * default route must not swallow the M-SEARCH), with an extra
     * directed-broadcast copy for stacks that ignore multicast.
     */
    fun discover(
        wifi: WifiManager?,
        onProgress: ((String) -> Unit)? = null,
        listenMs: Int = 3000
    ): Flow<String> = callbackFlow {
        trySend(";; SSDP discovery on $GROUP:$PORT [backend: udp multicast, no root]")
        val budget = listenMs.coerceIn(2000, 10000)
        val lock = MulticastLock.acquire(wifi, "izs-ssdp")
        if (lock == null) trySend(";; note: multicast lock unavailable — SSDP results may be partial")
        // Socket handed between reader thread and awaitClose without a race;
        // Stop closes it — interrupt() cannot unblock receive().
        val sockRef = java.util.concurrent.atomic.AtomicReference<DatagramSocket?>(null)
        val lockReleased = java.util.concurrent.atomic.AtomicBoolean(false)
        fun releaseLockOnce() {
            if (lockReleased.compareAndSet(false, true)) MulticastLock.release(lock)
        }
        val reader = Thread {
            try {
                val group = InetAddress.getByName(GROUP)
                val iface = IpScan.lanInterface()
                val s = try {
                    java.net.MulticastSocket().apply {
                        soTimeout = 500
                        broadcast = true
                        if (iface != null) setNetworkInterface(iface)
                    }
                } catch (e: Exception) {
                    trySend("ERROR: cannot open UDP socket (${e.message}).")
                    return@Thread
                }
                sockRef.set(s)
                val bcast = IpScan.ownNetwork()?.let { IpScan.directedBroadcast(it) }
                val bcastAddr = bcast?.let {
                    try {
                        InetAddress.getByName(it)
                    } catch (_: Exception) {
                        null
                    }
                }
                val seen = mutableSetOf<String>()
                var n = 0
                val deadline = System.currentTimeMillis() + budget
                val buf = ByteArray(8192)
                var searches = 0
                var lastSearch = 0L
                fun sendSearch() {
                    val qb = msearch()
                    try {
                        s.send(DatagramPacket(qb, qb.size, group, PORT))
                    } catch (_: Exception) {
                    }
                    if (bcastAddr != null) {
                        try {
                            s.send(DatagramPacket(qb, qb.size, bcastAddr, PORT))
                        } catch (_: Exception) {
                        }
                    }
                    lastSearch = System.currentTimeMillis()
                    searches++
                }
                while (System.currentTimeMillis() < deadline) {
                    // Re-query every ~1.5 s: late joiners and lost datagrams.
                    if (System.currentTimeMillis() - lastSearch > 1500) sendSearch()
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        val text = pkt.data.copyOfRange(0, pkt.length).toString(Charsets.UTF_8)
                        val hit = parseResponse(text) ?: continue
                        if (seen.add(deviceKey(hit))) {
                            n++
                            trySend(formatLine(hit))
                        }
                    } catch (_: java.net.SocketTimeoutException) {
                    } catch (_: Exception) {
                        break
                    }
                    val left = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    onProgress?.invoke("Listening SSDP... ${left}s")
                }
                if (searches == 0) trySend(";; M-SEARCH never left the device")
                trySend(";; done: $n device(s) in ${budget / 1000}s")
            } catch (e: Exception) {
                trySend("ERROR: SSDP listen failed (${e.message})")
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
