package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class MdnsTest {

    /** Builds a response; AN3's owner is a compression pointer (exercises it). */
    private fun response(): ByteArray {
        val out = mutableListOf<Byte>()
        fun raw(vararg b: Int) = out.addAll(b.map { it.toByte() })
        fun u16(v: Int) = raw(v shr 8, v and 0xFF)
        fun name(vararg labels: String) {
            for (l in labels) {
                raw(l.length)
                out.addAll(l.toByteArray(Charsets.UTF_8).toList())
            }
            raw(0)
        }
        fun rrHeader(type: Int, rdLen: Int) {
            u16(type); u16(1); raw(0, 0, 0, 120); u16(rdLen)
        }
        raw(0, 0, 0x84, 0x00, 0, 0, 0, 4, 0, 0, 0, 0) // flags response, AN=4
        // AN1: PTR _http._tcp.local -> Printer._http._tcp.local
        name("_http", "_tcp", "local")
        val target = mutableListOf<Byte>()
        for (l in listOf("Printer", "_http", "_tcp", "local")) {
            target.add(l.length.toByte())
            target.addAll(l.toByteArray(Charsets.UTF_8).toList())
        }
        target.add(0)
        rrHeader(12, target.size)
        out.addAll(target)
        // AN2: SRV Printer._http._tcp.local -> myprinter.local :80
        val srvOwner = out.size
        name("Printer", "_http", "_tcp", "local")
        val srv = mutableListOf<Byte>()
        srv.addAll(listOf<Byte>(0, 0, 0, 0, 0, 80)) // pri, weight, port
        for (l in listOf("myprinter", "local")) {
            srv.add(l.length.toByte())
            srv.addAll(l.toByteArray(Charsets.UTF_8).toList())
        }
        srv.add(0)
        rrHeader(33, srv.size)
        out.addAll(srv)
        // AN3: TXT with pointer owner -> note=hi
        raw(0xC0, srvOwner)
        val txt = byteArrayOf(7) + "note=hi".toByteArray()
        rrHeader(16, txt.size)
        out.addAll(txt.toList())
        // AN4: A myprinter.local -> 192.168.1.20
        name("myprinter", "local")
        rrHeader(1, 4)
        raw(192, 168, 1, 20)
        return out.toByteArray()
    }

    @Test
    fun recordsCorrelate() {
        val p = MdnsDiscover.parseMessage(response(), response().size)
        assertEquals(1, p.services.size)
        val s = p.services[0]
        assertEquals("Printer._http._tcp.local", s.instance)
        assertEquals("myprinter.local", s.host)
        assertEquals("192.168.1.20", s.ip)
        assertEquals(80, s.port)
        assertEquals("hi", s.txt["note"])
        assertTrue(p.hosts.isEmpty()) // myprinter.local is consumed by the service
    }

    @Test
    fun serviceLineFormat() {
        val p = MdnsDiscover.parseMessage(response(), response().size)
        assertEquals(
            "MDNS Printer._http._tcp.local myprinter.local 192.168.1.20 :80 [note=hi]",
            MdnsDiscover.formatService(p.services[0])
        )
    }

    private fun hostResponse(): ByteArray {
        val out = mutableListOf<Byte>()
        fun raw(vararg b: Int) = out.addAll(b.map { it.toByte() })
        fun u16(v: Int) = raw(v shr 8, v and 0xFF)
        fun name(vararg labels: String) {
            for (l in labels) {
                raw(l.length)
                out.addAll(l.toByteArray(Charsets.UTF_8).toList())
            }
            raw(0)
        }
        raw(0, 0, 0x84, 0x00, 0, 0, 0, 2, 0, 0, 0, 0) // AN=2
        name("Test", "_http", "_tcp", "local")
        val srv = mutableListOf<Byte>()
        srv.addAll(listOf<Byte>(0, 0, 0, 0, 0, 80)) // pri, weight, port
        for (l in listOf("testbox", "local")) {
            srv.add(l.length.toByte())
            srv.addAll(l.toByteArray(Charsets.UTF_8).toList())
        }
        srv.add(0)
        u16(33); u16(1); raw(0, 0, 0, 120); u16(srv.size)
        out.addAll(srv)
        name("testbox", "local")
        u16(1); u16(1); raw(0, 0, 0, 120); u16(4)
        raw(127, 0, 0, 1)
        return out.toByteArray()
    }

    @Test(timeout = 15000)
    fun queryHostFindsNameViaLoopbackResponder() {
        val answer = hostResponse()
        val server = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val t = Thread {
            try {
                server.soTimeout = 8000
                val buf = ByteArray(2048)
                val pkt = DatagramPacket(buf, buf.size)
                server.receive(pkt)
                server.send(DatagramPacket(answer, answer.size, pkt.address, pkt.port))
            } catch (_: Exception) {
            } finally {
                server.close()
            }
        }
        t.isDaemon = true
        t.start()
        assertEquals("testbox.local", MdnsDiscover.queryHost("127.0.0.1", 3000, server.localPort))
        t.join(5000)
    }

    @Test(timeout = 10000)
    fun queryHostSilentIsNull() {
        val s = DatagramSocket(0, InetAddress.getByName("127.0.0.1"))
        val port = s.localPort
        s.close() // nothing listens here anymore
        assertNull(MdnsDiscover.queryHost("127.0.0.1", 800, port))
    }

    @Test
    fun truncatedIsEmpty() {
        val r = response()
        val p = MdnsDiscover.parseMessage(r, 7)
        assertTrue(p.services.isEmpty() && p.hosts.isEmpty())
    }

    @Test
    fun garbageNeverCrashes() {
        val g = ByteArray(64) { it.toByte() }
        val p = MdnsDiscover.parseMessage(g, g.size)
        assertTrue(p.services.isEmpty() && p.hosts.isEmpty())
    }
}
