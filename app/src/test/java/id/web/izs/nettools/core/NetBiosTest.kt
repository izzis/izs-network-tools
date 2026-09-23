package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetBiosTest {

    @Test
    fun starNameEncodesTo32Bytes() {
        val e = NetBios.encodeName("*")
        assertEquals(32, e.size)
        // '*' = 0x2A -> 'C' (2+'A') 'K' (10+'A')
        assertEquals('C'.code.toByte(), e[0])
        assertEquals('K'.code.toByte(), e[1])
        // space = 0x20 -> 'C' 'A'
        assertEquals('C'.code.toByte(), e[2])
        assertEquals('A'.code.toByte(), e[3])
    }

    @Test
    fun queryShape() {
        val q = NetBios.buildQuery(0x1234)
        assertEquals(0x12.toByte(), q[0])
        assertEquals(0x34.toByte(), q[1])
        assertEquals(1.toByte(), q[5]) // QDCOUNT
        assertEquals(0x00.toByte(), q[12 + 32]) // zero terminator
        assertEquals(0x21.toByte(), q[12 + 32 + 2]) // NBSTAT
    }

    private fun nodeStatusReply(firstName: String, numNames: Int = 1): ByteArray {
        val head = byteArrayOf(
            0x12, 0x34, // tid
            0x84.toByte(), 0x00, // flags: response
            0x00, 0x00, // QDCOUNT
            0x00, 0x01, // ANCOUNT
            0x00, 0x00, 0x00, 0x00
        )
        val rr = byteArrayOf(
            0xC0.toByte(), 0x0C, // compressed name pointer
            0x00, 0x21, // NBSTAT
            0x00, 0x01, // IN
            0x00, 0x00, 0x00, 0x00 // TTL
        )
        val names = ByteArray(numNames * 18)
        if (numNames > 0) {
            val raw = firstName.toByteArray(Charsets.US_ASCII)
            raw.copyInto(names, 0, 0, minOf(raw.size, 15))
            names[15] = 0x00 // suffix
            names[16] = 0x04 // flags: unique
        }
        val rdLen = (1 + names.size + 6)
        val rdata = byteArrayOf(numNames.toByte()) + names + ByteArray(6) // + stats
        val rrLen = byteArrayOf(0x00, rdLen.toByte())
        return head + rr + rrLen + rdata
    }

    @Test
    fun firstNameParsed() {
        val r = nodeStatusReply("MYPCLAPTOP     ")
        assertEquals("MYPCLAPTOP", NetBios.parseName(r, r.size))
    }

    @Test
    fun zeroNamesRejected() {
        val r = nodeStatusReply("", 0)
        assertNull(NetBios.parseName(r, r.size))
    }

    @Test
    fun truncatedRejected() {
        val r = nodeStatusReply("MYPCLAPTOP     ")
        assertNull(NetBios.parseName(r, 10))
    }
}
