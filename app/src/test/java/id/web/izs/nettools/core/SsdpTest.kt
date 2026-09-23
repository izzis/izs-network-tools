package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SsdpTest {

    private val resp = "HTTP/1.1 200 OK\r\n" +
        "CACHE-CONTROL: max-age=1800\r\n" +
        "ST: upnp:rootdevice\r\n" +
        "USN: uuid:abc-123::upnp:rootdevice\r\n" +
        "LOCATION: http://192.168.1.1:1900/root.xml\r\n" +
        "SERVER: Linux/4.4 UPnP/1.0 MyRouter/2.1\r\n" +
        "\r\n"

    @Test
    fun responseParsed() {
        val h = SsdpDiscover.parseResponse(resp)!!
        assertEquals("http://192.168.1.1:1900/root.xml", h.location)
        assertEquals("upnp:rootdevice", h.st)
        assertEquals("uuid:abc-123::upnp:rootdevice", h.usn)
    }

    @Test
    fun lineFormat() {
        val h = SsdpDiscover.parseResponse(resp)!!
        assertEquals(
            "SSDP upnp:rootdevice http://192.168.1.1:1900/root.xml [Linux/4.4 UPnP/1.0 MyRouter/2.1]",
            SsdpDiscover.formatLine(h)
        )
    }

    @Test
    fun non200Rejected() {
        assertNull(SsdpDiscover.parseResponse("HTTP/1.1 404 Not Found\r\n\r\n"))
    }

    @Test
    fun missingLocationRejected() {
        assertNull(SsdpDiscover.parseResponse("HTTP/1.1 200 OK\r\nST: upnp:rootdevice\r\n\r\n"))
    }

    @Test
    fun headersCaseInsensitive() {
        val r = "HTTP/1.1 200 OK\r\nlocation: http://10.0.0.5/d.xml\r\nSt: roku:ecp\r\n\r\n"
        val h = SsdpDiscover.parseResponse(r)!!
        assertEquals("http://10.0.0.5/d.xml", h.location)
        assertEquals("roku:ecp", h.st)
    }
}
