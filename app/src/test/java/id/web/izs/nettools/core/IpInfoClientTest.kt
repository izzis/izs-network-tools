package id.web.izs.nettools.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
                "https://api.ipify.org?format=json", "1.2.3.4", me = false, selfOnly = true
            ))
    }
}
