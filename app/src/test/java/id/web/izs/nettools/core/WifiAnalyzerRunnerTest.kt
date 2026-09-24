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
        widthMhz: Int = 20,
        standard: String = ""
    ) = WifiAnalyzerRunner.ApInfo(
        bssid, ssid, rssi, freq, security, connected, centerFreq, widthMhz, standard
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

    // --- Wi-Fi standard (ScanResult.wifiStandard, API 30+) ---

    @Test
    fun standardFromWifiStandardId() {
        assertEquals("", WifiAnalyzerRunner.standardOf(0))
        assertEquals("802.11a/b/g", WifiAnalyzerRunner.standardOf(1))
        assertEquals("802.11n", WifiAnalyzerRunner.standardOf(4))
        assertEquals("802.11ac", WifiAnalyzerRunner.standardOf(5))
        assertEquals("802.11ax", WifiAnalyzerRunner.standardOf(6))
        assertEquals("802.11ad", WifiAnalyzerRunner.standardOf(7))
        assertEquals("802.11be", WifiAnalyzerRunner.standardOf(8))
        assertEquals("", WifiAnalyzerRunner.standardOf(99))
    }

    @Test
    fun widthMhzFromChannelWidth() {
        assertEquals(20, WifiAnalyzerRunner.widthMhzOf(0))
        assertEquals(40, WifiAnalyzerRunner.widthMhzOf(1))
        assertEquals(80, WifiAnalyzerRunner.widthMhzOf(2))
        assertEquals(160, WifiAnalyzerRunner.widthMhzOf(3))
        assertEquals(160, WifiAnalyzerRunner.widthMhzOf(4))
        assertEquals(320, WifiAnalyzerRunner.widthMhzOf(5))
        assertEquals(20, WifiAnalyzerRunner.widthMhzOf(-1))
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
        val rows = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4", "5"))
        )
        // sort by channel number only; only ID-legal primaries appear —
        // no ch14, no 5 GHz 34/35/37/38, no DFS 100–144.
        assertEquals(
            (1..13).toList() + WifiAnalyzerRunner.validChannels("5"),
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
        assertEquals(1, rows.first { it.channel == 36 }.count)
        assertEquals(0, rows.first { it.channel == 40 }.count) // empty seeded 5 GHz primary
        assertEquals("2.4", rows.first { it.channel == 1 }.band)
        assertEquals("5", rows.first { it.channel == 36 }.band)
        // forbidden / non-primary numbers never appear
        assertFalse(rows.any { it.channel == 14 })
        assertFalse(rows.any { it.band == "5" && it.channel in listOf(34, 35, 37, 38, 100, 104) })
    }

    @Test
    fun channelCrowdingAdjacentPrimaryChannelsBothHitMiddle() {
        // VREM case: list has no primary ch2 AP, but ch1+ch3 both overlap ch2.
        val aps = listOf(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 2412), // ch 1 → 2402–2422
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 2422)  // ch 3 → 2412–2432
        )
        val rows = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4"))
        )
        assertEquals(2, rows.first { it.channel == 2 }.count)
        assertEquals(2, rows.first { it.channel == 1 }.count)
        assertEquals(2, rows.first { it.channel == 3 }.count)
        // sorted by channel, not by count
        assertEquals(
            (1..13).toList(),
            rows.map { it.channel }
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
        assertEquals(WifiAnalyzerRunner.validChannels("5"), five.map { it.channel })

        val two = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4"))
        )
        assertTrue(two.all { it.band == "2.4" })
        assertEquals((1..13).toList(), two.map { it.channel })

        // multi-select: 2.4 + 5 together keeps both landscapes
        val both = WifiAnalyzerRunner.channelCrowding(
            aps, WifiAnalyzerRunner.Filters(band = setOf("2.4", "5"))
        )
        assertTrue(both.any { it.band == "2.4" })
        assertTrue(both.any { it.band == "5" })
    }

    @Test
    fun validChannelsFollowCountryTable() {
        // ID default (and fallback): 2.4 = 1–13, never ch14
        assertEquals((1..13).toList(), WifiAnalyzerRunner.validChannels("2.4", "ID"))
        val id5 = WifiAnalyzerRunner.validChannels("5", "ID")
        assertEquals(listOf(36, 40, 44, 48, 52, 56, 60, 64), id5.take(8))
        assertTrue(149 in id5 && 165 in id5)
        assertFalse(34 in id5 || 35 in id5 || 37 in id5 || 38 in id5)
        assertFalse(id5.any { it in 100..144 })
        val id6 = WifiAnalyzerRunner.validChannels("6", "ID")
        assertEquals(listOf(1, 5, 9, 13), id6.take(4))
        assertTrue(id6.all { (it - 1) % 4 == 0 && it <= 93 })
        // US: 2.4 = 1–11; 5 includes DFS 100–144 + 149–165; 6 up to 233
        assertEquals((1..11).toList(), WifiAnalyzerRunner.validChannels("2.4", "US"))
        val us5 = WifiAnalyzerRunner.validChannels("5", "US")
        assertTrue(100 in us5 && 144 in us5 && 149 in us5 && 165 in us5)
        assertEquals(233, WifiAnalyzerRunner.validChannels("6", "US").last())
        // JP: 2.4 has ch14; 5 has no 149–165
        assertEquals((1..14).toList(), WifiAnalyzerRunner.validChannels("2.4", "JP"))
        val jp5 = WifiAnalyzerRunner.validChannels("5", "JP")
        assertTrue(100 in jp5 && jp5.none { it in 149..165 })
        // EU: DFS 100–140, no 149–165
        val eu5 = WifiAnalyzerRunner.validChannels("5", "DE")
        assertTrue(100 in eu5 && 140 in eu5 && eu5.none { it in 149..165 })
        // unknown → WORLD: same shape as ID for 5 GHz, 1–13 for 2.4
        assertEquals((1..13).toList(), WifiAnalyzerRunner.validChannels("2.4", "XX"))
        assertEquals(
            ((36..64 step 4) + (149..165 step 4)).toList(),
            WifiAnalyzerRunner.validChannels("5", "XX")
        )
    }

    @Test
    fun countryOfPrefersNetworkThenLocaleThenFallback() {
        assertEquals("US", WifiAnalyzerRunner.countryOf("us", "id"))
        assertEquals("DE", WifiAnalyzerRunner.countryOf(null, "de"))
        assertEquals("ID", WifiAnalyzerRunner.countryOf(null, null))
        assertEquals("ID", WifiAnalyzerRunner.countryOf("", "1", fallback = "ID"))
        assertEquals("JP", WifiAnalyzerRunner.countryOf("1", "jp"))
    }

    @Test
    fun channelFreqOfRejectsNonCountryChannels() {
        assertEquals(5180, WifiAnalyzerRunner.channelFreqOf(36, "5", "ID"))
        assertEquals(5745, WifiAnalyzerRunner.channelFreqOf(149, "5", "ID"))
        assertEquals(2412, WifiAnalyzerRunner.channelFreqOf(1, "2.4", "ID"))
        // ch14 (JP) / non-primary 5 GHz / DFS 100+ / 6 GHz above 93 — ID table
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(14, "2.4", "ID"))
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(34, "5", "ID"))
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(37, "5", "ID"))
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(100, "5", "ID"))
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(97, "6", "ID"))
        assertEquals(5955, WifiAnalyzerRunner.channelFreqOf(1, "6", "ID"))
        // legal under their own country tables
        assertEquals(2484, WifiAnalyzerRunner.channelFreqOf(14, "2.4", "JP"))
        assertEquals(5500, WifiAnalyzerRunner.channelFreqOf(100, "5", "US"))
        assertEquals(2462, WifiAnalyzerRunner.channelFreqOf(11, "2.4", "US"))
        assertEquals(null, WifiAnalyzerRunner.channelFreqOf(149, "5", "DE"))
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
        // line 1: SSID · stair · dBm · ~distance (connected is green UI, no marker)
        assertTrue(line1.startsWith("Office"))
        assertFalse(line1.contains("aa:bb:cc:dd:ee:ff")) // MAC not on line 1
        assertTrue(line1.contains("-60"))
        assertTrue(line1.contains("▁"))
        assertTrue(line1.contains("~")) // FSPL distance estimate
        assertTrue(line1.contains("m"))
        assertFalse(line1.endsWith("*"))
        // line 2: MAC · ch · width · band · sec · 802.11 (no indent)
        assertFalse(line2.startsWith(" "))
        assertTrue(line2.startsWith("aa:bb:cc:dd:ee:ff"))
        assertTrue(line2.contains("ch  6"))
        assertTrue(line2.contains("20MHz"))
        assertTrue(line2.contains("2.4G"))
        assertTrue(line2.contains("WPA2"))

        // (gone)/(filter) ride on the SSID line, not the MAC line
        val goneBlock = WifiAnalyzerRunner.formatAp(ap(), gone = true)
        val (gone1, gone2) = goneBlock.split("\n", limit = 2)
        assertTrue(gone1.contains("(gone)"))
        assertTrue(gone1.endsWith("(gone)"))
        assertFalse(gone2.contains("(gone)"))

        val filterBlock = WifiAnalyzerRunner.formatAp(ap(), filter = true)
        val (f1, f2) = filterBlock.split("\n", limit = 2)
        assertTrue(f1.contains("(filter)"))
        assertTrue(f1.endsWith("(filter)"))
        assertFalse(f2.contains("(filter)"))
    }

    @Test
    fun formatShowsWidthAndStandardWhenKnown() {
        val block = WifiAnalyzerRunner.formatAp(
            ap(freq = 2432, widthMhz = 40, standard = "802.11ax")
        )
        val line2 = block.split("\n", limit = 2)[1]
        assertTrue(line2.contains("40MHz"))
        assertTrue(line2.contains("802.11ax"))
        // unknown standard → field omitted (no dangling spaces before end/mark)
        val noStd = WifiAnalyzerRunner.formatAp(ap(standard = ""))
        assertFalse(noStd.split("\n", limit = 2)[1].contains("802.11"))
    }

    @Test
    fun formatLine2ColumnsStayAligned() {
        // Wide channel width must not glue to the band; band/sec columns
        // must start at the same offset with or without a standard.
        val wide = WifiAnalyzerRunner.formatAp(
            ap(bssid = "aa:aa:aa:aa:aa:01", freq = 5180, widthMhz = 160, standard = "802.11ac")
        ).split("\n", limit = 2)[1]
        val narrow = WifiAnalyzerRunner.formatAp(
            ap(bssid = "aa:aa:aa:aa:aa:02", freq = 2437, widthMhz = 20, standard = "")
        ).split("\n", limit = 2)[1]
        // band starts right after the fixed 17+2+5+2+7 column prefix
        val bandCol = 17 + 2 + 5 + 2 + 7
        assertTrue(wide.substring(bandCol).startsWith("5G"))
        assertTrue(narrow.substring(bandCol).startsWith("2.4G"))
        // space between MHz and band on the 160 MHz row
        assertTrue(wide.substring(0, bandCol).endsWith("160MHz "))
        // standard sits after padded security (4) + two spaces
        assertTrue(wide.substring(bandCol + 6 + 4).startsWith("  802.11ac"))
        assertFalse(narrow.contains("802.11"))
        assertTrue(narrow.trimEnd().endsWith("WPA2"))
    }

    @Test
    fun calculateDistanceMatchesVremFspl() {
        // Same formula + expected values as VREM WiFiUtilsTest.calculateDistance.
        fun d(freq: Int, rssi: Int) =
            java.text.DecimalFormat("#.##").format(
                WifiAnalyzerRunner.calculateDistance(freq, rssi)
            )
        assertEquals("0.62", d(2437, -36))
        assertEquals("1.23", d(2437, -42))
        assertEquals("246.34", d(2432, -88))
        assertEquals("350.85", d(2412, -91))
        assertEquals("~0.6m", WifiAnalyzerRunner.formatDistance(2437, -36))
        assertEquals("~1.2m", WifiAnalyzerRunner.formatDistance(2437, -42))
    }

    @Test
    fun hiddenSsidShowsHiddenMarkerOnLine1() {
        val block = WifiAnalyzerRunner.formatAp(ap(ssid = ""))
        val (line1, line2) = block.split("\n", limit = 2)
        assertTrue(line1.contains("(hidden)"))
        assertFalse(line1.contains("aa:bb:cc:dd:ee:ff"))
        assertTrue(line2.startsWith("aa:bb:cc:dd:ee:ff"))
    }

    // --- sort / re-sort ---

    @Test
    fun sortApsConnectedThenRssiSsidChannel() {
        val a = ap(bssid = "aa:aa:aa:aa:aa:01", ssid = "Bravo", rssi = -40, freq = 2437)
        val b = ap(bssid = "aa:aa:aa:aa:aa:02", ssid = "alpha", rssi = -70, freq = 2412)
        val c = ap(bssid = "aa:aa:aa:aa:aa:03", ssid = "Charlie", rssi = -50, freq = 5180, connected = true)
        val raw = listOf(b, c, a)

        assertEquals(
            listOf("aa:aa:aa:aa:aa:03", "aa:aa:aa:aa:aa:01", "aa:aa:aa:aa:aa:02"),
            WifiAnalyzerRunner.sortAps(raw, WifiAnalyzerRunner.SORT_RSSI).map { it.bssid }
        )
        assertEquals(
            listOf("aa:aa:aa:aa:aa:03", "aa:aa:aa:aa:aa:02", "aa:aa:aa:aa:aa:01"),
            WifiAnalyzerRunner.sortAps(raw, WifiAnalyzerRunner.SORT_SSID).map { it.bssid }
        )
        assertEquals(
            listOf("aa:aa:aa:aa:aa:03", "aa:aa:aa:aa:aa:02", "aa:aa:aa:aa:aa:01"),
            WifiAnalyzerRunner.sortAps(raw, WifiAnalyzerRunner.SORT_CHANNEL).map { it.bssid }
        )
    }

    @Test
    fun reorderApLinesSortsWithoutTouchingPlainOrChannelRows() {
        val weak = "${GlobalpingRunner.LIVE}aa:aa:aa:aa:aa:01\n" +
            WifiAnalyzerRunner.formatAp(ap(bssid = "aa:aa:aa:aa:aa:01", ssid = "Weak", rssi = -80))
        val strong = "${GlobalpingRunner.LIVE}aa:aa:aa:aa:aa:02\n" +
            WifiAnalyzerRunner.formatAp(ap(bssid = "aa:aa:aa:aa:aa:02", ssid = "Strong", rssi = -30))
        val mid = "${GlobalpingRunner.LIVE}aa:aa:aa:aa:aa:03\n" +
            WifiAnalyzerRunner.formatAp(ap(bssid = "aa:aa:aa:aa:aa:03", ssid = "Mid", rssi = -55))
        val header = "== WiFi Analyzer =="
        val notice = ";; 3 APs on air"
        val chRow = "${GlobalpingRunner.LIVE}ch:6\nch   6  2.4G    1 AP  █"
        // First-fetch order is deliberately wrong vs RSSI.
        val lines = listOf(header, weak, mid, strong, notice, chRow)

        val byRssi = WifiAnalyzerRunner.reorderApLines(lines, WifiAnalyzerRunner.SORT_RSSI)
        assertEquals(header, byRssi[0])
        assertEquals(notice, byRssi[4])
        assertEquals(chRow, byRssi[5]) // Channel row never moves / never sorts as AP
        assertEquals(
            listOf("Strong", "Mid", "Weak"),
            byRssi.filter { WifiAnalyzerRunner.isApLiveLine(it) }
                .map { WifiAnalyzerRunner.apLineSsid(it) }
        )

        val bySsid = WifiAnalyzerRunner.reorderApLines(lines, WifiAnalyzerRunner.SORT_SSID)
        assertEquals(
            listOf("Mid", "Strong", "Weak"),
            bySsid.filter { WifiAnalyzerRunner.isApLiveLine(it) }
                .map { WifiAnalyzerRunner.apLineSsid(it) }
        )

        val byConn = WifiAnalyzerRunner.reorderApLines(
            lines, WifiAnalyzerRunner.SORT_RSSI, connBssid = "aa:aa:aa:aa:aa:01"
        )
        assertEquals("Weak", WifiAnalyzerRunner.apLineSsid(byConn[1]))
    }

    @Test
    fun apLineParsersReadFormattedRows() {
        val line = "${GlobalpingRunner.LIVE}aa:bb:cc:dd:ee:ff\n" +
            WifiAnalyzerRunner.formatAp(ap(ssid = "Office", rssi = -60, freq = 2437))
        assertEquals("aa:bb:cc:dd:ee:ff", WifiAnalyzerRunner.apLineBssid(line))
        assertEquals("Office", WifiAnalyzerRunner.apLineSsid(line))
        assertEquals(-60, WifiAnalyzerRunner.apLineRssi(line))
        assertEquals(6, WifiAnalyzerRunner.apLineChannel(line))
        assertTrue(WifiAnalyzerRunner.isApLiveLine(line))
        assertFalse(WifiAnalyzerRunner.isApLiveLine("${GlobalpingRunner.LIVE}ch:6\nch   6"))
        assertFalse(WifiAnalyzerRunner.isApLiveLine("plain notice"))
    }
}
