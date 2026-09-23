package id.web.izs.nettools.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.SocketTimeoutException

/**
 * MikroTik neighbor discovery (MNDP) without root.
 *
 * Every RouterOS/SwOS device broadcasts to `255.255.255.255:5678/udp`,
 * roughly every 30 s plus a burst when the link comes up — even on an
 * interface with **no IP address yet**, in which case the IPv4 TLV is
 * simply absent but MAC + identity are still there. Sending the 4-byte
 * refresh frame back to the broadcast address makes neighbors answer
 * immediately instead of waiting for their next announcement.
 *
 * Wire format (matches Wireshark's `packet-mndp.c`): 4-byte header
 * (2 unknown + 2 seqno), then TLVs of u16be type + u16be length.
 * Known types: 1 = MAC, 5 = identity, 7 = version, 8 = platform,
 * 10 = uptime (u32 *little-endian*, the only LE value), 11 = software-id,
 * 12 = board, 15 = IPv6, 16 = interface name, 17 = IPv4. Unknown TLVs
 * are skipped so future RouterOS fields can't break parsing.
 *
 * Plain [parse] is unit-testable; only [discover] touches sockets.
 * No IP of our own is needed: the socket binds a local port and talks
 * broadcast, so this also works while the phone itself has no address.
 * MikroTik-only by nature — other vendors' CDP/LLDP are pure-L2
 * EtherTypes and need raw sockets (root).
 */
object MndpDiscover {

    const val PORT = 5678
    private val REFRESH = byteArrayOf(0, 0, 0, 0)

    data class Neighbor(
        val mac: String,
        val identity: String?,
        val version: String?,
        val platform: String?,
        val board: String?,
        val ifname: String?,
        val ipv4: String?,
        val uptimeSec: Long?
    )

    private fun u16be(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    private fun macOf(v: ByteArray) = v.joinToString(":") { "%02x".format(it) }

    /**
     * Parse one MNDP datagram (`len` bytes of `buf`). Returns null when the
     * frame is truncated, fails the heuristic header check, or carries no
     * MAC TLV (a neighbor without MAC is not a neighbor).
     */
    fun parse(buf: ByteArray, len: Int): Neighbor? {
        if (len < 8 || len > buf.size) return null
        // Same heuristic as Wireshark: TLV type high bytes must be zero.
        if (buf[4] != 0.toByte() || buf[6] != 0.toByte()) return null
        var mac: String? = null
        var identity: String? = null
        var version: String? = null
        var platform: String? = null
        var board: String? = null
        var ifname: String? = null
        var ipv4: String? = null
        var uptimeSec: Long? = null
        var o = 4 // skip 2-byte header + 2-byte sequence
        while (o + 4 <= len) {
            val type = u16be(buf, o)
            val tlvLen = u16be(buf, o + 2)
            o += 4
            if (tlvLen < 0 || o + tlvLen > len) break
            val v = buf.copyOfRange(o, o + tlvLen)
            when (type) {
                1 -> if (tlvLen == 6) mac = macOf(v)
                5 -> identity = v.toString(Charsets.UTF_8)
                7 -> version = v.toString(Charsets.UTF_8)
                8 -> platform = v.toString(Charsets.UTF_8)
                10 -> if (tlvLen == 4) {
                    uptimeSec = ((v[0].toLong() and 0xFF)) or
                        ((v[1].toLong() and 0xFF) shl 8) or
                        ((v[2].toLong() and 0xFF) shl 16) or
                        ((v[3].toLong() and 0xFF) shl 24)
                }
                12 -> board = v.toString(Charsets.UTF_8)
                16 -> ifname = v.toString(Charsets.UTF_8)
                17 -> if (tlvLen == 4) ipv4 = v.joinToString(".") { (it.toInt() and 0xFF).toString() }
                // 11 = software-id, 15 = IPv6, and any future type: skipped.
            }
            o += tlvLen
        }
        val m = mac ?: return null
        return Neighbor(m, identity, version, platform, board, ifname, ipv4, uptimeSec)
    }

    /** Pure line formatter (unit-testable). No-IP neighbors say so explicitly. */
    fun formatLine(n: Neighbor): String {
        val parts = mutableListOf("MNDP ${n.mac}")
        n.identity?.let { parts += it }
        n.version?.let { parts += it }
        n.board?.let { parts += "($it)" }
        parts += (n.ipv4 ?: "(no IP)")
        return parts.joinToString(" ")
    }

    /**
     * Broadcast one refresh and collect answers for [listenMs] (2–10 s).
     * Needs no target and no local IP — just a link that carries broadcast.
     */
    fun discover(
        onProgress: ((String) -> Unit)? = null,
        listenMs: Int = 4000
    ): Flow<String> = callbackFlow {
        trySend(";; MNDP discovery on 255.255.255.255:$PORT [backend: udp broadcast, no root]")
        val budget = listenMs.coerceIn(2000, 10000)
        var sock: DatagramSocket? = null
        val reader = Thread {
            try {
                val s = try {
                    DatagramSocket(null).apply {
                        reuseAddress = true
                        broadcast = true
                        soTimeout = 500
                        bind(InetSocketAddress(PORT))
                    }
                } catch (e: Exception) {
                    trySend("ERROR: cannot bind UDP :$PORT (${e.message}) — another scanner may hold the port.")
                    return@Thread
                }
                sock = s
                // Nudge neighbors to answer now instead of at their next announcement.
                try {
                    s.send(DatagramPacket(REFRESH, REFRESH.size, InetAddress.getByName("255.255.255.255"), PORT))
                } catch (_: Exception) {
                }
                // Directed broadcast too: some stacks drop limited broadcast.
                IpScan.ownNetwork()?.let { own ->
                    try {
                        s.send(DatagramPacket(REFRESH, REFRESH.size, InetAddress.getByName("${own.base24}.255"), PORT))
                    } catch (_: Exception) {
                    }
                }
                val seen = linkedMapOf<String, Neighbor>()
                val deadline = System.currentTimeMillis() + budget
                val buf = ByteArray(1500)
                while (System.currentTimeMillis() < deadline) {
                    try {
                        val pkt = DatagramPacket(buf, buf.size)
                        s.receive(pkt)
                        val n = parse(pkt.data, pkt.length) ?: continue
                        if (seen.putIfAbsent(n.mac, n) == null) trySend(formatLine(n))
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                        break
                    }
                    val left = ((deadline - System.currentTimeMillis()) / 1000).toInt().coerceAtLeast(0)
                    onProgress?.invoke("Listening MNDP... ${left}s")
                }
                if (seen.isEmpty()) {
                    trySend(";; no MikroTik neighbors heard — non-MikroTik APs don't speak MNDP")
                }
                trySend(";; done: ${seen.size} MikroTik neighbor(s) in ${budget / 1000}s")
            } catch (e: Exception) {
                trySend("ERROR: MNDP listen failed (${e.message})")
            } finally {
                try {
                    sock?.close()
                } catch (_: Exception) {
                }
                close()
            }
        }
        reader.isDaemon = true
        reader.start()
        awaitClose {
            try {
                sock?.close()
            } catch (_: Exception) {
            }
            try {
                reader.interrupt()
            } catch (_: Exception) {
            }
        }
    }.flowOn(Dispatchers.IO)
}
