package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NetDevWatcherTest {

    private val sample = "Inter-|   Receive                                                |  Transmit\n" +
        " face |bytes    packets errs drop fifo frame compressed multicast|bytes    packets errs drop fifo colls carrier compressed\n" +
        "    lo: 1000      10    0    0    0     0          0         0      1000      10    0    0    0     0       0          0\n" +
        " eth0: 2000     100    0    0    0     0          0         0      3000     150    0    0    0     0       0          0\n" +
        " wlan0: 4000     300    0    0    0     0          0         0      5000     250    0    0    0     0       0          0\n"

    @Test
    fun sumsNonLoopbackInterfaces() {
        val s = NetDevWatcher.parse(sample, atMs = 0L)
        assertEquals(400L, s.rxPackets) // 100 + 300, lo excluded
        assertEquals(6000L, s.rxBytes)
        assertEquals(400L, s.txPackets) // 150 + 250
        assertEquals(8000L, s.txBytes)
    }

    @Test
    fun ppsAcrossWindow() {
        val before = NetDevWatcher.parse(sample, atMs = 0L)
        val after = NetDevWatcher.Sample(
            20_000L, before.rxPackets + 40_000, before.rxBytes + 1_000_000,
            before.txPackets + 100, before.txBytes + 10_000
        )
        assertEquals(2000.0, NetDevWatcher.rxPps(before, after)!!, 0.001)
        assertEquals(5.0, NetDevWatcher.txPps(before, after)!!, 0.001)
    }

    @Test
    fun ppsNullOnBadInput() {
        val before = NetDevWatcher.parse(sample, atMs = 0L)
        assertNull(NetDevWatcher.rxPps(null, before))
        assertNull(NetDevWatcher.rxPps(before, NetDevWatcher.Sample(0L, 0, 0, 0, 0))) // zero/negative dt
    }

    @Test
    fun mismatchedCounterSourcesYieldNullRate() {
        // TrafficStats vs /proc/net/dev crossing domains would invent a
        // fake flood — the rate must refuse to compute.
        val procSample = NetDevWatcher.parse(sample, atMs = 0L) // source = "proc"
        val trafficSample = NetDevWatcher.Sample(
            10_000L, 400_000, 6_000_000, 400, 80_000,
            NetDevWatcher.SOURCE_TRAFFIC
        )
        assertNull(NetDevWatcher.rxPps(procSample, trafficSample))
        assertNull(NetDevWatcher.txPps(trafficSample, procSample))
    }
}
