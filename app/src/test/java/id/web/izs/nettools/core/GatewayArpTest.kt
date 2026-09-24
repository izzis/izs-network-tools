package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayArpTest {

    @Test
    fun routeTableDefaultGatewayParsed() {
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n" +
            "enp2s0\t00000000\t0120A8C0\t0003\t0\t0\t100\t00000000\t0\t0\t0\n" +
            "enp2s0\t0020A8C0\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n"
        assertEquals("192.168.32.1", GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun routeTableWithoutDefaultReturnsNull() {
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n" +
            "enp2s0\t0020A8C0\t00000000\t0001\t0\t0\t100\t00FFFFFF\t0\t0\t0\n"
        assertNull(GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun zeroGatewayDefaultIsSkipped() {
        // Point-to-point default (PPP/cellular/VPN): Gateway 00000000 must
        // never surface as "0.0.0.0" — fall through to the .1 guess instead.
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n" +
            "rmnet0\t00000000\t00000000\t0003\t0\t0\t100\t00000000\t0\t0\t0\n"
        assertNull(GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun nonGatewayFlagsAreSkipped() {
        // UP-only default row (no RTF_GATEWAY) is not a usable gateway.
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n" +
            "tun0\t00000000\t0100000A\t0001\t0\t0\t100\t00000000\t0\t0\t0\n"
        assertNull(GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun lowestMetricDefaultWinsAcrossInterfaces() {
        // Cellular row listed first with a high metric; WiFi row second with
        // a low metric — the WiFi gateway must win, not file order.
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n" +
            "rmnet_data0\t00000000\t0100000A\t0003\t0\t0\t2048\t00000000\t0\t0\t0\n" +
            "wlan0\t00000000\t0120A8C0\t0003\t0\t0\t10\t00000000\t0\t0\t0\n"
        assertEquals("192.168.32.1", GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun routeTableHeaderOnlyReturnsNull() {
        val text = "Iface\tDestination\tGateway \tFlags\tRefCnt\tUse\tMetric\tMask\t\tMTU\tWindow\tIRTT\n"
        assertNull(GatewayResolver.gatewayFromRouteTable(text))
    }

    @Test
    fun arpTableParsed() {
        val text = "IP address       HW type     Flags       HW address            Mask     Device\n" +
            "192.168.32.1     0x1         0x2         74:4d:28:43:24:0b     *        enp2s0\n" +
            "192.168.32.99    0x1         0x0         00:00:00:00:00:00     *        enp2s0\n"
        val t = ArpWatcher.parse(text)
        assertEquals("74:4d:28:43:24:0b", t["192.168.32.1"]!!.mac)
        assertTrue(t["192.168.32.1"]!!.complete)
        assertTrue(!(t["192.168.32.99"]!!.complete))
    }

    @Test
    fun stableMacIsStable() {
        val mk = { mac: String -> mapOf("192.168.1.1" to ArpWatcher.Entry("192.168.1.1", mac, true)) }
        val r = ArpWatcher.detectFlap(listOf(mk("aa:bb:cc:dd:ee:01"), mk("aa:bb:cc:dd:ee:01")), "192.168.1.1")
        assertTrue(r is ArpWatcher.ArpResult.Stable)
    }

    @Test
    fun changedMacIsFlap() {
        val mk = { mac: String -> mapOf("192.168.1.1" to ArpWatcher.Entry("192.168.1.1", mac, true)) }
        val r = ArpWatcher.detectFlap(listOf(mk("aa:bb:cc:dd:ee:01"), mk("aa:bb:cc:dd:ee:02")), "192.168.1.1")
        assertTrue(r is ArpWatcher.ArpResult.Flap)
        assertEquals(2, (r as ArpWatcher.ArpResult.Flap).macs.size)
    }

    @Test
    fun missingGatewayIsIncomplete() {
        val r = ArpWatcher.detectFlap(listOf(emptyMap(), emptyMap()), "192.168.1.1")
        assertTrue(r is ArpWatcher.ArpResult.Incomplete)
    }
}
