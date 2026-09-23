package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Test

class IpScanTest {

    @Test
    fun upWithMacAndGatewayFlag() {
        assertEquals(
            "UP  192.168.1.1 (router)  0.42 ms  [aa:bb:cc:dd:ee:ff]  [gw]",
            IpScan.formatUp("192.168.1.1", "router", "0.42 ms", "aa:bb:cc:dd:ee:ff", true)
        )
    }

    @Test
    fun upBareWithoutMac() {
        assertEquals(
            "UP  192.168.1.9  1.20 ms",
            IpScan.formatUp("192.168.1.9", null, "1.20 ms", null, false)
        )
    }

    @Test
    fun upWithMacButNotGateway() {
        assertEquals(
            "UP  192.168.1.5  reply  [11:22:33:44:55:66]",
            IpScan.formatUp("192.168.1.5", null, "reply", "11:22:33:44:55:66", false)
        )
    }
}
