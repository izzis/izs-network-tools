package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MndpTest {

    private fun tlv(type: Int, value: ByteArray): ByteArray {
        val n = value.size
        return byteArrayOf((type shr 8).toByte(), type.toByte(), (n shr 8).toByte(), n.toByte()) + value
    }

    private fun fullPacket(): ByteArray {
        var p = byteArrayOf(0, 0, 0, 1) // header + seqno
        p += tlv(1, byteArrayOf(0x74, 0x4d, 0x28, 0x43, 0x24, 0x0b.toByte()))
        p += tlv(5, "MikroTik".toByteArray())
        p += tlv(7, "7.15".toByteArray())
        p += tlv(12, "RB750".toByteArray())
        p += tlv(17, byteArrayOf(192.toByte(), 168.toByte(), 88, 1))
        p += tlv(99, byteArrayOf(1, 2, 3)) // unknown type: must be skipped
        return p
    }

    @Test
    fun fullPacketParsed() {
        val p = fullPacket()
        val n = MndpDiscover.parse(p, p.size)!!
        assertEquals("74:4d:28:43:24:0b", n.mac)
        assertEquals("MikroTik", n.identity)
        assertEquals("7.15", n.version)
        assertEquals("RB750", n.board)
        assertEquals("192.168.88.1", n.ipv4)
    }

    @Test
    fun uptimeIsLittleEndian() {
        var p = byteArrayOf(0, 0, 0, 1)
        p += tlv(1, byteArrayOf(0, 0, 0, 0, 0, 1))
        p += tlv(10, byteArrayOf(0x04, 0x03, 0x02, 0x01))
        assertEquals(16909060L, MndpDiscover.parse(p, p.size)!!.uptimeSec)
    }

    @Test
    fun noIpNeighborStillParses() {
        var p = byteArrayOf(0, 0, 0, 1)
        p += tlv(1, byteArrayOf(0x74, 0x4d, 0x28, 0x43, 0x24, 0x0c.toByte()))
        p += tlv(5, "ap-toko".toByteArray())
        val n = MndpDiscover.parse(p, p.size)!!
        assertNull(n.ipv4)
        assertTrue(MndpDiscover.formatLine(n).contains("(no IP)"))
    }

    @Test
    fun fullLineFormat() {
        val p = fullPacket()
        val n = MndpDiscover.parse(p, p.size)!!
        assertEquals("MNDP 74:4d:28:43:24:0b MikroTik 7.15 (RB750) 192.168.88.1", MndpDiscover.formatLine(n))
    }

    @Test
    fun truncatedPacketRejected() {
        val p = fullPacket()
        assertNull(MndpDiscover.parse(p, 7))
        assertNull(MndpDiscover.parse(p.copyOfRange(0, 10), 10))
    }

    @Test
    fun missingMacRejected() {
        var p = byteArrayOf(0, 0, 0, 1)
        p += tlv(5, "ghost".toByteArray())
        assertNull(MndpDiscover.parse(p, p.size))
    }

    @Test
    fun nonMndpHeaderRejected() {
        val p = fullPacket()
        p[4] = 0x45 // looks like an IPv4 header, not a TLV type
        assertNull(MndpDiscover.parse(p, p.size))
    }
}
