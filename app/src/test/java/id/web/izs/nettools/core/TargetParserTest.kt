package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TargetParserTest {

    @Test
    fun stripsSchemePathAndPort() {
        assertEquals(TargetParser.Target("example.com", 8080), TargetParser.parse("https://example.com:8080/path?q=1"))
        assertEquals(TargetParser.Target("example.com", null), TargetParser.parse("example.com/"))
        assertEquals(TargetParser.Target("192.168.1.1", 22), TargetParser.parse("192.168.1.1:22"))
    }

    @Test
    fun ipv6BracketFormKeepsWholeAddress() {
        assertEquals(TargetParser.Target("2a00:1450:4001:80f::200e", 443), TargetParser.parse("[2a00:1450:4001:80f::200e]:443"))
        assertEquals(TargetParser.Target("::1", null), TargetParser.parse("[::1]"))
    }

    @Test
    fun pingHeaderExposesResolvedIpForDomain() {
        assertEquals(
            "142.251.12.139",
            TargetParser.resolvedIpIn("PING google.com (142.251.12.139) 56(84) bytes of data.", "google.com")
        )
        assertEquals(
            "2a00:1450:4001:80f::200e",
            TargetParser.resolvedIpIn("PING dns.google (2a00:1450:4001:80f::200e) 56(64) bytes of data.", "dns.google")
        )
    }

    @Test
    fun literalIpTargetCarriesNothingToFold() {
        // `(8.8.8.8)` only repeats the target — the header already shows it.
        assertNull(TargetParser.resolvedIpIn("PING 8.8.8.8 (8.8.8.8) 56(84) bytes of data.", "8.8.8.8"))
        assertNull(
            TargetParser.resolvedIpIn(
                "PING [2a00:1450:4001:80f::200e] (2a00:1450:4001:80f::200e) 56(64) bytes of data.",
                "2a00:1450:4001:80f::200e"
            )
        )
    }

    @Test
    fun tracerouteIntrosExposeResolvedIp() {
        assertEquals(
            "142.251.12.139",
            TargetParser.resolvedIpIn(
                "traceroute to google.com (142.251.12.139), 30 hops max, 60 byte packets",
                "google.com"
            )
        )
        assertEquals(
            "142.251.12.139",
            TargetParser.resolvedIpIn(
                "traceroute to google.com (142.251.12.139) [TTL-ping: no binary, silent hops show *]",
                "google.com"
            )
        )
    }

    @Test
    fun introsWithoutAnIpAreNotFoldable() {
        // Runner intro: no parenthesized dest — nothing the header lacks.
        assertNull(
            TargetParser.resolvedIpIn(
                "traceroute to google.com [backend: system traceroute binary]",
                "google.com"
            )
        )
        // TTL-ping intro before DNS answered: "(unresolved)" is not an IP.
        assertNull(
            TargetParser.resolvedIpIn(
                "traceroute to google.com (unresolved) [TTL-ping: no binary, silent hops show *]",
                "google.com"
            )
        )
    }

    @Test
    fun withResolvedIpFoldsIntoHeaderInPlace() {
        val prefix = "== PING google.com "
        val header = prefix + "[via system ping x4] 16:00:00 =="
        assertEquals(
            "== PING google.com (142.251.12.139) [via system ping x4] 16:00:00 ==",
            TargetParser.withResolvedIp(
                header, prefix,
                "PING google.com (142.251.12.139) 56(84) bytes of data.",
                "google.com"
            )
        )
    }

    @Test
    fun withResolvedIpNeedsMatchingPrefixAndAnIp() {
        val header = "== PING google.com [via system ping x4] 16:00:00 =="
        // Prefix from another run/tool never matches.
        assertNull(
            TargetParser.withResolvedIp(header, "== TRACE google.com ", "PING google.com (1.2.3.4) 56(84) bytes of data.", "google.com")
        )
        // Literal-IP target: nothing to fold, header stays untouched.
        assertNull(
            TargetParser.withResolvedIp(header, "== PING google.com ", "PING google.com (1.2.3.4) 56(84) bytes of data.", "1.2.3.4")
        )
    }
}
