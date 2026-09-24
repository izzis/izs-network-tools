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
        connected: Boolean = false,
        centerFreq: Int = 0,
        widthMhz: Int = 20
    ) = WifiAnalyzerRunner.ApInfo(
        bssid, ssid, rssi, freq, security, connected, centerFreq, widthMhz
    )

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
        val f = WifiAnalyzerRunner.Filters(
            band = setOf("5"), channel = 36, security = setOf("WPA3")
        )
        assertTrue(WifiAnalyzerRunner.matches(ap(freq = 5180, security = "WPA3"), f))
        // right band, wrong channel
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 5200, security = "WPA3"), f))
        // right band+channel, wrong security
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 5180, security = "WPA2"), f))
        // channel 6 exists on 2.4 GHz — band={5} must still reject it
        assertFalse(
            WifiAnalyzerRunner.matches(
                ap(freq = 2437, security = "WPA3"),
                WifiAnalyzerRunner.Filters(band = setOf("5"), channel = 6)
            )
        )
    }

    @Test
    fun multiSelectBandAndSecurityOrWithinGroup() {
        // 2.4+5 selected (not 6); WPA2+open (not WPA3)
        val f = WifiAnalyzerRunner.Filters(
            band = setOf("2.4", "5"),
            security = setOf("WPA2", "open")
        )
        assertTrue(WifiAnalyzerRunner.matches(ap(freq = 2412, security = "WPA2"), f))
        assertTrue(WifiAnalyzerRunner.matches(ap(freq = 5180, security = "open"), f))
        // 6 GHz not in band set
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 6135, security = "WPA2"), f))
        // WPA3 not in security set
        assertFalse(WifiAnalyzerRunner.matches(ap(freq = 2412, security = "WPA3"), f))
        // nothing selected → match nothing
        val none = WifiAnalyzerRunner.Filters(band = emptySet(), security = emptySet())
        assertFalse(WifiAnalyzerRunner.matches(ap(), none))
    }

    // --- channel overlap counts (Display = Channel) ---

    @Test
    fun channelCrowdingCountsOverlapSortedByChannel() {
        // 20 MHz ranges: ch1 2402–2422, ch6 2427–2447, ch11 2452–2472, ch36 5170–5190
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2412), // ch 1
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 2437), // ch 6
            ap(bssid = "aa:aa:aa:aa:aa:03", freq = 2462), // ch 11
            ap(bssid = "aa:aa:aa:aa:aa:04", freq = 2462),
            ap(bssid = "aa:aa:aa:aa:aa:05", freq = 2462),
            ap(bssid = "aa:aa:aa:aa:aa:06", freq = 5180)  // ch 36
        )
        val rows = WifiAnalyzerRunner.channelCrowding(aps)
        // sort by channel number only; 20 MHz ch36 (5170–5190) also covers
        // centers of ch34–ch38 — same inclusive-range model as VREM.
        assertEquals(
            listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 34, 35, 36, 37, 38),
            rows.map { it.channel }
        )
        // ch1 AP covers centers ch1–ch3; ch6 covers ch4–ch8; ch11 covers ch9–ch13
        assertEquals(1, rows.first { it.channel == 1 }.count)
        assertEquals(1, rows.first { it.channel == 2 }.count)
        assertEquals(1, rows.first { it.channel == 3 }.count)
        assertEquals(1, rows.first { it.channel == 4 }.count)  // only via ch6 AP (2427 edge)
        assertEquals(1, rows.first { it.channel == 5 }.count)
        assertEquals(1, rows.first { it.channel == 6 }.count)
        assertEquals(3, rows.first { it.channel == 11 }.count) // three ch11 APs
        assertEquals(0, rows.first { it.channel == 14 }.count)
        assertEquals(1, rows.first { it.channel == 36 }.count)
        assertEquals("2.4", rows.first { it.channel == 1 }.band)
        assertEquals("5", rows.first { it.channel == 36 }.band)
    }

    @Test
    fun channelCrowdingAdjacentPrimaryChannelsBothHitMiddle() {
        // VREM case: list has no primary ch2 AP, but ch1+ch3 both overlap ch2.
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2412), // ch 1 → 2402–2422
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 2422)  // ch 3 → 2412–2432
        )
        val rows = WifiAnalyzerRunner.channelCrowding(aps)
        assertEquals(2, rows.first { it.channel == 2 }.count)
        assertEquals(2, rows.first { it.channel == 1 }.count)
        assertEquals(2, rows.first { it.channel == 3 }.count)
        // sorted by channel, not by count
        assertEquals(
            (1..14).toList(),
            rows.map { it.channel }.filter { it <= 14 }
        )
    }

    @Test
    fun channelCrowdingWideApCoversNeighborChannels() {
        // 40 MHz AP centered on ch6 (2437): 2417–2457 → ch2..ch10
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2437, centerFreq = 2437, widthMhz = 40)
        )
        val rows = WifiAnalyzerRunner.channelCrowding(aps)
        val covered = rows.filter { it.count > 0 && it.channel in 1..14 }.map { it.channel }
        assertEquals((2..10).toList(), covered)
    }

    @Test
    fun channelCrowdingBandFilterDropsOtherBands() {
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2412), // ch 1 2.4
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 5180)  // ch 36 5
        )
        val five = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("5"))
        )
        assertTrue(five.isNotEmpty())
        assertTrue(five.all { it.band == "5" })
        assertFalse(five.any { it.band == "2.4" })

        val two = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4"))
        )
        assertTrue(two.all { it.band == "2.4" })
        assertEquals((1..14).toList(), two.map { it.channel })

        // multi-select: 2.4 + 5 together keeps both landscapes
        val both = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4", "5"))
        )
        assertTrue(both.any { it.band == "2.4" })
        assertTrue(both.any { it.band == "5" })
    }

    @Test
    fun channelCrowdingChannelChipKeepsOnlyFocusRow() {
        // ch1 + ch3 APs both overlap ch2; focus chip = 2 → single row, count 2.
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2412),
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 2422)
        )
        val rows = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(channel = 2)
        )
        assertEquals(listOf(2), rows.map { it.channel })
        assertEquals(2, rows[0].count)
    }

    @Test
    fun formatChannelCrowdShowsCountAndBar() {
        val busy = WifiAnalyzerRunner.formatChannelCrowd(
            WifiAnalyzerRunner.ChannelCrowd(channel = 6, count = 3, band = "2.4")
        )
        assertTrue(busy.startsWith("ch   6"))
        assertTrue(busy.contains("2.4G"))
        assertTrue(busy.contains("3 APs"))
        assertTrue(busy.contains("███"))

        val empty = WifiAnalyzerRunner.formatChannelCrowd(
            WifiAnalyzerRunner.ChannelCrowd(channel = 149, count = 0, band = "5")
        )
        assertTrue(empty.startsWith("ch 149"))
        assertTrue(empty.contains("0 AP"))
        assertFalse(empty.contains("█"))
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
