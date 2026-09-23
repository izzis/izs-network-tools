package id.web.izs.nettools.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

/**
 * NetBIOS Node Status (UDP 137) without root.
 *
 * Asks a host directly for the names it advertises — the first entry is
 * normally the Windows machine name. Unicast, one datagram each way, so
 * it works for any host with an IP (unlike MNDP it cannot see IP-less
 * devices). Used by IP Scan as a hostname fallback when reverse DNS
 * draws a blank.
 *
 * Pure [encodeName]/[buildQuery]/[parseName] are unit-testable; only
 * [queryName] touches sockets.
 */
object NetBios {

    const val PORT = 137
    private const val NBSTAT = 0x21

    /**
     * Encode a 15-char name + 1 suffix byte into the 32-byte 'A'-'P'
     * alphabet (RFC 1002 §4.2.1.1). The adapter-status query name is
     * `"*"` + suffix `0x00`.
     */
    fun encodeName(name: String, suffix: Byte = 0x00): ByteArray {
        val raw = ByteArray(16)
        val chars = name.uppercase().toByteArray(Charsets.US_ASCII)
        val n = minOf(chars.size, 15)
        chars.copyInto(raw, 0, 0, n)
        for (i in n until 15) raw[i] = 0x20 // space padding
        raw[15] = suffix
        val out = ByteArray(32)
        for (i in raw.indices) {
            val b = raw[i].toInt() and 0xFF
            out[i * 2] = ((b shr 4) + 'A'.code).toByte()
            out[i * 2 + 1] = ((b and 0x0F) + 'A'.code).toByte()
        }
        return out
    }

    /** Build one Node Status request. [tid] echoes back in the reply. */
    fun buildQuery(tid: Int): ByteArray {
        val name = encodeName("*")
        val q = ByteArray(12 + 32 + 1 + 2 + 2)
        q[0] = (tid shr 8).toByte()
        q[1] = tid.toByte()
        // flags 0x0000 (query), QDCOUNT 1, AN/NS/AR 0
        q[5] = 1
        name.copyInto(q, 12)
        var o = 12 + 32 + 1 // +1 leaves the zero terminator (already 0)
        q[o++] = 0x00
        q[o++] = NBSTAT.toByte()
        q[o++] = 0x00
        q[o] = 0x01 // IN
        return q
    }

    /**
     * Parse a Node Status reply into the first advertised name, or null.
     * Handles a compressed answer name (`0xC0…` pointer) and zero names.
     */
    fun parseName(buf: ByteArray, len: Int): String? {
        if (len < 12 || len > buf.size) return null
        var o = 12
        // Answer RR name: pointer (2B) or labels terminated by zero.
        if (o + 2 > len) return null
        if ((buf[o].toInt() and 0xC0) == 0xC0) {
            o += 2
        } else {
            while (true) {
                if (o >= len) return null
                val l = buf[o++].toInt() and 0xFF
                if (l == 0) break
                if (l and 0xC0 == 0xC0) { o++; break }
                o += l
                if (o > len) return null
            }
        }
        if (o + 10 > len) return null
        val type = ((buf[o].toInt() and 0xFF) shl 8) or (buf[o + 1].toInt() and 0xFF)
        if (type != NBSTAT) return null
        val rdLen = ((buf[o + 8].toInt() and 0xFF) shl 8) or (buf[o + 9].toInt() and 0xFF)
        o += 10
        if (rdLen < 1 || o + rdLen > len) return null
        if ((buf[o].toInt() and 0xFF) < 1) return null // Num_Names == 0
        if (o + 1 + 15 > len) return null
        val name = buf.copyOfRange(o + 1, o + 16).toString(Charsets.US_ASCII)
            .trim { it == ' ' || it == '\u0000' }
        return name.ifEmpty { null }
    }

    /** Ask [ip] for its machine name. Null = silent / not Windows / blocked. */
    fun queryName(ip: String, timeoutMs: Int = 400): String? = try {
        val tid = (Math.random() * 0xFFFF).toInt()
        val req = buildQuery(tid)
        DatagramSocket().use { s ->
            s.soTimeout = timeoutMs.coerceIn(200, 2000)
            s.send(DatagramPacket(req, req.size, InetAddress.getByName(ip), PORT))
            val buf = ByteArray(1500)
            val pkt = DatagramPacket(buf, buf.size)
            s.receive(pkt)
            // Replies echo our transaction ID; anything else is a stray datagram.
            if (pkt.length < 2 || ((buf[0].toInt() and 0xFF) shl 8) or (buf[1].toInt() and 0xFF) != tid) null
            else parseName(pkt.data, pkt.length)
        }
    } catch (_: Exception) {
        null
    }
}
