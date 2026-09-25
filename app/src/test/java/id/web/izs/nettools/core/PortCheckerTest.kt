package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PortCheckerTest {

    private fun bytes(vararg ints: Int) = ByteArray(ints.size) { ints[it].toByte() }

    @Test
    fun fingerprintBanners() {
        assertEquals("ssh", PortChecker.fingerprint("SSH-2.0-OpenSSH_9.6\r\n".toByteArray()))
        assertEquals("http", PortChecker.fingerprint("HTTP/1.1 200 OK\r\nServer: nginx\r\n".toByteArray()))
        assertEquals("pop3", PortChecker.fingerprint("+OK Dovecot ready.\r\n".toByteArray()))
        assertEquals("imap", PortChecker.fingerprint("* OK IMAP4rev1 server ready\r\n".toByteArray()))
        assertEquals("vnc", PortChecker.fingerprint("RFB 003.008\n".toByteArray()))
    }

    @Test
    fun fingerprint220NeedsKeywords() {
        assertEquals(
            "smtp",
            PortChecker.fingerprint("220 mail.example ESMTP Postfix\r\n".toByteArray())
        )
        assertEquals(
            "ftp",
            PortChecker.fingerprint("220 FTP server (vsFTPd 3.0.3)\r\n".toByteArray())
        )
        // ambiguous: no keyword -> stays unlabeled, falls back to the port map
        assertNull(PortChecker.fingerprint("220 mystery daemon ready\r\n".toByteArray()))
    }

    @Test
    fun fingerprintMysqlGreeting() {
        // payload length [0..3], protocol 10 [4], ASCII version "8.0" [5..]
        val greeting = bytes(50, 0, 0, 0, 10, 0x38, 0x2e, 0x30, 0x2e, 0x33, 0x36, 0)
        assertEquals("mysql", PortChecker.fingerprint(greeting))
    }

    @Test
    fun fingerprintTlsRecord() {
        // alert 15 03 03 ... - the answer a TLS-only port gives to plaintext
        assertEquals("https", PortChecker.fingerprint(bytes(0x15, 0x03, 0x03, 0x00, 0x02, 0x02, 0x28)))
        // handshake ServerHello 16 03 01 ...
        assertEquals("https", PortChecker.fingerprint(bytes(0x16, 0x03, 0x01, 0x00, 0x04, 0x02)))
    }

    @Test
    fun fingerprintUnknownStaysNull() {
        assertNull(PortChecker.fingerprint(ByteArray(0)))
        assertNull(PortChecker.fingerprint("hello world\r\n".toByteArray()))
        assertNull(PortChecker.fingerprint(bytes(0x00, 0x01, 0x02, 0x03)))
    }

    @Test
    fun fallbackMapCoversCommonPorts() {
        assertEquals("https", PortChecker.serviceByPort[443])
        assertEquals("ssh", PortChecker.serviceByPort[22])
        assertEquals("mysql", PortChecker.serviceByPort[3306])
        assertEquals("https", PortChecker.serviceByPort[8443])
        assertEquals("http-alt", PortChecker.serviceByPort[8080])
        assertNull(PortChecker.serviceByPort[12345])
    }

    @Test
    fun fallbackMapCoversProxySqlNeighbors() {
        assertEquals("proxysql", PortChecker.serviceByPort[6033])
        assertEquals("proxysql-admin", PortChecker.serviceByPort[6032])
        assertEquals("pgbouncer", PortChecker.serviceByPort[6432])
        assertEquals("socks", PortChecker.serviceByPort[1080])
        assertEquals("postgres", PortChecker.serviceByPort[5432])
    }

    @Test
    fun openLineConfirmedGetsCheckmark() {
        val line = PortChecker.openLine(443, 12, "https", confirmed = true)
        assertTrue(line.startsWith("OPEN     443/tcp"))
        assertTrue(line.contains("12 ms"))
        assertTrue(line.endsWith("https ✓"))
    }

    @Test
    fun openLineFallbackGetsQuestionMark() {
        val line = PortChecker.openLine(8443, 4, "https", confirmed = false)
        assertTrue(line.endsWith("https ?"))
    }

    @Test
    fun openLineWithoutServiceIsTrimmed() {
        assertEquals(
            "OPEN    4711/tcp     9 ms",
            PortChecker.openLine(4711, 9, null, confirmed = false)
        )
    }

    @Test
    fun portColumnAlignsAcrossPortWidths() {
        val narrow = PortChecker.openLine(21, 3, "ftp", confirmed = true)
        val mid = PortChecker.openLine(110, 3, "pop3", confirmed = true)
        val wide = PortChecker.openLine(65535, 3, null, confirmed = false)
        val closed = PortChecker.closedLine(8443)
        // "/tcp" sits at the same column as if ports were zero-padded
        assertEquals(narrow.indexOf("/tcp"), mid.indexOf("/tcp"))
        assertEquals(narrow.indexOf("/tcp"), wide.indexOf("/tcp"))
        assertEquals(narrow.indexOf("/tcp"), closed.indexOf("/tcp"))
        // and the service labels line up too
        assertEquals(narrow.indexOf("ftp"), mid.indexOf("pop3"))
    }

    @Test
    fun msColumnRightAligns() {
        val fast = PortChecker.openLine(80, 3, "http", confirmed = true)
        val slow = PortChecker.openLine(80, 12345, "http", confirmed = true)
        assertEquals(fast.indexOf("ms"), slow.indexOf("ms"))
        assertEquals(fast.indexOf("http"), slow.indexOf("http"))
    }

    @Test
    fun closedLineMatchesOpenPrefixWidth() {
        assertEquals("closed    21/tcp", PortChecker.closedLine(21))
        assertEquals("closed 65535/tcp", PortChecker.closedLine(65535))
    }

    @Test
    fun openLinePassiveRowKeepsServiceColumn() {
        // Global (Shodan) rows carry no timing; ms slot stays blank
        val line = PortChecker.openLine(6033, null, "proxysql", confirmed = false)
        assertTrue(line.startsWith("OPEN    6033/tcp"))
        assertTrue(line.endsWith("proxysql ?"))
    }

    @Test
    fun parsePortsAcceptsTheFullRange() {
        assertEquals(65535, PortChecker.parsePorts("1-65535").size)
        assertEquals(listOf(1, 2, 3, 4, 5), PortChecker.parsePorts("5-1"))
    }

    @Test
    fun groupsStayInsideAllKnown() {
        val known = PortChecker.serviceByPort.keys
        assertTrue(PortChecker.webPorts.all { it in known })
        assertTrue(PortChecker.servicePorts.all { it in known })
        assertEquals(PortChecker.servicePorts, PortChecker.servicePorts.distinct().sorted())
        assertTrue(
            PortChecker.webPorts.all { p ->
                PortChecker.serviceByPort[p] in setOf("http", "https", "http-alt", "ajp")
            }
        )
        // the groups do not overlap: a port is either web or service
        assertTrue(PortChecker.webPorts.intersect(PortChecker.servicePorts.toSet()).isEmpty())
    }

    @Test
    fun scanPresetsNestAsLadder() {
        val p = PortChecker.scanPresets.values.map { PortChecker.parsePorts(it).toSet() }
        assertEquals(5, p.size)
        assertTrue(p[1].containsAll(p[0])) // +Web  ⊇ Default
        assertTrue(p[2].containsAll(p[0])) // +Service ⊇ Default
        assertTrue(p[3].containsAll(p[1])) // +Both ⊇ +Web
        assertTrue(p[3].containsAll(p[2])) // +Both ⊇ +Service
        assertTrue(p[4].containsAll(p[3])) // All known ⊇ +Both
        assertTrue(p[3].size < p[4].size)  // +Both stays a strict subset
    }

    @Test
    fun scanPresetsParseBackToTheirLists() {
        val values = PortChecker.scanPresets.values.toList()
        assertEquals(5, values.size)
        assertEquals(PortChecker.commonPorts, PortChecker.parsePorts(values[0]))
        assertEquals(
            (PortChecker.commonPorts + PortChecker.webPorts).distinct().sorted(),
            PortChecker.parsePorts(values[1])
        )
        assertEquals(
            (PortChecker.commonPorts + PortChecker.servicePorts).distinct().sorted(),
            PortChecker.parsePorts(values[2])
        )
        assertEquals(PortChecker.serviceByPort.size, PortChecker.parsePorts(values[4]).size)
    }

    @Test
    fun sortLinesOpenFirstGroupsAndKeepsComments() {
        val lines = listOf(
            ";; checking 4 TCP ports on x (timeout 1000ms)",
            "",
            "OPEN   21/tcp     3 ms  ftp ✓",
            "closed 22/tcp",
            "OPEN   80/tcp     9 ms  http ✓",
            "closed 25/tcp",
            "\nDone: 2/4 ports open."
        )
        assertEquals(
            listOf(
                ";; checking 4 TCP ports on x (timeout 1000ms)",
                "",
                "OPEN   21/tcp     3 ms  ftp ✓",
                "OPEN   80/tcp     9 ms  http ✓",
                "closed 22/tcp",
                "closed 25/tcp",
                "\nDone: 2/4 ports open."
            ),
            PortChecker.sortLinesOpenFirst(lines)
        )
    }

    @Test
    fun sortLinesOpenFirstPassesThroughWithoutResults() {
        val lines = listOf(";; checking", "ERROR: boom")
        assertEquals(lines, PortChecker.sortLinesOpenFirst(lines))
    }
}
