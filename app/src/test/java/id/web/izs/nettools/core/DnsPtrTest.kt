package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.xbill.DNS.DClass
import org.xbill.DNS.Name
import org.xbill.DNS.PTRRecord

class DnsPtrTest {

    private fun ptr(target: String) = PTRRecord(
        Name.fromString("91.32.168.192.in-addr.arpa."),
        DClass.IN, 3600,
        Name.fromString("$target.")
    )

    @Test
    fun firstPtrTargetReturned() {
        assertEquals("EPSONFAE901", DnsPtr.firstName(arrayOf(ptr("EPSONFAE901"))))
    }

    @Test
    fun emptyAndNullAreNull() {
        assertNull(DnsPtr.firstName(null))
        assertNull(DnsPtr.firstName(emptyArray()))
    }

    @Test
    fun invalidIpIsNull() {
        assertNull(DnsPtr.queryServer("not-an-ip", "192.168.32.1", 300))
    }

    @Test
    fun emptyServerListIsNull() {
        assertNull(DnsPtr.query("192.168.32.91", emptyList()))
    }
}
