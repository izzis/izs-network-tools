package id.web.izs.nettools.core

import id.web.izs.nettools.model.HopInfo
import org.junit.Assert.assertTrue
import org.junit.Test

class LoopDetectorTest {

    private fun hop(ttl: Int, ip: String?): HopInfo = HopInfo(ttl, ip, null, null, false)

    @Test
    fun normalTraceHasNoLoop() {
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "172.16.0.1"),
            hop(4, "8.8.8.8")
        )
        val r = LoopDetector.analyze(hops, destReached = true, maxHops = 20)
        assertTrue(r is LoopResult.NoLoop)
    }

    @Test
    fun consecutiveDuplicateIsNotLoop() {
        // Anycast/MPLS case: same IP twice in a row must not flag.
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "10.0.0.1"),
            hop(4, "8.8.8.8")
        )
        val r = LoopDetector.analyze(hops, destReached = true, maxHops = 20)
        assertTrue(r is LoopResult.NoLoop)
    }

    @Test
    fun nonConsecutiveRepeatIsLoop() {
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "10.0.0.2"),
            hop(4, "10.0.0.1"),
            hop(5, "10.0.0.2")
        )
        val r = LoopDetector.analyze(hops, destReached = false, maxHops = 20)
        assertTrue(r is LoopResult.Loop)
    }

    @Test
    fun alternatingCycleIsLoop() {
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.1.1.1"),
            hop(3, "10.2.2.2"),
            hop(4, "10.1.1.1"),
            hop(5, "10.2.2.2")
        )
        val r = LoopDetector.analyze(hops, destReached = false, maxHops = 20)
        assertTrue(r is LoopResult.Loop)
    }

    @Test
    fun fullLengthWithoutDestinationIsSuspected() {
        val hops = (1..10).map { hop(it, "10.0.0.$it") }
        val r = LoopDetector.analyze(hops, destReached = false, maxHops = 10)
        assertTrue(r is LoopResult.Suspected)
    }

    @Test
    fun silentRunIsNotLoop() {
        val hops = (1..5).map { hop(it, null) }
        val r = LoopDetector.analyze(hops, destReached = false, maxHops = 20)
        assertTrue(r is LoopResult.NoLoop)
    }

    @Test
    fun singleRepeatIsSuspicionOnlyNeverStops() {
        // One non-consecutive repeat must NOT stop the run — it needs
        // a second confirmation. End-of-run verdict still flags it.
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "10.0.0.2"),
            hop(4, "10.0.0.1")
        )
        assertTrue(LoopDetector.detectConfirmed(hops) == null)
        assertTrue(LoopDetector.analyze(hops, destReached = false, maxHops = 20) is LoopResult.Loop)
    }

    @Test
    fun tripleSeenIpConfirmsAndStops() {
        val soFar = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "10.0.0.2"),
            hop(4, "10.0.0.1")
        )
        assertTrue(LoopDetector.detectConfirmed(soFar) == null)
        // Came back a second time: proven circulation, stop now.
        val confirmed = soFar + hop(5, "10.0.0.3") + hop(6, "10.0.0.1")
        val stop = LoopDetector.detectConfirmed(confirmed)
        assertTrue(stop is LoopResult.Loop)
        assertTrue(stop!!.message.contains("10.0.0.1"))
    }

    @Test
    fun consecutiveTripleIsNotProof() {
        // Same IP 3x in a row (long anycast run) proves nothing.
        val hops = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.0.0.1"),
            hop(3, "10.0.0.1"),
            hop(4, "10.0.0.1"),
            hop(5, "8.8.8.8")
        )
        assertTrue(LoopDetector.detectConfirmed(hops) == null)
    }

    @Test
    fun singleCycleDoesNotStopDoubleCycleDoes() {
        val single = listOf(
            hop(1, "192.168.1.1"),
            hop(2, "10.1.1.1"),
            hop(3, "10.2.2.2"),
            hop(4, "10.1.1.1"),
            hop(5, "10.2.2.2")
        )
        assertTrue(LoopDetector.detectConfirmed(single) == null)
        val doubled = single + hop(6, "10.1.1.1") + hop(7, "10.2.2.2")
        assertTrue(LoopDetector.detectConfirmed(doubled) is LoopResult.Loop)
    }

    @Test
    fun confirmedDetectionNeverSuspects() {
        // Full-length-without-destination is an end-of-run verdict only.
        val hops = (1..10).map { hop(it, "10.0.0.$it") }
        assertTrue(LoopDetector.detectConfirmed(hops) == null)
    }

    @Test
    fun verdictLineRoundTrip() {
        assertTrue(LoopDetector.verdictOfLine("LOOP DETECTED: 10.0.0.1 seen at hops 2, 4") is LoopResult.Loop)
        assertTrue(LoopDetector.verdictOfLine("Suspected loop or filtering: max hops") is LoopResult.Suspected)
        assertTrue(LoopDetector.verdictOfLine("No loop: 4 unique hops, destination reached.") is LoopResult.NoLoop)
        assertTrue(LoopDetector.verdictOfLine(" 3  10.0.0.1  1.2 ms") == null)
    }

    @Test
    fun binaryHopLineParsing() {
        assertTrue(TraceRunner.parseBinaryHopLine(" 3  10.0.0.1  1.234 ms") == "10.0.0.1")
        assertTrue(TraceRunner.parseBinaryHopLine(" 4  * * *") == null)
        assertTrue(TraceRunner.parseBinaryHopLine("traceroute to 8.8.8.8 (8.8.8.8)") == null)
    }
}
