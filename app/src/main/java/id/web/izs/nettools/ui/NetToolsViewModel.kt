package id.web.izs.nettools.ui

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import id.web.izs.nettools.core.CertChecker
import id.web.izs.nettools.core.DnsRunner
import id.web.izs.nettools.core.LoopDetector
import id.web.izs.nettools.core.LoopResult
import id.web.izs.nettools.core.LoopRunner
import id.web.izs.nettools.core.StormDetector
import id.web.izs.nettools.core.StormResult
import id.web.izs.nettools.core.GlobalpingRunner
import id.web.izs.nettools.core.HttpHeadersFetcher
import id.web.izs.nettools.core.IpScan
import id.web.izs.nettools.core.InternetDbClient
import id.web.izs.nettools.core.IpInfoClient
import id.web.izs.nettools.core.NeighborRunner
import id.web.izs.nettools.core.OuiDb
import id.web.izs.nettools.core.PingRunner
import id.web.izs.nettools.core.PortChecker
import id.web.izs.nettools.core.TargetParser
import id.web.izs.nettools.core.TraceRunner
import id.web.izs.nettools.core.WhoisRdapClient
import id.web.izs.nettools.core.WifiAnalyzerRunner
import id.web.izs.nettools.data.SettingsRepository
import id.web.izs.nettools.model.AppSettings
import id.web.izs.nettools.model.GlobalPrefs
import id.web.izs.nettools.model.SavedHost
import id.web.izs.nettools.model.SavedSort
import id.web.izs.nettools.model.Tool
import id.web.izs.nettools.model.orderedEnabledTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val target: String = "",
    val tool: Tool = Tool.PING,
    val digType: String = "A",
    val pingGlobal: Boolean = false,
    val traceGlobal: Boolean = false,
    val portsGlobal: Boolean = false,
    val globalProbes: Int = 10,
    val globalCountry: String = "",
    val lines: List<String> = emptyList(),
    val loopVerdict: LoopResult? = null,
    val stormVerdict: StormResult? = null,
    val loopMode: LoopRunner.LoopMode = LoopRunner.LoopMode.BOTH,
    val running: Boolean = false,
    val progress: String? = null,
    val startedAt: Long = 0L,
    val message: String? = null,
    val settings: AppSettings = AppSettings(),
    val settingsLoaded: Boolean = false,
    val saved: List<SavedHost> = emptyList(),
    val recent: List<String> = emptyList(),
    val dropExpanded: Boolean = false,
    val isTargetSaved: Boolean = false,
    /** WiFi Analyzer chip filters. Band + row count are persisted (DataStore)
     *  across launches; channel / security / display are session-only.
     *  Band + security are multi-select; default = every option. */
    val wifiBand: Set<String> = setOf("2.4", "5", "6"),
    val wifiChannel: Int = -1,
    val wifiSecurity: Set<String> = setOf("WPA3", "WPA2", "WPA", "WEP", "open"),
    /** Channels seen in the last scan — feeds the channel chip row. */
    val wifiChannels: List<Int> = emptyList(),
    /** Timestamp of the last completed WiFi scan cycle (countdown basis). */
    val lastRefreshAt: Long = 0L,
    /** True while a startScan + cache-grace window is open (spinner, no countdown). */
    val wifiScanning: Boolean = false,
    /** Completed WiFi scan cycles this run — 0 = still filling the first batch. */
    val wifiCycles: Int = 0,
    /** BSSID of the associated AP — colors that row green in the console. */
    val wifiConnBssid: String = "",
    /** Display mode: WifiAnalyzerRunner.DISPLAY_LIST | DISPLAY_CHANNEL. */
    val wifiDisplay: String = WifiAnalyzerRunner.DISPLAY_LIST,
    /** List-sort: WifiAnalyzerRunner.SORT_RSSI | SORT_SSID | SORT_CHANNEL.
     *  Session-only; Channel display ignores it (always channel no). */
    val wifiSort: String = WifiAnalyzerRunner.SORT_RSSI,
    /** AP row count 2 | 3 (3 = +vendor/standard/(gone) line). Persisted. */
    val wifiRows: Int = WifiAnalyzerRunner.ROWS_2,
    /** Collapse the home tool grid (top-bar Hide). Persisted
     *  (AppSettings.hideToolGrid) and restored on app start; mirrored here so
     *  rapid toggles stay in sync before the DataStore write lands. */
    val hideToolGrid: Boolean = false
)

class NetToolsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)
    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()
    private var job: Job? = null

    /** Session mirror of the host-tool target bar (DataStore `last_target`). */
    private var hostTarget: String = ""
    /** Session mirror of the WiFi Analyzer SSID/MAC filter (`wifi_filter`). */
    private var wifiTarget: String = ""

    init {
        viewModelScope.launch { repo.settings.collect { s ->
            _state.update { cur ->
                val next = cur.copy(settings = s, settingsLoaded = true)
                // The active tool may have just been disabled in Settings:
                // fall back to the first enabled tool instead of stranding the UI.
                if (next.tool.name in s.disabledTools) {
                    val fallback = s.orderedEnabledTools().firstOrNull() ?: Tool.PING
                    next.copy(tool = fallback, loopVerdict = null, stormVerdict = null)
                } else next
            }
            // Shrink a legacy long recent list to the current limit.
            repo.trimRecent(s.maxRecent)
        } }
        viewModelScope.launch { repo.savedHosts.collect { l -> _state.update { it.copy(saved = l) } } }
        viewModelScope.launch { repo.recentHosts.collect { l -> _state.update { it.copy(recent = l) } } }
        // Restore last tool + the matching target-bar slot in one step (host
        // tools share last_target; WiFi has its own filter — never cross-load).
        viewModelScope.launch {
            val settings = repo.settings.first()
            hostTarget = repo.lastTarget.first()
            wifiTarget = repo.wifiFilter.first()
            val t = Tool.of(repo.lastTool.first())
            val tool = if (t != null && t.name !in settings.disabledTools) t else Tool.PING
            val bar = if (tool == Tool.WIFIANALYZER) wifiTarget else hostTarget
            _state.update {
                it.copy(
                    tool = tool,
                    target = bar,
                    hideToolGrid = settings.hideToolGrid,
                    loopVerdict = null,
                    stormVerdict = null,
                    isTargetSaved = bar.isNotEmpty() && repo.isSaved(bar)
                )
            }
        }
        // Restore the last Band multi-select (e.g. 2.4+5 only, no 6 GHz).
        viewModelScope.launch {
            val bands = repo.wifiBands.first()
            _state.update { it.copy(wifiBand = bands) }
        }
        // Restore the AP row count (2 | 3) for the List display.
        viewModelScope.launch {
            val rows = repo.wifiRows.first()
            _state.update { it.copy(wifiRows = rows) }
        }
        // Restore the Global-vs-Local engine choice (Ping/Trace/Ports) + options.
        viewModelScope.launch {
            val g = repo.globalPrefs.first()
            _state.update {
                it.copy(
                    pingGlobal = g.ping,
                    traceGlobal = g.trace,
                    portsGlobal = g.ports,
                    globalProbes = g.probes,
                    globalCountry = g.country
                )
            }
        }
        // Preload the OUI vendor table off the main thread (3-row display).
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            OuiDb.ensureLoaded(getApplication<Application>().assets)
        }
    }

    fun setTarget(v: String) {
        _state.update { it.copy(target = v, message = null) }
        val tool = _state.value.tool
        if (tool == Tool.WIFIANALYZER) {
            // Filter is live for the scan; persist every edit so clear sticks.
            wifiTarget = v.trim()
            viewModelScope.launch { repo.saveWifiFilter(wifiTarget) }
        } else {
            hostTarget = v.trim()
            // Empty clear must overwrite last_target or reopen resurrects it.
            if (v.isEmpty()) viewModelScope.launch { repo.saveLastTarget("") }
        }
        viewModelScope.launch {
            _state.update { it.copy(isTargetSaved = repo.isSaved(v)) }
        }
    }

    fun setTool(t: Tool) {
        val prev = _state.value.tool
        val leavingWifi = prev == Tool.WIFIANALYZER && t != Tool.WIFIANALYZER
        val enteringWifi = prev != Tool.WIFIANALYZER && t == Tool.WIFIANALYZER
        // Swap target-bar slots: outgoing tool's text goes to its own key,
        // incoming tool's saved text fills the bar (empty on fresh install).
        if (leavingWifi) {
            wifiTarget = _state.value.target.trim()
            viewModelScope.launch { repo.saveWifiFilter(wifiTarget) }
        } else if (enteringWifi) {
            hostTarget = _state.value.target.trim()
            viewModelScope.launch { repo.saveLastTarget(hostTarget) }
        }
        val bar = if (t == Tool.WIFIANALYZER) wifiTarget else hostTarget
        // Drop any stale loop banner: it belonged to the previous tool/target.
        _state.update {
            it.copy(
                tool = t,
                target = bar,
                loopVerdict = null,
                stormVerdict = null,
                dropExpanded = false
            )
        }
        viewModelScope.launch {
            repo.saveLastTool(t.name)
            _state.update { it.copy(isTargetSaved = bar.isNotEmpty() && repo.isSaved(bar)) }
        }
        // IP Scan: autofill the target bar with your own /24 network.
        if (t == Tool.SWEEP && bar.isBlank()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                IpScan.ownNetwork()?.let { own ->
                    _state.update { s ->
                        if (s.tool == Tool.SWEEP && s.target.isBlank()) {
                            hostTarget = "${own.base24}.0/24"
                            s.copy(target = hostTarget)
                        } else s
                    }
                }
            }
        }
    }

    /** Toggle a Band chip; the choice is saved for the next launch. */
    fun toggleWifiBand(v: String) {
        var next: Set<String> = emptySet()
        _state.update {
            val cur = it.wifiBand
            next = if (v in cur) cur - v else cur + v
            it.copy(wifiBand = next)
        }
        viewModelScope.launch { repo.saveWifiBands(next) }
    }

    /** "Select All" on the Band row: restore the default (every band). */
    fun selectAllWifiBands() {
        val all = setOf("2.4", "5", "6")
        _state.update { it.copy(wifiBand = all) }
        viewModelScope.launch { repo.saveWifiBands(all) }
    }

    fun setWifiChannel(v: Int) = _state.update { it.copy(wifiChannel = v) }
    fun toggleWifiSecurity(v: String) = _state.update {
        val cur = it.wifiSecurity
        it.copy(wifiSecurity = if (v in cur) cur - v else cur + v)
    }

    /** List sort order (RSSI / SSID / channel). Session-only like Display. */
    fun setWifiSort(v: String) = _state.update { it.copy(wifiSort = v) }

    /** Switch List ↔ Channel display; re-runs a live WiFi session so the
     *  new view appears now instead of after the next 30 s tick. */
    fun setWifiDisplay(v: String) {
        val cur = _state.value
        if (cur.wifiDisplay == v) return
        _state.update { it.copy(wifiDisplay = v) }
        if (cur.tool != Tool.WIFIANALYZER) return
        clearWifiView()
        if (cur.running) {
            stop()
            run()
        }
    }

    /** Switch AP row count 2 ↔ 3; persisted for the next launch (the one
     *  Display setting that survives restarts). Re-runs a live session so
     *  the new layout appears now — rows are baked into the emitted text. */
    fun setWifiRows(v: Int) {
        val cur = _state.value
        if (cur.wifiRows == v) return
        _state.update { it.copy(wifiRows = v) }
        viewModelScope.launch { repo.saveWifiRows(v) }
        if (cur.tool != Tool.WIFIANALYZER) return
        clearWifiView()
        if (cur.running) {
            stop()
            run()
        }
    }

    /** Drop the old view's LIVE rows (and count notices) so modes never
     *  mix under the same blue header. */
    private fun clearWifiView() = _state.update { s ->
        s.copy(lines = s.lines.filter {
            !it.startsWith(GlobalpingRunner.LIVE) &&
                !it.startsWith(";; 0 APs") &&
                !(it.startsWith(";; ") && it.contains("APs on air"))
        })
    }

    /** ISO country for WiFi channel tables: network SIM/roaming ISO → device
     *  locale → ID. Coarse ISO only, never lat/lng (privacy). */
    private val wifiCountry: String by lazy { detectCountry() }

    private fun detectCountry(): String = try {
        val tm = getApplication<Application>().applicationContext
            .getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        WifiAnalyzerRunner.countryOf(
            networkIso = tm?.networkCountryIso,
            localeCountry = Locale.getDefault().country
        )
    } catch (_: Exception) {
        "ID"
    }

    /** Tap "next Ns": wake the WiFi scan cycle now instead of waiting out
     *  the 30 s countdown (no Stop → Run). CONFLATED: taps while not
     *  waiting collapse into one. */
    private val wifiRefreshTick = Channel<Unit>(Channel.CONFLATED)

    fun refreshWifiNow() {
        if (_state.value.tool == Tool.WIFIANALYZER && _state.value.running) {
            wifiRefreshTick.trySend(Unit)
        }
    }

    private fun wifiFilters() = WifiAnalyzerRunner.Filters(
        query = _state.value.target.trim(),
        band = _state.value.wifiBand,
        channel = _state.value.wifiChannel,
        security = _state.value.wifiSecurity,
        display = _state.value.wifiDisplay,
        sort = _state.value.wifiSort,
        rows = _state.value.wifiRows,
        country = wifiCountry
    )

    /** Tap a tool = run it immediately (except IP Scan and Loop: too heavy
     *  to trigger by accident, they need an explicit Run press). Tapping again re-runs. */
    fun selectAndRun(t: Tool) {
        if (_state.value.running) return
        setTool(t)
        // Heavy or permission-gated: select first, explicit Run presses the button.
        if (t == Tool.SWEEP || t == Tool.LOOP || t == Tool.WIFIANALYZER) return
        run()
    }
    /** Loop detection mode (hold the Loop tool to change). Per-session, like scopes. */
    fun setLoopMode(m: LoopRunner.LoopMode) = _state.update { it.copy(loopMode = m) }
    fun setDigType(t: String) {
        _state.update { it.copy(digType = t) }
        // Switching record type re-runs Dig immediately, no need to tap again.
        if (_state.value.tool == Tool.DIG) {
            stop()
            run()
        }
    }
    /** Global scope (Local vs Globalping / InternetDB) + probes/country:
     *  saved on every toggle and restored on launch (fresh install = Local). */
    fun setPingGlobal(g: Boolean) {
        _state.update { it.copy(pingGlobal = g) }
        persistGlobalPrefs()
    }
    fun setTraceGlobal(g: Boolean) {
        _state.update { it.copy(traceGlobal = g) }
        persistGlobalPrefs()
    }
    fun setPortsGlobal(g: Boolean) {
        _state.update { it.copy(portsGlobal = g) }
        persistGlobalPrefs()
    }
    fun setGlobalProbes(n: Int) {
        _state.update { it.copy(globalProbes = n) }
        persistGlobalPrefs()
    }
    fun setGlobalCountry(c: String) {
        _state.update { it.copy(globalCountry = c.trim().uppercase().take(2)) }
        persistGlobalPrefs()
    }
    private fun persistGlobalPrefs() {
        val s = _state.value
        viewModelScope.launch {
            repo.saveGlobalPrefs(
                GlobalPrefs(
                    ping = s.pingGlobal,
                    trace = s.traceGlobal,
                    ports = s.portsGlobal,
                    probes = s.globalProbes,
                    country = s.globalCountry
                )
            )
        }
    }
    fun setDrop(e: Boolean) = _state.update { it.copy(dropExpanded = e) }
    fun clearMessage() = _state.update { it.copy(message = null) }
    fun setMessage(m: String) = _state.update { it.copy(message = m) }

    /** Top-bar Hide/Show: collapse the tool grid for more terminal height.
     *  Persisted — the collapsed state is restored on the next app start. */
    fun toggleToolGrid() {
        _state.update { it.copy(hideToolGrid = !it.hideToolGrid) }
        val hidden = _state.value.hideToolGrid
        viewModelScope.launch {
            repo.saveSettings(_state.value.settings.copy(hideToolGrid = hidden))
        }
    }

    fun pickTarget(host: String) {
        setTarget(host)
        setDrop(false)
        viewModelScope.launch {
            repo.touchSaved(host)
            if (_state.value.settings.autoRunOnPick) run()
        }
    }

    fun toggleSave(label: String = "") {
        val host = _state.value.target.trim()
        if (host.isEmpty()) {
            _state.update { it.copy(message = "Enter a target first before saving") }
            return
        }
        viewModelScope.launch {
            if (repo.isSaved(host)) {
                _state.value.saved.firstOrNull { it.host.equals(host, ignoreCase = true) }?.let {
                    repo.deleteSaved(it.id)
                }
                _state.update { it.copy(isTargetSaved = false, message = "Removed from saved") }
            } else {
                val ok = repo.addSaved(label, host)
                _state.update {
                    it.copy(
                        isTargetSaved = ok,
                        message = if (ok) "Saved — just pick it next time" else "Already in saved"
                    )
                }
            }
        }
    }

    fun clearOutput() = _state.update { it.copy(lines = emptyList(), loopVerdict = null, stormVerdict = null) }

    fun bumpFont(deltaSp: Float) {
        val next = (_state.value.settings.outputFontSp + deltaSp).coerceIn(9f, 22f)
        viewModelScope.launch { repo.saveSettings(_state.value.settings.copy(outputFontSp = next)) }
    }

    fun deleteSaved(id: Long) {
        viewModelScope.launch { repo.deleteSaved(id) }
    }

    fun setSavedSort(mode: SavedSort) {
        val cur = _state.value.settings
        if (cur.savedSort == mode.name) return
        viewModelScope.launch { repo.saveSettings(cur.copy(savedSort = mode.name)) }
    }

    /** Long-press on a tool cell: swap that tool's server/provider. */
    fun setToolServer(tool: Tool, value: String) {
        val v = value.trim()
        if (v.isEmpty()) return
        val cur = _state.value.settings
        val next = when (tool) {
            Tool.DIG -> cur.copy(dnsServer = v)
            Tool.WHOIS -> cur.copy(whoisServer = v)
            Tool.IPINFO -> cur.copy(ipLookupBase = v)
            Tool.MYIP -> cur.copy(myIpBase = v)
            else -> return
        }
        viewModelScope.launch { repo.saveSettings(next) }
    }

    fun setCustomColor(role: String, hex: String) {
        viewModelScope.launch {
            val updated = _state.value.settings.customColors + (role to hex)
            repo.saveCustomColors(updated)
        }
    }

    fun clearCustomColor(role: String) {
        viewModelScope.launch {
            repo.saveCustomColors(_state.value.settings.customColors - role)
        }
    }

    fun resetCustomColors() {
        viewModelScope.launch {
            repo.saveCustomColors(emptyMap())
            repo.saveSchemeName("")
        }
    }

    /** Save the current working colors under [name]. Same name = overwrite, new name = new scheme. */
    fun saveScheme(name: String) {
        val n = name.trim()
        if (n.isEmpty()) return
        viewModelScope.launch {
            val s = _state.value.settings
            repo.saveColorSchemes(s.colorSchemes + (n to s.customColors))
            repo.saveSchemeName(n)
        }
    }

    fun applyScheme(name: String) {
        val colors = _state.value.settings.colorSchemes[name] ?: return
        viewModelScope.launch {
            repo.saveCustomColors(colors)
            repo.saveSchemeName(name)
        }
    }

    fun deleteScheme(name: String) {
        viewModelScope.launch {
            repo.saveColorSchemes(_state.value.settings.colorSchemes - name)
            if (_state.value.settings.schemeName == name) repo.saveSchemeName("")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(running = false, progress = null, lastRefreshAt = 0L, wifiScanning = false, dropExpanded = false) }
    }

    /** Best-effort WiFi handle for the Neighbor multicast phases; null = they proceed anyway. */
    private fun wifiManager(): WifiManager? = try {
        getApplication<Application>().applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    } catch (_: Exception) {
        null
    }

    /** DNS servers of the active network, for explicit LAN PTR lookups. */
    private fun dnsServers(): List<String> = try {
        val cm = getApplication<Application>().applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.getLinkProperties(cm.activeNetwork)?.dnsServers
            ?.mapNotNull { it.hostAddress }?.filter { it.isNotEmpty() } ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    fun run() {
        val st = _state.value
        val rawTarget = st.target.trim()
        // My IP, LAN sweep, Neighbor, Loop and WiFi Analyzer work without a
        // target (Loop uses the gateway, Neighbor listens, WiFi scans the air;
        // the WiFi target bar is an optional SSID/MAC filter).
        if (st.tool != Tool.MYIP && st.tool != Tool.SWEEP && st.tool != Tool.NEIGHBOR && st.tool != Tool.LOOP && st.tool != Tool.WIFIANALYZER && rawTarget.isEmpty()) {
            _state.update { it.copy(message = "Enter a target first (IP / host)") }
            return
        }
        if (st.running) return
        val parsed = TargetParser.parse(rawTarget)
        if (st.tool != Tool.MYIP && st.tool != Tool.SWEEP && st.tool != Tool.NEIGHBOR && st.tool != Tool.LOOP && st.tool != Tool.WIFIANALYZER && parsed.host.isEmpty()) {
            _state.update { it.copy(message = "Invalid target") }
            return
        }
        job?.cancel()
        val s = st.settings
        // Port list parsed once: the count goes into the header, the list into the run.
        val portList = PortChecker.parsePorts(s.portList)
        val backend = when (st.tool) {
            Tool.PING -> if (st.pingGlobal) "globalping x${st.globalProbes}" + globalWhere(st)
                else "system ping " + (if (s.pingCount > 0) "x${s.pingCount}" else "nonstop")
            Tool.DIG -> "dnsjava ${st.digType} via ${s.dnsServer}"
            Tool.TRACE -> if (st.traceGlobal) "globalping trace x${st.globalProbes}" + globalWhere(st)
                else "system traceroute if present, else TTL-ping"
            Tool.WHOIS -> "RDAP + WHOIS port 43"
            Tool.IPINFO -> s.ipLookupBase
            Tool.MYIP -> s.myIpBase
            Tool.PORTS -> if (st.portsGlobal) "internetdb"
                else if (parsed.port != null) "TCP connect" else "TCP connect ${portList.size} ports"
            Tool.CERT -> "TLS handshake"
            Tool.HEADERS -> "HTTP GET"
            Tool.SWEEP -> "ping sweep"
            Tool.NEIGHBOR -> "mndp/mdns/ssdp"
            Tool.LOOP -> "loop " + when (st.loopMode) {
                LoopRunner.LoopMode.L2_ONLY -> "L2 storm check"
                LoopRunner.LoopMode.L3_ONLY -> "L3 loop trace"
                LoopRunner.LoopMode.BOTH -> "L2+L3"
            }
            Tool.WIFIANALYZER -> "wifi scan ${WifiAnalyzerRunner.REFRESH_MS / 1000}s"
        }
        // The blue "== ... ==" line is the single intro: it already carries tool,
        // target, backend and time, so per-runner echo lines are dropped in collect().
        // Heads-up extras that don't fit elsewhere are merged here: Dig record type
        // (backend), port count (backend), Cert/Ports :port (target), full URL (Headers).
        val portSuffix =
            if ((st.tool == Tool.CERT || st.tool == Tool.PORTS) && parsed.port != null) ":${parsed.port}" else ""
        val headerTarget = when (st.tool) {
            Tool.SWEEP -> rawTarget.ifEmpty { "auto /24" }
            Tool.NEIGHBOR -> "LAN broadcast"
            Tool.LOOP -> rawTarget.ifEmpty { "auto gateway" }
            Tool.WIFIANALYZER -> rawTarget.ifEmpty { "all APs" }
            Tool.HEADERS -> rawTarget.ifEmpty { "this device" }
            Tool.MYIP -> "this device"
            else -> parsed.host.ifEmpty { "this device" } + portSuffix
        }
        val header = "== ${st.tool.title} $headerTarget [via $backend] " +
            SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()) + " =="
        _state.update { it.copy(lines = ((if (s.autoClearOutput) emptyList() else it.lines) + header).takeLast(2000), loopVerdict = null, stormVerdict = null, running = true, progress = "Starting...", startedAt = System.currentTimeMillis(), lastRefreshAt = if (st.tool == Tool.WIFIANALYZER) 0L else it.lastRefreshAt, wifiScanning = st.tool == Tool.WIFIANALYZER, wifiCycles = 0, wifiConnBssid = "", dropExpanded = false) }
        if (st.tool != Tool.SWEEP && st.tool != Tool.WIFIANALYZER && parsed.host.isNotEmpty()) viewModelScope.launch { repo.pushRecent(parsed.host, _state.value.settings.maxRecent) }
        // Remember the used target for the next startup. My IP ignores the
        // target bar, so it never overwrites; empty sweep keeps the old one;
        // WiFi SSID/MAC filter lives in wifi_filter (saved in setTarget/setTool).
        if (st.tool != Tool.MYIP && st.tool != Tool.WIFIANALYZER && rawTarget.isNotEmpty()) {
            hostTarget = rawTarget
            viewModelScope.launch { repo.saveLastTarget(rawTarget) }
        }

        val onProgress: (String) -> Unit = { msg -> _state.update { it.copy(progress = msg) } }
        val flow = when (st.tool) {
            Tool.PING -> if (st.pingGlobal) GlobalpingRunner.ping(parsed.host, st.globalProbes, st.globalCountry, s.globalpingToken, onProgress)
                else PingRunner.ping(parsed.host, s.pingCount)
            Tool.DIG -> DnsRunner.lookup(parsed.host, st.digType, s.dnsServer, s.timeoutMs)
            Tool.TRACE -> if (st.traceGlobal) GlobalpingRunner.trace(parsed.host, st.globalProbes, st.globalCountry, s.globalpingToken, onProgress)
                else TraceRunner.traceroute(parsed.host, s.maxHops, onProgress)
            Tool.WHOIS -> WhoisRdapClient.lookup(parsed.host, s.rdapBase, s.whoisServer, s.whoisPort, s.timeoutMs)
            Tool.IPINFO -> IpInfoClient.lookup(parsed.host, s.ipLookupBase, s.ipinfoToken)
            Tool.MYIP -> IpInfoClient.lookup("", s.myIpBase, s.ipinfoToken)
            Tool.PORTS -> if (st.portsGlobal) InternetDbClient.lookup(parsed.host)
                else PortChecker.check(parsed.host, parsed.port, s.timeoutMs, portList)
            Tool.CERT -> CertChecker.fetch(parsed.host, parsed.port ?: 443, s.timeoutMs)
            Tool.HEADERS -> HttpHeadersFetcher.fetch(rawTarget, s.timeoutMs)
            Tool.SWEEP -> IpScan.sweep(rawTarget, s.timeoutMs, onProgress, s.maxParallel, s.scanShowOffline, s.scanShowMac, dnsServers())
            Tool.NEIGHBOR -> NeighborRunner.discover(wifiManager(), onProgress, s.timeoutMs)
            Tool.LOOP -> LoopRunner.run(rawTarget, st.loopMode, s.maxHops, s.timeoutMs, s.loopPingCount, onProgress)
            Tool.WIFIANALYZER -> WifiAnalyzerRunner.scan(
                wifi = wifiManager(),
                filters = { wifiFilters() },
                refresh = wifiRefreshTick,
                onScanStart = { _state.update { it.copy(wifiScanning = true) } },
                onScanDone = {
                    _state.update { it.copy(lastRefreshAt = System.currentTimeMillis(), wifiCycles = it.wifiCycles + 1, wifiScanning = false) }
                },
                onChannels = { ch -> _state.update { it.copy(wifiChannels = ch) } },
                onConnected = { bssid -> _state.update { it.copy(wifiConnBssid = bssid) } }
            )
        }
        job = viewModelScope.launch {
            flow.catch { e -> _state.update { it.copy(lines = it.lines + "ERROR: ${e.message}") } }
                .collect { line ->
                    if (isEchoLine(st.tool, st, line)) return@collect
                    // Loop verdict lines feed the banners above the console.
                    val loopVerdict = if (st.tool == Tool.LOOP) LoopDetector.verdictOfLine(line) else null
                    val stormVerdict = if (st.tool == Tool.LOOP) StormDetector.verdictOfLine(line) else null
                    // Clear the WiFi "0 APs / no match" notice once a later
                    // cycle shows results again (they are plain lines, not LIVE).
                    _state.update { cur ->
                        // REMOVE drops the stored LIVE line with that key;
                        // LIVE lines ("key\ntext") replace in place; plain append.
                        when {
                            line.startsWith(GlobalpingRunner.REMOVE) -> {
                                val liveKey =
                                    "${GlobalpingRunner.LIVE}${line.substringAfter(GlobalpingRunner.REMOVE)}"
                                cur.copy(lines = cur.lines.filterNot { it.startsWith("$liveKey\n") })
                            }
                            line.startsWith(GlobalpingRunner.LIVE) -> {
                                val key = line.substringBefore('\n')
                                val idx = cur.lines.indexOfLast { l -> l.startsWith("$key\n") }
                                val next = if (idx >= 0) {
                                    cur.lines.toMutableList().also { it[idx] = line }
                                } else {
                                    cur.lines + line
                                }
                                cur.copy(lines = next.takeLast(2000))
                            }
                            else -> {
                                var lines = cur.lines
                                if (st.tool == Tool.WIFIANALYZER &&
                                    (line.startsWith(";; 0 APs") ||
                                        (line.startsWith(";; ") && line.contains("APs on air")))
                                ) {
                                    // One AP-count notice at a time: drop the previous.
                                    lines = lines.filterNot {
                                        it.startsWith(";; 0 APs") ||
                                            (it.startsWith(";; ") && it.contains("APs on air"))
                                    }
                                }
                                cur.copy(
                                    lines = (lines + line).takeLast(2000),
                                    loopVerdict = loopVerdict ?: cur.loopVerdict,
                                    stormVerdict = stormVerdict ?: cur.stormVerdict
                                )
                            }
                        }
                    }
                }
            _state.update { it.copy(running = false, progress = null, lastRefreshAt = 0L, wifiScanning = false) }
        }
    }

    fun outputText(): String = _state.value.lines.joinToString("\n") { GlobalpingRunner.displayOf(it) }

    /** Runner intro lines that only repeat what the blue "== ... ==" header
     *  already says (tool + target + backend). Dropped so each run opens with
     *  a single header line. Lines carrying unique info (resolved IPs, counts,
     *  warnings, errors) never match and are kept. */
    private fun isEchoLine(tool: Tool, st: HomeUiState, line: String): Boolean {
        if (line.startsWith(GlobalpingRunner.LIVE) || line.startsWith(GlobalpingRunner.REMOVE)) return false
        if ((tool == Tool.PING && st.pingGlobal) || (tool == Tool.TRACE && st.traceGlobal))
            return line.startsWith(";; global ")
        return when (tool) {
            Tool.PING -> line.startsWith("PING ")
            Tool.DIG -> line.startsWith("; DiG via ")
            Tool.TRACE -> line.startsWith("traceroute to ")
            Tool.WHOIS -> line.startsWith(";; whois/rdap for ")
            Tool.IPINFO, Tool.MYIP -> line.startsWith(";; GET ") ||
                line.startsWith(";; public IP of this device") ||
                line.startsWith(";; note: this provider only")
            Tool.HEADERS -> line.startsWith(";; HEADERS ")
            Tool.PORTS -> !st.portsGlobal && line.startsWith(";; checking ")
            Tool.CERT -> line.startsWith(";; TLS certificate for ")
            Tool.SWEEP -> line.startsWith(";; IP scan on ")
            Tool.NEIGHBOR -> line.startsWith(";; MNDP discovery ") ||
                line.startsWith(";; mDNS discovery ") ||
                line.startsWith(";; SSDP discovery ")
            Tool.LOOP -> false
            Tool.WIFIANALYZER -> line.startsWith(";; refresh every")
        }
    }

    private fun globalWhere(st: HomeUiState): String =
        if (st.globalCountry.isEmpty()) " worldwide" else " ${st.globalCountry}"

}
