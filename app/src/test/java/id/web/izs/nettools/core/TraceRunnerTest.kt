package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRunnerTest {

    @Test
    fun numericReplyMarksReached() {
        val p = TraceRunner.parseProbeOutput(
            "PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.\n" +
                "64 bytes from 8.8.8.8: icmp_seq=1 ttl=115 time=10.5 ms\n"
        )
        assertEquals("8.8.8.8", p.ip)
        assertEquals(10.5, p.rttMs!!, 0.001)
        assertTrue(p.reached)
    }

    @Test
    fun hostnameReplyWithParenthesizedIpIsNotGarbage() {
        // The old fromIp regex captured the hex prefix of the hostname
        // ("dns.google" -> "d") and then skipped the bytesFrom branch,
        // so reached never became true.
        val p = TraceRunner.parseProbeOutput(
            "64 bytes from dns.google (8.8.8.8): icmp_seq=1 ttl=115 time=5.0 ms\n"
        )
        assertEquals("8.8.8.8", p.ip)
        assertTrue(p.reached)
    }

    @Test
    fun bareHostnameReplyStillReaches() {
        val p = TraceRunner.parseProbeOutput(
            "64 bytes from edge.example.com: icmp_seq=1 ttl=55 time=5.0 ms\n"
        )
        assertEquals("edge.example.com", p.ip)
        assertTrue(p.reached)
    }

    @Test
    fun ttlExceededLineIsIntermediateHopOnly() {
        val p = TraceRunner.parseProbeOutput(
            "From 192.168.1.1 (192.168.1.1) icmp_seq=1 Time to live exceeded\n"
        )
        assertEquals("192.168.1.1", p.ip)
        assertFalse(p.reached)
        assertNull(p.rttMs)
    }

    @Test
    fun ttlExceededWithHostnameKeepsParenthesizedIp() {
        val p = TraceRunner.parseProbeOutput(
            "From router.lan (192.168.1.254) icmp_seq=1 Time to live exceeded\n"
        )
        assertEquals("192.168.1.254", p.ip)
        assertFalse(p.reached)
    }

    @Test
    fun replyThenExceededPrefersLastValidIp() {
        val p = TraceRunner.parseProbeOutput(
            "64 bytes from 10.0.0.1: icmp_seq=1 ttl=64 time=1.0 ms\n" +
                "From 10.0.0.2 (10.0.0.2) icmp_seq=2 Time to live exceeded\n"
        )
        assertEquals("10.0.0.2", p.ip)
        assertTrue(p.reached) // any echo reply during the probe = arrival signal
    }

    @Test
    fun emptyOutputIsNullHop() {
        val p = TraceRunner.parseProbeOutput("")
        assertNull(p.ip)
        assertNull(p.rttMs)
        assertFalse(p.reached)
    }
}
