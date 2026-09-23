package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StormDetectorTest {

    private fun stats(
        sent: Int = 20,
        received: Int = 20,
        dups: Int = 0,
        rtts: List<Double> = List(20) { 1.0 + it * 0.1 }
    ) = StormDetector.PingStats(sent, received, dups, rtts)

    private val stable = ArpWatcher.ArpResult.Stable
    private val gw = "192.168.1.1"

    @Test
    fun healthyGatewayIsNoStorm() {
        val r = StormDetector.analyze(stats(), stable, gw)
        assertTrue(r is StormResult.NoStorm)
    }

    @Test
    fun silentGatewayWithUnreachableAndQuietIsNotStorm() {
        val s = stats(sent = 20, received = 0, rtts = emptyList()).copy(rxPps = 5.0, netUnreachable = true)
        val r = StormDetector.analyze(s, stable, gw)
        assertTrue(r is StormResult.NoStorm)
    }

    @Test
    fun silentGatewayWithTimeoutsOnlyIsSuspected() {
        // A storm can kill every reply — but so can a dead cable. Ambiguous.
        val s = stats(sent = 20, received = 0, rtts = emptyList()).copy(rxPps = 5.0)
        val r = StormDetector.analyze(s, stable, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun silentGatewayUnderFloodIsStorm() {
        val s = stats(sent = 20, received = 0, rtts = emptyList()).copy(rxPps = 5000.0, txPps = 5.0)
        val r = StormDetector.analyze(s, stable, gw)
        assertTrue(r is StormResult.Storm)
        assertTrue((r as StormResult.Storm).message.contains("5000"))
    }

    @Test
    fun silentGatewayWithTwoWayTrafficIsNotFlood() {
        // Heavy RX *with* TX ACKs = downloads over another interface
        // (TrafficStats is device-wide), not a one-way storm flood.
        val s = stats(sent = 20, received = 0, rtts = emptyList()).copy(rxPps = 5000.0, txPps = 400.0)
        val r = StormDetector.analyze(s, stable, gw)
        assertTrue(r is StormResult.Suspected)
        assertTrue(!(r is StormResult.Storm))
    }

    @Test
    fun silentGatewayWithBusyTrafficIsSuspected() {
        val s = stats(sent = 20, received = 0, rtts = emptyList()).copy(rxPps = 400.0)
        val r = StormDetector.analyze(s, stable, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun doubleDupIsStorm() {
        val r = StormDetector.analyze(stats(dups = 2), stable, gw)
        assertTrue(r is StormResult.Storm)
        assertTrue((r as StormResult.Storm).message.contains("DUP"))
    }

    @Test
    fun singleDupIsSuspicionOnly() {
        val r = StormDetector.analyze(stats(dups = 1), stable, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun explodedRttWithLossIsStorm() {
        val rtts = List(18) { 600.0 + it } // 18/20 answered, all ~600ms
        val r = StormDetector.analyze(stats(received = 18, rtts = rtts), stable, gw)
        assertTrue(r is StormResult.Storm)
    }

    @Test
    fun slowGatewayAloneIsSuspected() {
        val rtts = List(20) { 150.0 }
        val r = StormDetector.analyze(stats(rtts = rtts), stable, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun flapWithAnomalyIsStorm() {
        val flap = ArpWatcher.ArpResult.Flap(gw, listOf("aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02"))
        val r = StormDetector.analyze(stats(rtts = List(20) { 150.0 }), flap, gw)
        assertTrue(r is StormResult.Storm)
        assertTrue((r as StormResult.Storm).message.contains("aa:bb:cc:dd:ee:01"))
    }

    @Test
    fun flapAloneIsSuspected() {
        val flap = ArpWatcher.ArpResult.Flap(gw, listOf("aa:bb:cc:dd:ee:01", "aa:bb:cc:dd:ee:02"))
        val r = StormDetector.analyze(stats(), flap, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun incompleteArpWithAnswersIsSuspected() {
        val r = StormDetector.analyze(stats(), ArpWatcher.ArpResult.Incomplete, gw)
        assertTrue(r is StormResult.Suspected)
    }

    @Test
    fun pingLineParsing() {
        val dup = StormDetector.parsePingLine("64 bytes from 192.168.1.1: icmp_seq=3 ttl=64 time=1.23 ms (DUP!)")
        assertEquals(1.23, dup!!.rttMs!!, 0.001)
        assertTrue(dup.dup)
        val ok = StormDetector.parsePingLine("64 bytes from 192.168.1.1: icmp_seq=1 ttl=64 time=0.42 ms")
        assertTrue(ok != null && !ok.dup)
        assertNull(StormDetector.parsePingLine("PING 192.168.1.1 (192.168.1.1) 56(84) bytes of data."))
        assertNull(StormDetector.parsePingLine("Request timeout for icmp_seq=5"))
    }

    @Test
    fun verdictLineRoundTrip() {
        assertTrue(StormDetector.verdictOfLine("STORM DETECTED: 2 duplicate replies") is StormResult.Storm)
        assertTrue(StormDetector.verdictOfLine("Suspected storm: gateway slow") is StormResult.Suspected)
        assertTrue(StormDetector.verdictOfLine("No storm: gateway healthy") is StormResult.NoStorm)
        assertNull(StormDetector.verdictOfLine("64 bytes from 192.168.1.1: icmp_seq=1 time=0.4 ms"))
    }
}
