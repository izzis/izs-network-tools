package id.web.izs.nettools.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.SavedHost
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.prefs by preferencesDataStore(name = "izs_nettools")

class SettingsRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    private object K {
        val DNS = stringPreferencesKey("dns_server")
        val RDAP = stringPreferencesKey("rdap_base")
        val WHOIS = stringPreferencesKey("whois_server")
        val WHOIS_PORT = intPreferencesKey("whois_port")
        val GPTOKEN = stringPreferencesKey("globalping_token")
        val IPINFOTOKEN = stringPreferencesKey("ipinfo_token")
        val IPINFO = stringPreferencesKey("ipinfo_base")
        val IPLOOKUP = stringPreferencesKey("iplookup_base")
        val MYIP = stringPreferencesKey("myip_base")
        val MAX_HOPS = intPreferencesKey("max_hops")
        val TIMEOUT = intPreferencesKey("timeout_ms")
        val PORTS = stringPreferencesKey("port_list")
        val MAXPARALLEL = intPreferencesKey("max_parallel")
        val PINGCOUNT = intPreferencesKey("ping_count")
        val SCANOFFLINE = intPreferencesKey("scan_show_offline")
        val AUTORUN = intPreferencesKey("autorun")
        val AUTORUN_TOOL = intPreferencesKey("autorun_tool")
        val AUTOCLEAR = intPreferencesKey("autoclear_output")
        val HIDEDUPES = intPreferencesKey("hide_recent_dupes")
        val SAVEDSORT = stringPreferencesKey("saved_sort")
        val COLORED = intPreferencesKey("colored_output")
        val OUTFONT = floatPreferencesKey("output_font_sp")
        val MAXRECENT = intPreferencesKey("max_recent")
        val THEME = stringPreferencesKey("app_theme")
        val COLORS = stringPreferencesKey("custom_colors")
        val SCHEMES = stringPreferencesKey("color_schemes")
        val SCHEME_NAME = stringPreferencesKey("color_scheme_name")
        val SAVED = stringPreferencesKey("saved_hosts")
        val RECENT = stringPreferencesKey("recent_hosts")
    }

    val settings: Flow<AppSettings> = context.prefs.data.map { p ->
        // Migrate from the old single ipinfo_base key if the split keys are absent.
        val legacy = p[K.IPINFO]
        AppSettings(
            dnsServer = p[K.DNS] ?: AppSettings().dnsServer,
            rdapBase = p[K.RDAP] ?: AppSettings().rdapBase,
            whoisServer = p[K.WHOIS] ?: AppSettings().whoisServer,
            whoisPort = p[K.WHOIS_PORT] ?: AppSettings().whoisPort,
            globalpingToken = p[K.GPTOKEN] ?: "",
            ipinfoToken = p[K.IPINFOTOKEN] ?: "",
            ipLookupBase = p[K.IPLOOKUP] ?: legacy ?: AppSettings().ipLookupBase,
            myIpBase = p[K.MYIP] ?: legacy ?: AppSettings().myIpBase,
            maxHops = p[K.MAX_HOPS] ?: AppSettings().maxHops,
            timeoutMs = p[K.TIMEOUT] ?: AppSettings().timeoutMs,
            portList = p[K.PORTS] ?: AppSettings().portList,
            maxParallel = p[K.MAXPARALLEL] ?: AppSettings().maxParallel,
            pingCount = p[K.PINGCOUNT] ?: AppSettings().pingCount,
            scanShowOffline = (p[K.SCANOFFLINE] ?: 0) == 1,
            autoRunOnPick = (p[K.AUTORUN] ?: 0) == 1,
            autoRunOnTool = (p[K.AUTORUN_TOOL] ?: 0) == 1,
            autoClearOutput = (p[K.AUTOCLEAR] ?: 1) == 1,
            hideRecentDupes = (p[K.HIDEDUPES] ?: 0) == 1,
            savedSort = p[K.SAVEDSORT] ?: AppSettings().savedSort,
            coloredOutput = (p[K.COLORED] ?: 1) == 1,
            outputFontSp = p[K.OUTFONT] ?: AppSettings().outputFontSp,
            maxRecent = p[K.MAXRECENT] ?: AppSettings().maxRecent,
            theme = p[K.THEME] ?: AppSettings().theme,
            customColors = decodeColors(p[K.COLORS]),
            colorSchemes = decodeSchemes(p[K.SCHEMES]),
            schemeName = p[K.SCHEME_NAME] ?: ""
        )
    }

    suspend fun saveSettings(s: AppSettings) {
        context.prefs.edit { p ->
            p[K.DNS] = s.dnsServer
            p[K.RDAP] = s.rdapBase
            p[K.WHOIS] = s.whoisServer
            p[K.WHOIS_PORT] = s.whoisPort
            p[K.GPTOKEN] = s.globalpingToken
            p[K.IPINFOTOKEN] = s.ipinfoToken
            p[K.IPLOOKUP] = s.ipLookupBase
            p[K.MYIP] = s.myIpBase
            p[K.MAX_HOPS] = s.maxHops
            p[K.TIMEOUT] = s.timeoutMs
            p[K.PORTS] = s.portList
            p[K.MAXPARALLEL] = s.maxParallel
            p[K.PINGCOUNT] = s.pingCount
            p[K.SCANOFFLINE] = if (s.scanShowOffline) 1 else 0
            p[K.AUTORUN] = if (s.autoRunOnPick) 1 else 0
            p[K.AUTORUN_TOOL] = if (s.autoRunOnTool) 1 else 0
            p[K.AUTOCLEAR] = if (s.autoClearOutput) 1 else 0
            p[K.HIDEDUPES] = if (s.hideRecentDupes) 1 else 0
            p[K.SAVEDSORT] = s.savedSort
            p[K.COLORED] = if (s.coloredOutput) 1 else 0
            p[K.OUTFONT] = s.outputFontSp
            p[K.MAXRECENT] = s.maxRecent
            p[K.THEME] = s.theme
            p[K.COLORS] = json.encodeToString(s.customColors)
            p[K.SCHEMES] = json.encodeToString(s.colorSchemes)
            p[K.SCHEME_NAME] = s.schemeName
        }
    }

    val savedHosts: Flow<List<SavedHost>> = context.prefs.data.map { p ->
        decodeHosts(p[K.SAVED])
    }

    val recentHosts: Flow<List<String>> = context.prefs.data.map { p ->
        p[K.RECENT]?.split('\n')?.filter { it.isNotBlank() }?.take(20) ?: emptyList()
    }

    private fun decodeColors(s: String?): Map<String, String> {
        if (s.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(s)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveCustomColors(map: Map<String, String>) {
        context.prefs.edit { it[K.COLORS] = json.encodeToString(map) }
    }

    private fun decodeSchemes(s: String?): Map<String, Map<String, String>> {
        if (s.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, Map<String, String>>>(s)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    suspend fun saveColorSchemes(map: Map<String, Map<String, String>>) {
        context.prefs.edit { it[K.SCHEMES] = json.encodeToString(map) }
    }

    suspend fun saveSchemeName(name: String) {
        context.prefs.edit { it[K.SCHEME_NAME] = name }
    }

    private fun decodeHosts(s: String?): List<SavedHost> {
        if (s.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString<List<SavedHost>>(s)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun addSaved(label: String, host: String): Boolean {
        val h = host.trim()
        if (h.isEmpty()) return false
        val current = savedHosts.first().toMutableList()
        if (current.any { it.host.equals(h, ignoreCase = true) }) return false
        val id = (current.maxOfOrNull { it.id } ?: 0L) + 1
        current.add(0, SavedHost(id, label.ifBlank { h }, h))
        persistSaved(current)
        return true
    }

    suspend fun updateSaved(item: SavedHost) {
        val current = savedHosts.first().map { if (it.id == item.id) item else it }
        persistSaved(current)
    }

    suspend fun deleteSaved(id: Long) {
        persistSaved(savedHosts.first().filter { it.id != id })
    }

    suspend fun touchSaved(host: String) {
        val current = savedHosts.first().map {
            if (it.host.equals(host, ignoreCase = true)) it.copy(lastUsed = System.currentTimeMillis()) else it
        }
        persistSaved(current)
    }

    suspend fun isSaved(host: String): Boolean =
        savedHosts.first().any { it.host.equals(host.trim(), ignoreCase = true) }

    suspend fun pushRecent(host: String, max: Int = AppSettings().maxRecent) {
        if (max <= 0) return // recents disabled: store nothing.
        val h = host.trim()
        if (h.isEmpty()) return
        val current = (listOf(h) + recentHosts.first())
            .distinctBy { it.lowercase() }
            .take(max.coerceIn(1, 50))
        context.prefs.edit { it[K.RECENT] = current.joinToString("\n") }
    }

    /** Drop stored recents beyond [max]. 0 clears the list (feature off). */
    suspend fun trimRecent(max: Int) {
        val limit = max.coerceIn(0, 50)
        val current = recentHosts.first()
        if (current.size > limit) {
            context.prefs.edit { it[K.RECENT] = current.take(limit).joinToString("\n") }
        }
    }

    private suspend fun persistSaved(list: List<SavedHost>) {
        context.prefs.edit { it[K.SAVED] = json.encodeToString<List<SavedHost>>(list) }
    }
}
