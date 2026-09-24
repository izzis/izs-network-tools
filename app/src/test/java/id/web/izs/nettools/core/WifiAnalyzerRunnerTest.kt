package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WifiAnalyzerRunnerTest {

    private fun ap(
        ssid: String = "Office",
        bssid: String = "aa:bb:cc:dd:ee:ff",
        rssi: Int = -60,
        freq: Int = 2437,
        security: String = "WPA2",
        connected: Boolean = false
    ) = WifiAnalyzerRunner.ApInfo(bssid, ssid, rssi, freq, security, connected)

    // --- band / channel ---

    @Test
    fun bandsFromFrequency() {
        assertEquals("2.4", WifiAnalyzerRunner.bandOf(2412))
        assertEquals("2.4", WifiAnalyzerRunner.bandOf(2484))
        assertEquals("5", WifiAnalyzerRunner.bandOf(5180))
        assertEquals("5", WifiAnalyzerRunner.bandOf(5825))
        assertEquals("6", WifiAnalyzerRunner.bandOf(6135))
        assertEquals("?", WifiAnalyzerRunner.bandOf(900))
    }

    @Test
    fun channelsFromFrequency() {
        assertEquals(1, WifiAnalyzerRunner.channelOf(2412))
        assertEquals(6, WifiAnalyzerRunner.channelOf(2437))
        assertEquals(11, WifiAnalyzerRunner.channelOf(2462))
        assertEquals(14, WifiAnalyzerRunner.channelOf(2484))
        assertEquals(36, WifiAnalyzerRunner.channelOf(5180))
        assertEquals(149, WifiAnalyzerRunner.channelOf(5745))
        assertEquals(37, WifiAnalyzerRunner.channelOf(6135))
    }

    // --- security label ---

    @Test
    fun securityFromCapabilities() {
        assertEquals("WPA3", WifiAnalyzerRunner.securityOf("[WPA3-SAE-CCMP]"))
        assertEquals("WPA3", WifiAnalyzerRunner.securityOf("[RSN-SAE-CCMP][WPA2-PSK-CCMP]"))
        assertEquals("WPA2", WifiAnalyzerRunner.securityOf("[WPA2-PSK-CCMP]"))
        assertEquals("WPA2", WifiAnalyzerRunner.securityOf("[RSN-PSK-CCMP]"))
        assertEquals("WPA", WifiAnalyzerRunner.securityOf("[WPA-PSK-TKIP]"))
        assertEquals("WEP", WifiAnalyzerRunner.securityOf("[WEP]"))
        assertEquals("open", WifiAnalyzerRunner.securityOf("[ESS]"))
        assertEquals("open", WifiAnalyzerRunner.securityOf(""))
    }

    // --- filters (free text = SSID OR MAC; chips are AND) ---

    @Test
    fun emptyFilterMatchesEverything() {
        val f = WifiAnalyzerRunner.Filters()
        assertTrue(WifiAnalyzerRunner.matches(ap(ssid = "Any"), f))
    }

    @Test
    fun queryMatchesSsidOrMacIgnoreCase() {
        val f = WifiAnalyzerRunner.Filters(query = "office")
        assertTrue(WifiAnalyzerRunner.matches(ap(ssid = "Office-5G"), f))
        assertFalse(WifiAnalyzerRunner.matches(ap(ssid = "Guest"), f))
        // same free text hits the MAC instead
        val byMac = WifiAnalyzerRunner.Filters(query = "AA:BB:CC")
        assertTrue(WifiAnalyzerRunner.matches(ap(bssid = "aa:bb:cc:dd:ee:ff"), byMac))
        assertFalse(WifiAnalyzerRunner.matches(ap(bssid = "11:22:33:44:55:66"), byMac))
    }

    @Test
    fun chipsAndTogether() {
        val f = WifiAnalyzerRunner.Filters(band = "5", channel = 36, security = "WPA3")
        assertTrue(WifiAnalyzerRunner.matches(ap(freq = 5180, security = "WPA3"), f))
        // right band, wrong channel
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 5200, security = "WPA3"), f))
        // right band+channel, wrong security
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 5180, security = "WPA2"), f))
        // channel 6 exists on 2.4 GHz — band=5 must still reject it
        assertFalse(
            WifiAnalyzerRunner.matches(
                ap(freq = 2437, security = "WPA3"),
                WifiAnalyzerRunner.Filters(band = "5", channel = 6)
            )
        )
    }

    // --- formatting ---

    @Test
    fun barsStaircaseGrowsWithRssi() {
        // empty → full stair (8 steps)
        assertEquals("", WifiAnalyzerRunner.barsOf(-100))
        assertEquals("▁▂▃▄▅▆▇█", WifiAnalyzerRunner.barsOf(-30))
        // half signal ≈ first half of the stair
        val half = WifiAnalyzerRunner.barsOf(-65)
        assertTrue(half.isNotEmpty() && half.length <= 8)
        assertTrue("▁▂▃▄▅▆▇█".startsWith(half))
        // strictly non-decreasing length across the ramp
        val ramp = listOf(-100, -90, -80, -70, -60, -50, -40, -30)
            .map { WifiAnalyzerRunner.barsOf(it).length }
        assertTrue(ramp.zipWithNext().all { (a, b) -> a <= b })
        assertEquals(8, WifiAnalyzerRunner.barsOf(-30).length)
    }

    @Test
    fun formatIsTwoLinesSsidThenMac() {
        val block = WifiAnalyzerRunner.formatAp(ap(connected = true))
        val (line1, line2) = block.split("\n", limit = 2)
        // line 1: SSID · stair · dBm (connected is a green UI color, no marker)
        assertTrue(line1.startsWith("Office"))
        assertFalse(line1.contains("aa:bb:cc:dd:ee:ff")) // MAC not on line 1
        assertTrue(line1.contains("-60"))
        assertTrue(line1.contains("▁"))
        assertFalse(line1.endsWith("*"))
        // line 2: MAC · ch · band · sec (no indent)
        assertFalse(line2.startsWith(" "))
        assertTrue(line2.startsWith("aa:bb:cc:dd:ee:ff"))
        assertTrue(line2.contains("ch  6"))
        assertTrue(line2.contains("2.4G"))
        assertTrue(line2.contains("WPA2"))

        val gone = WifiAnalyzerRunner.formatAp(ap(), gone = true)
        assertTrue(gone.endsWith("(gone)"))
    }

    @Test
    fun hiddenSsidShowsHiddenMarkerOnLine1() {
        val block = WifiAnalyzerRunner.formatAp(ap(ssid = ""))
        val (line1, line2) = block.split("\n", limit = 2)
        assertTrue(line1.contains("(hidden)"))
        assertFalse(line1.contains("aa:bb:cc:dd:ee:ff"))
        assertTrue(line2.startsWith("aa:bb:cc:dd:ee:ff"))
    }
}
