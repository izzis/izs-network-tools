package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IpInfoClientTest {

    @Test
    fun needsResolveHostnameOnly() {
        assertTrue(IpInfoClient.needsResolve("izs.web.id", me = false, selfOnly = false))
        assertTrue(IpInfoClient.needsResolve("example.com", me = false, selfOnly = false))
    }

    @Test
    fun needsResolveSkipsIpLiteralsAndSpecialTargets() {
        assertFalse(IpInfoClient.needsResolve("104.21.86.248", me = false, selfOnly = false))
        assertFalse(IpInfoClient.needsResolve("2001:db8::1", me = false, selfOnly = false))
        assertFalse(IpInfoClient.needsResolve("example.com", me = true, selfOnly = false))
        assertFalse(IpInfoClient.needsResolve("example.com", me = false, selfOnly = true))
        assertFalse(IpInfoClient.needsResolve("", me = false, selfOnly = false))
    }

    @Test
    fun buildUrlIpwhoWithIp() {
        assertEquals(
            "https://ipwho.is/104.21.86.248",
            IpInfoClient.buildUrl("https://ipwho.is", "104.21.86.248", me = false, selfOnly = false)
        )
    }

    @Test
    fun buildUrlIpApiKeepsFields() {
        assertEquals(
            "http://ip-api.com/json/1.2.3.4?fields=status,message,country,countryCode,region,regionName,city,zip,lat,lon,timezone,isp,org,as,query",
            IpInfoClient.buildUrl(
                "http://ip-api.com/json", "1.2.3.4", me = false, selfOnly = false
            )
        )
    }

    @Test
    fun buildUrlIpinfoJsonSpecialCase() {
        assertEquals(
            "https://ipinfo.io/1.2.3.4/json",
            IpInfoClient.buildUrl("https://ipinfo.io/json", "1.2.3.4", me = false, selfOnly = false)
        )
        assertEquals(
            "https://ipinfo.io/json",
            IpInfoClient.buildUrl("https://ipinfo.io/json", "", me = true, selfOnly = false)
        )
    }

    @Test
    fun buildUrlMeBranches() {
        assertEquals("https://api.ipinfo.io/lite/me",
            IpInfoClient.buildUrl("https://api.ipinfo.io/lite", "", me = true, selfOnly = false))
        assertEquals("https://ipaddress.to/api/lookup/my",
            IpInfoClient.buildUrl("https://ipaddress.to/api/lookup", "", me = true, selfOnly = false))
        assertEquals("https://api.ipify.org?format=json",
            IpInfoClient.buildUrl("https://api.ipify.org?format=json", "", me = true, selfOnly = false))
    }

    @Test
    fun buildUrlTemplateAndSelfOnly() {
        assertEquals("https://api.example.com/1.2.3.4",
            IpInfoClient.buildUrl("https://api.example.com/{ip}", "1.2.3.4", me = false, selfOnly = false))
        assertEquals("https://api.example.com/my",
            IpInfoClient.buildUrl("https://api.example.com/{ip}", "", me = true, selfOnly = false))
        assertEquals("https://api.ipify.org?format=json",
            IpInfoClient.buildUrl(
                "https://api.ipify.org?format=json", "", me = true, selfOnly = true
            ))
    }

    @Test
    fun prettyJsonIndentsNestedObjectsAndCleansScalars() {
        val out = IpInfoClient.prettyJson(
            """{"success":true,"ip":"1.2.3.4","location":{"country":"Indonesia","city":"Pekanbaru","timezone":"Asia\/Jakarta","latitude":0.5166700000000001},"drop":null,"duration_ms":1020}"""
        )!!
        assertTrue(out.contains("success: true"))
        assertTrue(out.contains("ip: 1.2.3.4"))
        assertTrue(out.contains("location:"))
        assertTrue(out.contains("  country: Indonesia"))
        assertTrue(out.contains("  city: Pekanbaru"))
        assertTrue(out.contains("  timezone: Asia/Jakarta"))
        assertTrue(out.contains("  latitude: 0.51667"))
        assertTrue(out.contains("duration_ms: 1020"))
        assertFalse(out.contains("drop"))
        assertFalse(out.contains("\""))
        assertFalse(out.contains("{"))
    }

    @Test
    fun prettyJsonGroupsTopLevelIsFlags() {
        val out = IpInfoClient.prettyJson(
            """{"is_private":false,"is_cgnat":true,"is_vpn":true,"is_proxy":false,"is_tor":false,"is_hosting":false}"""
        )!!
        val flags = out.lines().filter { it.startsWith("flags:") }
        assertEquals(1, flags.size)
        assertTrue(flags[0].contains("private=no"))
        assertTrue(flags[0].contains("cgnat=yes"))
        assertTrue(flags[0].contains("vpn=yes"))
        assertTrue(flags[0].contains("proxy=no"))
        assertTrue(flags[0].contains("tor=no"))
        assertTrue(flags[0].contains("hosting=no"))
        assertFalse(out.contains("is_vpn:"))
    }

    @Test
    fun prettyJsonRendersArrays() {
        val out = IpInfoClient.prettyJson("""{"languages":["id","en"]}""")!!
        assertTrue(out.contains("languages:\n  - id\n  - en"))
    }

    @Test
    fun prettyJsonRendersObjectArrayItems() {
        val out = IpInfoClient.prettyJson("""{"entities":[{"handle":"x"}]}""")!!
        assertTrue(out.contains("entities:\n  - handle: x"))
    }

    @Test
    fun prettyJsonRejectsGarbageAndEmptyResults() {
        assertNull(IpInfoClient.prettyJson("not json"))
        assertNull(IpInfoClient.prettyJson("[]"))
        assertNull(IpInfoClient.prettyJson("""{"a":null}"""))
    }

    @Test
    fun prettyJsonTruncatesLongOutput() {
        val big = (1..500).joinToString(",") { "\"k$it\":\"v$it\"" }
        val out = IpInfoClient.prettyJson("{$big}", maxChars = 200)!!
        assertTrue(out.endsWith("… (truncated)"))
        assertTrue(out.length < 400)
    }
}
