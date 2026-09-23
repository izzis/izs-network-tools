package id.web.izs.nettools.model

import kotlinx.serialization.Serializable

@Serializable
data class SavedHost(
    val id: Long,
    val label: String,
    val host: String,
    val createdAt: Long = System.currentTimeMillis(),
    val lastUsed: Long = 0L
)

/** Sort order for the saved-hosts list. Stored as [AppSettings.savedSort] by enum name. */
enum class SavedSort(val label: String) {
    NAME_ASC("Name A–Z"),
    NAME_DESC("Name Z–A"),
    NEWEST("Newest first"),
    RECENT_USE("Recently used");

    companion object {
        fun of(name: String): SavedSort = try { valueOf(name) } catch (_: Exception) { NAME_ASC }
    }
}

fun List<SavedHost>.sortedFor(sort: SavedSort): List<SavedHost> = when (sort) {
    SavedSort.NAME_ASC -> sortedBy { it.label.lowercase() }
    SavedSort.NAME_DESC -> sortedByDescending { it.label.lowercase() }
    SavedSort.NEWEST -> sortedByDescending { it.createdAt }
    SavedSort.RECENT_USE -> sortedByDescending { it.lastUsed }
}

@Serializable
data class AppSettings(
    val dnsServer: String = "1.1.1.1",
    val rdapBase: String = "https://rdap.org",
    val whoisServer: String = "whois.iana.org",
    val whoisPort: Int = 43,
    val globalpingToken: String = "",
    val ipinfoToken: String = "",
    val ipLookupBase: String = "https://ipwho.is",
    val myIpBase: String = "https://ipwho.is",
    val maxHops: Int = 20,
    val timeoutMs: Int = 3000,
    val portList: String = "21 22 25 53 80 110 143 443 465 587 993 995 3306 8080 8443",
    val maxParallel: Int = 32,
    val pingCount: Int = 4,
    /** Gateway ping burst length for the Loop L2 phase (~1 packet/sec). */
    val loopPingCount: Int = 10,
    val scanShowOffline: Boolean = false,
    val autoRunOnPick: Boolean = false,
    val autoRunOnTool: Boolean = false,
    val autoClearOutput: Boolean = true,
    val hideRecentDupes: Boolean = false,
    val savedSort: String = SavedSort.NAME_ASC.name,
    val coloredOutput: Boolean = true,
    val outputFontSp: Float = 13f,
    val theme: String = "dark",
    val maxRecent: Int = 5,
    val customColors: Map<String, String> = emptyMap(),
    val colorSchemes: Map<String, Map<String, String>> = emptyMap(),
    val schemeName: String = "",
    /** Home tool grid order (Tool names). Unknown entries are ignored; tools
     *  missing from the list are appended in enum order (forward-compatible). */
    val toolOrder: List<String> = Tool.entries.map { it.name },
    /** Tools hidden from the home grid (Tool names). At least one must stay enabled. */
    val disabledTools: Set<String> = setOf(Tool.HEADERS.name, Tool.CERT.name)
)

object IpInfoPresets {
    /** Geo-capable providers: can look up any IP/domain. Used by IP Info. */
    val lookup = listOf(
        "ipwho.is (geo)" to "https://ipwho.is",
        "ip-api.com (geo)" to "http://ip-api.com/json",
        "ipaddress.to (geo+ASN)" to "https://ipaddress.to/api/lookup",
        "ipinfo.io (geo)" to "https://ipinfo.io/json",
        "ipinfo.io Lite (token required)" to "https://api.ipinfo.io/lite"
    )

    /** All providers incl. IP-only ones (own IP only). Used by My IP. */
    val myIp = lookup + listOf(
        "ipify (IP only)" to "https://api.ipify.org?format=json",
        "icanhazip (IP only)" to "https://icanhazip.com",
        "amazon (IP only)" to "https://checkip.amazonaws.com"
    )

    @Deprecated("Use lookup or myIp", ReplaceWith("lookup"))
    val all = lookup
}

object DnsPresets {
    val all = listOf(
        "Cloudflare (1.1.1.1)" to "1.1.1.1",
        "Google (8.8.8.8)" to "8.8.8.8",
        "Quad9 (9.9.9.9)" to "9.9.9.9",
        "Cloudflare alt (1.0.0.1)" to "1.0.0.1"
    )
}

object WhoisPresets {
    val all = listOf(
        "IANA (auto referral)" to "whois.iana.org",
        "RIPE" to "whois.ripe.net",
        "ARIN" to "whois.arin.net",
        "APNIC" to "whois.apnic.net",
        "LACNIC" to "whois.lacnic.net",
        "AFRINIC" to "whois.afrinic.net"
    )
}

object RdapPresets {
    val all = listOf(
        "rdap.org (auto)" to "https://rdap.org"
    )
}

/** Globalping probe origin. Empty code = API picks randomly worldwide. */
object GlobalpingCountries {
    val all = listOf(
        "Auto (worldwide)" to "",
        "Indonesia" to "ID",
        "Singapore" to "SG",
        "Malaysia" to "MY",
        "Thailand" to "TH",
        "Vietnam" to "VN",
        "Philippines" to "PH",
        "Hong Kong" to "HK",
        "Taiwan" to "TW",
        "Japan" to "JP",
        "South Korea" to "KR",
        "India" to "IN",
        "Australia" to "AU",
        "UAE" to "AE",
        "Germany" to "DE",
        "Netherlands" to "NL",
        "France" to "FR",
        "United Kingdom" to "GB",
        "Canada" to "CA",
        "United States" to "US",
        "Brazil" to "BR",
        "South Africa" to "ZA"
    )
}

enum class Tool(val title: String) {
    PING("Ping"),
    DIG("Dig"),
    WHOIS("Whois"),
    IPINFO("IP Info"),
    MYIP("My IP"),
    HEADERS("Headers"),
    TRACE("Trace"),
    PORTS("Ports"),
    LOOP("Loop"),
    CERT("Cert"),
    NEIGHBOR("Neighbor"),
    SWEEP("IP Scan");

    companion object {
        fun of(name: String): Tool? = try { valueOf(name) } catch (_: Exception) { null }
    }
}

/** Home-grid tools after applying the user's order + enable/disable settings. */
fun AppSettings.orderedEnabledTools(): List<Tool> {
    val order = toolOrder.mapNotNull { Tool.of(it) }
    val missing = Tool.entries.filter { it !in order }
    return (order + missing).filter { it.name !in disabledTools }
}

data class HopInfo(
    val ttl: Int,
    val ip: String?,
    val hostName: String?,
    val rttMs: Double?,
    val reached: Boolean
)
